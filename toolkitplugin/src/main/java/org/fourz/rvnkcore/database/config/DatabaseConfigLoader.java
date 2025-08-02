package org.fourz.rvnkcore.database.config;

import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration loader for RVNKCore database settings.
 * 
 * Loads database configuration from config.yml file,
 * supporting both SQLite and MySQL database configurations.
 * 
 * @since 1.0.0
 */
public class DatabaseConfigLoader {
    
    private final Plugin plugin;
    private final LogManager logger;
    private FileConfiguration config;
    
    /**
     * Constructor for DatabaseConfigLoader.
     * 
     * @param plugin The plugin instance
     */
    public DatabaseConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        loadConfiguration();
    }
    
    /**
     * Loads the database configuration from config.yml.
     * 
     * @return DatabaseConfig instance based on configured type
     * @throws IllegalStateException If configuration is invalid or missing
     */
    public DatabaseConfig loadDatabaseConfig() {
        String databaseType = config.getString("database.type", "sqlite").toLowerCase();
        
        logger.info("Loading database configuration for type: " + databaseType);
        
        switch (databaseType) {
            case "sqlite":
                return loadSQLiteConfig();
            case "mysql":
                return loadMySQLConfig();
            default:
                throw new IllegalStateException("Unsupported database type: " + databaseType + 
                                              ". Supported types: sqlite, mysql");
        }
    }
    
    /**
     * Loads the plugin configuration from config.yml.
     */
    private void loadConfiguration() {
        try {
            plugin.reloadConfig();
            config = plugin.getConfig();
            
            if (config == null) {
                throw new IllegalStateException("Failed to load config.yml - configuration is null");
            }
            
            logger.info("Successfully loaded configuration from config.yml");
        } catch (Exception e) {
            logger.error("Failed to load configuration from config.yml", e);
            throw new IllegalStateException("Configuration loading failed", e);
        }
    }
    
    /**
     * Creates SQLite configuration from config.yml settings.
     * 
     * @return DatabaseConfig for SQLite
     */
    private DatabaseConfig loadSQLiteConfig() {
        String databaseFile = config.getString("database.sqlite.file", "rvnkcore.db");
        
        logger.info("Configuring SQLite database: " + databaseFile);
        
        return DatabaseConfig.builder()
                .type("sqlite")
                .database(databaseFile)
                .build();
    }
    
    /**
     * Creates MySQL configuration from config.yml settings.
     * 
     * @return DatabaseConfig for MySQL
     * @throws IllegalStateException If required MySQL settings are missing
     */
    private DatabaseConfig loadMySQLConfig() {
        ConfigurationSection mysqlSection = config.getConfigurationSection("database.mysql");
        if (mysqlSection == null) {
            throw new IllegalStateException("MySQL configuration section not found in config.yml");
        }
        
        // Required settings
        String host = mysqlSection.getString("host");
        int port = mysqlSection.getInt("port", 3306);
        String database = mysqlSection.getString("database");
        String username = mysqlSection.getString("username");
        String password = mysqlSection.getString("password");
        
        // Validate required settings
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("MySQL host is required but not configured in config.yml");
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalStateException("MySQL database name is required but not configured in config.yml");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("MySQL username is required but not configured in config.yml");
        }
        if (password == null) {
            logger.warning("MySQL password is empty - this may cause connection issues");
            password = "";
        }
        
        // Optional settings
        boolean useSSL = mysqlSection.getBoolean("useSSL", true);
        String connectionParameters = mysqlSection.getString("connectionParameters", "");
        
        // Connection pool settings
        ConfigurationSection poolSection = mysqlSection.getConfigurationSection("pool");
        DatabaseConfig.Builder builder = DatabaseConfig.builder()
                .type("mysql")
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .useSSL(useSSL)
                .connectionParameters(connectionParameters);
        
        if (poolSection != null) {
            builder.maxConnections(poolSection.getInt("maxConnections", 20))
                   .minIdleConnections(poolSection.getInt("minIdleConnections", 5))
                   .connectionTimeoutMs(poolSection.getLong("connectionTimeoutMs", 30000))
                   .idleTimeoutMs(poolSection.getLong("idleTimeoutMs", 600000))
                   .maxLifetimeMs(poolSection.getLong("maxLifetimeMs", 1800000))
                   .leakDetectionMs(poolSection.getLong("leakDetectionMs", 60000));
        }
        
        logger.info("Configuring MySQL database - Host: " + host + ":" + port + 
                   ", Database: " + database + ", SSL: " + useSSL);
        
        return builder.build();
    }
    
    /**
     * Reloads the configuration from disk.
     * This allows for dynamic configuration updates without server restart.
     */
    public void reloadConfiguration() {
        logger.info("Reloading database configuration from config.yml");
        loadConfiguration();
    }
    
    /**
     * Validates that the database configuration section exists.
     * 
     * @throws IllegalStateException If database configuration is missing
     */
    public void validateConfigurationExists() {
        if (!config.contains("database")) {
            throw new IllegalStateException("Database configuration section not found in config.yml. " +
                                          "Please ensure 'database:' section is present in your configuration.");
        }
        
        String databaseType = config.getString("database.type");
        if (databaseType == null || databaseType.trim().isEmpty()) {
            throw new IllegalStateException("Database type not specified in config.yml. " +
                                          "Please set 'database.type' to either 'sqlite' or 'mysql'.");
        }
    }
}
