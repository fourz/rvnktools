package org.fourz.rvnktools.dhlogfilter.manager;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.dhlogfilter.repository.DHLogFilterConfigRepository;
import org.fourz.rvnktools.dhlogfilter.repository.YamlDHLogFilterConfigRepository;
import org.fourz.rvnktools.dhlogfilter.service.DHLogFilterService;
import org.fourz.rvnktools.dhlogfilter.service.DefaultDHLogFilterService;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * Manager class for the DH log filter system lifecycle.
 * Handles initialization, configuration, and cleanup of all DH log filter components
 * following RVNK plugin ecosystem patterns.
 * 
 * This manager acts as the central coordination point for the DH log filter system,
 * managing dependencies and ensuring proper resource cleanup.
 * 
 * @since 1.1-alpha
 */
public class DHLogFilterManager {
    
    private final Plugin plugin;
    private final LogManager logger;
    
    private DHLogFilterConfigRepository configRepository;
    private DHLogFilterService filterService;
    private boolean initialized = false;
    
    /**
     * Constructor for DHLogFilterManager.
     * 
     * @param plugin The plugin instance
     */
    public DHLogFilterManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Initialize the DH log filter system.
     * This should be called during plugin startup after all dependencies are available.
     * 
     * @throws IllegalStateException if already initialized
     */
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("DHLogFilterManager is already initialized");
        }
        
        try {
            logger.info("Initializing DH log filter system...");
            
            // Initialize repository layer
            initializeRepository();
            
            // Initialize service layer
            initializeService();
            
            // Create default configuration if needed
            ensureDefaultConfiguration();
            
            initialized = true;
            logger.info("DH log filter system initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize DH log filter system", e);
            cleanup();
            throw new RuntimeException("DH log filter initialization failed", e);
        }
    }
    
    /**
     * Shutdown the DH log filter system and clean up resources.
     * This should be called during plugin shutdown.
     */
    public void shutdown() {
        if (!initialized) {
            return; // Nothing to shutdown
        }
        
        logger.info("Shutting down DH log filter system...");
        
        try {
            // Shutdown service (this will remove any active filters)
            if (filterService instanceof DefaultDHLogFilterService) {
                ((DefaultDHLogFilterService) filterService).shutdown();
            }
            
        } catch (Exception e) {
            logger.error("Error during DH log filter service shutdown", e);
        }
        
        cleanup();
        logger.info("DH log filter system shutdown complete");
    }
    
    /**
     * Get the DH log filter service.
     * 
     * @return The filter service instance
     * @throws IllegalStateException if not initialized
     */
    public DHLogFilterService getFilterService() {
        if (!initialized) {
            throw new IllegalStateException("DHLogFilterManager is not initialized");
        }
        return filterService;
    }
    
    /**
     * Get the configuration repository.
     * 
     * @return The configuration repository instance
     * @throws IllegalStateException if not initialized
     */
    public DHLogFilterConfigRepository getConfigRepository() {
        if (!initialized) {
            throw new IllegalStateException("DHLogFilterManager is not initialized");
        }
        return configRepository;
    }
    
    /**
     * Check if the manager is initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Apply the log filter with current configuration.
     * This is a convenience method that delegates to the service.
     * 
     * @return true if the filter was applied successfully
     */
    public boolean applyFilter() {
        if (!initialized) {
            logger.warning("Cannot apply filter - DH log filter system is not initialized");
            return false;
        }
        
        try {
            filterService.applyFilter().join();
            return true;
        } catch (Exception e) {
            logger.error("Failed to apply DH log filter", e);
            return false;
        }
    }
    
    /**
     * Remove the currently applied log filter.
     * This is a convenience method that delegates to the service.
     * 
     * @return true if the filter was removed successfully
     */
    public boolean removeFilter() {
        if (!initialized) {
            logger.warning("Cannot remove filter - DH log filter system is not initialized");
            return false;
        }
        
        try {
            filterService.removeFilter().join();
            return true;
        } catch (Exception e) {
            logger.error("Failed to remove DH log filter", e);
            return false;
        }
    }
    
    /**
     * Reload the configuration from disk and apply changes.
     * This is a convenience method that delegates to the service.
     * 
     * @return true if the configuration was reloaded successfully
     */
    public boolean reloadConfiguration() {
        if (!initialized) {
            logger.warning("Cannot reload configuration - DH log filter system is not initialized");
            return false;
        }
        
        try {
            return filterService.reloadConfiguration().join();
        } catch (Exception e) {
            logger.error("Failed to reload DH log filter configuration", e);
            return false;
        }
    }
    
    /**
     * Initialize the repository layer.
     */
    private void initializeRepository() {
        logger.debug("Initializing DH log filter configuration repository...");
        configRepository = new YamlDHLogFilterConfigRepository(plugin);
        logger.debug("Configuration repository initialized");
    }
    
    /**
     * Initialize the service layer.
     */
    private void initializeService() {
        logger.debug("Initializing DH log filter service...");
        filterService = new DefaultDHLogFilterService(plugin, configRepository);
        logger.debug("Filter service initialized");
    }
    
    /**
     * Ensure a default configuration exists.
     */
    private void ensureDefaultConfiguration() {
        try {
            boolean exists = configRepository.configurationExists().join();
            if (!exists) {
                logger.info("Creating default DH log filter configuration...");
                configRepository.createDefaultConfiguration().join();
            }
        } catch (Exception e) {
            logger.warning("Failed to create default configuration: " + e.getMessage());
        }
    }
    
    /**
     * Clean up resources and reset state.
     */
    private void cleanup() {
        filterService = null;
        configRepository = null;
        initialized = false;
    }
    
    /**
     * Get diagnostic information about the manager state.
     * This is useful for troubleshooting and monitoring.
     * 
     * @return Diagnostic information string
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("DHLogFilterManager Diagnostic Info:\n");
        info.append("Initialized: ").append(initialized).append("\n");
        info.append("Plugin: ").append(plugin.getName()).append(" v").append(plugin.getDescription().getVersion()).append("\n");
        
        if (initialized) {
            info.append("Config Repository: ").append(configRepository.getClass().getSimpleName()).append("\n");
            info.append("Filter Service: ").append(filterService.getClass().getSimpleName()).append("\n");
            
            try {
                boolean filterActive = filterService.isFilterActive().join();
                String currentLevel = filterService.getCurrentLogLevel().join();
                info.append("Filter Active: ").append(filterActive).append("\n");
                info.append("Current Level: ").append(currentLevel).append("\n");
            } catch (Exception e) {
                info.append("Service Status: Error retrieving status - ").append(e.getMessage()).append("\n");
            }
        } else {
            info.append("Components: Not initialized\n");
        }
        
        return info.toString();
    }
}