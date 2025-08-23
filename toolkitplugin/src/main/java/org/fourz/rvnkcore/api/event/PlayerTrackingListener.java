package org.fourz.rvnkcore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

/**
 * Event listener for tracking player activity using RVNKCore services.
 * 
 * This listener captures player join/quit events and location changes to
 * maintain comprehensive player tracking data in the RVNKCore system.
 * 
 * TODO: This will eventually replace individual tracking in other listeners
 * once full migration to RVNKCore is complete.
 * 
 * @since 1.0.0
 */
public class PlayerTrackingListener implements Listener {
    
    private final RVNKTools plugin;
    private final LogManager logger;
    private final RVNKCore rvnkCore;
    
    /**
     * Constructor for PlayerTrackingListener.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param rvnkCore The RVNKCore instance
     */
    public PlayerTrackingListener(RVNKTools plugin, RVNKCore rvnkCore) {
        this.plugin = plugin;
        this.rvnkCore = rvnkCore;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Log initialization with proper class prefix
        logger.info("Player listener initialized");
    }
    
    /**
     * Handles player join events to create or update player records.
     * 
     * @param event The player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            PlayerWorldService playerWorldService = rvnkCore.getService(PlayerWorldService.class);
            
            // Track both global and world-specific data
            CompletableFuture<Void> globalUpdate = playerService.getPlayer(player.getUniqueId())
                .thenCompose((playerOpt) -> {
                    if (playerOpt.isEmpty()) {
                        // Create new player record
                        PlayerDTO newPlayer = new PlayerDTO.Builder()
                            .id(player.getUniqueId())
                            .currentName(player.getName())
                            .firstJoin(new Timestamp(System.currentTimeMillis()))
                            .lastSeen(new Timestamp(System.currentTimeMillis()))
                            .currentWorld(player.getWorld().getName())
                            .timesJoined(1)
                            .totalPlaytimeSeconds(0L)
                            .build();
                        
                        return playerService.savePlayer(newPlayer).thenApply((dto) -> {
                            logger.info("Created new player record: " + player.getName());
                            return (Void) null;
                        });
                    } else {
                        // Update existing player's data
                        PlayerDTO playerDTO = playerOpt.get();
                        playerDTO.updateName(player.getName());
                        playerDTO.setCurrentWorld(player.getWorld().getName());
                        playerDTO.recordJoin();
                        
                        return playerService.savePlayer(playerDTO).thenApply((saved) -> (Void) null);
                    }
                });
                
            // Track per-world data separately
            CompletableFuture<Void> worldUpdate = playerWorldService.updatePlayerLocation(
                player.getUniqueId(),
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                player.getLocation().getBlock().getBiome().name()
            );
            
            // Wait for both global and world tracking to complete
            CompletableFuture.allOf(globalUpdate, worldUpdate)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to update player data for: " + player.getName(), throwable);
                    } else {
                        logger.debug("Successfully updated player data for: " + player.getName());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Failed to get PlayerService for join event", e);
        }
    }
    
    /**
     * Handles player quit events to update last seen time and location.
     * 
     * @param event The player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            // Update player's last location before they quit
            playerService.updatePlayerLocation(
                player.getUniqueId(),
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            ).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update player location on quit: " + player.getName(), throwable);
                } else {
                    logger.debug("Updated player location on quit: " + player.getName());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to get PlayerService for quit event", e);
        }
    }
    
    /**
     * Handles player world change events to update location tracking.
     * 
     * @param event The player changed world event
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        try {
            PlayerWorldService playerWorldService = rvnkCore.getService(PlayerWorldService.class);
            
            // Record world change with comprehensive tracking
            playerWorldService.recordWorldChange(
                player.getUniqueId(),
                event.getFrom().getName(),
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
            ).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update player location on world change: " + player.getName(), throwable);
                } else {
                    logger.debug("Updated player location on world change: " + player.getName() + 
                               " from " + event.getFrom().getName() + " to " + player.getWorld().getName());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to get PlayerWorldService for world change event", e);
        }
    }
    
    /**
     * Handles significant player movement to periodically update location.
     * 
     * This only updates on significant movement to avoid database spam.
     * 
     * @param event The player move event
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only track significant movement (crossing block boundaries)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Rate limit location updates to every 30 seconds per player
        String playerKey = "location_update_" + player.getUniqueId();
        long lastUpdate = plugin.getConfig().getLong(playerKey, 0);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastUpdate < 30000) { // 30 seconds
            return;
        }
        
        try {
            PlayerWorldService playerWorldService = rvnkCore.getService(PlayerWorldService.class);
            
            // Update player's current location with comprehensive world data
            playerWorldService.updatePlayerLocation(
                player.getUniqueId(),
                player.getWorld().getName(),
                event.getTo().getX(),
                event.getTo().getY(),
                event.getTo().getZ(),
                event.getTo().getYaw(),
                event.getTo().getPitch(),
                event.getTo().getBlock().getBiome().name()
            ).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update player location on move: " + player.getName(), throwable);
                } else {
                    // Update the last update timestamp
                    plugin.getConfig().set(playerKey, currentTime);
                    logger.debug("Updated player location on move: " + player.getName());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to get PlayerWorldService for move event", e);
        }
    }
}
