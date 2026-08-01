package org.fourz.rvnkcore.service.portal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.database.repository.PortalRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime service for cross-server portals.
 *
 * <p>Registered in the ServiceRegistry (mirroring {@code TransferService} / {@code ChatRelayService}).
 * Holds the {@link PortalConfig}, the {@link PortalRepository}, and an authoritative <b>in-memory
 * index</b> keyed by a {@code world:x:y:z} location string. The index is populated once from the
 * database on {@link #loadIndex()} (called on enable) and is the source of truth for runtime
 * step-detection lookups, so portals keep working even while the database is unreachable.</p>
 *
 * <p>Write operations update the index first (it is authoritative at runtime) and then attempt to
 * persist. A failed DB write is logged but does not roll back the index; the two layers resync on
 * the next {@link #loadIndex()} (e.g. a plugin reload after the DB recovers).</p>
 *
 * <p><b>Foundation scope:</b> data + config + service only. The block/sign listeners that call
 * {@link #isPortalBlock} and {@link #getPortal} are built separately (#1713/#1714).</p>
 *
 * @since 1.5.24
 */
public class PortalService {

    /** Outcome status of a portal create/delete request. */
    public enum Status {
        SUCCESS,
        DISABLED,
        ALREADY_EXISTS,
        NOT_FOUND,
        PERSIST_FAILED,
        /**
         * The portal exists but cannot be acted on right now — typically its world or chunk is not
         * loaded. Distinct from {@link #NOT_FOUND}: the portal is fine, we simply cannot reach it,
         * so a caller must not treat this as grounds for deleting anything (#1859).
         */
        UNAVAILABLE
    }

    /**
     * Result of a portal mutation request.
     *
     * @param status  The outcome status
     * @param message A human-readable message describing the outcome
     * @param portal  The affected portal, or null when none applies
     */
    public record PortalResult(Status status, String message, PortalDTO portal) {

        /** @return true when the mutation succeeded. */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    private final Plugin plugin;
    private volatile PortalConfig config;
    private final PortalRepository repository;
    private final LogManager logger;

    /**
     * In-memory index: {@code world:x:y:z} -> portal. Authoritative for runtime lookups. Every
     * interior portal block of a framed portal is registered here so walk-through detection is O(1).
     *
     * <p><b>volatile, and replaced wholesale by {@link #loadIndex()} rather than cleared in place.</b>
     * A reader on the main thread holds whichever map reference it read, so it always sees a complete
     * index — never a half-built one. Clearing in place would open a window where
     * {@code PortalStepListener} finds no portal, skips its {@code event.setCancelled(true)}, and
     * vanilla sends a player standing in a cross-server portal to the Nether instead.</p>
     */
    private volatile Map<String, PortalDTO> index = new ConcurrentHashMap<>();

    /**
     * Portal-id -> portal, so a framed portal can be removed by its (sign-stamped) id in one call.
     * Swapped atomically alongside {@link #index}; see that field for why.
     */
    private volatile Map<String, PortalDTO> byId = new ConcurrentHashMap<>();

    /** Shared frame filler/clearer, used to return interior blocks to AIR on delete. */
    private final PortalFrameBuilder frameBuilder = new PortalFrameBuilder();

    /**
     * Creates a new PortalService.
     *
     * @param plugin     The owning plugin
     * @param config     Portal configuration
     * @param repository Portal repository (backed by the shared connection provider)
     * @param logger     LogManager instance
     */
    public PortalService(Plugin plugin, PortalConfig config, PortalRepository repository, LogManager logger) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.logger = logger;
    }

    /**
     * Ensures the schema exists and loads the in-memory index from the database.
     *
     * <p>Call once on enable, after the connection provider is ready. On a database failure the
     * schema step is logged and the index is left empty so the plugin can still start (degraded).</p>
     */
    public void loadIndex() {
        try {
            repository.ensureSchema();
        } catch (Exception e) {
            logger.error("Portal schema could not be ensured; starting with an empty index", e);
        }

        // Build into fresh maps, then publish both by reference. Never clear the live maps: a reader
        // on the main thread must see either the whole old index or the whole new one. Clearing in
        // place let PortalStepListener miss a portal mid-reload and fall through to vanilla nether
        // travel (Copilot review, rvnktools#41).
        Map<String, PortalDTO> newIndex = new ConcurrentHashMap<>();
        Map<String, PortalDTO> newById = new ConcurrentHashMap<>();

        List<PortalDTO> portals = repository.listAll();
        for (PortalDTO portal : portals) {
            newById.put(portal.getPortalId(), portal);
            List<int[]> blocks = portal.getPortalBlocks();
            if (blocks == null || blocks.isEmpty()) {
                // Legacy single-block portal: the trigger is the anchor block itself.
                newIndex.put(locationKey(portal.getWorld(), portal.getX(), portal.getY(), portal.getZ()), portal);
            } else {
                for (int[] b : blocks) {
                    newIndex.put(locationKey(portal.getWorld(), b[0], b[1], b[2]), portal);
                }
            }
        }

        // Publish. byId first, then index: index is what gates the vanilla-cancel, so it is the last
        // thing to change and is never live before its portals are resolvable by id.
        this.byId = newById;
        this.index = newIndex;

        logger.info("Portal index loaded: " + newById.size() + " portal(s), " + newIndex.size()
                + " portal block(s) (enabled=" + config.isEnabled() + ")");
    }

    /**
     * Tests whether a block location is a registered portal trigger block.
     *
     * <p>Pure in-memory check — safe to call on the main thread and during a DB outage.</p>
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return true if a portal is registered at this location
     */
    public boolean isPortalBlock(String world, int x, int y, int z) {
        return index.containsKey(locationKey(world, x, y, z));
    }

    /**
     * Returns the portal registered at a block location, if any.
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return the portal at this location, or empty
     */
    public Optional<PortalDTO> getPortal(String world, int x, int y, int z) {
        return Optional.ofNullable(index.get(locationKey(world, x, y, z)));
    }

    /**
     * Returns a portal by its id (used to re-heal a portal sign's display).
     *
     * @param portalId The portal id
     * @return the portal, or empty
     */
    public Optional<PortalDTO> getPortalById(String portalId) {
        return Optional.ofNullable(byId.get(portalId));
    }

    /**
     * Creates a portal at a block location and persists it.
     *
     * <p>Updates the in-memory index (authoritative) and then attempts to persist. If the DB write
     * fails the index still holds the portal and {@link Status#PERSIST_FAILED} is returned so the
     * caller can surface a warning; the row will be written on the next successful reload path.</p>
     *
     * @param world        The world name
     * @param x            Block X
     * @param y            Block Y
     * @param z            Block Z
     * @param targetServer The target server name (resolved later by TransferService)
     * @param ownerUuid    The creating player's UUID string (may be null)
     * @return a {@link PortalResult} describing the outcome
     */
    public PortalResult createPortal(String world, int x, int y, int z, String targetServer, String ownerUuid) {
        if (!config.isEnabled()) {
            return new PortalResult(Status.DISABLED, "Cross-server portals are disabled on this server.", null);
        }

        String key = locationKey(world, x, y, z);
        if (index.containsKey(key)) {
            return new PortalResult(Status.ALREADY_EXISTS,
                    "A portal already exists at that location.", index.get(key));
        }

        PortalDTO portal = new PortalDTO(
                UUID.randomUUID().toString(), world, x, y, z, targetServer, ownerUuid,
                System.currentTimeMillis());

        // Index is source of truth at runtime — update it first so detection works immediately.
        index.put(key, portal);
        byId.put(portal.getPortalId(), portal);

        boolean persisted = repository.create(portal);
        if (!persisted) {
            logger.warning("Portal " + portal.getPortalId()
                    + " added to in-memory index but failed to persist — will resync on reload");
            return new PortalResult(Status.PERSIST_FAILED,
                    "Portal created in memory but could not be saved — check the database.", portal);
        }

        logger.info("Portal created at " + key + " -> '" + targetServer + "' by " + ownerUuid);
        return new PortalResult(Status.SUCCESS, "Portal created targeting '" + targetServer + "'.", portal);
    }

    /**
     * Creates a multi-block framed portal and registers every interior block for walk-through
     * detection.
     *
     * <p>The {@code anchor} coordinates identify the frame block the registration sign is mounted on
     * (the row identity); {@code interior} is the list of enclosed {@code NETHER_PORTAL} block
     * locations that trigger the transfer. All interior blocks are added to the in-memory index
     * (authoritative) before the DB write, so detection works immediately even if persistence
     * fails. The caller is responsible for filling the interior with portal material.</p>
     *
     * @param world        The world name
     * @param anchorX      Anchor (sign mount) block X
     * @param anchorY      Anchor block Y
     * @param anchorZ      Anchor block Z
     * @param targetServer The target server name (resolved later by TransferService)
     * @param ownerUuid    The creating player's UUID string (may be null)
     * @param interior     Interior portal-block locations as {@code int[]{x, y, z}} (must be non-empty)
     * @return a {@link PortalResult} describing the outcome
     */
    public PortalResult createFramePortal(String world, int anchorX, int anchorY, int anchorZ,
                                          String targetServer, String ownerUuid, List<int[]> interior) {
        if (!config.isEnabled()) {
            return new PortalResult(Status.DISABLED, "Cross-server portals are disabled on this server.", null);
        }
        if (interior == null || interior.isEmpty()) {
            return new PortalResult(Status.NOT_FOUND, "No portal interior to register.", null);
        }

        // Reject if any interior block already belongs to a registered portal.
        for (int[] b : interior) {
            String key = locationKey(world, b[0], b[1], b[2]);
            if (index.containsKey(key)) {
                return new PortalResult(Status.ALREADY_EXISTS,
                        "A portal already occupies that space.", index.get(key));
            }
        }

        PortalDTO portal = new PortalDTO(
                UUID.randomUUID().toString(), world, anchorX, anchorY, anchorZ, targetServer, ownerUuid,
                System.currentTimeMillis());
        portal.setPortalBlocks(interior);

        // Index every interior block first (source of truth at runtime).
        byId.put(portal.getPortalId(), portal);
        for (int[] b : interior) {
            index.put(locationKey(world, b[0], b[1], b[2]), portal);
        }

        boolean persisted = repository.create(portal);
        if (!persisted) {
            logger.warning("Framed portal " + portal.getPortalId()
                    + " added to in-memory index but failed to persist — will resync on reload");
            return new PortalResult(Status.PERSIST_FAILED,
                    "Portal lit in memory but could not be saved — check the database.", portal);
        }

        logger.info("Framed portal created (" + interior.size() + " blocks) in " + world
                + " anchor " + anchorX + "," + anchorY + "," + anchorZ
                + " -> '" + targetServer + "' by " + ownerUuid);
        return new PortalResult(Status.SUCCESS, "Portal lit, targeting '" + targetServer + "'.", portal);
    }

    /**
     * Deletes a portal by its id: removes every index entry, returns its interior
     * {@code NETHER_PORTAL} blocks to {@code AIR}, and deletes the persisted row.
     *
     * @param portalId The portal id (as stamped on the registration sign)
     * @return true if a portal with that id was present and removed
     */
    public boolean deletePortalById(String portalId) {
        return deletePortalById(portalId, true);
    }

    /**
     * Deletes a portal by id, optionally stripping its registration sign back to a plain sign.
     *
     * @param portalId  The portal id (as stamped on the registration sign)
     * @param clearSign true to blank the sign and remove its portal-id stamp. Pass false when the
     *                  sign is already being destroyed (the block-break path), where clearing it
     *                  would be pointless work on a block about to become air.
     * @return true if a portal with that id was present and removed
     */
    public boolean deletePortalById(String portalId, boolean clearSign) {
        PortalDTO portal = byId.remove(portalId);
        if (portal == null) {
            return false;
        }

        if (clearSign) {
            clearRegistrationSign(portal, portalId);
        }

        World world = Bukkit.getWorld(portal.getWorld());
        List<int[]> blocks = portal.getPortalBlocks();
        if (blocks == null || blocks.isEmpty()) {
            // Legacy single-block portal.
            index.remove(locationKey(portal.getWorld(), portal.getX(), portal.getY(), portal.getZ()));
        } else {
            for (int[] b : blocks) {
                index.remove(locationKey(portal.getWorld(), b[0], b[1], b[2]));
                if (world != null) {
                    Block blk = world.getBlockAt(b[0], b[1], b[2]);
                    if (blk.getType() == Material.NETHER_PORTAL) {
                        blk.setType(Material.AIR, false);
                    }
                }
            }
        }

        boolean persisted = repository.deleteById(portalId);
        if (!persisted) {
            logger.warning("Portal " + portalId
                    + " removed from in-memory index but DB delete did not confirm — will resync on reload");
        } else {
            logger.info("Portal deleted (id " + portalId + ")");
        }
        return true;
    }

    /**
     * Strips a deleted portal's registration sign back to a plain sign.
     *
     * <p>A sign left carrying the stamp of a portal that no longer exists is <b>inert</b>: the
     * sign-change handler short-circuits on the stamp, so the sign can be neither re-registered nor
     * edited into anything else, and the block-break handler demands delete permission to remove
     * it. Clearing the stamp on delete is what keeps the sign reusable.</p>
     *
     * <p>Best-effort: an unloaded world or chunk is skipped rather than force-loaded. Any sign left
     * behind that way is recovered by the sign-change handler, which clears a stale stamp on the
     * next edit.</p>
     */
    private void clearRegistrationSign(PortalDTO portal, String portalId) {
        World world = Bukkit.getWorld(portal.getWorld());
        if (world == null || !world.isChunkLoaded(portal.getX() >> 4, portal.getZ() >> 4)) {
            logger.debug("Portal " + portalId + " sign not cleared — world or chunk not loaded");
            return;
        }
        Block anchor = world.getBlockAt(portal.getX(), portal.getY(), portal.getZ());
        PortalSignWriter.findSignOnAnchor(anchor, PortalSignWriter.portalIdKey(plugin), portalId)
                .ifPresent(signBlock -> PortalSignWriter.clearStamp(
                        signBlock, PortalSignWriter.portalIdKey(plugin)));
    }

    /**
     * Deletes the portal at a block location from the index and the database.
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return true if a portal was present at that location and removed from the index
     */
    public boolean deletePortal(String world, int x, int y, int z) {
        String key = locationKey(world, x, y, z);
        PortalDTO removed = index.remove(key);
        if (removed == null) {
            return false;
        }
        byId.remove(removed.getPortalId());

        boolean persisted = repository.deleteByLocation(world, x, y, z);
        if (!persisted) {
            logger.warning("Portal at " + key
                    + " removed from in-memory index but DB delete did not confirm — will resync on reload");
        } else {
            logger.info("Portal deleted at " + key);
        }
        return true;
    }

    /**
     * Result of checking a registered portal against the actual world (#1859, #1860).
     *
     * <p>{@link #UNVERIFIED} is deliberately distinct from {@link #ORPHANED}: a portal in an
     * unloaded world or chunk cannot be inspected without force-loading it, and reporting
     * "not currently readable" as "gone" would condemn healthy portals. Callers must treat the two
     * differently — in particular nothing should ever be auto-deleted on UNVERIFIED.</p>
     */
    public enum Verification {
        /** Trigger block and a correctly-stamped sign are both present. */
        VERIFIED,
        /** The world or chunk is not loaded, so the portal could not be checked. */
        UNVERIFIED,
        /** The trigger block or the stamped sign is missing — the row no longer matches the world. */
        ORPHANED,
        /** No portal is registered under that id. */
        UNKNOWN
    }

    /**
     * Returns every registered portal, ordered by world then coordinates.
     *
     * <p>Served from the in-memory index, which is authoritative for runtime lookups, so this is
     * safe to call on the main thread and keeps working during a database outage.</p>
     *
     * @return all registered portals; never null
     */
    public List<PortalDTO> listPortals() {
        List<PortalDTO> portals = new java.util.ArrayList<>(byId.values());
        portals.sort(java.util.Comparator
                .comparing(PortalDTO::getWorld, java.util.Comparator.nullsLast(String::compareTo))
                .thenComparingInt(PortalDTO::getX)
                .thenComparingInt(PortalDTO::getY)
                .thenComparingInt(PortalDTO::getZ));
        return portals;
    }

    /**
     * Returns registered portals in one world.
     *
     * @param world the world name; null or blank returns every portal
     * @return matching portals; never null
     */
    public List<PortalDTO> listPortalsInWorld(String world) {
        if (world == null || world.isBlank()) {
            return listPortals();
        }
        List<PortalDTO> portals = new java.util.ArrayList<>();
        for (PortalDTO portal : listPortals()) {
            if (world.equalsIgnoreCase(portal.getWorld())) {
                portals.add(portal);
            }
        }
        return portals;
    }

    /**
     * Resolves a portal by a full id or an unambiguous id prefix.
     *
     * <p>Portal signs display only the first eight characters of the id, so an operator reading a
     * sign has a prefix rather than the full UUID. An ambiguous prefix resolves to empty rather
     * than picking one — guessing which portal an admin meant is how the wrong one gets deleted.</p>
     *
     * @param idOrPrefix the full portal id, or a prefix of it
     * @return the single matching portal, or empty when nothing or more than one matches
     */
    public Optional<PortalDTO> resolvePortal(String idOrPrefix) {
        if (idOrPrefix == null || idOrPrefix.isBlank()) {
            return Optional.empty();
        }
        PortalDTO exact = byId.get(idOrPrefix);
        if (exact != null) {
            return Optional.of(exact);
        }
        String prefix = idOrPrefix.toLowerCase(java.util.Locale.ROOT);
        PortalDTO match = null;
        for (Map.Entry<String, PortalDTO> entry : byId.entrySet()) {
            if (entry.getKey().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                if (match != null) {
                    return Optional.empty(); // ambiguous
                }
                match = entry.getValue();
            }
        }
        return Optional.ofNullable(match);
    }

    /**
     * Checks a registered portal against the world: is its trigger block still the configured
     * material, and is a sign carrying its id still mounted on it.
     *
     * <p>Never force-loads a world or chunk — an unloaded portal reports {@link Verification#UNVERIFIED}.
     * Must be called on the main thread (it reads block state).</p>
     *
     * @param portalId the portal id
     * @return the verification outcome
     */
    public Verification verify(String portalId) {
        PortalDTO portal = byId.get(portalId);
        if (portal == null) {
            return Verification.UNKNOWN;
        }

        World world = Bukkit.getWorld(portal.getWorld());
        if (world == null) {
            return Verification.UNVERIFIED;
        }
        if (!world.isChunkLoaded(portal.getX() >> 4, portal.getZ() >> 4)) {
            return Verification.UNVERIFIED;
        }

        Block anchor = world.getBlockAt(portal.getX(), portal.getY(), portal.getZ());
        Material triggerMaterial = config.getTriggerMaterial();
        if (triggerMaterial != null && anchor.getType() != triggerMaterial) {
            return Verification.ORPHANED;
        }

        return PortalSignWriter
                .findSignOnAnchor(anchor, PortalSignWriter.portalIdKey(plugin), portalId)
                .isPresent()
                ? Verification.VERIFIED
                : Verification.ORPHANED;
    }

    /**
     * Rewrites and re-stamps the registration sign for an existing portal.
     *
     * <p>Recovers a portal whose sign text was overwritten or whose PDC stamp was lost — for
     * example after a rollback or a schematic paste, which restores the sign block without its
     * persistent data (#1614). Requires a sign still mounted on the anchor; it will not place one,
     * because choosing a face and material on an admin's behalf is a build decision, not a repair.</p>
     *
     * <p>Must be called on the main thread.</p>
     *
     * @param portalId        the portal id
     * @param transferService the transfer service used to render the destination line; may be null
     * @return a result describing the outcome
     */
    public PortalResult repairSign(String portalId, org.fourz.rvnkcore.service.transfer.TransferService transferService) {
        PortalDTO portal = byId.get(portalId);
        if (portal == null) {
            return new PortalResult(Status.NOT_FOUND, "No portal registered with id " + portalId, null);
        }

        World world = Bukkit.getWorld(portal.getWorld());
        if (world == null) {
            return new PortalResult(Status.UNAVAILABLE, "World '" + portal.getWorld() + "' is not loaded", portal);
        }
        if (!world.isChunkLoaded(portal.getX() >> 4, portal.getZ() >> 4)) {
            return new PortalResult(Status.UNAVAILABLE,
                    "Chunk at " + portal.getX() + "," + portal.getZ() + " is not loaded", portal);
        }

        Block anchor = world.getBlockAt(portal.getX(), portal.getY(), portal.getZ());
        // Accept any sign mounted on the anchor — the whole point of a repair is that the stamp may
        // be missing, so matching on the id here would reject exactly the case we came to fix.
        Optional<Block> signBlock = PortalSignWriter
                .findSignOnAnchor(anchor, PortalSignWriter.portalIdKey(plugin), null);
        if (signBlock.isEmpty()) {
            return new PortalResult(Status.NOT_FOUND,
                    "No sign is mounted on this portal's anchor block — place one, then repair again", portal);
        }

        String[] lines = PortalSignWriter.buildDisplayLines(
                transferService, portal.getTargetServer(), portalId, config.getSignHeader());
        boolean written = PortalSignWriter.write(
                signBlock.get(), PortalSignWriter.portalIdKey(plugin), portalId, lines);
        if (!written) {
            return new PortalResult(Status.UNAVAILABLE, "Block is no longer a sign", portal);
        }

        logger.info("Portal sign repaired (id " + portalId + ") at " + portal.getWorld() + " "
                + portal.getX() + "," + portal.getY() + "," + portal.getZ());
        return new PortalResult(Status.SUCCESS, "Portal sign repaired", portal);
    }

    /**
     * @return the resolved trigger {@link Material}, or null when the configured block is invalid
     */
    public Material getTriggerMaterial() {
        return config.getTriggerMaterial();
    }

    /**
     * @return the backing portal configuration
     */
    public PortalConfig getConfig() {
        return config;
    }

    /**
     * Swaps in a freshly-parsed configuration (e.g. from {@code /rvnkcore reload}) so trigger-block /
     * sign-header / permission changes take effect without a restart (#1743). No-op on null. The
     * in-memory portal index is unaffected (existing portals stay registered).
     *
     * @param newConfig the new portal configuration
     */
    public void refreshConfig(PortalConfig newConfig) {
        if (newConfig == null) return;
        this.config = newConfig;
        logger.info("PortalService config refreshed — trigger=" + config.getTriggerBlock()
            + ", header=" + config.getSignHeader());
    }

    /**
     * @return the number of logical portals currently held in the in-memory index
     */
    public int getPortalCount() {
        return byId.size();
    }

    /**
     * @return the shared frame builder used to fill/clear portal interiors
     */
    public PortalFrameBuilder getFrameBuilder() {
        return frameBuilder;
    }

    /**
     * Clears the in-memory index. Called on disable.
     */
    public void clear() {
        index.clear();
        byId.clear();
    }

    /**
     * Builds the index key for a block location.
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return a {@code world:x:y:z} key
     */
    private String locationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
