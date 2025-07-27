package org.fourz.rvnktools.dhlogfilter;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * Manager for DH log filter lifecycle and resource management.
 * 
 * Handles initialization, shutdown, and integration with the RVNKTools
 * plugin lifecycle following RVNK ecosystem patterns.
 */
public class DHLogFilterManager {
    
    private final RVNKTools plugin;
    private final LogManager logger;
    private DHLogFilterService filterService;
    private DHLogFilterCommand filterCommand;
    private boolean initialized = false;
    
    /**
     * Constructor for DHLogFilterManager.
     * 
     * @param plugin The RVNKTools plugin instance
     */
    public DHLogFilterManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Initializes the DH log filter system.
     * 
     * This method should be called during plugin initialization to set up
     * all filter components and register commands.
     */
    public void initialize() {
        if (initialized) {
            logger.warning("DHLogFilterManager is already initialized");
            return;
        }
        
        try {
            logger.info("Initializing DH log filter system...");
            
            // Initialize service
            filterService = new DefaultDHLogFilterService(plugin);
            
            // Initialize and register command
            filterCommand = new DHLogFilterCommand(plugin, filterService);
            
            CommandManager commandManager = CommandManager.getInstance(plugin);
            if (commandManager != null) {
                commandManager.registerCommand(filterCommand);
                logger.info("DHLogFilter command registered with CommandManager");
            } else {
                logger.error("CommandManager not available, cannot register dhfilter command");
            }
            
            // Apply filter with default configuration
            filterService.applyFilter()
                .thenRun(() -> logger.info("DH log filter applied with default configuration"))
                .exceptionally(ex -> {
                    logger.error("Failed to apply initial DH log filter", ex);
                    return null;
                });
            
            initialized = true;
            logger.info("DH log filter system initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize DH log filter system", e);
            throw new RuntimeException("DHLogFilterManager initialization failed", e);
        }
    }
    
    /**
     * Shuts down the DH log filter system and cleans up resources.
     * 
     * This method should be called during plugin shutdown to ensure
     * all resources are properly cleaned up.
     */
    public void shutdown() {
        if (!initialized) {
            logger.debug("DHLogFilterManager is not initialized, skipping shutdown");
            return;
        }
        
        logger.info("Shutting down DH log filter system...");
        
        try {
            // Remove filter
            if (filterService != null) {
                filterService.removeFilter()
                    .thenRun(() -> logger.info("DH log filter removed"))
                    .exceptionally(ex -> {
                        logger.error("Error removing DH log filter", ex);
                        return null;
                    });
            }
            
            // Unregister command
            if (filterCommand != null) {
                CommandManager commandManager = CommandManager.getInstance();
                if (commandManager != null) {
                    commandManager.unregisterCommand("dhfilter");
                    logger.info("DHLogFilter command unregistered");
                }
            }
            
            // Clean up references
            filterService = null;
            filterCommand = null;
            initialized = false;
            
            logger.info("DH log filter system shutdown complete");
            
        } catch (Exception e) {
            logger.error("Error during DH log filter system shutdown", e);
        }
    }
    
    /**
     * Checks if the manager is initialized.
     * 
     * @return true if the manager is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets the filter service instance.
     * 
     * @return The filter service, or null if not initialized
     */
    public DHLogFilterService getFilterService() {
        return filterService;
    }
    
    /**
     * Gets the filter command instance.
     * 
     * @return The filter command, or null if not initialized
     */
    public DHLogFilterCommand getFilterCommand() {
        return filterCommand;
    }
    
    /**
     * Reloads the filter configuration and applies changes.
     * 
     * This is a convenience method that delegates to the service.
     */
    public void reloadConfiguration() {
        if (!initialized || filterService == null) {
            logger.warning("Cannot reload configuration: DHLogFilterManager not initialized");
            return;
        }
        
        logger.info("Reloading DH log filter configuration...");
        
        filterService.reloadConfiguration()
            .thenAccept(success -> {
                if (success) {
                    logger.info("DH log filter configuration reloaded successfully");
                } else {
                    logger.error("Failed to reload DH log filter configuration");
                }
            })
            .exceptionally(ex -> {
                logger.error("Error reloading DH log filter configuration", ex);
                return null;
            });
    }
}