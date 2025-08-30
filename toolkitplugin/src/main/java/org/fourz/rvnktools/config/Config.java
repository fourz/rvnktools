package org.fourz.rvnktools.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.Debug;

import java.util.logging.Level;

/**
 * Configuration data holder for RVNKTools plugin settings.
 * 
 * Provides type-safe access to configuration values loaded from config.yml.
 * Includes validation and default value handling.
 */
public class Config {
    
    // Logging configuration
    private final Level logLevel;
    
    // LogFilter configuration
    private final boolean logFilterEnabled;
    private final boolean logFilterShowSpigot;
    private final boolean logFilterShowBukkit;
    private final boolean logFilterShowCraftBukkit;
    private final boolean logFilterShowPaper;
    private final boolean logFilterShowPlugins;
    
    // Plugin feature toggles
    private final boolean enableAnnouncements;
    private final boolean enableHatCommand;
    private final boolean enableLinkCommand;
    private final boolean enableWorldSwap;
    
    private final LogManager logger;

    /**
     * Creates Config from FileConfiguration with validation.
     *
     * @param config The loaded configuration
     * @param logger LogManager instance for logging
     */
    public Config(FileConfiguration config, LogManager logger) {
        this.logger = logger;
        
        // Load logging configuration
        String logLevelStr = config.getString("logging.level", "INFO");
        this.logLevel = Debug.getLevel(logLevelStr);
        
        // Load LogFilter configuration
        this.logFilterEnabled = config.getBoolean("logfilter.enabled", true);
        this.logFilterShowSpigot = config.getBoolean("logfilter.show.spigot", false);
        this.logFilterShowBukkit = config.getBoolean("logfilter.show.bukkit", false);
        this.logFilterShowCraftBukkit = config.getBoolean("logfilter.show.craftbukkit", false);
        this.logFilterShowPaper = config.getBoolean("logfilter.show.paper", false);
        this.logFilterShowPlugins = config.getBoolean("logfilter.show.plugins", true);
        
        // Load feature toggles
        this.enableAnnouncements = config.getBoolean("features.announcements", true);
        this.enableHatCommand = config.getBoolean("features.hat-command", true);
        this.enableLinkCommand = config.getBoolean("features.link-command", true);
        this.enableWorldSwap = config.getBoolean("features.world-swap", true);
        
        // Log configuration summary
        logger.info("RVNKTools configuration loaded - Log Level: " + logLevelStr + 
                   ", LogFilter: " + (logFilterEnabled ? "enabled" : "disabled") +
                   ", Features: announcements=" + enableAnnouncements + 
                   ", hat=" + enableHatCommand + 
                   ", link=" + enableLinkCommand + 
                   ", worldswap=" + enableWorldSwap);
    }

    // Logging configuration getters
    public Level getLogLevel() { 
        return logLevel; 
    }
    
    // LogFilter configuration getters
    public boolean isLogFilterEnabled() { 
        return logFilterEnabled; 
    }
    
    public boolean isLogFilterShowSpigot() { 
        return logFilterShowSpigot; 
    }
    
    public boolean isLogFilterShowBukkit() { 
        return logFilterShowBukkit; 
    }
    
    public boolean isLogFilterShowCraftBukkit() { 
        return logFilterShowCraftBukkit; 
    }
    
    public boolean isLogFilterShowPaper() { 
        return logFilterShowPaper; 
    }
    
    public boolean isLogFilterShowPlugins() { 
        return logFilterShowPlugins; 
    }
    
    // Feature toggle getters
    public boolean isAnnouncementsEnabled() { 
        return enableAnnouncements; 
    }
    
    public boolean isHatCommandEnabled() { 
        return enableHatCommand; 
    }
    
    public boolean isLinkCommandEnabled() { 
        return enableLinkCommand; 
    }
    
    public boolean isWorldSwapEnabled() { 
        return enableWorldSwap; 
    }
    
    /**
     * Validates the current configuration and logs any issues.
     * 
     * @return true if configuration is valid, false if issues were found
     */
    public boolean validateConfiguration() {
        boolean isValid = true;
        
        // Validate log level
        if (logLevel == null) {
            logger.warning("Invalid log level in configuration - using INFO as default");
            isValid = false;
        }
        
        // Validate LogFilter settings - warn if all logging is disabled
        if (logFilterEnabled && !logFilterShowSpigot && !logFilterShowBukkit && 
            !logFilterShowCraftBukkit && !logFilterShowPaper && !logFilterShowPlugins) {
            logger.warning("LogFilter is enabled but all message types are disabled - no logs will be shown");
        }
        
        // Validate feature flags - warn if all features are disabled
        if (!enableAnnouncements && !enableHatCommand && !enableLinkCommand && !enableWorldSwap) {
            logger.warning("All plugin features are disabled - plugin functionality will be limited");
        }
        
        return isValid;
    }
    
    /**
     * Returns a summary of the current configuration for debugging.
     * 
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("RVNKTools Configuration Summary:\n");
        summary.append("  Logging Level: ").append(logLevel.getName()).append("\n");
        summary.append("  LogFilter: ").append(logFilterEnabled ? "enabled" : "disabled").append("\n");
        if (logFilterEnabled) {
            summary.append("    Show Spigot: ").append(logFilterShowSpigot).append("\n");
            summary.append("    Show Bukkit: ").append(logFilterShowBukkit).append("\n");
            summary.append("    Show CraftBukkit: ").append(logFilterShowCraftBukkit).append("\n");
            summary.append("    Show Paper: ").append(logFilterShowPaper).append("\n");
            summary.append("    Show Plugins: ").append(logFilterShowPlugins).append("\n");
        }
        summary.append("  Features:\n");
        summary.append("    Announcements: ").append(enableAnnouncements).append("\n");
        summary.append("    Hat Command: ").append(enableHatCommand).append("\n");
        summary.append("    Link Command: ").append(enableLinkCommand).append("\n");
        summary.append("    World Swap: ").append(enableWorldSwap).append("\n");
        
        return summary.toString();
    }
}
