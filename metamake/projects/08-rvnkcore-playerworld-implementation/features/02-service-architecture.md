# Service Architecture Feature Specification

**Feature ID**: 02-service-architecture  
**Priority**: Critical  
**Dependencies**: Database Schema, RVNKCore Service Registry  
**Implementation Phase**: 2

## Overview

This feature implements the comprehensive service layer for per-world player tracking, providing clean abstraction over database operations and enabling advanced world management capabilities through the PlayerWorldService interface.

## Service Architecture Design

### PlayerWorldService Interface

**Primary Interface**: Comprehensive world-specific player management

```java
package org.fourz.rvnkcore.api.service;

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
    
    // Global Player Management (delegates to PlayerService)
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    
    // World-Specific Location Management
    CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName);
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, 
                                               double x, double y, double z, 
                                               float yaw, float pitch, String biome);
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, 
                                            double x, double y, double z, float yaw, float pitch);
    
    // Visit and Playtime Management
    CompletableFuture<PlayerWorldDataDTO> recordJoin(UUID playerId, String worldName, 
                                                   double x, double y, double z, 
                                                   float yaw, float pitch, String biome);
    CompletableFuture<Void> recordLeave(UUID playerId, String worldName, long sessionSeconds);
    CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId);
    CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
    
    // Analytics and Statistics
    CompletableFuture<Long> getTotalPlaytime(UUID playerId);
    CompletableFuture<Long> getWorldPlaytime(UUID playerId, String worldName);
    CompletableFuture<List<PlayerWorldDataDTO>> getMostActiveWorlds(UUID playerId, int limit);
    CompletableFuture<Map<String, Integer>> getWorldVisitCounts(UUID playerId);
    
    // World-Focused Analytics
    CompletableFuture<List<PlayerWorldDataDTO>> getWorldVisitors(String worldName, int limit);
    CompletableFuture<List<PlayerWorldDataDTO>> getRecentWorldVisitors(String worldName, int hoursAgo);
    CompletableFuture<Long> getWorldTotalPlaytime(String worldName);
    CompletableFuture<Map<String, Long>> getWorldPlaytimeRankings(int limit);
    
    // Death Tracking
    CompletableFuture<Void> recordDeath(UUID playerId, String worldName, 
                                      double x, double y, double z, String cause);
    CompletableFuture<Integer> getDeathCount(UUID playerId, String worldName);
    CompletableFuture<Map<String, Integer>> getPlayerDeathsByWorld(UUID playerId);
    
    // WorldSwap Support
    CompletableFuture<Boolean> hasPlayerVisitedWorld(UUID playerId, String worldName);
    CompletableFuture<Optional<String>> getPlayerPreviousWorld(UUID playerId, String currentWorld);
    CompletableFuture<Void> recordWorldSwap(UUID playerId, String fromWorld, String toWorld);
}
```

### DefaultPlayerWorldService Implementation

**Core Implementation**: Business logic and orchestration

```java
package org.fourz.rvnkcore.service.player;

/**
 * Default implementation of PlayerWorldService providing comprehensive 
 * world-specific player tracking and management.
 * 
 * This service coordinates between PlayerRepository and PlayerWorldDataRepository
 * to provide unified access to both global and world-specific player data.
 * 
 * Features:
 * - Rate-limited location updates (configurable, default 30 seconds)
 * - Intelligent caching for frequently accessed data
 * - Session-based playtime calculation
 * - Comprehensive world analytics and reporting
 * 
 * @since 1.0.0
 */
public class DefaultPlayerWorldService implements PlayerWorldService {
    
    private final PlayerRepository playerRepository;
    private final PlayerWorldDataRepository playerWorldDataRepository;
    private final RateLimiter locationUpdateRateLimiter;
    private final Cache<String, PlayerWorldDataDTO> worldDataCache;
    private final Map<UUID, PlayerSession> activeSessions;
    private final LogManager logger;
    
    // Core service implementation with rate limiting and caching
    // Event integration for real-time updates
    // Analytics and reporting methods
}
```

## Service Layer Components

### Rate Limiting Strategy

**Location Update Rate Limiting:**

```java
public class LocationUpdateRateLimiter {
    private final Map<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    private final long updateIntervalMs;
    
    /**
     * Checks if location update is allowed for player in world.
     * 
     * @param playerId Player UUID
     * @param worldName World name
     * @return true if update should proceed
     */
    public boolean isUpdateAllowed(UUID playerId, String worldName) {
        String key = playerId + ":" + worldName;
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastUpdateTimes.getOrDefault(key, 0L);
        
        if (currentTime - lastUpdate >= updateIntervalMs) {
            lastUpdateTimes.put(key, currentTime);
            return true;
        }
        return false;
    }
    
    /**
     * Forces an update by resetting the rate limit for player/world.
     * Used for critical events like world changes.
     */
    public void forceUpdate(UUID playerId, String worldName) {
        String key = playerId + ":" + worldName;
        lastUpdateTimes.put(key, 0L);
    }
}
```

**Rate Limiting Configuration:**

- Default interval: 30 seconds per player per world
- Configurable through RVNKCore configuration
- Immediate updates for world changes and critical events
- Session cleanup to prevent memory leaks

### Caching Strategy

**World Data Caching:**

```java
public class WorldDataCache {
    private final Cache<String, PlayerWorldDataDTO> cache;
    private final int maxSize;
    private final Duration expiration;
    
    public WorldDataCache(int maxSize, Duration expiration) {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(expiration)
            .build();
    }
    
    public Optional<PlayerWorldDataDTO> get(UUID playerId, String worldName) {
        String key = playerId + ":" + worldName;
        return Optional.ofNullable(cache.getIfPresent(key));
    }
    
    public void put(UUID playerId, String worldName, PlayerWorldDataDTO data) {
        String key = playerId + ":" + worldName;
        cache.put(key, data);
    }
    
    public void invalidate(UUID playerId, String worldName) {
        String key = playerId + ":" + worldName;
        cache.invalidate(key);
    }
}
```

**Cache Configuration:**

- Default size: 1000 entries (configurable)
- Default expiration: 30 minutes (configurable)
- Cache invalidation on data updates
- Hit rate monitoring and alerting

### Session Management

**Player Session Tracking:**

```java
public class PlayerSession {
    private final UUID playerId;
    private final String worldName;
    private final long startTime;
    private final AtomicReference<String> currentBiome = new AtomicReference<>();
    private final AtomicLong lastLocationUpdate = new AtomicLong(0L);
    
    public PlayerSession(UUID playerId, String worldName) {
        this.playerId = playerId;
        this.worldName = worldName;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Calculates session duration in seconds.
     */
    public long getSessionDurationSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
    
    /**
     * Updates session with current location information.
     */
    public void updateLocation(String biome) {
        this.currentBiome.set(biome);
        this.lastLocationUpdate.set(System.currentTimeMillis());
    }
}
```

## Integration Points

### Repository Integration

**Dual Repository Pattern:**

The service coordinates between two repositories:

1. **PlayerRepository**: Global player data (names, join counts, total playtime)
2. **PlayerWorldDataRepository**: World-specific data (locations, world playtime, visits)

**Transaction Management:**

```java
@Transactional
public CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, 
                                                double x, double y, double z, float yaw, float pitch) {
    return CompletableFuture.runAsync(() -> {
        try {
            // Update global player data with new current world
            playerRepository.updateCurrentWorld(playerId, toWorld);
            
            // Record exit from previous world
            if (fromWorld != null) {
                playerWorldDataRepository.updateLastVisit(playerId, fromWorld);
            }
            
            // Record entry to new world
            playerWorldDataRepository.recordWorldEntry(playerId, toWorld, x, y, z, yaw, pitch);
            
            // Cache invalidation
            worldDataCache.invalidate(playerId, fromWorld);
            worldDataCache.invalidate(playerId, toWorld);
            
        } catch (Exception e) {
            logger.error("Failed to record world change for player " + playerId, e);
            throw new ServiceException("World change recording failed", e);
        }
    });
}
```

### Event System Integration

**Bukkit Event Integration:**

The service integrates with Bukkit events through dedicated event listeners:

```java
@Component
public class PlayerWorldEventListener implements Listener {
    
    private final PlayerWorldService playerWorldService;
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        Location loc = player.getLocation();
        
        playerWorldService.recordJoin(
            player.getUniqueId(),
            worldName,
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getYaw(), loc.getPitch(),
            loc.getBlock().getBiome().name()
        );
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        if (to != null && hasSignificantMovement(event.getFrom(), to)) {
            playerWorldService.updatePlayerLocation(
                player.getUniqueId(),
                to.getWorld().getName(),
                to.getX(), to.getY(), to.getZ(),
                to.getYaw(), to.getPitch(),
                to.getBlock().getBiome().name()
            );
        }
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        Location loc = player.getLocation();
        
        playerWorldService.recordWorldChange(
            player.getUniqueId(),
            fromWorld, toWorld,
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getYaw(), loc.getPitch()
        );
    }
}
```

## Service Configuration

### Configuration Properties

**PlayerWorld Tracking Configuration:**

```yaml
rvnkcore:
  player_tracking:
    location_update_interval_seconds: 30
    cache:
      max_size: 1000
      expiration_minutes: 30
    analytics:
      world_rankings_limit: 50
      recent_visitor_hours: 24
    performance:
      batch_size: 100
      connection_timeout_seconds: 30
```

### Configuration Management

**Dynamic Configuration Updates:**

```java
public class PlayerWorldConfig {
    private int locationUpdateIntervalSeconds = 30;
    private int cacheMaxSize = 1000;
    private int cacheExpirationMinutes = 30;
    private int worldRankingsLimit = 50;
    private int recentVisitorHours = 24;
    
    public void reload(ConfigurationSection config) {
        this.locationUpdateIntervalSeconds = config.getInt("location_update_interval_seconds", 30);
        this.cacheMaxSize = config.getInt("cache.max_size", 1000);
        this.cacheExpirationMinutes = config.getInt("cache.expiration_minutes", 30);
        // ... other configuration updates
        
        // Apply configuration changes
        updateRateLimiter();
        updateCache();
    }
}
```

## Performance Optimization

### Async Operation Patterns

**CompletableFuture Usage:**

All service methods use CompletableFuture for non-blocking operations:

```java
public CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> {
        // Check cache first
        List<PlayerWorldDataDTO> cached = worldHistoryCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        
        // Fetch from repository
        List<PlayerWorldDataDTO> history = playerWorldDataRepository.findAllByPlayer(playerId);
        
        // Cache result
        worldHistoryCache.put(playerId, history);
        
        return history;
    });
}
```

### Batch Operations

**Efficient Bulk Updates:**

```java
public CompletableFuture<Void> batchUpdateLocations(List<LocationUpdate> updates) {
    return CompletableFuture.runAsync(() -> {
        List<LocationUpdate> filteredUpdates = updates.stream()
            .filter(update -> rateLimiter.isUpdateAllowed(update.getPlayerId(), update.getWorldName()))
            .collect(Collectors.toList());
            
        if (!filteredUpdates.isEmpty()) {
            playerWorldDataRepository.batchUpdateLocations(filteredUpdates);
            
            // Invalidate cache for updated entries
            filteredUpdates.forEach(update -> 
                worldDataCache.invalidate(update.getPlayerId(), update.getWorldName())
            );
        }
    });
}
```

## Error Handling and Resilience

### Exception Handling Strategy

**Service-Level Error Handling:**

```java
public CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Input validation
            if (playerId == null || worldName == null || worldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID and world name must be provided");
            }
            
            // Check cache first
            Optional<PlayerWorldDataDTO> cached = worldDataCache.get(playerId, worldName);
            if (cached.isPresent()) {
                return cached;
            }
            
            // Fetch from repository
            Optional<PlayerWorldDataDTO> result = playerWorldDataRepository.findByPlayerAndWorld(playerId, worldName);
            
            // Cache successful result
            result.ifPresent(data -> worldDataCache.put(playerId, worldName, data));
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get last known location for player " + playerId + " in world " + worldName, e);
            throw new ServiceException("Location retrieval failed", e);
        }
    });
}
```

### Fallback Mechanisms

**Graceful Degradation:**

```java
public CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistoryWithFallback(UUID playerId) {
    return getPlayerWorldHistory(playerId)
        .handle((result, throwable) -> {
            if (throwable != null) {
                logger.warning("Failed to get complete world history for player " + playerId + ", using cache");
                return getCachedWorldHistory(playerId).orElse(Collections.emptyList());
            }
            return result;
        });
}
```

## Testing Strategy

### Unit Testing Approach

**Service Method Testing:**

```java
@Test
public void testGetLastKnownLocation_CacheHit() {
    // Given
    UUID playerId = UUID.randomUUID();
    String worldName = "world_test";
    PlayerWorldDataDTO cachedData = createTestWorldData(playerId, worldName);
    worldDataCache.put(playerId, worldName, cachedData);
    
    // When
    CompletableFuture<Optional<PlayerWorldDataDTO>> result = 
        playerWorldService.getLastKnownLocation(playerId, worldName);
    
    // Then
    assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
    assertThat(result.join()).isPresent();
    assertThat(result.join().get()).isEqualTo(cachedData);
    
    // Verify repository was not called
    verify(playerWorldDataRepository, never()).findByPlayerAndWorld(any(), any());
}
```

### Integration Testing

**End-to-End Service Testing:**

```java
@IntegrationTest
public void testWorldChangeRecording() {
    // Given
    UUID playerId = UUID.randomUUID();
    String fromWorld = "world_survival";
    String toWorld = "world_creative";
    
    // When
    CompletableFuture<Void> result = playerWorldService.recordWorldChange(
        playerId, fromWorld, toWorld, 100.0, 64.0, -50.0, 90.0f, 0.0f
    );
    
    // Then
    assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
    
    // Verify database state
    Optional<PlayerDTO> player = playerRepository.findById(playerId).join();
    assertThat(player).isPresent();
    assertThat(player.get().getCurrentWorld()).isEqualTo(toWorld);
    
    Optional<PlayerWorldDataDTO> worldData = 
        playerWorldDataRepository.findByPlayerAndWorld(playerId, toWorld).join();
    assertThat(worldData).isPresent();
    assertThat(worldData.get().getLastX()).isEqualTo(100.0);
}
```

## Implementation Checklist

### Core Service Implementation
- [ ] Implement PlayerWorldService interface with all required methods
- [ ] Create DefaultPlayerWorldService with rate limiting and caching
- [ ] Integrate with repository layer using dependency injection
- [ ] Implement session management for playtime calculation
- [ ] Add comprehensive error handling and logging

### Performance Optimization
- [ ] Implement configurable rate limiting for location updates
- [ ] Add caching layer with appropriate invalidation strategies
- [ ] Create batch operation methods for efficiency
- [ ] Add async operation support throughout service layer
- [ ] Monitor and optimize query performance

### Integration and Configuration
- [ ] Register service with ServiceRegistry
- [ ] Create event listeners for Bukkit integration
- [ ] Implement configuration management with reload support
- [ ] Add service lifecycle management (startup/shutdown)
- [ ] Create monitoring and metrics collection

This service architecture provides a robust foundation for per-world player tracking while maintaining high performance and reliability standards required for production Minecraft servers.
