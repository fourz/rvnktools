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
        PERSIST_FAILED
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
     */
    private final Map<String, PortalDTO> index = new ConcurrentHashMap<>();

    /** Portal-id -> portal, so a framed portal can be removed by its (sign-stamped) id in one call. */
    private final Map<String, PortalDTO> byId = new ConcurrentHashMap<>();

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

        index.clear();
        byId.clear();
        List<PortalDTO> portals = repository.listAll();
        for (PortalDTO portal : portals) {
            byId.put(portal.getPortalId(), portal);
            List<int[]> blocks = portal.getPortalBlocks();
            if (blocks == null || blocks.isEmpty()) {
                // Legacy single-block portal: the trigger is the anchor block itself.
                index.put(locationKey(portal.getWorld(), portal.getX(), portal.getY(), portal.getZ()), portal);
            } else {
                for (int[] b : blocks) {
                    index.put(locationKey(portal.getWorld(), b[0], b[1], b[2]), portal);
                }
            }
        }
        logger.info("Portal index loaded: " + byId.size() + " portal(s), " + index.size()
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
        PortalDTO portal = byId.remove(portalId);
        if (portal == null) {
            return false;
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
