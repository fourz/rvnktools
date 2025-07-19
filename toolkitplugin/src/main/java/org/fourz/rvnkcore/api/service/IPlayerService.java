package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.PlayerDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing player data across the RVNK plugin ecosystem.
 * 
 * Provides centralized access to player information including activity tracking,
 * location data, name history, and permission group management.
 * 
 * All operations are asynchronous to prevent blocking the main thread.
 * 
 * @since 1.0.0
 */
public interface IPlayerService {
    
    /**
     * Retrieves a player by their UUID.
     * 
     * @param playerId The unique identifier of the player
     * @return CompletableFuture containing the player data if found
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    
    /**
     * Retrieves a player by their current name.
     * 
     * @param playerName The current name of the player
     * @return CompletableFuture containing the player data if found
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    
    /**
     * Saves or updates player information.
     * 
     * @param player The player data to save
     * @return CompletableFuture containing the saved player data
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if save operation fails
     * @since 1.0.0
     */
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    
    /**
     * Updates the player's last seen time and location.
     * 
     * @param playerId The player's UUID
     * @param world The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture that completes when the update is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if update fails
     * @since 1.0.0
     */
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String world, double x, double y, double z);
    
    /**
     * Updates the player's name and maintains name history.
     * 
     * @param playerId The player's UUID
     * @param newName The new player name
     * @return CompletableFuture that completes when the update is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if update fails
     * @since 1.0.0
     */
    CompletableFuture<Void> updatePlayerName(UUID playerId, String newName);
    
    /**
     * Updates the player's permission group information.
     * 
     * @param playerId The player's UUID
     * @param primaryGroup The primary permission group
     * @param allGroups List of all groups the player belongs to
     * @return CompletableFuture that completes when the update is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if update fails
     * @since 1.0.0
     */
    CompletableFuture<Void> updatePlayerGroups(UUID playerId, String primaryGroup, List<String> allGroups);
    
    /**
     * Retrieves all players who have been seen within the specified hours.
     * 
     * @param hoursAgo The number of hours to look back
     * @return CompletableFuture containing list of recently active players
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<List<PlayerDTO>> getRecentPlayers(int hoursAgo);
    
    /**
     * Retrieves players by their permission group.
     * 
     * @param groupName The name of the permission group
     * @return CompletableFuture containing list of players in the group
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<List<PlayerDTO>> getPlayersByGroup(String groupName);
    
    /**
     * Searches for players whose names match the provided pattern.
     * 
     * @param namePattern The pattern to match (supports SQL LIKE syntax)
     * @return CompletableFuture containing list of matching players
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if search fails
     * @since 1.0.0
     */
    CompletableFuture<List<PlayerDTO>> searchPlayersByName(String namePattern);
    
    /**
     * Gets the total count of registered players.
     * 
     * @return CompletableFuture containing the total player count
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if count operation fails
     * @since 1.0.0
     */
    CompletableFuture<Long> getPlayerCount();
    
    /**
     * Creates a new player record when they first join.
     * 
     * @param playerId The player's UUID
     * @param playerName The player's name
     * @param world The world they joined in
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture containing the created player data
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if creation fails
     * @since 1.0.0
     */
    CompletableFuture<PlayerDTO> createPlayer(UUID playerId, String playerName, String world, double x, double y, double z);
    
    /**
     * Checks if a player exists in the database.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing true if the player exists
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if check fails
     * @since 1.0.0
     */
    CompletableFuture<Boolean> playerExists(UUID playerId);
}
