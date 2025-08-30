package org.fourz.rvnkcore.api.event;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

/**
 * Event listener for tracking world loading and unloading events using RVNKCore services.
 * 
 * This listener captures world lifecycle events to maintain comprehensive world tracking
 * data in the RVNKCore system, ensuring that all worlds are properly registered and
 * tracked in the database.
 * 
 * <p>The listener handles the following events:
 * <ul>
 *   <li>{@link WorldInitEvent} - When a world is initialized (before loading)</li>
 *   <li>{@link WorldLoadEvent} - When a world is loaded and ready for use</li>
 *   <li>{@link WorldUnloadEvent} - When a world is unloaded (mark as inactive)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class WorldTrackingListener implements Listener {
    
    private final LogManager logger;
    private final RVNKCore rvnkCore;
    
    /**
     * Constructor for WorldTrackingListener.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param rvnkCore The RVNKCore instance for service access
     */
    public WorldTrackingListener(RVNKTools plugin, RVNKCore rvnkCore) {
        this.rvnkCore = rvnkCore;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Log initialization with proper class prefix
        logger.info("World tracking listener initialized");
    }
    
    /**
     * Handles world initialization events.
     * Called when a world is first initialized, before it's fully loaded.
     * 
     * @param event The world init event
     */
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        logger.debug("World initialization detected: " + world.getName());
        
        // Pre-register world during initialization phase
        registerWorldAsync(world, "initialization");
    }
    
    /**
     * Handles world load events to register worlds into the database.
     * This is the primary event for world registration as the world is fully ready.
     * 
     * @param event The world load event
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        logger.info("World loaded: " + world.getName() + " (" + world.getEnvironment() + ")");
        
        // Register or update world in the tracking system
        registerWorldAsync(world, "load");
    }
    
    /**
     * Handles world unload events to update world status.
     * Marks the world as inactive but preserves tracking data.
     * 
     * @param event The world unload event
     */
    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        logger.info("World unloading: " + world.getName());
        
        try {
            WorldService worldService = rvnkCore.getService(WorldService.class);
            
            // Mark world as inactive instead of removing it
            worldService.setActiveStatus(world.getName(), false)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to mark world as inactive: " + world.getName(), throwable);
                    } else {
                        logger.info("Marked world as inactive: " + world.getName());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Failed to get WorldService for unload event", e);
        }
    }
    
    /**
     * Registers a world asynchronously using the WorldService.
     * This method handles both new world registration and updates to existing worlds.
     * 
     * @param world The Bukkit World object to register
     * @param context Context string for logging (e.g., "load", "initialization")
     */
    private void registerWorldAsync(World world, String context) {
        try {
            WorldService worldService = rvnkCore.getService(WorldService.class);
            
            logger.debug("Registering world '" + world.getName() + "' during " + context + 
                        " [" + world.getEnvironment() + ", Seed: " + world.getSeed() + "]");
            
            // Register or update the world
            worldService.registerWorld(world)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to register world during " + context + ": " + world.getName(), throwable);
                    } else {
                        int currentPlayers = world.getPlayers().size();
                        logger.info("Successfully registered world during " + context + ": " + 
                                  world.getName() + " [" + world.getEnvironment() + 
                                  ", " + world.getDifficulty() + 
                                  ", Players: " + currentPlayers + "/" + currentPlayers + " max]");
                    }
                });
                
            // Also ensure the world is marked as active
            worldService.setActiveStatus(world.getName(), true)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.warning("Failed to set active status for world: " + world.getName());
                    } else {
                        logger.debug("Set world as active: " + world.getName());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Failed to get WorldService for " + context + " registration", e);
        }
    }
    
    /**
     * Sync all currently loaded worlds with the database.
     * This method delegates to the WorldService for proper separation of concerns.
     * Called during plugin initialization to ensure all worlds are properly tracked.
     */
    public void syncAllLoadedWorlds() {
        try {
            WorldService worldService = rvnkCore.getService(WorldService.class);
            
            logger.info("Initiating world sync through WorldService");
            
            // Delegate to the WorldService for proper business logic handling
            worldService.syncLoadedWorlds()
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("World sync failed through WorldService", throwable);
                    } else {
                        logger.info("World sync completed through WorldService");
                    }
                });
                
        } catch (Exception e) {
            logger.error("Failed to get WorldService for world sync", e);
        }
    }
}
