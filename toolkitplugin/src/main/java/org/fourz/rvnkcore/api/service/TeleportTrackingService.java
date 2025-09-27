package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.LocationDTO;
import org.fourz.rvnkcore.api.model.TeleportEventDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Privacy-focused teleport tracking service.
 * 
 * This service tracks only meaningful location changes (teleports, portals, world changes)
 * rather than comprehensive player movement to maintain privacy. Designed to support
 * worldswap functionality and event-based teleportation while respecting player privacy.
 *  * 
 * @since 1.3.0
 */
public interface TeleportTrackingService {
    
    // === Core Teleport Tracking ===
    
    /**
     * Records a teleport event for audit and worldswap functionality.
     * 
     * @param playerId The player's UUID
     * @param from The source location (null if first join or login)
     * @param to The destination location
     * @param reason The reason for teleport (COMMAND, PORTAL, PLUGIN, WORLD_CHANGE)
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordTeleport(UUID playerId, LocationDTO from, LocationDTO to, String reason);
    
    /**
     * Gets the last known location for a player in a specific world.
     * This is the core method for worldswap teleport functionality.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @return CompletableFuture containing the last location if available
     */
    CompletableFuture<Optional<LocationDTO>> getLastLocationInWorld(UUID playerId, String worldName);
    
    /**
     * Gets all last known locations for a player across all worlds.
     * Used for worldswap command to show available teleport destinations.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing map of world names to last locations
     */
    CompletableFuture<Map<String, LocationDTO>> getAllWorldLocations(UUID playerId);
    
    // === Teleport History and Analytics ===
    
    /**
     * Gets the teleport history for a player (not movement history).
     * Useful for admin audit trails and player statistics.
     * 
     * @param playerId The player's UUID
     * @param limit Maximum number of teleport events to return
     * @return CompletableFuture containing list of teleport events
     */
    CompletableFuture<List<TeleportEventDTO>> getTeleportHistory(UUID playerId, int limit);
    
    /**
     * Gets portal usage tracking for a player.
     * 
     * @param playerId The player's UUID
     * @param period Time period to analyze
     * @return CompletableFuture containing list of portal usage events
     */
    CompletableFuture<List<TeleportEventDTO>> getPortalUsage(UUID playerId, Duration period);
    
    /**
     * Gets teleport events filtered by reason (e.g., only portal teleports).
     * 
     * @param playerId The player's UUID
     * @param reason Filter by teleport reason
     * @param limit Maximum number of events to return
     * @return CompletableFuture containing filtered teleport events
     */
    CompletableFuture<List<TeleportEventDTO>> getTeleportsByReason(UUID playerId, String reason, int limit);
    
    // === World Change Tracking ===
    
    /**
     * Records a world change event (special case of teleport).
     * 
     * @param playerId The player's UUID
     * @param fromWorld The source world
     * @param toWorld The destination world
     * @param toLocation The destination location
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, LocationDTO toLocation);
    
    /**
     * Gets the count of world changes for a player.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing world change count
     */
    CompletableFuture<Integer> getWorldChangeCount(UUID playerId);
    
    /**
     * Gets worlds the player has visited (based on teleport records).
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing list of visited world names
     */
    CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
    
    // === Statistics and Metrics ===
    
    /**
     * Gets teleport statistics for a player.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing teleport statistics
     */
    CompletableFuture<TeleportStatisticsDTO> getTeleportStatistics(UUID playerId);
    
    /**
     * Gets the most frequently teleported-to worlds for a player.
     * 
     * @param playerId The player's UUID  
     * @param limit Maximum number of worlds to return
     * @return CompletableFuture containing world names and teleport counts
     */
    CompletableFuture<Map<String, Integer>> getMostTeleportedWorlds(UUID playerId, int limit);
    
    // === Administrative Functions ===
    
    /**
     * Cleans up old teleport records beyond a certain age.
     * Helps maintain database performance and storage efficiency.
     * 
     * @param olderThan Age threshold for cleanup
     * @return CompletableFuture containing number of records cleaned
     */
    CompletableFuture<Integer> cleanupOldTeleportRecords(Duration olderThan);
    
    /**
     * Gets total count of teleport records for all players.
     * Used for database monitoring and analytics.
     * 
     * @return CompletableFuture containing total record count
     */
    CompletableFuture<Long> getTotalTeleportCount();
    
    /**
     * Data Transfer Object for teleport statistics.
     */
    class TeleportStatisticsDTO {
        private final int totalTeleports;
        private final int portalUsages;
        private final int commandTeleports;
        private final int worldChanges;
        private final String mostVisitedWorld;
        
        public TeleportStatisticsDTO(int totalTeleports, int portalUsages, 
                                   int commandTeleports, int worldChanges, String mostVisitedWorld) {
            this.totalTeleports = totalTeleports;
            this.portalUsages = portalUsages;
            this.commandTeleports = commandTeleports;
            this.worldChanges = worldChanges;
            this.mostVisitedWorld = mostVisitedWorld;
        }
        
        public int getTotalTeleports() { return totalTeleports; }
        public int getPortalUsages() { return portalUsages; }
        public int getCommandTeleports() { return commandTeleports; }
        public int getWorldChanges() { return worldChanges; }
        public String getMostVisitedWorld() { return mostVisitedWorld; }
    }
}