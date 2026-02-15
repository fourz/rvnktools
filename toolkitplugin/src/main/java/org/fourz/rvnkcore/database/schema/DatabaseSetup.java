package org.fourz.rvnkcore.database.schema;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.util.log.LogManager;
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
    private final String tablePrefix;

    // Table name constants (base names without prefix)
    public static final String TABLE_WORLDS = "rvnk_worlds";
    public static final String TABLE_PLAYERS = "rvnk_players";
    public static final String TABLE_PLAYER_WORLD_DATA = "rvnk_player_world_data";
    public static final String TABLE_ANNOUNCEMENTS = "rvnk_announcements";
    public static final String TABLE_PLAYER_PREFERENCES = "rvnk_player_preferences";
    public static final String TABLE_PLAYER_NOTIFICATION_TYPES = "rvnk_player_notification_types";
    public static final String TABLE_PLAYER_NOTIFICATION_CHANNELS = "rvnk_player_notification_channels";
    public static final String TABLE_PREFERENCE_DEFAULTS = "rvnk_preference_defaults";
    public static final String TABLE_SCHEMA_VERSION = "rvnk_schema_version";

    public DatabaseSetup(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.databaseType = connectionProvider.getDatabaseType();

        // Load table prefix from config
        String storageType = plugin.getConfig().getString("storage.type", "sqlite");
        this.tablePrefix = plugin.getConfig().getString("storage." + storageType + ".tablePrefix", "");

        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            logger.info("DatabaseSetup using table prefix: " + tablePrefix);
        }
        logger.info("DatabaseSetup initialized for database type: " + databaseType);
    }

    /**
     * Get the table name with prefix applied.
     * @param baseName The base table name (e.g., "rvnk_worlds")
     * @return The prefixed table name (e.g., "core_rvnk_worlds")
     */
    public String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    /**
     * Get the configured table prefix.
     * @return The table prefix, or empty string if none
     */
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
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
                logger.info("Database schema already initialized - ensuring all tables exist");
            } else {
                logger.info("Initializing database schema for the first time...");
            }

            // Always run createTables - uses CREATE TABLE IF NOT EXISTS so it's idempotent
            // This ensures new tables added in updates are created on existing databases
            createTables(connection);
            createIndexes(connection);

            // Run migrations to add new columns to existing tables
            runMigrations(connection);

            if (!schemaExists) {
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
            String worldsTable = table(TABLE_WORLDS);
            String checkQuery;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                checkQuery = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + worldsTable + "' LIMIT 1";
            } else {
                checkQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + worldsTable + "' LIMIT 1";
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
            String playersTable = table(TABLE_PLAYERS);
            var stmt = connection.createStatement();
            String query;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + playersTable + "'";
            } else {
                query = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + playersTable + "'";
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

        // Get prefixed table names
        String worldsTable = table(TABLE_WORLDS);
        String playersTable = table(TABLE_PLAYERS);
        String playerWorldDataTable = table(TABLE_PLAYER_WORLD_DATA);
        String announcementsTable = table(TABLE_ANNOUNCEMENTS);
        String playerPreferencesTable = table(TABLE_PLAYER_PREFERENCES);
        String notificationTypesTable = table(TABLE_PLAYER_NOTIFICATION_TYPES);
        String notificationChannelsTable = table(TABLE_PLAYER_NOTIFICATION_CHANNELS);
        String preferenceDefaultsTable = table(TABLE_PREFERENCE_DEFAULTS);

        String createPlayersTable;
        String createPlayerWorldDataTable;
        String createWorldsTable;
        String createAnnouncementsTable;
        String createPlayerPreferencesTable;
        String createNotificationTypesTable;
        String createNotificationChannelsTable;
        String createPreferenceDefaultsTable;

        if ("MySQL".equalsIgnoreCase(databaseType)) {
            // MySQL-specific table definitions with proper column types
            createPlayersTable = "CREATE TABLE IF NOT EXISTS " + playersTable + " (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "current_name VARCHAR(255) NOT NULL, " +
                "name_history TEXT, " +
                "first_join TIMESTAMP NOT NULL, " +
                "last_seen TIMESTAMP NOT NULL, " +
                "current_world VARCHAR(255), " +
                "times_joined INT DEFAULT 1, " +
                "total_playtime_seconds BIGINT DEFAULT 0, " +
                "primary_group VARCHAR(255) DEFAULT 'default', " +
                "groups TEXT, " +
                "banned BOOLEAN DEFAULT FALSE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createPlayerWorldDataTable = "CREATE TABLE IF NOT EXISTS " + playerWorldDataTable + " (" +
                "player_id VARCHAR(36) NOT NULL, " +
                "world_name VARCHAR(255) NOT NULL, " +
                "first_visit TIMESTAMP NOT NULL, " +
                "last_visit TIMESTAMP NOT NULL, " +
                "visit_count INT DEFAULT 1, " +
                "playtime_seconds BIGINT DEFAULT 0, " +
                "last_x DOUBLE DEFAULT 0, " +
                "last_y DOUBLE DEFAULT 0, " +
                "last_z DOUBLE DEFAULT 0, " +
                "last_yaw FLOAT DEFAULT 0, " +
                "last_pitch FLOAT DEFAULT 0, " +
                "last_biome VARCHAR(255), " +
                "death_count INT DEFAULT 0, " +
                "world_specific_data TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_id, world_name), " +
                "FOREIGN KEY (world_name) REFERENCES " + worldsTable + "(name) ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createWorldsTable = "CREATE TABLE IF NOT EXISTS " + worldsTable + " (" +
                "name VARCHAR(255) PRIMARY KEY, " +
                "display_name VARCHAR(255) NOT NULL, " +
                "world_type VARCHAR(100) NOT NULL DEFAULT 'NORMAL', " +
                "environment VARCHAR(50) NOT NULL DEFAULT 'NORMAL', " +
                "world_folder VARCHAR(500), " +
                "seed BIGINT, " +
                "generator_name VARCHAR(255), " +
                "generator_settings TEXT, " +
                "difficulty VARCHAR(50) DEFAULT 'EASY', " +
                "game_rule_settings TEXT, " +
                "spawn_x DOUBLE DEFAULT 0, " +
                "spawn_y DOUBLE DEFAULT 64, " +
                "spawn_z DOUBLE DEFAULT 0, " +
                "world_border_size DOUBLE, " +
                "world_border_center_x DOUBLE DEFAULT 0, " +
                "world_border_center_z DOUBLE DEFAULT 0, " +
                "is_active BOOLEAN DEFAULT TRUE, " +
                "is_auto_save BOOLEAN DEFAULT TRUE, " +
                "keep_spawn_in_memory BOOLEAN DEFAULT TRUE, " +
                "allow_animals BOOLEAN DEFAULT TRUE, " +
                "allow_monsters BOOLEAN DEFAULT TRUE, " +
                "allow_pvp BOOLEAN DEFAULT FALSE, " +
                "weather_enabled BOOLEAN DEFAULT TRUE, " +
                "thunder_enabled BOOLEAN DEFAULT TRUE, " +
                "first_loaded TIMESTAMP, " +
                "last_accessed TIMESTAMP, " +
                "total_playtime_seconds BIGINT DEFAULT 0, " +
                "player_count INT DEFAULT 0, " +
                "max_players_seen INT DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createAnnouncementsTable = "CREATE TABLE IF NOT EXISTS " + announcementsTable + " (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "title VARCHAR(500), " +
                "message TEXT NOT NULL, " +
                "type VARCHAR(100) NOT NULL DEFAULT 'general', " +
                "active BOOLEAN DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "scheduled_for TIMESTAMP NULL, " +
                "expires_at TIMESTAMP NULL, " +
                "interval_seconds INT DEFAULT 0, " +
                "target_worlds TEXT, " +
                "target_groups TEXT, " +
                "metadata TEXT" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createPlayerPreferencesTable = "CREATE TABLE IF NOT EXISTS " + playerPreferencesTable + " (" +
                "player_id VARCHAR(36) NOT NULL, " +
                "plugin_id VARCHAR(64) NOT NULL, " +
                "master_enabled BOOLEAN DEFAULT FALSE, " +
                "quiet_hours_start INT DEFAULT -1, " +
                "quiet_hours_end INT DEFAULT -1, " +
                "metadata TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_id, plugin_id), " +
                "INDEX idx_player_prefs_player (player_id), " +
                "INDEX idx_player_prefs_plugin (plugin_id), " +
                "FOREIGN KEY (player_id) REFERENCES " + playersTable + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createNotificationTypesTable = "CREATE TABLE IF NOT EXISTS " + notificationTypesTable + " (" +
                "player_id VARCHAR(36) NOT NULL, " +
                "plugin_id VARCHAR(64) NOT NULL, " +
                "notification_type VARCHAR(64) NOT NULL, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "PRIMARY KEY (player_id, plugin_id, notification_type), " +
                "INDEX idx_notif_types_player (player_id), " +
                "INDEX idx_notif_types_plugin_type (plugin_id, notification_type), " +
                "FOREIGN KEY (player_id, plugin_id) REFERENCES " + playerPreferencesTable + "(player_id, plugin_id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createNotificationChannelsTable = "CREATE TABLE IF NOT EXISTS " + notificationChannelsTable + " (" +
                "player_id VARCHAR(36) NOT NULL, " +
                "plugin_id VARCHAR(64) NOT NULL, " +
                "notification_type VARCHAR(64) NOT NULL, " +
                "channel_name VARCHAR(32) NOT NULL, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "PRIMARY KEY (player_id, plugin_id, notification_type, channel_name), " +
                "INDEX idx_channels_player (player_id), " +
                "INDEX idx_channels_plugin_type (plugin_id, notification_type), " +
                "FOREIGN KEY (player_id, plugin_id, notification_type) REFERENCES " + notificationTypesTable + "(player_id, plugin_id, notification_type) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

            createPreferenceDefaultsTable = "CREATE TABLE IF NOT EXISTS " + preferenceDefaultsTable + " (" +
                "plugin_id VARCHAR(64) NOT NULL, " +
                "preference_key VARCHAR(64) NOT NULL, " +
                "preference_value TEXT NOT NULL, " +
                "description TEXT, " +
                "PRIMARY KEY (plugin_id, preference_key), " +
                "INDEX idx_defaults_plugin (plugin_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            // SQLite table definitions
            createPlayersTable = "CREATE TABLE IF NOT EXISTS " + playersTable + " (" +
                "id TEXT PRIMARY KEY, " +
                "current_name TEXT NOT NULL, " +
                "name_history TEXT DEFAULT '', " +
                "first_join TIMESTAMP NOT NULL, " +
                "last_seen TIMESTAMP NOT NULL, " +
                "current_world TEXT, " +
                "times_joined INTEGER DEFAULT 1, " +
                "total_playtime_seconds BIGINT DEFAULT 0, " +
                "primary_group TEXT DEFAULT 'default', " +
                "groups TEXT DEFAULT '', " +
                "banned BOOLEAN DEFAULT FALSE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

            createPlayerWorldDataTable = "CREATE TABLE IF NOT EXISTS " + playerWorldDataTable + " (" +
                "player_id TEXT NOT NULL, " +
                "world_name TEXT NOT NULL, " +
                "first_visit TIMESTAMP NOT NULL, " +
                "last_visit TIMESTAMP NOT NULL, " +
                "visit_count INTEGER DEFAULT 1, " +
                "playtime_seconds BIGINT DEFAULT 0, " +
                "last_x REAL DEFAULT 0, " +
                "last_y REAL DEFAULT 0, " +
                "last_z REAL DEFAULT 0, " +
                "last_yaw REAL DEFAULT 0, " +
                "last_pitch REAL DEFAULT 0, " +
                "last_biome TEXT, " +
                "death_count INTEGER DEFAULT 0, " +
                "world_specific_data TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_id, world_name), " +
                "FOREIGN KEY (world_name) REFERENCES " + worldsTable + "(name) ON UPDATE CASCADE" +
                ")";

            createWorldsTable = "CREATE TABLE IF NOT EXISTS " + worldsTable + " (" +
                "name TEXT PRIMARY KEY, " +
                "display_name TEXT NOT NULL, " +
                "world_type TEXT NOT NULL DEFAULT 'NORMAL', " +
                "environment TEXT NOT NULL DEFAULT 'NORMAL', " +
                "world_folder TEXT, " +
                "seed INTEGER, " +
                "generator_name TEXT, " +
                "generator_settings TEXT, " +
                "difficulty TEXT DEFAULT 'EASY', " +
                "game_rule_settings TEXT, " +
                "spawn_x REAL DEFAULT 0, " +
                "spawn_y REAL DEFAULT 64, " +
                "spawn_z REAL DEFAULT 0, " +
                "world_border_size REAL, " +
                "world_border_center_x REAL DEFAULT 0, " +
                "world_border_center_z REAL DEFAULT 0, " +
                "is_active BOOLEAN DEFAULT TRUE, " +
                "is_auto_save BOOLEAN DEFAULT TRUE, " +
                "keep_spawn_in_memory BOOLEAN DEFAULT TRUE, " +
                "allow_animals BOOLEAN DEFAULT TRUE, " +
                "allow_monsters BOOLEAN DEFAULT TRUE, " +
                "allow_pvp BOOLEAN DEFAULT FALSE, " +
                "weather_enabled BOOLEAN DEFAULT TRUE, " +
                "thunder_enabled BOOLEAN DEFAULT TRUE, " +
                "first_loaded TIMESTAMP, " +
                "last_accessed TIMESTAMP, " +
                "total_playtime_seconds INTEGER DEFAULT 0, " +
                "player_count INTEGER DEFAULT 0, " +
                "max_players_seen INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

            createAnnouncementsTable = "CREATE TABLE IF NOT EXISTS " + announcementsTable + " (" +
                "id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "message TEXT NOT NULL, " +
                "type TEXT NOT NULL DEFAULT 'general', " +
                "active BOOLEAN DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "scheduled_for TIMESTAMP NULL, " +
                "expires_at TIMESTAMP NULL, " +
                "interval_seconds INTEGER DEFAULT 0, " +
                "target_worlds TEXT, " +
                "target_groups TEXT, " +
                "metadata TEXT" +
                ")";

            createPlayerPreferencesTable = "CREATE TABLE IF NOT EXISTS " + playerPreferencesTable + " (" +
                "player_id TEXT NOT NULL, " +
                "plugin_id TEXT NOT NULL, " +
                "master_enabled BOOLEAN DEFAULT FALSE, " +
                "quiet_hours_start INTEGER DEFAULT -1, " +
                "quiet_hours_end INTEGER DEFAULT -1, " +
                "metadata TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_id, plugin_id), " +
                "FOREIGN KEY (player_id) REFERENCES " + playersTable + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ")";

            createNotificationTypesTable = "CREATE TABLE IF NOT EXISTS " + notificationTypesTable + " (" +
                "player_id TEXT NOT NULL, " +
                "plugin_id TEXT NOT NULL, " +
                "notification_type TEXT NOT NULL, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "PRIMARY KEY (player_id, plugin_id, notification_type), " +
                "FOREIGN KEY (player_id, plugin_id) REFERENCES " + playerPreferencesTable + "(player_id, plugin_id) ON DELETE CASCADE" +
                ")";

            createNotificationChannelsTable = "CREATE TABLE IF NOT EXISTS " + notificationChannelsTable + " (" +
                "player_id TEXT NOT NULL, " +
                "plugin_id TEXT NOT NULL, " +
                "notification_type TEXT NOT NULL, " +
                "channel_name TEXT NOT NULL, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "PRIMARY KEY (player_id, plugin_id, notification_type, channel_name), " +
                "FOREIGN KEY (player_id, plugin_id, notification_type) REFERENCES " + notificationTypesTable + "(player_id, plugin_id, notification_type) ON DELETE CASCADE" +
                ")";

            createPreferenceDefaultsTable = "CREATE TABLE IF NOT EXISTS " + preferenceDefaultsTable + " (" +
                "plugin_id TEXT NOT NULL, " +
                "preference_key TEXT NOT NULL, " +
                "preference_value TEXT NOT NULL, " +
                "description TEXT, " +
                "PRIMARY KEY (plugin_id, preference_key)" +
                ")";
        }

        try (var stmt = connection.createStatement()) {
            logger.debug("Creating " + worldsTable + " table...");
            stmt.execute(createWorldsTable);
            logger.debug("Creating " + playersTable + " table...");
            stmt.execute(createPlayersTable);
            logger.debug("Creating " + playerWorldDataTable + " table...");
            stmt.execute(createPlayerWorldDataTable);
            logger.debug("Creating " + announcementsTable + " table...");
            stmt.execute(createAnnouncementsTable);
            logger.debug("Creating " + playerPreferencesTable + " table...");
            stmt.execute(createPlayerPreferencesTable);
            logger.debug("Creating " + notificationTypesTable + " table...");
            stmt.execute(createNotificationTypesTable);
            logger.debug("Creating " + notificationChannelsTable + " table...");
            stmt.execute(createNotificationChannelsTable);
            logger.debug("Creating " + preferenceDefaultsTable + " table...");
            stmt.execute(createPreferenceDefaultsTable);
            logger.debug("All tables created successfully");
        }
    }
    
    private void createIndexes(Connection connection) throws SQLException {
        logger.debug("Creating indexes for database type: " + databaseType);

        // Get prefixed table names
        String worldsTable = table(TABLE_WORLDS);
        String playersTable = table(TABLE_PLAYERS);
        String playerWorldDataTable = table(TABLE_PLAYER_WORLD_DATA);
        String announcementsTable = table(TABLE_ANNOUNCEMENTS);
        String playerPreferencesTable = table(TABLE_PLAYER_PREFERENCES);
        String notificationTypesTable = table(TABLE_PLAYER_NOTIFICATION_TYPES);
        String notificationChannelsTable = table(TABLE_PLAYER_NOTIFICATION_CHANNELS);
        String preferenceDefaultsTable = table(TABLE_PREFERENCE_DEFAULTS);
        String prefix = getTablePrefix().isEmpty() ? "" : getTablePrefix();

        String[] indexes;

        if ("MySQL".equalsIgnoreCase(databaseType)) {
            // MySQL indexes with proper column lengths for TEXT columns
            indexes = new String[]{
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_world_type ON " + worldsTable + "(world_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_environment ON " + worldsTable + "(environment)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_is_active ON " + worldsTable + "(is_active)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_last_accessed ON " + worldsTable + "(last_accessed)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_player_count ON " + worldsTable + "(player_count)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_name_history ON " + playersTable + "(current_name(100))",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_last_seen ON " + playersTable + "(last_seen)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_primary_group ON " + playersTable + "(primary_group(100))",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_current_world ON " + playersTable + "(current_world(100))",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_player ON " + playerWorldDataTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_world ON " + playerWorldDataTable + "(world_name(100))",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_last_visit ON " + playerWorldDataTable + "(last_visit)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_playtime ON " + playerWorldDataTable + "(playtime_seconds)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_active ON " + announcementsTable + "(active, expires_at)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_type ON " + announcementsTable + "(type(50))",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_scheduled ON " + announcementsTable + "(scheduled_for)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_created_at ON " + announcementsTable + "(created_at)",
                // Player preferences indexes (SQLite only - MySQL has inline indexes)
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_prefs_player ON " + playerPreferencesTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_prefs_plugin ON " + playerPreferencesTable + "(plugin_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "notif_types_player ON " + notificationTypesTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "notif_types_plugin_type ON " + notificationTypesTable + "(plugin_id, notification_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "channels_player ON " + notificationChannelsTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "channels_plugin_type ON " + notificationChannelsTable + "(plugin_id, notification_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "defaults_plugin ON " + preferenceDefaultsTable + "(plugin_id)"
            };
        } else {
            // SQLite indexes
            indexes = new String[]{
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_world_type ON " + worldsTable + "(world_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_environment ON " + worldsTable + "(environment)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_is_active ON " + worldsTable + "(is_active)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_last_accessed ON " + worldsTable + "(last_accessed)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "worlds_player_count ON " + worldsTable + "(player_count)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_name_history ON " + playersTable + "(current_name, name_history)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_last_seen ON " + playersTable + "(last_seen)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_primary_group ON " + playersTable + "(primary_group)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "players_current_world ON " + playersTable + "(current_world)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_player ON " + playerWorldDataTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_world ON " + playerWorldDataTable + "(world_name)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_last_visit ON " + playerWorldDataTable + "(last_visit)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_world_data_playtime ON " + playerWorldDataTable + "(playtime_seconds)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_active ON " + announcementsTable + "(active, expires_at)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_type ON " + announcementsTable + "(type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_scheduled ON " + announcementsTable + "(scheduled_for)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "announcements_created_at ON " + announcementsTable + "(created_at)",
                // Player preferences indexes
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_prefs_player ON " + playerPreferencesTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player_prefs_plugin ON " + playerPreferencesTable + "(plugin_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "notif_types_player ON " + notificationTypesTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "notif_types_plugin_type ON " + notificationTypesTable + "(plugin_id, notification_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "channels_player ON " + notificationChannelsTable + "(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "channels_plugin_type ON " + notificationChannelsTable + "(plugin_id, notification_type)",
                "CREATE INDEX IF NOT EXISTS idx_" + prefix + "defaults_plugin ON " + preferenceDefaultsTable + "(plugin_id)"
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
    
    /**
     * Runs database migrations to add new columns to existing tables.
     * This handles schema evolution for existing databases.
     */
    private void runMigrations(Connection connection) throws SQLException {
        logger.debug("Running database migrations...");

        String playerWorldDataTable = table(TABLE_PLAYER_WORLD_DATA);

        // Migration 1: Add world_specific_data column if missing
        if (!columnExists(connection, playerWorldDataTable, "world_specific_data")) {
            logger.info("Adding 'world_specific_data' column to " + playerWorldDataTable);
            try (var stmt = connection.createStatement()) {
                String alterSql = "ALTER TABLE " + playerWorldDataTable + " ADD COLUMN world_specific_data TEXT";
                stmt.execute(alterSql);
                logger.info("Successfully added 'world_specific_data' column");
            } catch (SQLException e) {
                logger.warning("Failed to add world_specific_data column: " + e.getMessage());
                // Don't throw - allow startup to continue, feature will just not persist
            }
        }

        logger.debug("Database migrations completed");
    }

    /**
     * Checks if a column exists in a table.
     */
    private boolean columnExists(Connection connection, String tableName, String columnName) {
        try (var stmt = connection.createStatement()) {
            String query;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                query = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                        "AND COLUMN_NAME = '" + columnName + "'";
            } else {
                // SQLite uses PRAGMA table_info
                query = "PRAGMA table_info(" + tableName + ")";
            }

            var rs = stmt.executeQuery(query);

            if ("MySQL".equalsIgnoreCase(databaseType)) {
                boolean exists = rs.next();
                rs.close();
                return exists;
            } else {
                // SQLite: iterate through columns to find match
                while (rs.next()) {
                    String colName = rs.getString("name");
                    if (columnName.equalsIgnoreCase(colName)) {
                        rs.close();
                        return true;
                    }
                }
                rs.close();
                return false;
            }
        } catch (SQLException e) {
            logger.debug("Column existence check failed for " + tableName + "." + columnName + ": " + e.getMessage());
            return false;
        }
    }

    private void verifySchema(Connection connection) throws SQLException {
        logger.debug("Verifying database schema...");
        // Verify critical tables exist (with prefix)
        String[] requiredTables = {
            table(TABLE_WORLDS),
            table(TABLE_PLAYERS),
            table(TABLE_PLAYER_WORLD_DATA),
            table(TABLE_ANNOUNCEMENTS)
        };

        try (var stmt = connection.createStatement()) {
            for (String tableName : requiredTables) {
                String query;
                if ("MySQL".equalsIgnoreCase(databaseType)) {
                    query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "'";
                } else {
                    query = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";
                }

                var rs = stmt.executeQuery(query);
                if (!rs.next()) {
                    throw new SQLException("Required table '" + tableName + "' not found");
                }
                rs.close();
                logger.debug("Verified table exists: " + tableName);
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
        String schemaVersionTable = table(TABLE_SCHEMA_VERSION);

        try (Connection connection = connectionProvider.getConnection()) {
            // Check if version table exists
            var stmt = connection.createStatement();
            String checkQuery;
            if ("MySQL".equalsIgnoreCase(databaseType)) {
                checkQuery = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + schemaVersionTable + "'";
            } else {
                checkQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + schemaVersionTable + "'";
            }

            var rs = stmt.executeQuery(checkQuery);
            boolean versionTableExists = rs.next();
            rs.close();

            if (!versionTableExists) {
                // Create version table
                String createVersionTable;
                if ("MySQL".equalsIgnoreCase(databaseType)) {
                    createVersionTable = "CREATE TABLE " + schemaVersionTable + " (" +
                        "version INT PRIMARY KEY, " +
                        "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
                } else {
                    createVersionTable = "CREATE TABLE " + schemaVersionTable + " (" +
                        "version INTEGER PRIMARY KEY, " +
                        "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                }

                stmt.execute(createVersionTable);
                stmt.execute("INSERT INTO " + schemaVersionTable + " (version) VALUES (1)");
                stmt.close();
                return 1;
            }

            // Get current version
            rs = stmt.executeQuery("SELECT MAX(version) as current_version FROM " + schemaVersionTable);
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
