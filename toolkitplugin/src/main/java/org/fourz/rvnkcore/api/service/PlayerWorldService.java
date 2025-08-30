package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for comprehensive player management with world-specific tracking.
 * 
 * This service provides centralized access to both global player data and
 * world-specific data such as locations, playtime, and visit history.
 * It supports the worldswap teleport functionality and general world-based
 * player tracking across the RVNK ecosystem.
 * 
 * @since 1.0.0
 */
public interface PlayerWorldService {
    
    // Global Player Management
    
    /**
     * Gets global player data by UUID.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing the player data if found
     */
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    
    /**
     * Gets global player data by current name.
     * 
     * @param playerName The player's current name
     * @return CompletableFuture containing the player data if found
     */
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    
    /**
     * Saves or updates global player data.
     * 
     * @param player The player data to save
     * @return CompletableFuture containing the saved player data
     */
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    
    /**
     * Records a player join event and updates global statistics.
     * 
     * @param playerId The player's UUID
     * @param playerName The player's current name
     * @param currentWorld The world they joined in
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordPlayerJoin(UUID playerId, String playerName, String currentWorld);
    
    /**
     * Records a player quit event and updates playtime.
     * 
     * @param playerId The player's UUID
     * @param sessionDurationSeconds Duration of the session in seconds
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordPlayerQuit(UUID playerId, long sessionDurationSeconds);
    
    // World-Specific Player Management
    
    /**
     * Gets player's data for a specific world.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @return CompletableFuture containing the world data if found
     */
    CompletableFuture<Optional<PlayerWorldDataDTO>> getPlayerWorldData(UUID playerId, String worldName);
    
    /**
     * Gets all world data for a specific player.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing list of world data
     */
    CompletableFuture<List<PlayerWorldDataDTO>> getAllPlayerWorldData(UUID playerId);
    
    /**
     * Gets the player's last known location in a specific world.
     * This is the core method for worldswap teleport functionality.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @return CompletableFuture containing the last location data if available
     */
    CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName);
    
    /**
     * Records a player's world change/teleport event.
     * 
     * @param playerId The player's UUID
     * @param fromWorld The world they're leaving (null if first join)
     * @param toWorld The world they're entering
     * @param x The new X coordinate
     * @param y The new Y coordinate
     * @param z The new Z coordinate
     * @param yaw The new view yaw
     * @param pitch The new view pitch
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, 
                                            double x, double y, double z, float yaw, float pitch);
    
    /**
     * Updates a player's location within their current world.
     * This method implements rate limiting to prevent excessive database writes.
     * 
     * @param playerId The player's UUID
     * @param worldName The current world name
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param z The Z coordinate
     * @param yaw The view yaw
     * @param pitch The view pitch
     * @param biome The current biome (optional)
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, 
                                               double x, double y, double z, 
                                               float yaw, float pitch, String biome);
    
    /**
     * Records a player death in a specific world.
     * 
     * @param playerId The player's UUID
     * @param worldName The world where the death occurred
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> recordPlayerDeath(UUID playerId, String worldName);
    
    /**
     * Adds playtime to a player's world-specific tracking.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @param additionalSeconds Additional playtime in seconds
     * @return CompletableFuture indicating completion
     */
    CompletableFuture<Void> addWorldPlaytime(UUID playerId, String worldName, long additionalSeconds);
    
    // World Analysis and Statistics
    
    /**
     * Gets all players who have visited a specific world.
     * 
     * @param worldName The world name
     * @return CompletableFuture containing list of player world data
     */
    CompletableFuture<List<PlayerWorldDataDTO>> getWorldVisitors(String worldName);
    
    /**
     * Gets players who visited a world recently.
     * 
     * @param worldName The world name
     * @param hoursAgo Number of hours to look back
     * @return CompletableFuture containing list of recent visitors
     */
    CompletableFuture<List<PlayerWorldDataDTO>> getRecentWorldVisitors(String worldName, int hoursAgo);
    
    /**
     * Gets the total playtime across all players for a specific world.
     * 
     * @param worldName The world name
     * @return CompletableFuture containing total playtime in seconds
     */
    CompletableFuture<Long> getWorldTotalPlaytime(String worldName);
    
    /**
     * Gets the most visited worlds for a specific player.
     * 
     * @param playerId The player's UUID
     * @param limit Maximum number of worlds to return
     * @return CompletableFuture containing list of world data ordered by visit count
     */
    CompletableFuture<List<PlayerWorldDataDTO>> getPlayerMostVisitedWorlds(UUID playerId, int limit);
    
    // Utility Methods for Worldswap Command
    
    /**
     * Gets a list of all worlds a player has visited, suitable for worldswap command.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing list of world names the player has visited
     */
    CompletableFuture<List<String>> getPlayerVisitedWorlds(UUID playerId);
    
    /**
     * Validates if a player has visited a world (for worldswap permission checking).
     * 
     * @param playerId The player's UUID
     * @param worldName The world name to check
     * @return CompletableFuture containing true if the player has visited the world
     */
    CompletableFuture<Boolean> hasPlayerVisitedWorld(UUID playerId, String worldName);
    
    /**
     * Gets the player's previous world for worldswap "back" functionality.
     * 
     * @param playerId The player's UUID
     * @param currentWorld The player's current world
     * @return CompletableFuture containing the previous world name if available
     */
    CompletableFuture<Optional<String>> getPlayerPreviousWorld(UUID playerId, String currentWorld);
}
