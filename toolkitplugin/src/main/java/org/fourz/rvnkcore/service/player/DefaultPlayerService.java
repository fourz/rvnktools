package org.fourz.rvnkcore.service.player;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PlayerService providing comprehensive player data management.
 * 
 * This service handles all player-related operations including tracking activity,
 * managing location data, maintaining name history, and handling permission groups.
 * All operations are asynchronous to prevent blocking the main thread.
 * 
 * @since 1.0.0
 */
public class DefaultPlayerService implements PlayerService {
    
    private final PlayerRepository playerRepository;
    private final LogManager logger;
    
    /**
     * Constructor for DefaultPlayerService.
     * 
     * @param playerRepository The repository for player data operations
     * @param plugin The plugin instance for logging
     */
    public DefaultPlayerService(PlayerRepository playerRepository, Plugin plugin) {
        this.playerRepository = playerRepository;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId) {
        logger.debug("Retrieving player data for ID: " + playerId);
        return playerRepository.findById(playerId)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to retrieve player " + playerId, throwable);
                } else if (result.isPresent()) {
                    logger.debug("Successfully retrieved player: " + result.get().getCurrentName());
                } else {
                    logger.debug("Player not found: " + playerId);
                }
            });
    }
    
    @Override
    public CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName) {
        logger.debug("Retrieving player data for name: " + playerName);
        return playerRepository.findByCurrentName(playerName)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to retrieve player by name " + playerName, throwable);
                } else if (result.isPresent()) {
                    logger.debug("Successfully retrieved player by name: " + playerName);
                } else {
                    logger.debug("Player not found by name: " + playerName);
                }
            });
    }
    
    @Override
    public CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player) {
        String operation = player.getId() == null ? "Creating new" : "Updating existing";
        logger.debug(operation + " player data for: " + player.getCurrentName());
        return playerRepository.save(player)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to save player " + player.getCurrentName(), throwable);
                } else {
                    String successOperation = player.getId() == null ? "Created new" : "Updated existing";
                    logger.debug(successOperation + " player: " + result.getCurrentName());
                }
            });
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerLocation(UUID playerId, String world, double x, double y, double z) {
        logger.debug("Updating current world for player: " + playerId + " to " + world);
        
        return getPlayer(playerId)
            .thenCompose(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO player = playerOpt.get();
                    player.setCurrentWorld(world);
                    player.setLastSeen(Timestamp.valueOf(LocalDateTime.now()));
                    return savePlayer(player).thenApply(savedPlayer -> null);
                } else {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalArgumentException("Player not found: " + playerId));
                    return future;
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update location for player " + playerId, throwable);
                } else {
                    logger.debug("Successfully stored new location for player: " + playerId);
                }
            });
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerName(UUID playerId, String newName) {
        logger.debug("Updating name in PlayerDTO for player: " + playerId + " to " + newName);
        
        return getPlayer(playerId)
            .thenCompose(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO player = playerOpt.get();
                    player.updateName(newName);
                    return savePlayer(player).thenApply(savedPlayer -> null);
                } else {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalArgumentException("Player not found: " + playerId));
                    return future;
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update name for player " + playerId, throwable);
                } else {
                    logger.debug("Successfully stored name change for player: " + playerId);
                }
            });
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerGroups(UUID playerId, String primaryGroup, List<String> allGroups) {
        logger.debug("Updating groups in PlayerDTO for player: " + playerId + " primary: " + primaryGroup);
        
        return getPlayer(playerId)
            .thenCompose(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO player = playerOpt.get();
                    player.updateGroups(primaryGroup, allGroups);
                    return savePlayer(player).thenApply(savedPlayer -> null);
                } else {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalArgumentException("Player not found: " + playerId));
                    return future;
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to update groups for player " + playerId, throwable);
                } else {
                    logger.debug("Successfully stored group changes for player: " + playerId);
                }
            });
    }
    
    @Override
    public CompletableFuture<List<PlayerDTO>> getRecentPlayers(int hoursAgo) {
        logger.debug("Retrieving players active within " + hoursAgo + " hours");
        return playerRepository.findRecentPlayers(hoursAgo)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to retrieve recent players", throwable);
                } else {
                    logger.debug("Successfully retrieved " + result.size() + " recent players");
                }
            });
    }
    
    @Override
    public CompletableFuture<List<PlayerDTO>> getPlayersByGroup(String groupName) {
        logger.debug("Retrieving players in group: " + groupName);
        return playerRepository.findByPrimaryGroup(groupName)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to retrieve players by group " + groupName, throwable);
                } else {
                    logger.debug("Successfully retrieved " + result.size() + " players in group: " + groupName);
                }
            });
    }
    
    @Override
    public CompletableFuture<List<PlayerDTO>> searchPlayersByName(String namePattern) {
        logger.debug("Searching players by name pattern: " + namePattern);
        return playerRepository.searchByNamePattern(namePattern)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to search players by name pattern " + namePattern, throwable);
                } else {
                    logger.debug("Successfully found " + result.size() + " players matching pattern: " + namePattern);
                }
            });
    }
    
    @Override
    public CompletableFuture<Long> getPlayerCount() {
        logger.debug("Retrieving total player count");
        return playerRepository.count()
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to retrieve player count", throwable);
                } else {
                    logger.debug("Total player count: " + result);
                }
            });
    }
    
    @Override
    public CompletableFuture<PlayerDTO> createPlayer(UUID playerId, String playerName, String world, double x, double y, double z) {
        logger.info("Creating new player record: " + playerName + " (" + playerId + ")");
        
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        
        PlayerDTO newPlayer = new PlayerDTO.Builder()
            .id(playerId)
            .currentName(playerName)
            .firstJoin(now)
            .lastSeen(now)
            .currentWorld(world)
            .banned(false)
            .build();
        
        return savePlayer(newPlayer)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to create new player " + playerName, throwable);
                } else {
                    logger.info("Successfully created new player: " + result.getCurrentName());
                }
            });
    }
    
    @Override
    public CompletableFuture<Boolean> playerExists(UUID playerId) {
        logger.debug("Checking if player exists: " + playerId);
        return playerRepository.existsById(playerId)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to check player existence " + playerId, throwable);
                } else {
                    logger.debug("Player exists check for " + playerId + ": " + result);
                }
            });
    }
}
