package org.fourz.rvnkcore.service.player;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of PlayerWorldService.
 * 
 * Manages both global player data and world-specific tracking with
 * performance optimizations including rate limiting for location updates
 * and caching for frequently accessed data.
 * 
 * @since 1.0.0
 */
public class DefaultPlayerWorldService implements PlayerWorldService {
    
    private final PlayerRepository playerRepository;
    private final PlayerWorldDataRepository worldDataRepository;
    private final LogManager logger;
    
    // Rate limiting for location updates (prevents excessive DB writes)
    private final Map<String, Long> lastLocationUpdate = new ConcurrentHashMap<>();
    private static final long LOCATION_UPDATE_INTERVAL_MS = 30000; // 30 seconds
    
    // Session tracking for playtime calculation
    private final Map<UUID, SessionData> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * Constructor for DefaultPlayerWorldService.
     * 
     * @param playerRepository Repository for global player data
     * @param worldDataRepository Repository for world-specific data
     * @param plugin Plugin instance for logging
     */
    public DefaultPlayerWorldService(PlayerRepository playerRepository,
                                   PlayerWorldDataRepository worldDataRepository,
                                   Plugin plugin) {
        this.playerRepository = playerRepository;
        this.worldDataRepository = worldDataRepository;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    // Global Player Management Implementation
    
    @Override
    public CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId) {
        return playerRepository.findById(playerId);
    }
    
    @Override
    public CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName) {
        return playerRepository.findByCurrentName(playerName);
    }
    
    @Override
    public CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player) {
        return playerRepository.save(player);
    }
    
    @Override
    public CompletableFuture<Void> recordPlayerJoin(UUID playerId, String playerName, String currentWorld) {
        return getPlayer(playerId)
            .thenCompose(existingPlayer -> {
                PlayerDTO player;
                if (existingPlayer.isPresent()) {
                    player = existingPlayer.get();
                    player.updateName(playerName);
                    player.recordJoin();
                    player.setCurrentWorld(currentWorld);
                } else {
                    // First time player
                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    player = new PlayerDTO.Builder()
                        .id(playerId)
                        .currentName(playerName)
                        .firstJoin(now)
                        .lastSeen(now)
                        .currentWorld(currentWorld)
                        .timesJoined(1)
                        .totalPlaytimeSeconds(0L)
                        .build();
                }
                
                // Start session tracking
                activeSessions.put(playerId, new SessionData(currentWorld, System.currentTimeMillis()));
                
                return savePlayer(player).thenApply(saved -> null);
            });
    }
    
    @Override
    public CompletableFuture<Void> recordPlayerQuit(UUID playerId, long sessionDurationSeconds) {
        SessionData session = activeSessions.remove(playerId);
        
        return getPlayer(playerId)
            .thenCompose(playerOpt -> {
                if (playerOpt.isEmpty()) {
                    logger.warning("Attempted to record quit for unknown player: " + playerId);
                    return CompletableFuture.completedFuture(null);
                }
                
                PlayerDTO player = playerOpt.get();
                player.addTotalPlaytime(sessionDurationSeconds);
                player.setLastSeen(Timestamp.valueOf(LocalDateTime.now()));
                
                CompletableFuture<PlayerDTO> savePlayerFuture = savePlayer(player);
                
                // Also update world-specific playtime if we have session data
                if (session != null) {
                    CompletableFuture<Void> addWorldPlaytimeFuture = 
                        addWorldPlaytime(playerId, session.worldName, sessionDurationSeconds);
                    
                    return CompletableFuture.allOf(
                        savePlayerFuture.thenApply(saved -> null),
                        addWorldPlaytimeFuture
                    );
                } else {
                    return savePlayerFuture.thenApply(saved -> null);
                }
            });
    }
    
    // World-Specific Player Management Implementation
    
    @Override
    public CompletableFuture<Optional<PlayerWorldDataDTO>> getPlayerWorldData(UUID playerId, String worldName) {
        return worldDataRepository.findByPlayerAndWorld(playerId, worldName);
    }
    
    @Override
    public CompletableFuture<List<PlayerWorldDataDTO>> getAllPlayerWorldData(UUID playerId) {
        return worldDataRepository.findAllByPlayer(playerId);
    }
    
    @Override
    public CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName) {
        return getPlayerWorldData(playerId, worldName);
    }
    
    @Override
    public CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld,
                                                   double x, double y, double z, float yaw, float pitch) {
        return recordWorldChange(playerId, fromWorld, toWorld, x, y, z, yaw, pitch, null);
    }

    @Override
    public CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld,
                                                   double x, double y, double z, float yaw, float pitch,
                                                   Map<String, Object> worldSpecificData) {
        // Update session tracking
        SessionData session = activeSessions.get(playerId);
        if (session != null) {
            session.worldName = toWorld;
        }

        // Update global player current world
        CompletableFuture<Void> updateGlobalFuture = getPlayer(playerId)
            .thenCompose(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO player = playerOpt.get();
                    player.setCurrentWorld(toWorld);
                    return savePlayer(player).thenApply(saved -> null);
                }
                return CompletableFuture.completedFuture(null);
            });

        // Create or update world data for the destination world
        CompletableFuture<Void> updateWorldDataFuture = getPlayerWorldData(playerId, toWorld)
            .thenCompose(existingData -> {
                PlayerWorldDataDTO worldData;
                if (existingData.isPresent()) {
                    worldData = existingData.get();
                    worldData.recordVisit();
                    worldData.updateLocation(x, y, z, yaw, pitch);
                } else {
                    worldData = new PlayerWorldDataDTO(playerId, toWorld);
                    worldData.updateLocation(x, y, z, yaw, pitch);
                }

                // Merge world-specific data if provided
                if (worldSpecificData != null && !worldSpecificData.isEmpty()) {
                    for (Map.Entry<String, Object> entry : worldSpecificData.entrySet()) {
                        worldData.setWorldData(entry.getKey(), entry.getValue());
                    }
                }

                return worldDataRepository.save(worldData).thenApply(saved -> null);
            });

        return CompletableFuture.allOf(updateGlobalFuture, updateWorldDataFuture);
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, 
                                                      double x, double y, double z, 
                                                      float yaw, float pitch, String biome) {
        // Rate limiting to prevent excessive database writes
        String key = playerId + ":" + worldName;
        long now = System.currentTimeMillis();
        Long lastUpdate = lastLocationUpdate.get(key);
        
        if (lastUpdate != null && (now - lastUpdate) < LOCATION_UPDATE_INTERVAL_MS) {
            // Skip update due to rate limiting
            return CompletableFuture.completedFuture(null);
        }
        
        lastLocationUpdate.put(key, now);
        
        return getPlayerWorldData(playerId, worldName)
            .thenCompose(existingData -> {
                PlayerWorldDataDTO worldData;
                if (existingData.isPresent()) {
                    worldData = existingData.get();
                } else {
                    worldData = new PlayerWorldDataDTO(playerId, worldName);
                }
                
                worldData.updateLocation(x, y, z, yaw, pitch);
                if (biome != null) {
                    worldData.setLastBiome(biome);
                }
                
                return worldDataRepository.save(worldData).thenApply(saved -> null);
            });
    }
    
    @Override
    public CompletableFuture<Void> recordPlayerDeath(UUID playerId, String worldName) {
        return getPlayerWorldData(playerId, worldName)
            .thenCompose(existingData -> {
                PlayerWorldDataDTO worldData;
                if (existingData.isPresent()) {
                    worldData = existingData.get();
                } else {
                    worldData = new PlayerWorldDataDTO(playerId, worldName);
                }
                
                worldData.recordDeath();
                return worldDataRepository.save(worldData).thenApply(saved -> null);
            });
    }
    
    @Override
    public CompletableFuture<Void> addWorldPlaytime(UUID playerId, String worldName, long additionalSeconds) {
        return getPlayerWorldData(playerId, worldName)
            .thenCompose(existingData -> {
                PlayerWorldDataDTO worldData;
                if (existingData.isPresent()) {
                    worldData = existingData.get();
                } else {
                    worldData = new PlayerWorldDataDTO(playerId, worldName);
                }
                
                worldData.addPlaytime(additionalSeconds);
                return worldDataRepository.save(worldData).thenApply(saved -> null);
            });
    }
    
    // World Analysis and Statistics Implementation
    
    @Override
    public CompletableFuture<List<PlayerWorldDataDTO>> getWorldVisitors(String worldName) {
        return worldDataRepository.findAllByWorld(worldName);
    }
    
    @Override
    public CompletableFuture<List<PlayerWorldDataDTO>> getRecentWorldVisitors(String worldName, int hoursAgo) {
        return worldDataRepository.findRecentVisitors(worldName, hoursAgo);
    }
    
    @Override
    public CompletableFuture<Long> getWorldTotalPlaytime(String worldName) {
        return getWorldVisitors(worldName)
            .thenApply(visitors -> 
                visitors.stream()
                    .mapToLong(PlayerWorldDataDTO::getPlaytimeSeconds)
                    .sum()
            );
    }
    
    @Override
    public CompletableFuture<List<PlayerWorldDataDTO>> getPlayerMostVisitedWorlds(UUID playerId, int limit) {
        return getAllPlayerWorldData(playerId)
            .thenApply(worldDataList -> 
                worldDataList.stream()
                    .sorted((a, b) -> Integer.compare(b.getVisitCount(), a.getVisitCount()))
                    .limit(limit)
                    .collect(Collectors.toList())
            );
    }
    
    // Utility Methods for Worldswap Command Implementation
    
    @Override
    public CompletableFuture<List<String>> getPlayerVisitedWorlds(UUID playerId) {
        return getAllPlayerWorldData(playerId)
            .thenApply(worldDataList -> 
                worldDataList.stream()
                    .map(PlayerWorldDataDTO::getWorldName)
                    .sorted()
                    .collect(Collectors.toList())
            );
    }
    
    @Override
    public CompletableFuture<Boolean> hasPlayerVisitedWorld(UUID playerId, String worldName) {
        return getPlayerWorldData(playerId, worldName)
            .thenApply(Optional::isPresent);
    }
    
    @Override
    public CompletableFuture<Optional<String>> getPlayerPreviousWorld(UUID playerId, String currentWorld) {
        return getAllPlayerWorldData(playerId)
            .thenApply(worldDataList -> {
                // Find the most recent world that's not the current world
                return worldDataList.stream()
                    .filter(data -> !data.getWorldName().equals(currentWorld))
                    .max((a, b) -> a.getLastVisit().compareTo(b.getLastVisit()))
                    .map(PlayerWorldDataDTO::getWorldName);
            });
    }
    
    /**
     * Internal class for tracking active player sessions.
     */
    private static class SessionData {
        String worldName;
        
        SessionData(String worldName, long startTime) {
            this.worldName = worldName;
            // startTime parameter kept for potential future use
        }
    }
}
