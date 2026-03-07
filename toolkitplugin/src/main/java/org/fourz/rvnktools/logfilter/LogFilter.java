package org.fourz.rvnktools.logfilter;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Log filter for server plugins to reduce console verbosity.
 * 
 * This filter intercepts log messages from configured server plugins
 * and applies configurable filters based on keyword patterns.
 * 
 * Features:
 * - Configurable target plugins
 * - Keyword-based filtering with pattern matching
 * - Dynamic configuration reloading
 * - Performance-optimized filtering
 * - Integration with RVNKTools logging framework
 * 
 * @since 1.1-alpha
 */
public class LogFilter extends Handler {
    
    private final JavaPlugin plugin;
    private final LogManager logger;
    private LogFilterConfig config;
    private boolean filterEnabled = false;
    private Logger targetLogger;
    
    public LogFilter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.config = new LogFilterConfig(plugin);
        
        initialize();
    }
    
    /**
     * Initializes the log filter by locating and attaching to the target plugin logger.
     */
    private void initialize() {
        try {
            // Load configuration
            config.loadConfig();
            
            // Find the target plugin
            Plugin targetPlugin = findTargetPlugin();
            if (targetPlugin == null) {
                logger.info("Target plugin not found - filter will activate when plugin loads");
                return;
            }
            
            // Attach to the target logger
            attachToTargetLogger(targetPlugin);
            
            logger.info("Log Filter initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Log Filter", e);
        }
    }
    
    /**
     * Finds the target plugin instance based on configuration.
     * 
     * @return The target plugin instance, or null if not found
     */
    private Plugin findTargetPlugin() {
        // Get target plugins from configuration
        List<String> targetPlugins = config.getTargetPlugins();
        
        for (String name : targetPlugins) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                logger.info("Found target plugin: " + plugin.getName() + " v" + plugin.getDescription().getVersion());
                return plugin;
            }
        }
        
        return null;
    }
    
    /**
     * Attaches this filter to the target plugin logger.
     * 
     * @param targetPlugin The target plugin instance
     */
    private void attachToTargetLogger(Plugin targetPlugin) {
        try {
            targetLogger = targetPlugin.getLogger();
            
            // Remove existing handlers to prevent duplicate logging
            Handler[] handlers = targetLogger.getHandlers();
            for (Handler handler : handlers) {
                if (handler instanceof LogFilter) {
                    targetLogger.removeHandler(handler);
                }
            }
            
            // Add our filter
            targetLogger.addHandler(this);
            filterEnabled = true;
            
            logger.info("Attached Log Filter to " + targetPlugin.getName() + " logger");
            
        } catch (Exception e) {
            logger.error("Failed to attach to target plugin logger", e);
        }
    }
    
    /**
     * Processes log records from target plugins and applies filtering.
     * 
     * @param record The log record to process
     */
    @Override
    public void publish(LogRecord record) {
        if (!filterEnabled || record == null) {
            return;
        }
        
        try {
            // Apply filtering logic
            if (shouldFilterMessage(record)) {
                // Message is filtered - don't log it
                return;
            }
            
            // Message passes filter - allow it to be logged normally
            // The original handlers will process it
            
        } catch (Exception e) {
            // Don't let filter errors break logging
            logger.debug("Error in DH log filter: " + e.getMessage());
        }
    }
    
    /**
     * Determines if a log message should be filtered out.
     * 
     * @param record The log record to evaluate
     * @return true if the message should be filtered (not logged), false otherwise
     */
    private boolean shouldFilterMessage(LogRecord record) {
        String message = record.getMessage();
        
        // Check keyword filters
        if (config.hasKeywordFilters()) {
            List<String> keywords = config.getFilterKeywords();
            
            for (String keyword : keywords) {
                if (message != null && message.contains(keyword)) {
                    // Message contains filtered keyword
                    if (config.isDebugEnabled()) {
                        logger.debug("Filtered plugin message: " + keyword + " -> " + message);
                    }
                    return true; // Filter out
                }
            }
        }
        
        // Message passes all filters
        return false;
    }
    
    /**
     * Attempts to dynamically attach to target plugin if it loads after RVNKTools.
     * Should be called from plugin enable/load events.
     */
    public void tryAttachToTargetPlugins() {
        if (!filterEnabled) {
            Plugin targetPlugin = findTargetPlugin();
            if (targetPlugin != null) {
                attachToTargetLogger(targetPlugin);
            }
        }
    }
    
    /**
     * Reloads the filter configuration.
     */
    public void reloadConfig() {
        try {
            config.loadConfig();
            logger.info("Log Filter configuration reloaded");
        } catch (Exception e) {
            logger.error("Failed to reload Log Filter configuration", e);
        }
    }
    
    /**
     * Gets the current filter configuration.
     * 
     * @return The filter configuration
     */
    public LogFilterConfig getConfig() {
        return config;
    }
    
    /**
     * Checks if the filter is currently active.
     * 
     * @return true if the filter is enabled and attached to target plugin logger
     */
    public boolean isFilterEnabled() {
        return filterEnabled;
    }
    
    /**
     * Shuts down the log filter and cleans up resources.
     */
    public void shutdown() {
        try {
            if (targetLogger != null && filterEnabled) {
                targetLogger.removeHandler(this);
                filterEnabled = false;
                logger.info("Log Filter detached and shut down");
            }
        } catch (Exception e) {
            logger.error("Error during Log Filter shutdown", e);
        }
    }
    
    @Override
    public void flush() {
        // No buffering, nothing to flush
    }
    
    @Override
    public void close() throws SecurityException {
        shutdown();
    }
}
