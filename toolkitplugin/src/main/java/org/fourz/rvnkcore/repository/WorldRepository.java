package org.fourz.rvnkcore.repository;

import org.fourz.rvnkcore.api.dto.WorldDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for world data access operations.
 * Provides comprehensive world metadata management with async operations.
 * 
 * @since 1.0.0
 */
public interface WorldRepository {
    
    /**
     * Find a world by its unique name.
     * 
     * @param name The world name to search for
     * @return CompletableFuture containing Optional with world data if found
     */
    CompletableFuture<Optional<WorldDTO>> findByName(String name);
    
    /**
     * Find worlds by their environment type.
     * 
     * @param environment The environment type (NORMAL, NETHER, THE_END, CUSTOM)
     * @return CompletableFuture containing list of worlds in the environment
     */
    CompletableFuture<List<WorldDTO>> findByEnvironment(String environment);
    
    /**
     * Find all active worlds.
     * 
     * @return CompletableFuture containing list of active worlds
     */
    CompletableFuture<List<WorldDTO>> findActiveWorlds();
    
    /**
     * Find all worlds ordered by last accessed time (most recent first).
     * 
     * @param limit Maximum number of worlds to return
     * @return CompletableFuture containing list of recently accessed worlds
     */
    CompletableFuture<List<WorldDTO>> findRecentlyAccessed(int limit);
    
    /**
     * Find worlds with players currently online.
     * 
     * @return CompletableFuture containing list of worlds with active players
     */
    CompletableFuture<List<WorldDTO>> findWorldsWithPlayers();
    
    /**
     * Get all worlds in the database.
     * 
     * @return CompletableFuture containing list of all worlds
     */
    CompletableFuture<List<WorldDTO>> findAll();
    
    /**
     * Save or update world information.
     * 
     * @param worldDTO The world data to save
     * @return CompletableFuture completing when operation is done
     */
    CompletableFuture<Void> save(WorldDTO worldDTO);
    
    /**
     * Update world tracking information (last accessed, playtime, player count).
     * 
     * @param worldName The world name to update
     * @param totalPlaytimeSeconds Updated total playtime
     * @param playerCount Current player count
     * @param maxPlayersSeen Maximum players seen (updated if current is higher)
     * @return CompletableFuture completing when operation is done
     */
    CompletableFuture<Void> updateTrackingInfo(String worldName, long totalPlaytimeSeconds, 
                                              int playerCount, int maxPlayersSeen);
    
    /**
     * Update world player count.
     * 
     * @param worldName The world name to update
     * @param playerCount Current player count
     * @return CompletableFuture completing when operation is done
     */
    CompletableFuture<Void> updatePlayerCount(String worldName, int playerCount);
    
    /**
     * Mark a world as active or inactive.
     * 
     * @param worldName The world name to update
     * @param isActive True if the world is active
     * @return CompletableFuture completing when operation is done
     */
    CompletableFuture<Void> updateActiveStatus(String worldName, boolean isActive);
    
    /**
     * Delete a world record from the database.
     * Note: This only removes the world metadata, not the actual world files.
     * 
     * @param worldName The world name to delete
     * @return CompletableFuture completing when operation is done
     */
    CompletableFuture<Void> delete(String worldName);
    
    /**
     * Check if a world exists in the database.
     * 
     * @param worldName The world name to check
     * @return CompletableFuture containing true if world exists
     */
    CompletableFuture<Boolean> exists(String worldName);
    
    /**
     * Get world statistics for all worlds.
     * 
     * @return CompletableFuture containing map of world names to total playtime
     */
    CompletableFuture<List<WorldDTO>> getWorldStatistics();
    
    /**
     * Find worlds that correlate to specific player world data.
     * Used for joining world metadata with player-world tracking.
     * 
     * @param playerUuid The player UUID to find worlds for
     * @return CompletableFuture containing list of worlds where player has data
     */
    CompletableFuture<List<WorldDTO>> findWorldsForPlayer(String playerUuid);
}
