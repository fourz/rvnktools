package org.fourz.rvnktools.dhlogfilter.service;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.dhlogfilter.filter.DHLogFilter;
import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig;
import org.fourz.rvnktools.dhlogfilter.model.FilterStats;
import org.fourz.rvnktools.dhlogfilter.repository.DHLogFilterConfigRepository;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Default implementation of DHLogFilterService.
 * Manages the lifecycle and operations of the DH log filtering system with
 * asynchronous patterns and proper resource management.
 * 
 * @since 1.1-alpha
 */
public class DefaultDHLogFilterService implements DHLogFilterService {
    
    private final Plugin plugin;
    private final DHLogFilterConfigRepository configRepository;
    private final LogManager logger;
    private final Logger rootLogger;
    
    private volatile DHLogFilter logFilter;
    private volatile DHLogFilterConfig currentConfig;
    private volatile FilterStats stats;
    private volatile boolean filterApplied = false;
    
    /**
     * Constructor for DefaultDHLogFilterService.
     * 
     * @param plugin The plugin instance
     * @param configRepository The configuration repository
     */
    public DefaultDHLogFilterService(Plugin plugin, DHLogFilterConfigRepository configRepository) {
        this.plugin = plugin;
        this.configRepository = configRepository;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.rootLogger = Logger.getLogger("");
        this.stats = new FilterStats();
        
        // Initialize with default configuration
        this.currentConfig = new DHLogFilterConfig();
    }
    
    @Override
    public CompletableFuture<Void> applyFilter() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (filterApplied) {
                    throw new IllegalStateException("Log filter is already applied");
                }
                
                try {
                    // Load current configuration
                    DHLogFilterConfig config = configRepository.loadConfiguration().join();
                    
                    // Create and configure the log filter
                    logFilter = new DHLogFilter(config, stats);
                    logFilter.activate();
                    
                    // Apply the filter to the root logger
                    rootLogger.setFilter(logFilter);
                    
                    filterApplied = true;
                    currentConfig = config;
                    stats.setFilterActive(true);
                    stats.setCurrentLogLevel(config.getLevel().getName());
                    
                    logger.info("DH log filter applied successfully with level: " + config.getLevel().getName());
                    
                } catch (Exception e) {
                    logger.error("Failed to apply DH log filter", e);
                    throw new RuntimeException("Failed to apply log filter", e);
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> removeFilter() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (!filterApplied) {
                    throw new IllegalStateException("Log filter is not currently applied");
                }
                
                try {
                    // Remove the filter from the root logger
                    rootLogger.setFilter(null);
                    
                    // Deactivate the filter
                    if (logFilter != null) {
                        logFilter.deactivate();
                    }
                    
                    filterApplied = false;
                    stats.setFilterActive(false);
                    
                    logger.info("DH log filter removed successfully");
                    
                } catch (Exception e) {
                    logger.error("Failed to remove DH log filter", e);
                    throw new RuntimeException("Failed to remove log filter", e);
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> reloadConfiguration() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Reloading DH log filter configuration...");
                
                // Load new configuration
                DHLogFilterConfig newConfig = configRepository.loadConfiguration().join();
                newConfig.validate();
                
                synchronized (this) {
                    // Update current configuration
                    currentConfig = newConfig;
                    stats.setCurrentLogLevel(newConfig.getLevel().getName());
                    
                    // Update the filter if it's active
                    if (logFilter != null) {
                        logFilter.updateConfiguration(newConfig);
                    }
                }
                
                logger.info("DH log filter configuration reloaded successfully");
                return true;
                
            } catch (Exception e) {
                logger.error("Failed to reload DH log filter configuration", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<FilterStats> getFilterStats() {
        return CompletableFuture.supplyAsync(() -> {
            // Update cache size if filter is active
            if (logFilter != null) {
                stats.setCacheSize(logFilter.getCacheSize());
            }
            
            return stats;
        });
    }
    
    @Override
    public CompletableFuture<Void> setLogLevel(String level) {
        return CompletableFuture.runAsync(() -> {
            try {
                DHLogFilterConfig.LogLevel newLevel = DHLogFilterConfig.LogLevel.fromString(level);
                
                synchronized (this) {
                    // Update current configuration
                    currentConfig.setLevel(newLevel);
                    stats.setCurrentLogLevel(newLevel.getName());
                    
                    // Update the filter if it's active
                    if (logFilter != null) {
                        logFilter.updateConfiguration(currentConfig);
                    }
                }
                
                logger.info("DH log filter level changed to: " + newLevel.getName());
                
            } catch (Exception e) {
                logger.error("Failed to set log level: " + level, e);
                throw new RuntimeException("Failed to set log level", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<String> getCurrentLogLevel() {
        return CompletableFuture.supplyAsync(() -> currentConfig.getLevel().getName());
    }
    
    @Override
    public CompletableFuture<Boolean> isFilterActive() {
        return CompletableFuture.supplyAsync(() -> filterApplied && stats.isFilterActive());
    }
    
    @Override
    public CompletableFuture<Void> clearMessageCache() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (logFilter != null) {
                    logFilter.clearCache();
                    logger.info("DH log filter message cache cleared");
                } else {
                    logger.warning("Cannot clear cache - log filter is not active");
                }
            }
        });
    }
    
    /**
     * Get the current configuration synchronously.
     * This is useful for internal operations that need immediate access.
     * 
     * @return Current configuration
     */
    public DHLogFilterConfig getCurrentConfig() {
        return currentConfig;
    }
    
    /**
     * Save the current configuration to storage.
     * This is useful when temporary changes need to be persisted.
     * 
     * @return CompletableFuture that completes when configuration is saved
     */
    public CompletableFuture<Void> saveCurrentConfiguration() {
        return configRepository.saveConfiguration(currentConfig)
            .thenRun(() -> logger.info("Current DH log filter configuration saved"));
    }
    
    /**
     * Reset statistics to zero.
     * This clears all accumulated metrics but preserves configuration.
     * 
     * @return CompletableFuture that completes when statistics are reset
     */
    public CompletableFuture<Void> resetStatistics() {
        return CompletableFuture.runAsync(() -> {
            stats.reset();
            logger.info("DH log filter statistics reset");
        });
    }
    
    /**
     * Get debug information about the filter state.
     * This is useful for troubleshooting and monitoring.
     * 
     * @return CompletableFuture containing debug information
     */
    public CompletableFuture<String> getDebugInfo() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder info = new StringBuilder();
            info.append("DefaultDHLogFilterService Debug Info:\n");
            info.append("Filter Applied: ").append(filterApplied).append("\n");
            info.append("Current Config: ").append(currentConfig).append("\n");
            info.append("Statistics: ").append(stats).append("\n");
            
            if (logFilter != null) {
                info.append("Filter Active: ").append(logFilter.isActive()).append("\n");
                info.append("Cache Size: ").append(logFilter.getCacheSize()).append("\n");
                info.append("\n").append(logFilter.getCacheDebugInfo());
            } else {
                info.append("Log Filter: Not initialized\n");
            }
            
            return info.toString();
        });
    }
    
    /**
     * Shutdown the service and clean up resources.
     * This should be called during plugin shutdown.
     */
    public void shutdown() {
        try {
            if (filterApplied) {
                removeFilter().join();
            }
            logger.info("DH log filter service shutdown complete");
        } catch (Exception e) {
            logger.error("Error during DH log filter service shutdown", e);
        }
    }
}