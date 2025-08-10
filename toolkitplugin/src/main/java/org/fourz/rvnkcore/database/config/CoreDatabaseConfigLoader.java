package org.fourz.rvnkcore.database.config;

import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

/**
 * Configuration loader for RVNKCore database settings from config-core.yml.
 * 
 * Loads database configuration from the centralized core configuration file,
 * supporting both SQLite and MySQL database configurations.
 * 
 * @since 1.0.0
 */
public class CoreDatabaseConfigLoader {
    
    private final Plugin plugin;
    private final LogManager logger;
    private FileConfiguration coreConfig;
    
    /**
     * Constructor for CoreDatabaseConfigLoader.
     * 
     * @param plugin The plugin instance
     */
    public CoreDatabaseConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        loadCoreConfiguration();
    }
    
    /**
     * Constructor that accepts an existing core configuration.
     * 
     * @param plugin The plugin instance
     * @param coreConfig The pre-loaded core configuration
     */
    public CoreDatabaseConfigLoader(Plugin plugin, FileConfiguration coreConfig) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.coreConfig = coreConfig;
    }
    
    /**
     * Loads the database configuration from config-core.yml.
     * 
     * @return DatabaseConfig instance based on configured type
     * @throws IllegalStateException If configuration is invalid or missing
     */
    public DatabaseConfig loadDatabaseConfig() {
        String databaseType = coreConfig.getString("rvnkcore.database.type", "sqlite").toLowerCase();
        
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
     * Loads the core configuration from config-core.yml.
     */
    private void loadCoreConfiguration() {
        try {
            File coreConfigFile = new File(plugin.getDataFolder(), "config-core.yml");
            if (!coreConfigFile.exists()) {
                throw new IllegalStateException("config-core.yml not found. Please ensure the file exists in the plugin data folder.");
            }
            
            coreConfig = YamlConfiguration.loadConfiguration(coreConfigFile);
            
            if (coreConfig == null) {
                throw new IllegalStateException("Failed to load config-core.yml - configuration is null");
            }
            
            logger.info("Successfully loaded core configuration from config-core.yml");
        } catch (Exception e) {
            logger.error("Failed to load core configuration from config-core.yml", e);
            throw new IllegalStateException("Core configuration loading failed", e);
        }
    }
    
    /**
     * Creates SQLite configuration from config-core.yml settings.
     * 
     * @return DatabaseConfig for SQLite
     */
    private DatabaseConfig loadSQLiteConfig() {
        String databaseFile = coreConfig.getString("rvnkcore.database.sqlite.file", "rvnkcore.db");
        
        logger.info("Configuring SQLite database: " + databaseFile);
        
        return DatabaseConfig.builder()
                .type("sqlite")
                .database(databaseFile)
                .build();
    }
    
    /**
     * Creates MySQL configuration from config-core.yml settings.
     * 
     * @return DatabaseConfig for MySQL
     * @throws IllegalStateException If required MySQL settings are missing
     */
    private DatabaseConfig loadMySQLConfig() {
        ConfigurationSection mysqlSection = coreConfig.getConfigurationSection("rvnkcore.database.mysql");
        if (mysqlSection == null) {
            throw new IllegalStateException("MySQL configuration section not found in config-core.yml");
        }
        
        // Required settings
        String host = mysqlSection.getString("host");
        int port = mysqlSection.getInt("port", 3306);
        String database = mysqlSection.getString("database");
        String username = mysqlSection.getString("username");
        String password = mysqlSection.getString("password");
        
        // Validate required settings
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("MySQL host is required but not configured in config-core.yml");
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalStateException("MySQL database name is required but not configured in config-core.yml");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("MySQL username is required but not configured in config-core.yml");
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
     * Reloads the core configuration from disk.
     * This allows for dynamic configuration updates without server restart.
     */
    public void reloadConfiguration() {
        logger.info("Reloading core database configuration from config-core.yml");
        loadCoreConfiguration();
    }
    
    /**
     * Validates that the database configuration section exists.
     * 
     * @throws IllegalStateException If database configuration is missing
     */
    public void validateConfigurationExists() {
        if (!coreConfig.contains("rvnkcore.database")) {
            throw new IllegalStateException("Database configuration section not found in config-core.yml. " +
                                          "Please ensure 'rvnkcore.database:' section is present in your configuration.");
        }
        
        String databaseType = coreConfig.getString("rvnkcore.database.type");
        if (databaseType == null || databaseType.trim().isEmpty()) {
            throw new IllegalStateException("Database type not specified in config-core.yml. " +
                                          "Please set 'rvnkcore.database.type' to either 'sqlite' or 'mysql'.");
        }
    }
}
