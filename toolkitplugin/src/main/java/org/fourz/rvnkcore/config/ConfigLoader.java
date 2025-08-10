package org.fourz.rvnkcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.Debug;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.database.config.DatabaseConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Unified configuration loader for RVNKCore components.
 * 
 * Handles loading and validation of API and database configuration from config-core.yml,
 * ensuring proper configuration file creation using Bukkit/Spigot methodology.
 * 
 * @since 1.0.0
 */
public class ConfigLoader {
    
    private final Plugin plugin;
    private final LogManager logger;
    private FileConfiguration coreConfig;
    private final String configFileName = "config-core.yml";
    
    public ConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Ensures config-core.yml exists and is properly initialized from resources.
     * Uses standard Bukkit/Spigot methodology for configuration handling.
     */
    public void ensureConfigExists() {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            boolean created = plugin.getDataFolder().mkdirs();
            if (created) {
                logger.info("Created plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
            } else {
                logger.warning("Failed to create plugin data folder");
            }
        }
        
        File configFile = new File(plugin.getDataFolder(), configFileName);
        
        // If config doesn't exist, copy from resources
        if (!configFile.exists()) {
            logger.info(configFileName + " not found, creating from default resources");
            copyDefaultConfig(configFile);
        }
        
        // Load and validate configuration
        loadConfig();
        validateConfiguration();
    }
    
    /**
     * Loads the core configuration from disk.
     */
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), configFileName);
        coreConfig = YamlConfiguration.loadConfiguration(configFile);
        logger.info("Core configuration loaded from " + configFileName);
    }
    
    /**
     * Copies the default config-core.yml from plugin resources to the data folder.
     * 
     * @param configFile The target config file
     */
    private void copyDefaultConfig(File configFile) {
        try (InputStream resourceStream = plugin.getResource(configFileName)) {
            if (resourceStream == null) {
                logger.error("Default " + configFileName + " not found in plugin resources");
                createMinimalConfig(configFile);
                return;
            }
            
            Files.copy(resourceStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Default core configuration copied to: " + configFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to copy default " + configFileName + " from resources", e);
            createMinimalConfig(configFile);
        }
    }
    
    /**
     * Creates a minimal core configuration file if resource copy fails.
     * 
     * @param configFile The target config file
     */
    private void createMinimalConfig(File configFile) {
        StringBuilder fallbackConfig = new StringBuilder();
        fallbackConfig.append("# RVNKCore Configuration\n");
        fallbackConfig.append("# This file was auto-generated due to missing default configuration\n\n");
        fallbackConfig.append("logging:\n");
        fallbackConfig.append("  level: INFO\n\n");
        fallbackConfig.append("database:\n");
        fallbackConfig.append("  type: sqlite\n");
        fallbackConfig.append("  sqlite:\n");
        fallbackConfig.append("    file: rvnkcore.db\n\n");
        fallbackConfig.append("api:\n");
        fallbackConfig.append("  enabled: false\n");
        fallbackConfig.append("  host: localhost\n");
        fallbackConfig.append("  context-path: /api\n");
        fallbackConfig.append("  http:\n");
        fallbackConfig.append("    port: 8080\n");
        fallbackConfig.append("  auth:\n");
        fallbackConfig.append("    key: changeme\n");
        
        try {
            Files.write(configFile.toPath(), fallbackConfig.toString().getBytes());
            logger.info("Created fallback core configuration file");
        } catch (IOException e) {
            logger.error("Failed to create fallback core configuration", e);
        }
    }
    
    /**
     * Validates the current configuration against requirements.
     */
    private void validateConfiguration() {
        if (coreConfig == null) {
            logger.error("Core configuration is null after loading");
            return;
        }
        
        // Validate API configuration
        validateApiConfiguration();
        
        // Validate database configuration  
        validateDatabaseConfiguration();
        
        // Validate logging configuration
        validateLoggingConfiguration();
    }
    
    /**
     * Validates API-specific configuration sections.
     */
    private void validateApiConfiguration() {
        if (!coreConfig.contains("api")) {
            logger.warning("API configuration section missing - API features will use defaults");
            return;
        }
        
        // Check for required settings
        String[] expectedPaths = {
            "api.enabled",
            "api.host", 
            "api.http.port",
            "api.auth.key"
        };
        
        for (String path : expectedPaths) {
            if (!coreConfig.contains(path)) {
                logger.warning("Missing API configuration: " + path + " (using default value)");
            }
        }
        
        // Validate port ranges
        int httpPort = coreConfig.getInt("api.http.port", 8080);
        int httpsPort = coreConfig.getInt("api.https.port", 8081);
        
        if (httpPort <= 0 || httpPort > 65535) {
            logger.error("Invalid HTTP port in core configuration: " + httpPort);
        }
        
        if (httpsPort <= 0 || httpsPort > 65535) {
            logger.error("Invalid HTTPS port in core configuration: " + httpsPort);
        }
        
        if (httpPort == httpsPort) {
            logger.error("HTTP and HTTPS ports cannot be the same: " + httpPort);
        }
    }
    
    /**
     * Validates database configuration sections.
     */
    private void validateDatabaseConfiguration() {
        if (!coreConfig.contains("database")) {
            logger.warning("Database configuration section missing - using SQLite defaults");
            return;
        }
        
        String dbType = coreConfig.getString("database.type", "sqlite");
        if (!"sqlite".equalsIgnoreCase(dbType) && !"mysql".equalsIgnoreCase(dbType)) {
            logger.error("Unsupported database type: " + dbType + " (supported: sqlite, mysql)");
        }
        
        if ("mysql".equalsIgnoreCase(dbType)) {
            String[] requiredMysqlPaths = {
                "database.mysql.host",
                "database.mysql.database", 
                "database.mysql.username"
            };
            
            for (String path : requiredMysqlPaths) {
                if (!coreConfig.contains(path) || coreConfig.getString(path, "").trim().isEmpty()) {
                    logger.error("Required MySQL configuration missing: " + path);
                }
            }
        }
    }
    
    /**
     * Validates logging configuration.
     */
    private void validateLoggingConfiguration() {
        String logLevel = coreConfig.getString("logging.level", "INFO");
        String[] validLevels = {"DEBUG", "INFO", "WARN", "WARNING", "ERROR"};
        
        boolean validLevel = false;
        for (String level : validLevels) {
            if (level.equalsIgnoreCase(logLevel)) {
                validLevel = true;
                break;
            }
        }
        
        if (!validLevel) {
            logger.warning("Invalid RVNKCore logging level: " + logLevel + " (valid: DEBUG, INFO, WARN, ERROR)");
        }
    }
    
    /**
     * Gets the RVNKCore log level from configuration.
     * 
     * @return The configured log level
     */
    public Level getCoreLogLevel() {
        String logLevel = coreConfig.getString("logging.level", "INFO");
        return Debug.getLevel(logLevel);
    }
    
    /**
     * Gets the API configuration instance.
     * 
     * @return ApiConfig instance
     */
    public ApiConfig getApiConfig() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        return new ApiConfig(plugin, coreConfig);
    }
    
    /**
     * Gets the database configuration instance.
     * 
     * @return DatabaseConfig instance
     */
    public DatabaseConfig getDatabaseConfig() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        return loadDatabaseConfig();
    }
    
    /**
     * Loads database configuration from core config.
     * 
     * @return DatabaseConfig instance
     */
    private DatabaseConfig loadDatabaseConfig() {
        String databaseType = coreConfig.getString("database.type", "sqlite").toLowerCase();
        
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
     * Creates SQLite configuration from core config settings.
     * 
     * @return DatabaseConfig for SQLite
     */
    private DatabaseConfig loadSQLiteConfig() {
        String databaseFile = coreConfig.getString("database.sqlite.file", "rvnkcore.db");
        
        logger.info("Configuring SQLite database: " + databaseFile);
        
        return DatabaseConfig.builder()
                .type("sqlite")
                .database(databaseFile)
                .build();
    }
    
    /**
     * Creates MySQL configuration from core config settings.
     * 
     * @return DatabaseConfig for MySQL
     */
    private DatabaseConfig loadMySQLConfig() {
        String host = coreConfig.getString("database.mysql.host");
        int port = coreConfig.getInt("database.mysql.port", 3306);
        String database = coreConfig.getString("database.mysql.database");
        String username = coreConfig.getString("database.mysql.username");
        String password = coreConfig.getString("database.mysql.password");
        
        // Validate required settings
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("MySQL host is required but not configured in " + configFileName);
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalStateException("MySQL database name is required but not configured in " + configFileName);
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("MySQL username is required but not configured in " + configFileName);
        }
        if (password == null) {
            logger.warning("MySQL password is empty - this may cause connection issues");
            password = "";
        }
        
        // Optional settings
        boolean useSSL = coreConfig.getBoolean("database.mysql.useSSL", true);
        String connectionParameters = coreConfig.getString("database.mysql.connectionParameters", "");
        
        // Connection pool settings from core config
        DatabaseConfig.Builder builder = DatabaseConfig.builder()
                .type("mysql")
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .useSSL(useSSL)
                .connectionParameters(connectionParameters);
        
        // Load pool configuration if present
        if (coreConfig.contains("database.mysql.pool")) {
            builder.maxConnections(coreConfig.getInt("database.mysql.pool.maxConnections", 20))
                   .minIdleConnections(coreConfig.getInt("database.mysql.pool.minIdleConnections", 5))
                   .connectionTimeoutMs(coreConfig.getLong("database.mysql.pool.connectionTimeoutMs", 30000))
                   .idleTimeoutMs(coreConfig.getLong("database.mysql.pool.idleTimeoutMs", 600000))
                   .maxLifetimeMs(coreConfig.getLong("database.mysql.pool.maxLifetimeMs", 1800000))
                   .leakDetectionMs(coreConfig.getLong("database.mysql.pool.leakDetectionMs", 60000));
        }
        
        logger.info("Configuring MySQL database - Host: " + host + ":" + port + 
                   ", Database: " + database + ", SSL: " + useSSL);
        
        return builder.build();
    }
    
    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfiguration() {
        logger.info("Reloading core configuration from " + configFileName);
        loadConfig();
        validateConfiguration();
    }
}
