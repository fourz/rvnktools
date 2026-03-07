package org.fourz.rvnktools.logfilter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration management for the Log Filter.
 * 
 * Handles loading, validation, and access to filter configuration settings
 * including log levels, keyword filters, and debug options.
 * 
 * Configuration Structure:
 * <pre>
 * log-filter:
 *   enabled: true
 *   debug: false            # Enable debug logging for filter itself
 *   target-plugins:         # List of plugin names to filter
 *     - "DHSupport"         # Example: Distant Horizons plugin
 *     - "SomeOtherPlugin"   # Example: Another plugin
 *   keywords:               # List of keywords to filter
 *     - "[DHS] Received"    # Example: filter plugin messages
 *     - "LOD data"          # Example: filter LOD-related messages
 * </pre>
 * 
 * @since 1.1-alpha
 */
public class LogFilterConfig {
    
    private final JavaPlugin plugin;
    private final LogManager logger;
    
    // Configuration values
    private boolean enabled = true;
    private boolean debugEnabled = false;
    private List<String> filterKeywords = new ArrayList<>();
    private List<String> targetPlugins = new ArrayList<>();
    
    // Configuration section name
    private static final String CONFIG_SECTION = "log-filter";
    
    public LogFilterConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Loads configuration from the plugin's config.yml file.
     * Creates default configuration if section doesn't exist.
     */
    public void loadConfig() {
        try {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_SECTION);
            
            if (section == null) {
                // Create default configuration
                createDefaultConfig();
                logger.info("Created default Log Filter configuration");
                return;
            }
            
            // Load configuration values
            enabled = section.getBoolean("enabled", true);
            debugEnabled = section.getBoolean("debug", false);
            
            
            // Load keyword filters
            filterKeywords = section.getStringList("keywords");
            if (filterKeywords == null) {
                filterKeywords = new ArrayList<>();
            }
            
            // Load target plugins
            targetPlugins = section.getStringList("target-plugins");
            if (targetPlugins == null) {
                targetPlugins = new ArrayList<>();
            }
            
            // Validate and log configuration
            validateConfiguration();
            logConfigurationStatus();
            
        } catch (Exception e) {
            logger.error("Failed to load Log Filter configuration", e);
            // Use defaults on error
            loadDefaults();
        }
    }
    
    /**
     * Creates default configuration section in config.yml.
     */
    private void createDefaultConfig() {
        ConfigurationSection section = plugin.getConfig().createSection(CONFIG_SECTION);
        
        section.set("enabled", true);
        section.set("debug", false);
        
        // Create default target plugins
        List<String> defaultTargetPlugins = new ArrayList<>();
        defaultTargetPlugins.add("DHSupport");
        section.set("target-plugins", defaultTargetPlugins);
        
        // Create example keyword filters
        List<String> exampleKeywords = new ArrayList<>();
        exampleKeywords.add("[DHS] Received");
        section.set("keywords", exampleKeywords);
        
        // Save configuration
        plugin.saveConfig();
        
        // Load the values we just created
        enabled = true;
        debugEnabled = false;
        targetPlugins = new ArrayList<>(defaultTargetPlugins);
        filterKeywords = new ArrayList<>(exampleKeywords);
    }
    
    /**
     * Loads default configuration values when config loading fails.
     */
    private void loadDefaults() {
        enabled = true;
        debugEnabled = false;
        filterKeywords = new ArrayList<>();
        targetPlugins = new ArrayList<>();
        logger.warning("Using default Log Filter configuration due to load error");
    }
    
    
    /**
     * Validates the loaded configuration and logs any issues.
     */
    private void validateConfiguration() {
        // Validate keyword filters
        if (filterKeywords != null) {
            filterKeywords.removeIf(keyword -> keyword == null || keyword.trim().isEmpty());
        }
        
    }
    
    /**
     * Logs the current configuration status for debugging.
     */
    private void logConfigurationStatus() {
        if (debugEnabled) {
            logger.debug("Log Filter Configuration:");
            logger.debug("  Enabled: " + enabled);
            logger.debug("  Debug: " + debugEnabled);
            logger.debug("  Keywords: " + filterKeywords.size() + " filters");
            for (int i = 0; i < filterKeywords.size(); i++) {
                logger.debug("    [" + i + "] " + filterKeywords.get(i));
            }
        } else {
            logger.info("Log Filter loaded - Keywords: " + filterKeywords.size() + " filters");
        }
    }
    
    
    /**
     * Get the list of target plugins to filter
     * @return List of plugin names to target for filtering
     */
    public List<String> getTargetPlugins() {
        return new ArrayList<>(targetPlugins);
    }

    /**
     * Checks if the filter is enabled.
     * 
     * @return true if the filter should be active
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if debug logging is enabled for the filter itself.
     * 
     * @return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    
    
    /**
     * Checks if any keyword filters are configured.
     * 
     * @return true if keyword filters exist
     */
    public boolean hasKeywordFilters() {
        return filterKeywords != null && !filterKeywords.isEmpty();
    }
    
    /**
     * Gets the list of keyword filters.
     * 
     * @return List of keyword strings to filter
     */
    public List<String> getFilterKeywords() {
        return new ArrayList<>(filterKeywords); // Return copy to prevent modification
    }
    
    /**
     * Adds a keyword filter at runtime.
     * 
     * @param keyword The keyword to filter
     */
    public void addKeywordFilter(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            if (!filterKeywords.contains(keyword)) {
                filterKeywords.add(keyword);
                logger.info("Added Log Filter keyword: " + keyword);
            }
        }
    }
    
    /**
     * Removes a keyword filter at runtime.
     * 
     * @param keyword The keyword to remove
     * @return true if the keyword was removed
     */
    public boolean removeKeywordFilter(String keyword) {
        if (filterKeywords.remove(keyword)) {
            logger.info("Removed Log Filter keyword: " + keyword);
            return true;
        }
        return false;
    }
    
    
    /**
     * Enables or disables the filter at runtime.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Log Filter " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Gets a summary of the current configuration for display purposes.
     * 
     * @return Configuration summary string
     */
    public String getConfigSummary() {
        return String.format("Log Filter: %s, Keywords: %d", 
                           enabled ? "Enabled" : "Disabled",
                           filterKeywords.size());
    }
}
