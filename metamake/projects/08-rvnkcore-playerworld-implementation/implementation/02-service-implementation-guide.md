# Service Architecture Implementation Guide

**Guide ID**: 02-service-implementation-guide  
**Phase**: Service Layer Implementation  
**Prerequisites**: Database schema implemented, repositories functional

## PlayerWorldService Interface Implementation

### Service Interface Definition

Create the `PlayerWorldService` interface with comprehensive method signatures:

```java
package org.fourz.rvnkcore.service;

import org.fourz.rvnkcore.dto.PlayerDTO;
import org.fourz.rvnkcore.dto.PlayerWorldDataDTO;
import org.fourz.rvnkcore.dto.WorldAnalyticsDTO;
import org.bukkit.Location;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing per-world player data and analytics.
 * Provides comprehensive player tracking across multiple worlds with
 * location management, visit tracking, and performance analytics.
 */
public interface PlayerWorldService {
    
    // Global Player Management
    CompletableFuture<PlayerDTO> getPlayer(UUID playerId);
    CompletableFuture<PlayerDTO> getPlayerByName(String playerName);
    CompletableFuture<Void> savePlayer(PlayerDTO player);
    
    // World-Specific Location Management
    CompletableFuture<Location> getLastKnownLocation(UUID playerId, String worldName);
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, Location location);
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld);
    
    // Visit and Playtime Management
    CompletableFuture<Void> recordJoin(UUID playerId, String worldName, Location location);
    CompletableFuture<Void> recordLeave(UUID playerId, String worldName);
    CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId);
    CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
    
    // Analytics and Statistics
    CompletableFuture<Long> getTotalPlaytime(UUID playerId);
    CompletableFuture<Long> getWorldPlaytime(UUID playerId, String worldName);
    CompletableFuture<List<String>> getMostActiveWorlds(UUID playerId, int limit);
    CompletableFuture<WorldAnalyticsDTO> getWorldVisitCounts(String worldName);
    
    // WorldSwap Support
    CompletableFuture<Boolean> hasPlayerVisitedWorld(UUID playerId, String worldName);
    CompletableFuture<String> getPlayerPreviousWorld(UUID playerId);
    CompletableFuture<Void> recordWorldSwap(UUID playerId, String fromWorld, String toWorld, String swapMethod);
}
```

### DefaultPlayerWorldService Implementation

#### Class Structure and Dependencies

```java
package org.fourz.rvnkcore.service.impl;

import org.fourz.rvnkcore.service.PlayerWorldService;
import org.fourz.rvnkcore.repository.PlayerRepository;
import org.fourz.rvnkcore.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.util.RateLimiter;
import org.fourz.rvnkcore.cache.CacheManager;
import org.fourz.rvnkcore.session.SessionManager;
import org.fourz.rvnkcore.logging.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Default implementation of PlayerWorldService providing comprehensive
 * per-world player tracking with rate limiting, caching, and session management.
 */
public class DefaultPlayerWorldService implements PlayerWorldService {
    
    private final Plugin plugin;
    private final LogManager logger;
    private final PlayerRepository playerRepository;
    private final PlayerWorldDataRepository worldDataRepository;
    private final RateLimiter<UUID> locationUpdateLimiter;
    private final CacheManager cache;
    private final SessionManager sessionManager;
    
    // Configuration
    private final long locationUpdateIntervalMs;
    private final int cacheMaxSize;
    private final long cacheExpirationMs;
    
    /**
     * Constructor with dependency injection
     */
    public DefaultPlayerWorldService(
            Plugin plugin,
            PlayerRepository playerRepository,
            PlayerWorldDataRepository worldDataRepository,
            RateLimiter<UUID> locationUpdateLimiter,
            CacheManager cache,
            SessionManager sessionManager) {
        
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.playerRepository = playerRepository;
        this.worldDataRepository = worldDataRepository;
        this.locationUpdateLimiter = locationUpdateLimiter;
        this.cache = cache;
        this.sessionManager = sessionManager;
        
        // Load configuration
        this.locationUpdateIntervalMs = plugin.getConfig().getLong("playerworld.location-update-interval", 30000);
        this.cacheMaxSize = plugin.getConfig().getInt("playerworld.cache.max-size", 1000);
        this.cacheExpirationMs = plugin.getConfig().getLong("playerworld.cache.expiration", 300000);
        
        logger.info("PlayerWorldService initialized with " + locationUpdateIntervalMs + "ms location update interval");
    }
```

#### Rate Limiting Implementation

```java
    /**
     * Updates player location with rate limiting to prevent spam
     */
    @Override
    public CompletableFuture<Void> updatePlayerLocation(UUID playerId, Location location) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Check rate limiting
                if (!locationUpdateLimiter.tryAcquire(playerId)) {
                    logger.debug("Location update rate limited for player " + playerId);
                    return;
                }
                
                // Validate location
                if (location == null || location.getWorld() == null) {
                    logger.warning("Invalid location provided for player " + playerId);
                    return;
                }
                
                String worldName = location.getWorld().getName();
                
                // Create location update DTO
                PlayerWorldDataDTO updateData = PlayerWorldDataDTO.builder()
                    .playerId(playerId)
                    .worldName(worldName)
                    .lastX(location.getX())
                    .lastY(location.getY())
                    .lastZ(location.getZ())
                    .lastYaw(location.getYaw())
                    .lastPitch(location.getPitch())
                    .biome(location.getBlock().getBiome().name())
                    .lastSeen(System.currentTimeMillis())
                    .build();
                
                // Update in database
                worldDataRepository.updatePlayerLocation(updateData)
                    .thenRun(() -> {
                        // Invalidate cache
                        cache.invalidate("player_location_" + playerId + "_" + worldName);
                        logger.debug("Updated location for player " + playerId + " in world " + worldName);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to update location for player " + playerId, throwable);
                        return null;
                    });
                
            } catch (Exception e) {
                logger.error("Error updating player location", e);
                throw new RuntimeException("Failed to update player location", e);
            }
        });
    }
```

#### Caching Implementation

```java
    /**
     * Gets last known location with caching
     */
    @Override
    public CompletableFuture<Location> getLastKnownLocation(UUID playerId, String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = "player_location_" + playerId + "_" + worldName;
                
                // Check cache first
                Location cachedLocation = cache.get(cacheKey, Location.class);
                if (cachedLocation != null) {
                    logger.debug("Cache hit for player location: " + playerId + " in " + worldName);
                    return cachedLocation;
                }
                
                // Query database
                PlayerWorldDataDTO worldData = worldDataRepository.getPlayerWorldData(playerId, worldName)
                    .join();
                
                if (worldData == null || worldData.getLastX() == null) {
                    logger.debug("No location data found for player " + playerId + " in world " + worldName);
                    return null;
                }
                
                // Convert to Bukkit Location
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    logger.warning("World not found: " + worldName);
                    return null;
                }
                
                Location location = new Location(
                    world,
                    worldData.getLastX(),
                    worldData.getLastY(),
                    worldData.getLastZ(),
                    worldData.getLastYaw() != null ? worldData.getLastYaw() : 0,
                    worldData.getLastPitch() != null ? worldData.getLastPitch() : 0
                );
                
                // Cache the result
                cache.put(cacheKey, location, cacheExpirationMs);
                
                return location;
                
            } catch (Exception e) {
                logger.error("Error retrieving player location", e);
                throw new RuntimeException("Failed to get player location", e);
            }
        });
    }
```

#### Session Management Implementation

```java
    /**
     * Records player join with session management
     */
    @Override
    public CompletableFuture<Void> recordJoin(UUID playerId, String worldName, Location location) {
        return CompletableFuture.runAsync(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                
                // Start new session
                sessionManager.startSession(playerId, worldName, location, currentTime);
                
                // Update global player data
                playerRepository.getPlayer(playerId)
                    .thenCompose(player -> {
                        if (player == null) {
                            // Create new player
                            PlayerDTO newPlayer = PlayerDTO.builder()
                                .playerId(playerId)
                                .currentName("Unknown") // Will be updated by event handler
                                .firstJoined(currentTime)
                                .lastSeen(currentTime)
                                .timesJoined(1L)
                                .build();
                            return playerRepository.savePlayer(newPlayer);
                        } else {
                            // Update existing player
                            player.setLastSeen(currentTime);
                            player.setTimesJoined(player.getTimesJoined() + 1);
                            return playerRepository.updatePlayer(player);
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to update global player data for " + playerId, throwable);
                        return null;
                    });
                
                // Update or create world-specific data
                worldDataRepository.getPlayerWorldData(playerId, worldName)
                    .thenCompose(worldData -> {
                        if (worldData == null) {
                            // Create new world data
                            PlayerWorldDataDTO newWorldData = PlayerWorldDataDTO.builder()
                                .playerId(playerId)
                                .worldName(worldName)
                                .firstVisit(currentTime)
                                .lastSeen(currentTime)
                                .visitCount(1L)
                                .totalPlaytimeMs(0L)
                                .lastX(location.getX())
                                .lastY(location.getY())
                                .lastZ(location.getZ())
                                .biome(location.getBlock().getBiome().name())
                                .build();
                            return worldDataRepository.savePlayerWorldData(newWorldData);
                        } else {
                            // Update existing world data
                            worldData.setLastSeen(currentTime);
                            worldData.setVisitCount(worldData.getVisitCount() + 1);
                            return worldDataRepository.updatePlayerWorldData(worldData);
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to update world data for player " + playerId + " in " + worldName, throwable);
                        return null;
                    });
                
                logger.info("Recorded join for player " + playerId + " in world " + worldName);
                
            } catch (Exception e) {
                logger.error("Error recording player join", e);
                throw new RuntimeException("Failed to record player join", e);
            }
        });
    }
    
    /**
     * Records player leave with session completion
     */
    @Override
    public CompletableFuture<Void> recordLeave(UUID playerId, String worldName) {
        return CompletableFuture.runAsync(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                
                // Complete session and get playtime
                Long sessionPlaytime = sessionManager.endSession(playerId, worldName, currentTime);
                
                if (sessionPlaytime != null && sessionPlaytime > 0) {
                    // Update total playtime in world data
                    worldDataRepository.getPlayerWorldData(playerId, worldName)
                        .thenCompose(worldData -> {
                            if (worldData != null) {
                                worldData.setTotalPlaytimeMs(worldData.getTotalPlaytimeMs() + sessionPlaytime);
                                worldData.setLastSeen(currentTime);
                                return worldDataRepository.updatePlayerWorldData(worldData);
                            }
                            return CompletableFuture.completedFuture(null);
                        })
                        .exceptionally(throwable -> {
                            logger.error("Failed to update playtime for player " + playerId, throwable);
                            return null;
                        });
                }
                
                // Update global player last seen
                playerRepository.getPlayer(playerId)
                    .thenCompose(player -> {
                        if (player != null) {
                            player.setLastSeen(currentTime);
                            return playerRepository.updatePlayer(player);
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to update global player data for " + playerId, throwable);
                        return null;
                    });
                
                // Invalidate relevant cache entries
                cache.invalidate("player_location_" + playerId + "_" + worldName);
                cache.invalidate("player_worlds_" + playerId);
                
                logger.info("Recorded leave for player " + playerId + " from world " + worldName + 
                           " (session playtime: " + (sessionPlaytime != null ? sessionPlaytime + "ms" : "unknown") + ")");
                
            } catch (Exception e) {
                logger.error("Error recording player leave", e);
                throw new RuntimeException("Failed to record player leave", e);
            }
        });
    }
```

#### Analytics Implementation

```java
    /**
     * Gets total playtime across all worlds
     */
    @Override
    public CompletableFuture<Long> getTotalPlaytime(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = "total_playtime_" + playerId;
                
                // Check cache
                Long cachedPlaytime = cache.get(cacheKey, Long.class);
                if (cachedPlaytime != null) {
                    return cachedPlaytime;
                }
                
                // Query database for total playtime
                Long totalPlaytime = worldDataRepository.getTotalPlaytime(playerId).join();
                
                // Add current session time if player is online
                Long activeSessionTime = sessionManager.getCurrentSessionTime(playerId);
                if (activeSessionTime != null) {
                    totalPlaytime += activeSessionTime;
                }
                
                // Cache result for shorter time since it includes active session
                cache.put(cacheKey, totalPlaytime, 60000); // 1 minute cache
                
                return totalPlaytime;
                
            } catch (Exception e) {
                logger.error("Error calculating total playtime for player " + playerId, e);
                throw new RuntimeException("Failed to calculate total playtime", e);
            }
        });
    }
    
    /**
     * Gets most active worlds for a player
     */
    @Override
    public CompletableFuture<List<String>> getMostActiveWorlds(UUID playerId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = "active_worlds_" + playerId + "_" + limit;
                
                // Check cache
                @SuppressWarnings("unchecked")
                List<String> cachedWorlds = cache.get(cacheKey, List.class);
                if (cachedWorlds != null) {
                    return cachedWorlds;
                }
                
                // Query database
                List<String> activeWorlds = worldDataRepository.getMostActiveWorlds(playerId, limit).join();
                
                // Cache result
                cache.put(cacheKey, activeWorlds, cacheExpirationMs);
                
                return activeWorlds;
                
            } catch (Exception e) {
                logger.error("Error retrieving active worlds for player " + playerId, e);
                throw new RuntimeException("Failed to get active worlds", e);
            }
        });
    }
```

#### WorldSwap Integration

```java
    /**
     * Checks if player has visited a world (WorldSwap integration)
     */
    @Override
    public CompletableFuture<Boolean> hasPlayerVisitedWorld(UUID playerId, String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = "visited_" + playerId + "_" + worldName;
                
                // Check cache
                Boolean cachedResult = cache.get(cacheKey, Boolean.class);
                if (cachedResult != null) {
                    return cachedResult;
                }
                
                // Query database
                PlayerWorldDataDTO worldData = worldDataRepository.getPlayerWorldData(playerId, worldName).join();
                boolean hasVisited = worldData != null && worldData.getVisitCount() > 0;
                
                // Cache result
                cache.put(cacheKey, hasVisited, cacheExpirationMs);
                
                return hasVisited;
                
            } catch (Exception e) {
                logger.error("Error checking world visit for player " + playerId + " world " + worldName, e);
                throw new RuntimeException("Failed to check world visit", e);
            }
        });
    }
    
    /**
     * Records a worldswap event with method tracking
     */
    @Override
    public CompletableFuture<Void> recordWorldSwap(UUID playerId, String fromWorld, String toWorld, String swapMethod) {
        return CompletableFuture.runAsync(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                
                // Record the world change
                recordWorldChange(playerId, fromWorld, toWorld).join();
                
                // Log worldswap event for analytics
                logger.info("WorldSwap: Player " + playerId + " swapped from " + fromWorld + 
                           " to " + toWorld + " using method: " + swapMethod);
                
                // Invalidate relevant cache entries
                cache.invalidatePattern("player_" + playerId + "_*");
                cache.invalidate("visited_" + playerId + "_" + toWorld);
                
            } catch (Exception e) {
                logger.error("Error recording worldswap for player " + playerId, e);
                throw new RuntimeException("Failed to record worldswap", e);
            }
        });
    }
```

## Service Configuration

### Configuration File Setup

Add configuration options to your plugin's `config.yml`:

```yaml
playerworld:
  # Rate limiting settings
  location-update-interval: 30000  # milliseconds between location updates
  
  # Caching settings
  cache:
    max-size: 1000           # maximum number of cached entries
    expiration: 300000       # cache expiration in milliseconds (5 minutes)
    
  # Session management
  session:
    cleanup-interval: 60000  # interval to clean up old sessions
    max-inactive-time: 600000 # maximum time before considering session inactive
    
  # Analytics settings
  analytics:
    batch-size: 100          # batch size for analytics operations
    calculation-interval: 300000 # interval for recalculating analytics
    
  # Performance settings
  database:
    connection-timeout: 30000 # database connection timeout
    query-timeout: 15000     # database query timeout
```

### Service Registration

Register the service with the RVNKCore ServiceRegistry:

```java
// In your plugin's onEnable method
private void initializePlayerWorldService() {
    // Get required dependencies
    PlayerRepository playerRepository = ServiceRegistry.getInstance().getService(PlayerRepository.class);
    PlayerWorldDataRepository worldDataRepository = ServiceRegistry.getInstance().getService(PlayerWorldDataRepository.class);
    RateLimiter<UUID> rateLimiter = new TokenBucketRateLimiter<>(locationUpdateIntervalMs);
    CacheManager cache = ServiceRegistry.getInstance().getService(CacheManager.class);
    SessionManager sessionManager = new DefaultSessionManager();
    
    // Create and register service
    PlayerWorldService playerWorldService = new DefaultPlayerWorldService(
        this,
        playerRepository,
        worldDataRepository,
        rateLimiter,
        cache,
        sessionManager
    );
    
    ServiceRegistry.getInstance().registerService(PlayerWorldService.class, playerWorldService);
    
    getLogger().info("PlayerWorldService registered successfully");
}
```

## Testing Implementation

### Unit Test Structure

```java
@ExtendWith(MockitoExtension.class)
class DefaultPlayerWorldServiceTest {
    
    @Mock private Plugin plugin;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerWorldDataRepository worldDataRepository;
    @Mock private RateLimiter<UUID> rateLimiter;
    @Mock private CacheManager cache;
    @Mock private SessionManager sessionManager;
    
    @InjectMocks
    private DefaultPlayerWorldService service;
    
    @Test
    void testUpdatePlayerLocation_WithRateLimiting() {
        // Arrange
        UUID playerId = UUID.randomUUID();
        Location location = new Location(null, 100, 64, 200);
        when(rateLimiter.tryAcquire(playerId)).thenReturn(false);
        
        // Act
        CompletableFuture<Void> result = service.updatePlayerLocation(playerId, location);
        
        // Assert
        assertDoesNotThrow(() -> result.join());
        verify(worldDataRepository, never()).updatePlayerLocation(any());
    }
    
    @Test
    void testGetLastKnownLocation_CacheHit() {
        // Arrange
        UUID playerId = UUID.randomUUID();
        String worldName = "world";
        Location cachedLocation = new Location(null, 100, 64, 200);
        when(cache.get(anyString(), eq(Location.class))).thenReturn(cachedLocation);
        
        // Act
        Location result = service.getLastKnownLocation(playerId, worldName).join();
        
        // Assert
        assertEquals(cachedLocation, result);
        verify(worldDataRepository, never()).getPlayerWorldData(any(), any());
    }
}
```

## Performance Considerations

### Optimization Guidelines

1. **Rate Limiting**: Configure appropriate intervals to balance data accuracy with performance
2. **Caching Strategy**: Cache frequently accessed data with appropriate expiration times
3. **Batch Operations**: Use batch database operations for bulk updates
4. **Async Processing**: Keep all database operations asynchronous to avoid blocking main thread
5. **Connection Pooling**: Use database connection pooling for optimal resource usage
6. **Session Management**: Clean up inactive sessions regularly to prevent memory leaks

### Monitoring and Metrics

```java
// Add performance monitoring to service methods
private void recordMethodPerformance(String methodName, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    if (duration > 100) { // Log slow operations
        logger.warning("Slow " + methodName + " operation: " + duration + "ms");
    }
}
```

---

**Implementation Notes:**
- All database operations are asynchronous using CompletableFuture
- Rate limiting prevents excessive location updates
- Caching reduces database load for frequently accessed data
- Session management provides accurate playtime calculations
- WorldSwap integration enables seamless world transitions
- Comprehensive error handling ensures system stability
