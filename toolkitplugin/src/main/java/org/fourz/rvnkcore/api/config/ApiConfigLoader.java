package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Configuration loader for API settings using Bukkit/Spigot methodology.
 * 
 * Ensures proper configuration file creation from resources and validates
 * configuration against expected API configuration structure.
 */
public class ApiConfigLoader {
    
    private final Plugin plugin;
    private final LogManager logger;
    
    public ApiConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
    }
    
    /**
     * Ensures config.yml exists and is properly initialized from resources.
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
        
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        // If config doesn't exist, copy from resources
        if (!configFile.exists()) {
            logger.info("config.yml not found, creating from default resources");
            copyDefaultConfig(configFile);
        }
        
        // Load and validate configuration
        plugin.reloadConfig();
        validateConfiguration();
    }
    
    /**
     * Copies the default config.yml from plugin resources to the data folder.
     * 
     * @param configFile The target config file
     */
    private void copyDefaultConfig(File configFile) {
        try (InputStream resourceStream = plugin.getResource("config.yml")) {
            if (resourceStream == null) {
                logger.error("Default config.yml not found in plugin resources");
                createMinimalConfig(configFile);
                return;
            }
            
            Files.copy(resourceStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Default configuration copied to: " + configFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to copy default config.yml from resources", e);
            createMinimalConfig(configFile);
        }
    }
    
    /**
     * Creates a minimal configuration file if resource copy fails.
     * 
     * @param configFile The target config file
     */
    private void createMinimalConfig(File configFile) {
        try {
            // Use Bukkit's saveDefaultConfig if available
            plugin.saveDefaultConfig();
            logger.info("Created default configuration using saveDefaultConfig()");
        } catch (Exception e) {
            logger.error("Failed to create default configuration", e);
            // Fall back to manual creation if needed
            createFallbackConfig(configFile);
        }
    }
    
    /**
     * Creates a fallback configuration with essential settings.
     * 
     * @param configFile The target config file
     */
    private void createFallbackConfig(File configFile) {
        StringBuilder fallbackConfig = new StringBuilder();
        fallbackConfig.append("# RVNKTools Configuration\n");
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
        fallbackConfig.append("  https:\n");
        fallbackConfig.append("    enabled: false\n");
        fallbackConfig.append("    port: 8081\n");
        fallbackConfig.append("  auth:\n");
        fallbackConfig.append("    key: changeme\n");
        
        try {
            Files.write(configFile.toPath(), fallbackConfig.toString().getBytes());
            logger.info("Created fallback configuration file");
        } catch (IOException e) {
            logger.error("Failed to create fallback configuration", e);
        }
    }
    
    /**
     * Validates the current configuration against API requirements.
     * Logs warnings for missing or invalid configurations.
     */
    public void validateConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        if (config == null) {
            logger.error("Configuration is null after loading");
            return;
        }
        
        // Validate API configuration structure
        validateApiConfiguration(config);
        
        // Validate database configuration
        validateDatabaseConfiguration(config);
        
        // Validate logging configuration
        validateLoggingConfiguration(config);
    }
    
    /**
     * Validates API-specific configuration sections.
     * 
     * @param config The configuration to validate
     */
    private void validateApiConfiguration(FileConfiguration config) {
        if (!config.contains("api")) {
            logger.warning("API configuration section missing - API features will use defaults");
            return;
        }
        
        // Check for deprecated or missing settings
        String[] expectedPaths = {
            "api.enabled",
            "api.host",
            "api.http.port",
            "api.https.port",
            "api.auth.key",
            "api.cors.enabled",
            "api.server.max-threads"
        };
        
        for (String path : expectedPaths) {
            if (!config.contains(path)) {
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
            if (config.contains(path)) {
                logger.warning("Deprecated API configuration found: " + path + " (will be ignored)");
            }
        }
        
        // Validate port ranges
        int httpPort = config.getInt("api.http.port", 8080);
        int httpsPort = config.getInt("api.https.port", 8081);
        
        if (httpPort <= 0 || httpPort > 65535) {
            logger.error("Invalid HTTP port in configuration: " + httpPort);
        }
        
        if (httpsPort <= 0 || httpsPort > 65535) {
            logger.error("Invalid HTTPS port in configuration: " + httpsPort);
        }
        
        if (httpPort == httpsPort) {
            logger.error("HTTP and HTTPS ports cannot be the same: " + httpPort);
        }
    }
    
    /**
     * Validates database configuration sections.
     * 
     * @param config The configuration to validate
     */
    private void validateDatabaseConfiguration(FileConfiguration config) {
        if (!config.contains("database")) {
            logger.warning("Database configuration section missing - using SQLite defaults");
            return;
        }
        
        String dbType = config.getString("database.type", "sqlite");
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
                if (!config.contains(path) || config.getString(path, "").trim().isEmpty()) {
                    logger.error("Required MySQL configuration missing: " + path);
                }
            }
        }
    }
    
    /**
     * Validates logging configuration.
     * 
     * @param config The configuration to validate
     */
    private void validateLoggingConfiguration(FileConfiguration config) {
        String logLevel = config.getString("logging.level", "INFO");
        String[] validLevels = {"DEBUG", "INFO", "WARN", "WARNING", "ERROR"};
        
        boolean validLevel = false;
        for (String level : validLevels) {
            if (level.equalsIgnoreCase(logLevel)) {
                validLevel = true;
                break;
            }
        }
        
        if (!validLevel) {
            logger.warning("Invalid logging level: " + logLevel + " (valid: DEBUG, INFO, WARN, ERROR)");
        }
    }
    
    /**
     * Gets the API configuration instance after ensuring proper loading.
     * 
     * @return ApiConfig instance
     */
    public ApiConfig loadApiConfig() {
        ensureConfigExists();
        return new ApiConfig(plugin);
    }
}
