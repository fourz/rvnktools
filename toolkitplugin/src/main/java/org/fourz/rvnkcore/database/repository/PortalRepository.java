package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for cross-server portal storage.
 * Table: {@code rvnk_cross_server_portal}.
 *
 * <p>Rides RVNKCore's MySQL-&gt;SQLite fallback like the sibling world-data repositories: it takes a
 * {@link ConnectionProvider} (which resolves to MySQL or SQLite per config) and branches its DDL on
 * {@link ConnectionProvider#getDatabaseType()} exactly as
 * {@link org.fourz.rvnkcore.database.schema.DatabaseSetup} does. Schema creation is self-contained
 * (guarded {@code CREATE TABLE IF NOT EXISTS} + a lookup index) so the portal feature does not
 * depend on the central schema pass.</p>
 *
 * <p>Structured as a plain repository mirroring
 * {@link org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository} (a {@link ConnectionProvider}
 * + {@link LogManager} holder). It deliberately does not extend {@link BaseRepository}: that base
 * declares {@code deleteById(ID)} which, with a {@code String} id, would collide with this
 * repository's required {@code boolean deleteById(String)} signature.</p>
 *
 * <p><b>Two-layer resilience:</b> (1) every DB operation records success/failure on a
 * {@link FallbackTracker} so repeated MySQL failures degrade gracefully; (2) the authoritative
 * runtime lookup index lives in {@link org.fourz.rvnkcore.service.portal.PortalService}, so
 * step-detection keeps working even while the database is unreachable.</p>
 *
 * @since 1.5.24
 */
public class PortalRepository {

    /** Base table name for cross-server portals. */
    public static final String TABLE_NAME = "rvnk_cross_server_portal";

    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private final FallbackTracker fallbackTracker;

    /**
     * Creates a new PortalRepository.
     *
     * @param connectionProvider The shared database connection provider (MySQL or SQLite per config)
     * @param plugin             The owning plugin, for logging
     */
    public PortalRepository(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());
        // 3 consecutive failures trip fallback; a 30s recovery window mirrors the conservative
        // defaults used elsewhere. The service's in-memory index is the real runtime safety net.
        this.fallbackTracker = new FallbackTracker(3, 30_000L, logger);
    }

    /**
     * Ensures the portal table and its location lookup index exist.
     *
     * <p>Dialect-safe: uses only column types valid on both MySQL and SQLite and guards every
     * statement with {@code IF NOT EXISTS}. Called once on service enable, after the connection
     * provider is ready.</p>
     *
     * @throws SQLException if the schema cannot be created
     */
    public void ensureSchema() throws SQLException {
        boolean mysql = "MySQL".equalsIgnoreCase(connectionProvider.getDatabaseType());

        String createTable;
        if (mysql) {
            createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "portal_id VARCHAR(36) PRIMARY KEY, " +
                    "world VARCHAR(255) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "y INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "target_server VARCHAR(255) NOT NULL, " +
                    "owner_uuid VARCHAR(36), " +
                    "created_at BIGINT NOT NULL DEFAULT 0, " +
                    "portal_blocks TEXT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "portal_id TEXT PRIMARY KEY, " +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "target_server TEXT NOT NULL, " +
                    "owner_uuid TEXT, " +
                    "created_at INTEGER NOT NULL DEFAULT 0, " +
                    "portal_blocks TEXT" +
                    ")";
        }

        // Lookup index on (world,x,y,z) — the hot path for step-detection when the index is cold.
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_cross_server_portal_location ON "
                + TABLE_NAME + " (world, x, y, z)";

        try (Connection conn = connectionProvider.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(createIndex);
            // Migrate pre-#1709 tables that predate the framed-portal block list. Neither dialect can
            // rely on "ADD COLUMN IF NOT EXISTS" (SQLite has no such clause; stock MySQL rejects it),
            // so probe the column first and add it only when absent.
            if (!columnExists(conn, mysql, "portal_blocks")) {
                stmt.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN portal_blocks TEXT");
                logger.info("Portal schema migrated: added portal_blocks column");
            }
            fallbackTracker.recordSuccess();
            logger.info("Portal schema ensured (" + (mysql ? "MySQL" : "SQLite") + ")");
        } catch (SQLException e) {
            fallbackTracker.recordFailure("ensureSchema: " + e.getMessage());
            logger.error("Failed to ensure portal schema", e);
            throw e;
        }
    }

    /**
     * Tests whether a column already exists on {@link #TABLE_NAME}.
     *
     * <p>Dialect-safe probe used by the guarded migration: SQLite reports columns via
     * {@code PRAGMA table_info}; MySQL via {@code information_schema.COLUMNS} scoped to the current
     * schema. Both avoid the non-portable {@code ADD COLUMN IF NOT EXISTS} clause.</p>
     *
     * @param conn   an open connection to reuse
     * @param mysql  true when the active dialect is MySQL
     * @param column the column name to test for
     * @return true if the column is present
     * @throws SQLException if the probe query fails
     */
    private boolean columnExists(Connection conn, boolean mysql, String column) throws SQLException {
        if (mysql) {
            String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, TABLE_NAME);
                stmt.setString(2, column);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement("PRAGMA table_info(" + TABLE_NAME + ")");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Persists a new portal row.
     *
     * @param portal The portal to insert
     * @return true if the row was inserted, false on database failure
     */
    public boolean create(PortalDTO portal) {
        String sql = "INSERT INTO " + TABLE_NAME
                + " (portal_id, world, x, y, z, target_server, owner_uuid, created_at, portal_blocks)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, portal.getPortalId());
            stmt.setString(2, portal.getWorld());
            stmt.setInt(3, portal.getX());
            stmt.setInt(4, portal.getY());
            stmt.setInt(5, portal.getZ());
            stmt.setString(6, portal.getTargetServer());
            stmt.setString(7, portal.getOwnerUuid());
            stmt.setLong(8, portal.getCreatedAt());
            stmt.setString(9, serializeBlocks(portal.getPortalBlocks()));
            stmt.executeUpdate();
            fallbackTracker.recordSuccess();
            return true;
        } catch (SQLException e) {
            fallbackTracker.recordFailure("create: " + e.getMessage());
            logger.error("Failed to insert portal " + portal.getPortalId(), e);
            return false;
        }
    }

    /**
     * Deletes a portal by its identifier.
     *
     * @param portalId The portal id
     * @return true if a row was deleted, false on failure or when nothing matched
     */
    public boolean deleteById(String portalId) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM " + TABLE_NAME + " WHERE portal_id = ?")) {
            stmt.setString(1, portalId);
            int rows = stmt.executeUpdate();
            fallbackTracker.recordSuccess();
            return rows > 0;
        } catch (SQLException e) {
            fallbackTracker.recordFailure("deleteById: " + e.getMessage());
            logger.error("Failed to delete portal " + portalId, e);
            return false;
        }
    }

    /**
     * Deletes a portal by its block location.
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return true if a row was deleted, false on failure or when nothing matched
     */
    public boolean deleteByLocation(String world, int x, int y, int z) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM " + TABLE_NAME + " WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            int rows = stmt.executeUpdate();
            fallbackTracker.recordSuccess();
            return rows > 0;
        } catch (SQLException e) {
            fallbackTracker.recordFailure("deleteByLocation: " + e.getMessage());
            logger.error("Failed to delete portal at " + world + " (" + x + "," + y + "," + z + ")", e);
            return false;
        }
    }

    /**
     * Looks up a portal by its block location.
     *
     * @param world The world name
     * @param x     Block X
     * @param y     Block Y
     * @param z     Block Z
     * @return the matching portal, or empty when none exists or a database failure occurs
     */
    public Optional<PortalDTO> getByLocation(String world, int x, int y, int z) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM " + TABLE_NAME + " WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            try (ResultSet rs = stmt.executeQuery()) {
                Optional<PortalDTO> result = rs.next() ? Optional.of(mapResultSet(rs)) : Optional.empty();
                fallbackTracker.recordSuccess();
                return result;
            }
        } catch (SQLException e) {
            fallbackTracker.recordFailure("getByLocation: " + e.getMessage());
            logger.error("Failed to query portal at " + world + " (" + x + "," + y + "," + z + ")", e);
            return Optional.empty();
        }
    }

    /**
     * Lists all persisted portals.
     *
     * <p>Used once on service enable to populate the in-memory index. Returns an empty list on
     * database failure so the service can still start (degraded, empty index).</p>
     *
     * @return all portals, or an empty list on failure
     */
    public List<PortalDTO> listAll() {
        List<PortalDTO> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM " + TABLE_NAME);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            fallbackTracker.recordSuccess();
        } catch (SQLException e) {
            fallbackTracker.recordFailure("listAll: " + e.getMessage());
            logger.error("Failed to list portals", e);
        }
        return results;
    }

    /**
     * @return the fallback tracker recording this repository's DB health
     */
    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    /**
     * Maps the current row of a ResultSet to a {@link PortalDTO}.
     *
     * @param rs The result set positioned at a row
     * @return the mapped portal
     * @throws SQLException if a column read fails
     */
    private PortalDTO mapResultSet(ResultSet rs) throws SQLException {
        PortalDTO portal = new PortalDTO(
                rs.getString("portal_id"),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getString("target_server"),
                rs.getString("owner_uuid"),
                rs.getLong("created_at")
        );
        portal.setPortalBlocks(deserializeBlocks(rs.getString("portal_blocks")));
        return portal;
    }

    /**
     * Serializes a list of interior block coordinates to the compact storage form
     * {@code "x:y:z;x:y:z;..."}. A null or empty list serializes to an empty string.
     *
     * @param blocks the interior block coordinates ({@code int[]{x, y, z}})
     * @return the serialized string (never null)
     */
    private String serializeBlocks(List<int[]> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int[] b : blocks) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(b[0]).append(':').append(b[1]).append(':').append(b[2]);
        }
        return sb.toString();
    }

    /**
     * Parses the compact {@code "x:y:z;x:y:z;..."} storage form back into a coordinate list.
     * Malformed or empty input yields an empty list (a legacy single-block portal row).
     *
     * @param raw the serialized string (may be null)
     * @return the parsed interior block coordinates (never null)
     */
    private List<int[]> deserializeBlocks(String raw) {
        List<int[]> blocks = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return blocks;
        }
        for (String triple : raw.split(";")) {
            String[] parts = triple.split(":");
            if (parts.length != 3) {
                continue;
            }
            try {
                blocks.add(new int[]{
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())
                });
            } catch (NumberFormatException ignored) {
                // Skip a corrupt triple rather than fail the whole load.
            }
        }
        return blocks;
    }
}
