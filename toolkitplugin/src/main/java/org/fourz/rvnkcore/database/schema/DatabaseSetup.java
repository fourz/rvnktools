package org.fourz.rvnkcore.database.schema;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized database setup and schema management.
 * 
 * Handles one-time database initialization during plugin startup
 * to prevent redundant schema verification on every operation.
 * 
 * @since 1.0.0
 */
public class DatabaseSetup {
    
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);
    private final AtomicBoolean schemaVerified = new AtomicBoolean(false);
    
    public DatabaseSetup(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Performs one-time database setup during plugin initialization.
     * This includes schema creation, indexes, and any version upgrades.
     * 
     * @throws SQLException if setup fails
     */
    public void initializeDatabase() throws SQLException {
        if (schemaInitialized.get()) {
            return; // Already initialized
        }
        
        logger.info("Initializing database schema...");
        
        try (Connection connection = connectionProvider.getConnection()) {
            createTables(connection);
            createIndexes(connection);
            verifySchema(connection);
            
            schemaInitialized.set(true);
            schemaVerified.set(true);
            
            logger.info("Database schema initialization completed successfully");
        } catch (SQLException e) {
            logger.error("Database schema initialization failed", e);
            throw e;
        }
    }
    
    /**
     * Quick verification that schema is ready without recreating tables.
     * Only runs if schema hasn't been verified yet.
     * 
     * @return true if schema is verified and ready
     */
    public boolean isSchemaReady() {
        if (schemaVerified.get()) {
            return true;
        }
        
        try (Connection connection = connectionProvider.getConnection()) {
            // Quick check for main tables
            var stmt = connection.createStatement();
            var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='rvnk_players'");
            boolean exists = rs.next();
            rs.close();
            stmt.close();
            
            if (exists) {
                schemaVerified.set(true);
                return true;
            }
        } catch (SQLException e) {
            logger.warning("Schema verification check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private void createTables(Connection connection) throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS rvnk_players (
                id TEXT PRIMARY KEY,
                current_name TEXT NOT NULL,
                name_history TEXT DEFAULT '',
                first_join TIMESTAMP NOT NULL,
                last_seen TIMESTAMP NOT NULL,
                current_world TEXT,
                times_joined INTEGER DEFAULT 1,
                total_playtime_seconds BIGINT DEFAULT 0,
                primary_group TEXT DEFAULT 'default',
                groups TEXT DEFAULT '',
                banned BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        String createPlayerWorldDataTable = """
            CREATE TABLE IF NOT EXISTS rvnk_player_world_data (
                player_id TEXT NOT NULL,
                world_name TEXT NOT NULL,
                first_visit TIMESTAMP NOT NULL,
                last_visit TIMESTAMP NOT NULL,
                visit_count INTEGER DEFAULT 1,
                playtime_seconds BIGINT DEFAULT 0,
                last_x REAL DEFAULT 0,
                last_y REAL DEFAULT 0,
                last_z REAL DEFAULT 0,
                last_yaw REAL DEFAULT 0,
                last_pitch REAL DEFAULT 0,
                last_biome TEXT,
                death_count INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_id, world_name)
            )
            """;
        
        // Add other table definitions here as needed
        String createAnnouncementsTable = """
            CREATE TABLE IF NOT EXISTS rvnk_announcements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                type TEXT DEFAULT 'general',
                active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP,
                created_by TEXT
            )
            """;
        
        try (var stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createPlayerWorldDataTable);
            stmt.execute(createAnnouncementsTable);
        }
    }
    
    private void createIndexes(Connection connection) throws SQLException {
        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_players_name_history ON rvnk_players(current_name, name_history)",
            "CREATE INDEX IF NOT EXISTS idx_players_last_seen ON rvnk_players(last_seen)",
            "CREATE INDEX IF NOT EXISTS idx_players_primary_group ON rvnk_players(primary_group)",
            "CREATE INDEX IF NOT EXISTS idx_players_current_world ON rvnk_players(current_world)",
            "CREATE INDEX IF NOT EXISTS idx_player_world_data_player ON rvnk_player_world_data(player_id)",
            "CREATE INDEX IF NOT EXISTS idx_player_world_data_world ON rvnk_player_world_data(world_name)",
            "CREATE INDEX IF NOT EXISTS idx_player_world_data_last_visit ON rvnk_player_world_data(last_visit)",
            "CREATE INDEX IF NOT EXISTS idx_player_world_data_playtime ON rvnk_player_world_data(playtime_seconds)",
            "CREATE INDEX IF NOT EXISTS idx_announcements_active ON rvnk_announcements(active, expires_at)"
        };
        
        try (var stmt = connection.createStatement()) {
            for (String index : indexes) {
                stmt.execute(index);
            }
        }
    }
    
    private void verifySchema(Connection connection) throws SQLException {
        // Verify critical tables exist
        String[] requiredTables = {"rvnk_players", "rvnk_player_world_data", "rvnk_announcements"};
        
        try (var stmt = connection.createStatement()) {
            for (String table : requiredTables) {
                var rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'"
                );
                if (!rs.next()) {
                    throw new SQLException("Required table '" + table + "' not found");
                }
                rs.close();
            }
        }
    }
    
    /**
     * Gets the current schema version.
     * 
     * @return the schema version number
     */
    public int getSchemaVersion() {
        try (Connection connection = connectionProvider.getConnection()) {
            // Check if version table exists
            var stmt = connection.createStatement();
            var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='rvnk_schema_version'");
            boolean versionTableExists = rs.next();
            rs.close();
            
            if (!versionTableExists) {
                // Create version table
                stmt.execute("""
                    CREATE TABLE rvnk_schema_version (
                        version INTEGER PRIMARY KEY,
                        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                stmt.execute("INSERT INTO rvnk_schema_version (version) VALUES (1)");
                stmt.close();
                return 1;
            }
            
            // Get current version
            rs = stmt.executeQuery("SELECT MAX(version) as current_version FROM rvnk_schema_version");
            int version = rs.next() ? rs.getInt("current_version") : 1;
            rs.close();
            stmt.close();
            
            return version;
        } catch (SQLException e) {
            logger.warning("Failed to get schema version: " + e.getMessage());
            return 1; // Default version
        }
    }
    
    /**
     * Resets the initialization state for testing purposes.
     */
    public void resetInitializationState() {
        schemaInitialized.set(false);
        schemaVerified.set(false);
    }
}
