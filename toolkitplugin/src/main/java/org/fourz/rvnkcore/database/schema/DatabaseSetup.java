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
    private final String databaseType;
    
    public DatabaseSetup(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.databaseType = connectionProvider.getDatabaseType();
        logger.info("DatabaseSetup initialized for database type: " + databaseType);
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

        try (Connection connection = connectionProvider.getConnection()) {
            // Check if schema already exists (to provide accurate logging)
            boolean schemaExists = checkSchemaExists(connection);

            if (schemaExists) {
                logger.info("Database schema already initialized - performing verification only");
                verifySchema(connection);
            } else {
                logger.info("Initializing database schema for the first time...");
                createTables(connection);
                createIndexes(connection);
                logger.info("Database schema initialization completed successfully");
            }

            schemaInitialized.set(true);
            schemaVerified.set(true);
        } catch (SQLException e) {
            logger.error("Database schema initialization failed", e);
            throw e;
        }
    }

    /**
     * Quick check to see if schema already exists without detailed logging.
     */
    private boolean checkSchemaExists(Connection connection) {
        try (var stmt = connection.createStatement()) {
            String checkQuery;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                checkQuery = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rvnk_worlds' LIMIT 1";
            } else {
                checkQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='rvnk_worlds' LIMIT 1";
            }

            var rs = stmt.executeQuery(checkQuery);
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException e) {
            logger.debug("Schema existence check failed: " + e.getMessage());
            return false; // Assume schema doesn't exist if check fails
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
            String query;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rvnk_players'";
            } else {
                query = "SELECT name FROM sqlite_master WHERE type='table' AND name='rvnk_players'";
            }
            
            var rs = stmt.executeQuery(query);
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
        logger.debug("Creating tables for database type: " + databaseType);

        String createPlayersTable;
        String createPlayerWorldDataTable;
        String createWorldsTable;
        String createAnnouncementsTable;
        
        if ("MySQL".equalsIgnoreCase(databaseType)) {
            // MySQL-specific table definitions with proper column types
            createPlayersTable = """
                CREATE TABLE IF NOT EXISTS rvnk_players (
                    id VARCHAR(36) PRIMARY KEY,
                    current_name VARCHAR(255) NOT NULL,
                    name_history TEXT,
                    first_join TIMESTAMP NOT NULL,
                    last_seen TIMESTAMP NOT NULL,
                    current_world VARCHAR(255),
                    times_joined INT DEFAULT 1,
                    total_playtime_seconds BIGINT DEFAULT 0,
                    primary_group VARCHAR(255) DEFAULT 'default',
                    groups TEXT,
                    banned BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            createPlayerWorldDataTable = """
                CREATE TABLE IF NOT EXISTS rvnk_player_world_data (
                    player_id VARCHAR(36) NOT NULL,
                    world_name VARCHAR(255) NOT NULL,
                    first_visit TIMESTAMP NOT NULL,
                    last_visit TIMESTAMP NOT NULL,
                    visit_count INT DEFAULT 1,
                    playtime_seconds BIGINT DEFAULT 0,
                    last_x DOUBLE DEFAULT 0,
                    last_y DOUBLE DEFAULT 0,
                    last_z DOUBLE DEFAULT 0,
                    last_yaw FLOAT DEFAULT 0,
                    last_pitch FLOAT DEFAULT 0,
                    last_biome VARCHAR(255),
                    death_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_id, world_name),
                    FOREIGN KEY (world_name) REFERENCES rvnk_worlds(name) ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            createWorldsTable = """
                CREATE TABLE IF NOT EXISTS rvnk_worlds (
                    name VARCHAR(255) PRIMARY KEY,
                    display_name VARCHAR(255) NOT NULL,
                    world_type VARCHAR(100) NOT NULL DEFAULT 'NORMAL',
                    environment VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
                    world_folder VARCHAR(500),
                    seed BIGINT,
                    generator_name VARCHAR(255),
                    generator_settings TEXT,
                    difficulty VARCHAR(50) DEFAULT 'EASY',
                    game_rule_settings TEXT,
                    spawn_x DOUBLE DEFAULT 0,
                    spawn_y DOUBLE DEFAULT 64,
                    spawn_z DOUBLE DEFAULT 0,
                    world_border_size DOUBLE,
                    world_border_center_x DOUBLE DEFAULT 0,
                    world_border_center_z DOUBLE DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE,
                    is_auto_save BOOLEAN DEFAULT TRUE,
                    keep_spawn_in_memory BOOLEAN DEFAULT TRUE,
                    allow_animals BOOLEAN DEFAULT TRUE,
                    allow_monsters BOOLEAN DEFAULT TRUE,
                    allow_pvp BOOLEAN DEFAULT FALSE,
                    weather_enabled BOOLEAN DEFAULT TRUE,
                    thunder_enabled BOOLEAN DEFAULT TRUE,
                    first_loaded TIMESTAMP,
                    last_accessed TIMESTAMP,
                    total_playtime_seconds BIGINT DEFAULT 0,
                    player_count INT DEFAULT 0,
                    max_players_seen INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            createAnnouncementsTable = """
                CREATE TABLE IF NOT EXISTS rvnk_announcements (
                    id VARCHAR(36) PRIMARY KEY,
                    title VARCHAR(500),
                    message TEXT NOT NULL,
                    type VARCHAR(100) NOT NULL DEFAULT 'general',
                    active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    scheduled_for TIMESTAMP NULL,
                    expires_at TIMESTAMP NULL,
                    interval_seconds INT DEFAULT 0,
                    target_worlds TEXT,
                    target_groups TEXT,
                    metadata TEXT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
        } else {
            // SQLite table definitions (original format)
            createPlayersTable = """
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
            
            createPlayerWorldDataTable = """
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
                    PRIMARY KEY (player_id, world_name),
                    FOREIGN KEY (world_name) REFERENCES rvnk_worlds(name) ON UPDATE CASCADE
                )
                """;

            createWorldsTable = """
                CREATE TABLE IF NOT EXISTS rvnk_worlds (
                    name TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    world_type TEXT NOT NULL DEFAULT 'NORMAL',
                    environment TEXT NOT NULL DEFAULT 'NORMAL',
                    world_folder TEXT,
                    seed INTEGER,
                    generator_name TEXT,
                    generator_settings TEXT,
                    difficulty TEXT DEFAULT 'EASY',
                    game_rule_settings TEXT,
                    spawn_x REAL DEFAULT 0,
                    spawn_y REAL DEFAULT 64,
                    spawn_z REAL DEFAULT 0,
                    world_border_size REAL,
                    world_border_center_x REAL DEFAULT 0,
                    world_border_center_z REAL DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE,
                    is_auto_save BOOLEAN DEFAULT TRUE,
                    keep_spawn_in_memory BOOLEAN DEFAULT TRUE,
                    allow_animals BOOLEAN DEFAULT TRUE,
                    allow_monsters BOOLEAN DEFAULT TRUE,
                    allow_pvp BOOLEAN DEFAULT FALSE,
                    weather_enabled BOOLEAN DEFAULT TRUE,
                    thunder_enabled BOOLEAN DEFAULT TRUE,
                    first_loaded TIMESTAMP,
                    last_accessed TIMESTAMP,
                    total_playtime_seconds INTEGER DEFAULT 0,
                    player_count INTEGER DEFAULT 0,
                    max_players_seen INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            
            createAnnouncementsTable = """
                CREATE TABLE IF NOT EXISTS rvnk_announcements (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    message TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'general',
                    active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    scheduled_for TIMESTAMP NULL,
                    expires_at TIMESTAMP NULL,
                    interval_seconds INTEGER DEFAULT 0,
                    target_worlds TEXT,
                    target_groups TEXT,
                    metadata TEXT
                )
                """;
        }
        
        try (var stmt = connection.createStatement()) {
            logger.debug("Creating rvnk_worlds table...");
            stmt.execute(createWorldsTable);
            logger.debug("Creating rvnk_players table...");
            stmt.execute(createPlayersTable);
            logger.debug("Creating rvnk_player_world_data table...");
            stmt.execute(createPlayerWorldDataTable);
            logger.debug("Creating rvnk_announcements table...");
            stmt.execute(createAnnouncementsTable);
            logger.debug("All tables created successfully");
        }
    }
    
    private void createIndexes(Connection connection) throws SQLException {
        logger.debug("Creating indexes for database type: " + databaseType);

        String[] indexes;
        
        if ("MySQL".equalsIgnoreCase(databaseType)) {
            // MySQL indexes with proper column lengths for TEXT columns
            indexes = new String[]{
                "CREATE INDEX IF NOT EXISTS idx_worlds_world_type ON rvnk_worlds(world_type)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_environment ON rvnk_worlds(environment)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_is_active ON rvnk_worlds(is_active)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_last_accessed ON rvnk_worlds(last_accessed)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_player_count ON rvnk_worlds(player_count)",
                "CREATE INDEX IF NOT EXISTS idx_players_name_history ON rvnk_players(current_name(100))",
                "CREATE INDEX IF NOT EXISTS idx_players_last_seen ON rvnk_players(last_seen)",
                "CREATE INDEX IF NOT EXISTS idx_players_primary_group ON rvnk_players(primary_group(100))",
                "CREATE INDEX IF NOT EXISTS idx_players_current_world ON rvnk_players(current_world(100))",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_player ON rvnk_player_world_data(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_world ON rvnk_player_world_data(world_name(100))",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_last_visit ON rvnk_player_world_data(last_visit)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_playtime ON rvnk_player_world_data(playtime_seconds)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_active ON rvnk_announcements(active, expires_at)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_type ON rvnk_announcements(type(50))",
                "CREATE INDEX IF NOT EXISTS idx_announcements_scheduled ON rvnk_announcements(scheduled_for)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_created_at ON rvnk_announcements(created_at)"
            };
        } else {
            // SQLite indexes (original format)
            indexes = new String[]{
                "CREATE INDEX IF NOT EXISTS idx_worlds_world_type ON rvnk_worlds(world_type)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_environment ON rvnk_worlds(environment)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_is_active ON rvnk_worlds(is_active)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_last_accessed ON rvnk_worlds(last_accessed)",
                "CREATE INDEX IF NOT EXISTS idx_worlds_player_count ON rvnk_worlds(player_count)",
                "CREATE INDEX IF NOT EXISTS idx_players_name_history ON rvnk_players(current_name, name_history)",
                "CREATE INDEX IF NOT EXISTS idx_players_last_seen ON rvnk_players(last_seen)",
                "CREATE INDEX IF NOT EXISTS idx_players_primary_group ON rvnk_players(primary_group)",
                "CREATE INDEX IF NOT EXISTS idx_players_current_world ON rvnk_players(current_world)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_player ON rvnk_player_world_data(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_world ON rvnk_player_world_data(world_name)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_last_visit ON rvnk_player_world_data(last_visit)",
                "CREATE INDEX IF NOT EXISTS idx_player_world_data_playtime ON rvnk_player_world_data(playtime_seconds)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_active ON rvnk_announcements(active, expires_at)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_type ON rvnk_announcements(type)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_scheduled ON rvnk_announcements(scheduled_for)",
                "CREATE INDEX IF NOT EXISTS idx_announcements_created_at ON rvnk_announcements(created_at)"
            };
        }
        
        try (var stmt = connection.createStatement()) {
            for (String index : indexes) {
                logger.debug("Creating index: " + index);
                stmt.execute(index);
            }
            logger.debug("All indexes created successfully");
        }
    }
    
    private void verifySchema(Connection connection) throws SQLException {
        logger.debug("Verifying database schema...");
        // Verify critical tables exist
        String[] requiredTables = {"rvnk_worlds", "rvnk_players", "rvnk_player_world_data", "rvnk_announcements"};

        try (var stmt = connection.createStatement()) {
            for (String table : requiredTables) {
                String query;
                if ("MySQL".equalsIgnoreCase(databaseType)) {
                    query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'";
                } else {
                    query = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'";
                }

                var rs = stmt.executeQuery(query);
                if (!rs.next()) {
                    throw new SQLException("Required table '" + table + "' not found");
                }
                rs.close();
                logger.debug("Verified table exists: " + table);
            }
        }
        logger.debug("Schema verification completed successfully");
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
            String checkQuery;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                checkQuery = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rvnk_schema_version'";
            } else {
                checkQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='rvnk_schema_version'";
            }
            
            var rs = stmt.executeQuery(checkQuery);
            boolean versionTableExists = rs.next();
            rs.close();
            
            if (!versionTableExists) {
                // Create version table
                String createVersionTable;
                if ("MySQL".equalsIgnoreCase(databaseType)) {
                    createVersionTable = """
                        CREATE TABLE rvnk_schema_version (
                            version INT PRIMARY KEY,
                            applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                        """;
                } else {
                    createVersionTable = """
                        CREATE TABLE rvnk_schema_version (
                            version INTEGER PRIMARY KEY,
                            applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """;
                }
                
                stmt.execute(createVersionTable);
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
