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
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private final LogManager logger;
    private final RVNKCore rvnkCore;

    /** Tracks when each player's current session started (epoch millis). */
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    /**
     * Constructor for PlayerTrackingListener.
     *
     * @param plugin The RVNKCore plugin instance
     * @param rvnkCore The RVNKCore instance for service access
     */
    public PlayerTrackingListener(RVNKCore plugin, RVNKCore rvnkCore) {
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
        
        // Record session start time for playtime tracking
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());

        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            PlayerWorldService playerWorldService = rvnkCore.getService(PlayerWorldService.class);
            WorldService worldService = rvnkCore.getService(WorldService.class);

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
            
            // Update world player count (including max_players_seen tracking)
            CompletableFuture<Void> worldPlayerCountUpdate = worldService.updatePlayerCount(
                player.getWorld().getName(),
                player.getWorld().getPlayers().size()
            );
            
            // Wait for all updates to complete
            CompletableFuture.allOf(globalUpdate, worldUpdate, worldPlayerCountUpdate)
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
        
        // Calculate session playtime
        Long joinTime = sessionStartTimes.remove(player.getUniqueId());
        long sessionSeconds = 0;
        if (joinTime != null) {
            sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000;
        }
        final long finalSessionSeconds = sessionSeconds;

        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            WorldService worldService = rvnkCore.getService(WorldService.class);

            // Update player location and add session playtime
            CompletableFuture<Void> playerUpdate = playerService.getPlayer(player.getUniqueId())
                .thenCompose(playerOpt -> {
                    if (playerOpt.isPresent()) {
                        PlayerDTO playerDTO = playerOpt.get();
                        playerDTO.setCurrentWorld(player.getWorld().getName());
                        playerDTO.setLastSeen(new Timestamp(System.currentTimeMillis()));
                        playerDTO.setTotalPlaytimeSeconds(
                                playerDTO.getTotalPlaytimeSeconds() + finalSessionSeconds);
                        return playerService.savePlayer(playerDTO).thenApply(saved -> (Void) null);
                    }
                    return CompletableFuture.completedFuture(null);
                });

            // Update world player count (subtract 1 since player is leaving)
            int newPlayerCount = Math.max(0, player.getWorld().getPlayers().size() - 1);
            CompletableFuture<Void> worldPlayerCountUpdate = worldService.updatePlayerCount(
                player.getWorld().getName(),
                newPlayerCount
            );

            // Wait for both updates to complete
            CompletableFuture.allOf(playerUpdate, worldPlayerCountUpdate)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to update player data on quit: " + player.getName(), throwable);
                    } else {
                        logger.debug("Updated player data on quit: " + player.getName() +
                                   " (session: " + finalSessionSeconds + "s)");
                    }
                });

        } catch (Exception e) {
            logger.error("Failed to get services for quit event", e);
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
            WorldService worldService = rvnkCore.getService(WorldService.class);
            
            // Record world change with comprehensive tracking
            CompletableFuture<Void> worldChangeUpdate = playerWorldService.recordWorldChange(
                player.getUniqueId(),
                event.getFrom().getName(),
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
            );
            
            // Update player counts for both worlds
            // Old world: decrease count (player left)
            CompletableFuture<Void> oldWorldUpdate = worldService.updatePlayerCount(
                event.getFrom().getName(),
                Math.max(0, event.getFrom().getPlayers().size())
            );
            
            // New world: increase count (player entered)
            CompletableFuture<Void> newWorldUpdate = worldService.updatePlayerCount(
                player.getWorld().getName(),
                player.getWorld().getPlayers().size()
            );
            
            // Wait for all updates to complete
            CompletableFuture.allOf(worldChangeUpdate, oldWorldUpdate, newWorldUpdate)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to update data on world change: " + player.getName(), throwable);
                    } else {
                        logger.debug("Updated data on world change: " + player.getName() + 
                                   " from " + event.getFrom().getName() + " to " + player.getWorld().getName());
                    }
                });
            
        } catch (Exception e) {
            logger.error("Failed to get services for world change event", e);
        }
    }
}
