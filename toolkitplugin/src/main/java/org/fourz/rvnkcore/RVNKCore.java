package org.fourz.rvnkcore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * RVNKCore - Centralized Data and Service Layer
 * 
 * Provides a foundational framework for the RVNK plugin ecosystem including
 * database abstraction, service registry, and cross-plugin communication.
 * 
 * This class serves as the main entry point for RVNKCore functionality
 * and manages the lifecycle of all core services.
 */
public class RVNKCore {
    private static RVNKCore instance;
    private final JavaPlugin plugin;
    private final LogManager logger;
    private boolean initialized = false;
    
    /**
     * Creates a new RVNKCore instance.
     * 
     * @param plugin The plugin instance that owns this RVNKCore
     */
    public RVNKCore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        instance = this;
    }
    
    /**
     * Gets the singleton instance of RVNKCore.
     * 
     * @return The RVNKCore instance, or null if not initialized
     */
    public static RVNKCore getInstance() {
        return instance;
    }
    
    /**
     * Initializes RVNKCore and all its services.
     * This should be called during the plugin's onEnable phase.
     */
    public void initialize() {
        if (initialized) {
            logger.warning("RVNKCore already initialized, skipping");
            return;
        }
        
        logger.info("Initializing RVNKCore...");
        
        try {
            // TODO: Initialize service registry
            // TODO: Initialize database providers
            // TODO: Initialize core services
            
            initialized = true;
            logger.info("RVNKCore initialization complete");
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKCore", e);
            throw new RuntimeException("RVNKCore initialization failed", e);
        }
    }
    
    /**
     * Shuts down RVNKCore and cleans up all resources.
     * This should be called during the plugin's onDisable phase.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.info("Shutting down RVNKCore...");
        
        try {
            // TODO: Shutdown services in reverse order
            // TODO: Close database connections
            // TODO: Clean up resources
            
            initialized = false;
            logger.info("RVNKCore shutdown complete");
        } catch (Exception e) {
            logger.error("Error during RVNKCore shutdown", e);
        }
    }
    
    /**
     * Gets the plugin instance that owns this RVNKCore.
     * 
     * @return The plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    /**
     * Checks if RVNKCore is initialized and ready for use.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}
