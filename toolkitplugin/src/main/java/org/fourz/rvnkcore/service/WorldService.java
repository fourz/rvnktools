package org.fourz.rvnkcore.service;

import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.bukkit.World;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for world management and tracking operations.
 * Provides comprehensive world metadata handling and correlation with player data.
 * 
 * @since 1.0.0
 */
public interface WorldService {
    
    /**
     * Get world information by name.
     * 
     * @param worldName The world name to retrieve
     * @return CompletableFuture containing Optional with world data if found
     */
    CompletableFuture<Optional<WorldDTO>> getWorld(String worldName);
    
    /**
     * Get all worlds with their metadata.
     * 
     * @return CompletableFuture containing list of all worlds
     */
    CompletableFuture<List<WorldDTO>> getAllWorlds();
    
    /**
     * Get only active worlds.
     * 
     * @return CompletableFuture containing list of active worlds
     */
    CompletableFuture<List<WorldDTO>> getActiveWorlds();
    
    /**
     * Get worlds by environment type.
     * 
     * @param environment The environment type (NORMAL, NETHER, THE_END, CUSTOM)
     * @return CompletableFuture containing list of worlds in the environment
     */
    CompletableFuture<List<WorldDTO>> getWorldsByEnvironment(String environment);
    
    /**
     * Get recently accessed worlds.
     * 
     * @param limit Maximum number of worlds to return
     * @return CompletableFuture containing list of recently accessed worlds
     */
    CompletableFuture<List<WorldDTO>> getRecentlyAccessedWorlds(int limit);
    
    /**
     * Get worlds that currently have players online.
     * 
     * @return CompletableFuture containing list of worlds with active players
     */
    CompletableFuture<List<WorldDTO>> getWorldsWithPlayers();
    
    /**
     * Register or update a world in the tracking system.
     * This is typically called when a world is loaded or its settings change.
     * 
     * @param world The Bukkit World object to register
     * @return CompletableFuture completing when world is registered
     */
    CompletableFuture<Void> registerWorld(World world);
    
    /**
     * Update world tracking information when players join/leave.
     * 
     * @param worldName The world name to update
     * @param playerCount Current player count in the world
     * @return CompletableFuture completing when tracking is updated
     */
    CompletableFuture<Void> updatePlayerCount(String worldName, int playerCount);
    
    /**
     * Record playtime for a world.
     * 
     * @param worldName The world name
     * @param playtimeSeconds Playtime to add to the world's total
     * @return CompletableFuture completing when playtime is recorded
     */
    CompletableFuture<Void> addPlaytime(String worldName, long playtimeSeconds);
    
    /**
     * Mark a world as active or inactive.
     * 
     * @param worldName The world name to update
     * @param isActive True if the world is active
     * @return CompletableFuture completing when status is updated
     */
    CompletableFuture<Void> setActiveStatus(String worldName, boolean isActive);
    
    /**
     * Get world statistics for reporting and analysis.
     * 
     * @return CompletableFuture containing list of worlds with their statistics
     */
    CompletableFuture<List<WorldDTO>> getWorldStatistics();
    
    /**
     * Find worlds where a specific player has activity.
     * This correlates world data with player-world tracking data.
     * 
     * @param playerUuid The player UUID to search for
     * @return CompletableFuture containing list of worlds with player activity
     */
    CompletableFuture<List<WorldDTO>> getWorldsForPlayer(String playerUuid);
    
    /**
     * Get comprehensive world and player data correlation.
     * Returns world information along with the player's specific data in those worlds.
     * 
     * @param playerUuid The player UUID to get correlated data for
     * @return CompletableFuture containing list of world-player data pairs
     */
    CompletableFuture<List<WorldPlayerCorrelation>> getWorldPlayerCorrelation(String playerUuid);
    
    /**
     * Sync all currently loaded Bukkit worlds with the tracking database.
     * This ensures the database reflects the current server state.
     * 
     * @return CompletableFuture completing when sync is done
     */
    CompletableFuture<Void> syncLoadedWorlds();
    
    /**
     * Remove world tracking data (does not delete world files).
     * 
     * @param worldName The world name to remove from tracking
     * @return CompletableFuture completing when world is removed
     */
    CompletableFuture<Void> removeWorldTracking(String worldName);
    
    /**
     * Data class for correlated world and player-world information.
     */
    class WorldPlayerCorrelation {
        private final WorldDTO world;
        private final PlayerWorldDataDTO playerWorldData;
        
        public WorldPlayerCorrelation(WorldDTO world, PlayerWorldDataDTO playerWorldData) {
            this.world = world;
            this.playerWorldData = playerWorldData;
        }
        
        public WorldDTO getWorld() { return world; }
        public PlayerWorldDataDTO getPlayerWorldData() { return playerWorldData; }
    }
}
