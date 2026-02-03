package org.fourz.rvnkcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.database.config.DatabaseConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified configuration loader for RVNKCore components.
 * 
 * Handles loading and validation of API and database configuration from config-core.yml,
 * ensuring proper configuration file creation using Bukkit/Spigot methodology.
 * Uses singleton pattern per plugin to prevent duplicate loading.
 * 
 * @since 1.0.0
 */
public class ConfigLoader {
    
    // Singleton instances per plugin
    private static final ConcurrentHashMap<String, ConfigLoader> instances = new ConcurrentHashMap<>();
    
    private final Plugin plugin;
    private final LogManager logger;
    private FileConfiguration coreConfig;
    private final String configFileName = "config-core.yml";
    
    // Cached configurations to prevent duplicate loading
    private DatabaseConfig cachedDatabaseConfig;
    private ApiConfig cachedApiConfig;
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    
    private ConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Gets the singleton ConfigLoader instance for a plugin.
     * 
     * @param plugin The plugin instance
     * @return The ConfigLoader instance for this plugin
     */
    public static ConfigLoader getInstance(Plugin plugin) {
        return instances.computeIfAbsent(plugin.getName(), k -> new ConfigLoader(plugin));
    }
    
    /**
     * Ensures config-core.yml exists and is properly initialized from resources.
     * Uses standard Bukkit/Spigot methodology for configuration handling.
     * Thread-safe and idempotent - only loads once.
     */
    public void ensureConfigExists() {
        if (initialized && coreConfig != null) {
            logger.debug("Core configuration already initialized; skipping re-load");
            return;
        }
        
        synchronized (initLock) {
            if (initialized && coreConfig != null) {
                logger.debug("Core configuration already initialized (within lock); skipping re-load");
                return;
            }
            
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
            
            // Load and validate configuration once
            loadConfig();
            validateConfiguration();
            applyCoreLoggingSettings();
            initialized = true;
        }
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
     * Applies core logging settings from the configuration.
     * Sets the global log level on both LogManager implementations
     * for all existing and future instances.
     */
    @SuppressWarnings("deprecation")
    private void applyCoreLoggingSettings() {
        String coreLogLevel = coreConfig.getString("logging.level", "INFO");
        Level level = LogManager.parseLevel(coreLogLevel);

        // Use plugin logger directly to guarantee visibility regardless of LogManager state
        plugin.getLogger().info("[ConfigLoader] Applying global logging level: "
            + coreLogLevel + " -> " + level.getName());

        // Apply to rvnkcore LogManager (primary - used by all plugins)
        LogManager.setGlobalLogLevel(level);

        // Apply to deprecated rvnktools LogManager (for any remaining legacy instances)
        org.fourz.rvnktools.util.log.LogManager.setGlobalLogLevel(level);
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
     * Validates API-specific configuration sections with comprehensive checks.
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
        
        // Check for deprecated settings that should be removed/renamed
        String[] deprecatedPaths = {
            "api.server.min-threads",
            "api.server.queue-size",
            "api.auth.enabled"
        };
        
        for (String path : deprecatedPaths) {
            if (coreConfig.contains(path)) {
                logger.warning("Deprecated API configuration found: " + path + " (will be ignored)");
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
        
        // Validate API key security
        String apiKey = coreConfig.getString("api.auth.key", "changeme");
        if ("changeme".equals(apiKey)) {
            logger.warning("API key is set to default value 'changeme' - please change for security");
        }
        
        // Validate HTTPS configuration if enabled
        boolean httpsEnabled = coreConfig.getBoolean("api.https.enabled", false);
        if (httpsEnabled) {
            String keystorePath = coreConfig.getString("api.https.keystore-path", "");
            if (keystorePath.trim().isEmpty()) {
                logger.error("HTTPS enabled but keystore path not specified");
            }
        }
    }
    
    /**
     * Validates database configuration sections with detailed error checking.
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
            
            // Validate MySQL pool configuration if present
            if (coreConfig.contains("database.mysql.pool")) {
                int maxConnections = coreConfig.getInt("database.mysql.pool.maxConnections", 20);
                int minIdleConnections = coreConfig.getInt("database.mysql.pool.minIdleConnections", 5);
                
                if (maxConnections <= 0) {
                    logger.error("Invalid MySQL pool maxConnections: " + maxConnections);
                }
                
                if (minIdleConnections < 0 || minIdleConnections > maxConnections) {
                    logger.error("Invalid MySQL pool minIdleConnections: " + minIdleConnections + 
                               " (must be >= 0 and <= maxConnections)");
                }
            }
        }
    }
    
    /**
     * Validates logging configuration with level validation.
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
        return LogManager.parseLevel(logLevel);
    }
    
    /**
     * Gets the API configuration instance with caching.
     * 
     * @return ApiConfig instance
     */
    public ApiConfig getApiConfig() {
        // Return cached config if available
        if (cachedApiConfig != null) {
            logger.debug("Using cached API configuration");
            return cachedApiConfig;
        }
        
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        // Get the API configuration section
        ConfigurationSection apiSection = coreConfig.getConfigurationSection("api");
        if (apiSection == null) {
            logger.warning("API configuration section missing - creating default config");
            // Create a minimal API section
            coreConfig.createSection("api");
            coreConfig.set("api.enabled", false);
            coreConfig.set("api.host", "localhost");
            coreConfig.set("api.http.port", 8080);
            coreConfig.set("api.https.port", 8081);
            coreConfig.set("api.auth.key", "changeme");
            apiSection = coreConfig.getConfigurationSection("api");
        }
        
        // Get global log level for API config
        String globalLogLevel = coreConfig.getString("logging.level", "INFO");
        
        // Create ApiConfig using static factory method and cache it
        cachedApiConfig = ApiConfig.fromConfigurationSection(plugin, apiSection, globalLogLevel);
        logger.debug("API configuration loaded and cached");
        return cachedApiConfig;
    }
    
    /**
     * Gets the database configuration instance with caching.
     * 
     * @return DatabaseConfig instance
     */
    public DatabaseConfig getDatabaseConfig() {
        // Return cached config if available
        if (cachedDatabaseConfig != null) {
            logger.debug("Using cached database configuration");
            return cachedDatabaseConfig;
        }
        
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        // Get database type
        String dbType = coreConfig.getString("database.type", "sqlite");
        
        // Build DatabaseConfig based on type
        if ("mysql".equalsIgnoreCase(dbType)) {
            cachedDatabaseConfig = DatabaseConfig.builder()
                .type("mysql")
                .host(coreConfig.getString("database.mysql.host", "localhost"))
                .port(coreConfig.getInt("database.mysql.port", 3306))
                .database(coreConfig.getString("database.mysql.database", "rvnkcore"))
                .username(coreConfig.getString("database.mysql.username", ""))
                .password(coreConfig.getString("database.mysql.password", ""))
                .useSSL(coreConfig.getBoolean("database.mysql.useSSL", true))
                .connectionParameters(coreConfig.getString("database.mysql.connectionParameters", ""))
                // Connection Pool Configuration
                .maxConnections(coreConfig.getInt("database.mysql.pool.maxConnections", 20))
                .minIdleConnections(coreConfig.getInt("database.mysql.pool.minIdleConnections", 5))
                .connectionTimeoutMs(coreConfig.getLong("database.mysql.pool.connectionTimeoutMs", 30000L))
                .idleTimeoutMs(coreConfig.getLong("database.mysql.pool.idleTimeoutMs", 600000L))
                .maxLifetimeMs(coreConfig.getLong("database.mysql.pool.maxLifetimeMs", 1800000L))
                .leakDetectionMs(coreConfig.getLong("database.mysql.pool.leakDetectionMs", 60000L))
                .build();
        } else {
            // Default to SQLite
            String databaseFile = coreConfig.getString("database.sqlite.file", "rvnkcore.db");
            cachedDatabaseConfig = DatabaseConfig.builder()
                .type("sqlite")
                .database(databaseFile)
                .build();
        }
        
        logger.debug("Database configuration loaded and cached: " + dbType);
        return cachedDatabaseConfig;
    }
    
    /**
     * Reloads the configuration from disk.
     * Invalidates all cached configurations to force fresh parsing.
     */
    public void reloadConfiguration() {
        logger.info("Reloading core configuration from " + configFileName);
        loadConfig();
        validateConfiguration();
        applyCoreLoggingSettings();
        
        // Invalidate caches so subsequent calls re-parse from the fresh config
        cachedApiConfig = null;
        cachedDatabaseConfig = null;
    }
    
    /**
     * Returns whether the core configuration has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Performs comprehensive validation of the current configuration.
     * 
     * @return true if configuration is valid, false if issues were found
     */
    public boolean validateConfigurationPublic() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        boolean isValid = true;
        
        // Validate each section
        try {
            validateApiConfiguration();
            validateDatabaseConfiguration(); 
            validateLoggingConfiguration();
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            isValid = false;
        }
        
        logger.info("Configuration validation completed");
        return isValid;
    }
    
    /**
     * Gets a comprehensive summary of the current configuration for debugging.
     * 
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        if (coreConfig == null) {
            return "Core configuration not loaded";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("RVNKCore Configuration Summary:\n");
        
        // Logging configuration
        String logLevel = coreConfig.getString("logging.level", "INFO");
        summary.append("  Logging Level: ").append(logLevel).append("\n");
        
        // Database configuration
        String dbType = coreConfig.getString("database.type", "sqlite");
        summary.append("  Database Type: ").append(dbType).append("\n");
        
        if ("sqlite".equalsIgnoreCase(dbType)) {
            String dbFile = coreConfig.getString("database.sqlite.file", "rvnkcore.db");
            summary.append("    SQLite File: ").append(dbFile).append("\n");
        } else if ("mysql".equalsIgnoreCase(dbType)) {
            String host = coreConfig.getString("database.mysql.host", "localhost");
            int port = coreConfig.getInt("database.mysql.port", 3306);
            String database = coreConfig.getString("database.mysql.database", "");
            summary.append("    MySQL Host: ").append(host).append(":").append(port).append("\n");
            summary.append("    MySQL Database: ").append(database).append("\n");
        }
        
        // API configuration
        boolean apiEnabled = coreConfig.getBoolean("api.enabled", false);
        summary.append("  API Enabled: ").append(apiEnabled).append("\n");
        
        if (apiEnabled) {
            String host = coreConfig.getString("api.host", "localhost");
            int httpPort = coreConfig.getInt("api.http.port", 8080);
            boolean httpsEnabled = coreConfig.getBoolean("api.https.enabled", false);
            int httpsPort = coreConfig.getInt("api.https.port", 8081);
            
            summary.append("    API Host: ").append(host).append("\n");
            summary.append("    HTTP Port: ").append(httpPort).append("\n");
            summary.append("    HTTPS Enabled: ").append(httpsEnabled);
            if (httpsEnabled) {
                summary.append(" (Port: ").append(httpsPort).append(")");
            }
            summary.append("\n");
            
            boolean corsEnabled = coreConfig.getBoolean("api.cors.enabled", true);
            summary.append("    CORS Enabled: ").append(corsEnabled).append("\n");
        }
        
        return summary.toString();
    }
}
