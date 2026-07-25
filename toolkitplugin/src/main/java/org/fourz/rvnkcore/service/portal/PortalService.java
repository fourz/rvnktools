package org.fourz.rvnkcore.service.portal;

import org.bukkit.Material;
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
    private final PortalConfig config;
    private final PortalRepository repository;
    private final LogManager logger;

    /** In-memory index: {@code world:x:y:z} -> portal. Authoritative for runtime lookups. */
    private final Map<String, PortalDTO> index = new ConcurrentHashMap<>();

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
        List<PortalDTO> portals = repository.listAll();
        for (PortalDTO portal : portals) {
            index.put(locationKey(portal.getWorld(), portal.getX(), portal.getY(), portal.getZ()), portal);
        }
        logger.info("Portal index loaded: " + index.size() + " portal(s) (enabled=" + config.isEnabled() + ")");
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
     * @return the number of portals currently held in the in-memory index
     */
    public int getPortalCount() {
        return index.size();
    }

    /**
     * Clears the in-memory index. Called on disable.
     */
    public void clear() {
        index.clear();
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
