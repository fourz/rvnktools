package org.fourz.rvnktools.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.logging.Level;

/**
 * Configuration loader for RVNKTools plugin settings.
 * 
 * Handles loading and validation of RVNKTools-specific configuration from config.yml,
 * ensuring proper configuration file creation using Bukkit/Spigot methodology.
 * 
 * @since 1.0.0
 */
public class ConfigLoader {
    
    private final Plugin plugin;
    private final LogManager logger;
    private Config config;
    
    public ConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Ensures config.yml exists and is properly initialized from resources.
     * Uses standard Bukkit/Spigot methodology for configuration handling.
     */
    public void ensureConfigExists() {
        // Use Bukkit's standard configuration handling
        plugin.saveDefaultConfig();
        
        // Load and validate configuration
        loadConfig();
        validateConfiguration();
    }
    
    /**
     * Loads the plugin configuration from disk.
     */
    private void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration fileConfig = plugin.getConfig();
        config = new Config(fileConfig, logger);
        logger.info("RVNKTools configuration loaded successfully");
    }
    
    /**
     * Validates the current configuration and logs any issues.
     */
    private void validateConfiguration() {
        if (config == null) {
            logger.error("Configuration is null after loading");
            return;
        }
        
        boolean isValid = config.validateConfiguration();
        if (!isValid) {
            logger.warning("Configuration validation found issues - check settings");
        }
    }
    
    /**
     * Gets the current configuration instance.
     * 
     * @return Config instance
     */
    public Config getConfig() {
        if (config == null) {
            ensureConfigExists();
        }
        return config;
    }
    
    /**
     * Gets the RVNKTools log level from configuration.
     * 
     * @return The configured log level for RVNKTools
     */
    public Level getLogLevel() {
        if (config == null) {
            ensureConfigExists();
        }
        return config.getLogLevel();
    }
    
    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfiguration() {
        logger.info("Reloading RVNKTools configuration");
        loadConfig();
        validateConfiguration();
    }
    
    /**
     * Gets a configuration summary for debugging purposes.
     * 
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        if (config == null) {
            return "Configuration not loaded";
        }
        return config.getConfigurationSummary();
    }
    
    /**
     * Checks if a specific feature is enabled in the configuration.
     * 
     * @param featureName The name of the feature to check
     * @return true if the feature is enabled, false otherwise
     */
    public boolean isFeatureEnabled(String featureName) {
        if (config == null) {
            return true; // Default to enabled if config not loaded
        }
        
        switch (featureName.toLowerCase()) {
            case "announcements":
                return config.isAnnouncementsEnabled();
            case "hat":
            case "hat-command":
                return config.isHatCommandEnabled();
            case "link":
            case "link-command":
                return config.isLinkCommandEnabled();
            case "worldswap":
            case "world-swap":
                return config.isWorldSwapEnabled();
            default:
                logger.warning("Unknown feature name for enablement check: " + featureName);
                return true; // Default to enabled for unknown features
        }
    }
    
    /**
     * Checks if LogFilter is enabled and configured properly.
     * 
     * @return true if LogFilter should be active
     */
    public boolean isLogFilterActive() {
        if (config == null) {
            return true; // Default to active if config not loaded
        }
        return config.isLogFilterEnabled();
    }
}
