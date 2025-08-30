# World Tracking Architecture Specification

**Feature ID**: 05-world-tracking-architecture  
**Priority**: Critical  
**Dependencies**: Database schema, Service layer, Event integration  
**Implementation Phase**: 2

## Overview

This feature implements comprehensive world tracking capabilities within the RVNKCore PlayerWorld system. It provides automated world discovery, metadata management, synchronization with server state, and comprehensive analytics for all tracked worlds.

## World Tracking Components

### 1. WorldService Interface

**Purpose**: Core service for world data management and analytics

```java
public interface WorldService {
    
    // Basic CRUD Operations
    CompletableFuture<Optional<WorldData>> getWorld(String worldName);
    CompletableFuture<List<WorldData>> getAllWorlds();
    CompletableFuture<List<WorldData>> getWorldsByType(WorldType type);
    CompletableFuture<List<WorldData>> getWorldsByEnvironment(World.Environment environment);
    CompletableFuture<WorldData> updateWorld(String worldName, WorldUpdateRequest request);
    CompletableFuture<Boolean> deleteWorld(String worldName);
    
    // World Discovery and Synchronization
    CompletableFuture<WorldData> registerWorld(World bukkitWorld);
    CompletableFuture<WorldData> synchronizeWorldData(String worldName);
    CompletableFuture<List<WorldData>> synchronizeAllWorlds();
    CompletableFuture<Boolean> isWorldTracked(String worldName);
    
    // World Status Management
    CompletableFuture<WorldData> updateWorldStatus(String worldName, boolean isLoaded, int playerCount);
    CompletableFuture<List<WorldData>> getLoadedWorlds();
    CompletableFuture<List<WorldData>> getEnabledWorlds();
    
    // World Analytics and Statistics
    CompletableFuture<WorldStatistics> getWorldStatistics(String worldName);
    CompletableFuture<WorldAnalytics> getWorldAnalytics(String worldName, Period period);
    CompletableFuture<List<WorldData>> getPopularWorlds(int limit);
    CompletableFuture<Map<String, Long>> getWorldPlaytimeRankings();
    
    // World-Player Correlation
    CompletableFuture<List<PlayerWorldData>> getWorldPlayers(String worldName);
    CompletableFuture<List<PlayerWorldData>> getActiveWorldPlayers(String worldName);
    CompletableFuture<Map<String, Integer>> getWorldPlayerCounts();
}
```

### 2. WorldRepository Interface

**Purpose**: Data access layer for world information

```java
public interface WorldRepository {
    
    // Basic Data Access
    CompletableFuture<Optional<WorldData>> findByName(String worldName);
    CompletableFuture<List<WorldData>> findAll();
    CompletableFuture<List<WorldData>> findByType(WorldType type);
    CompletableFuture<List<WorldData>> findByEnvironment(World.Environment environment);
    CompletableFuture<List<WorldData>> findByEnabled(boolean enabled);
    
    // CRUD Operations
    CompletableFuture<WorldData> save(WorldData worldData);
    CompletableFuture<WorldData> update(String worldName, WorldData worldData);
    CompletableFuture<Boolean> delete(String worldName);
    CompletableFuture<Boolean> exists(String worldName);
    
    // Statistical Queries
    CompletableFuture<Long> getTotalVisits(String worldName);
    CompletableFuture<Long> getTotalPlaytime(String worldName);
    CompletableFuture<Integer> getUniquePlayerCount(String worldName);
    CompletableFuture<List<String>> getMostPopularWorlds(int limit);
    
    // Batch Operations
    CompletableFuture<List<WorldData>> saveAll(List<WorldData> worlds);
    CompletableFuture<Integer> updatePlayerCounts(Map<String, Integer> worldPlayerCounts);
}
```

### 3. WorldData Entity

**Purpose**: Core data structure for world information

```java
public class WorldData {
    // Core Identification
    private String worldName;
    private String displayName;
    private WorldType worldType;
    private World.Environment environment;
    private String worldPath;
    
    // World Configuration
    private Difficulty difficulty;
    private GameMode defaultGameMode;
    private Long seed;
    private Location spawnLocation;
    private WorldBorderData worldBorder;
    
    // World Settings
    private boolean allowAnimals;
    private boolean allowMonsters;
    private boolean allowPvp;
    private boolean keepSpawnInMemory;
    private boolean autoSave;
    
    // Runtime Status
    private boolean isLoaded;
    private boolean isEnabled;
    private int currentPlayerCount;
    private Instant lastLoaded;
    
    // Statistics
    private long totalVisits;
    private long totalPlaytime;
    private int uniquePlayerCount;
    private Instant createdDate;
    
    // Metadata and Customization
    private List<String> tags;
    private Map<String, Object> metadata;
}
```

### 4. World Discovery Service

**Purpose**: Automatic world detection and registration

```java
public interface WorldDiscoveryService {
    
    // Discovery Operations
    CompletableFuture<List<World>> discoverWorlds();
    CompletableFuture<WorldData> analyzeWorld(World bukkitWorld);
    CompletableFuture<Boolean> registerDiscoveredWorld(World bukkitWorld);
    
    // Synchronization
    CompletableFuture<SyncResult> synchronizeWithServer();
    CompletableFuture<List<WorldData>> getUnregisteredWorlds();
    CompletableFuture<List<String>> getOrphanedWorldRecords();
    
    // Event-Driven Discovery
    void onWorldLoad(WorldLoadEvent event);
    void onWorldUnload(WorldUnloadEvent event);
    void onWorldInit(WorldInitEvent event);
}
```

## World Event Integration

### 1. World Event Handlers

**Purpose**: Real-time world state synchronization

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onWorldLoad(WorldLoadEvent event) {
    World world = event.getWorld();
    
    // Register or update world data
    worldService.registerWorld(world)
        .thenCompose(worldData -> {
            // Update loaded status
            return worldService.updateWorldStatus(
                world.getName(), 
                true, 
                world.getPlayers().size()
            );
        })
        .thenAccept(worldData -> {
            logger.info("World '{}' loaded and synchronized", world.getName());
        })
        .exceptionally(throwable -> {
            logger.error("Failed to sync world '{}' on load", world.getName(), throwable);
            return null;
        });
}

@EventHandler(priority = EventPriority.MONITOR)
public void onWorldUnload(WorldUnloadEvent event) {
    String worldName = event.getWorld().getName();
    
    // Update world status to unloaded
    worldService.updateWorldStatus(worldName, false, 0)
        .thenAccept(worldData -> {
            logger.info("World '{}' unloaded and status updated", worldName);
        })
        .exceptionally(throwable -> {
            logger.error("Failed to update world '{}' status on unload", worldName, throwable);
            return null;
        });
}

@EventHandler(priority = EventPriority.MONITOR)  
public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    World fromWorld = event.getFrom();
    World toWorld = player.getWorld();
    
    // Update player counts for both worlds
    CompletableFuture.allOf(
        worldService.updateWorldStatus(
            fromWorld.getName(), 
            fromWorld.getPlayers().size() > 0,  // Still loaded if has players
            fromWorld.getPlayers().size()
        ),
        worldService.updateWorldStatus(
            toWorld.getName(),
            true,  // Destination world is loaded
            toWorld.getPlayers().size()
        )
    ).thenRun(() -> {
        logger.debug("Updated player counts for world transition {} -> {}", 
            fromWorld.getName(), toWorld.getName());
    });
}
```

### 2. World Statistics Collection

**Purpose**: Automated collection of world metrics

```java
public class WorldStatisticsCollector {
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void collectWorldStatistics() {
        worldService.getAllWorlds()
            .thenCompose(worlds -> {
                List<CompletableFuture<Void>> tasks = worlds.stream()
                    .filter(world -> world.isLoaded())
                    .map(this::updateWorldStatistics)
                    .collect(Collectors.toList());
                    
                return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            })
            .thenRun(() -> logger.debug("World statistics collection completed"))
            .exceptionally(throwable -> {
                logger.error("Failed to collect world statistics", throwable);
                return null;
            });
    }
    
    private CompletableFuture<Void> updateWorldStatistics(WorldData worldData) {
        String worldName = worldData.getWorldName();
        
        return CompletableFuture.allOf(
            // Update player count
            updateCurrentPlayerCount(worldName),
            // Update total statistics from player world data
            updateTotalStatistics(worldName),
            // Update popular locations
            updatePopularLocations(worldName)
        );
    }
    
    private CompletableFuture<Void> updateCurrentPlayerCount(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            int playerCount = world.getPlayers().size();
            return worldService.updateWorldStatus(worldName, true, playerCount)
                .thenAccept(data -> {});
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

## World-Player Correlation

### 1. Enhanced PlayerWorldService

**Purpose**: Bridge between world and player data

```java
public interface PlayerWorldService {
    
    // Existing player-centric methods...
    
    // New world-centric methods
    CompletableFuture<List<PlayerWorldData>> getWorldPlayers(String worldName);
    CompletableFuture<List<PlayerWorldData>> getActiveWorldPlayers(String worldName);
    CompletableFuture<WorldPlayerStatistics> getWorldPlayerStatistics(String worldName);
    
    // World analytics with player correlation
    CompletableFuture<List<PlayerWorldData>> getTopPlayersInWorld(String worldName, int limit);
    CompletableFuture<Map<String, Long>> getWorldPlaytimeDistribution(String worldName);
    CompletableFuture<List<BiomePopularityData>> getPopularBiomes(String worldName);
    
    // Cross-world player analysis
    CompletableFuture<Map<String, Integer>> getPlayerWorldDistribution(UUID playerId);
    CompletableFuture<List<WorldTransitionData>> getWorldTransitions(UUID playerId);
}
```

### 2. World Analytics Data Structures

**Purpose**: Comprehensive world analytics DTOs

```java
public class WorldStatistics {
    private String worldName;
    private PlayerStatistics playerStats;
    private ActivityStatistics activityStats;
    private BlockStatistics blockStats;
    private LocationStatistics locationStats;
    private TimeStatistics timeStats;
}

public class PlayerStatistics {
    private int totalPlayers;
    private int activePlayers;
    private int currentPlayers;
    private long avgPlaytimePerPlayer;
    private List<TopPlayerData> topPlayers;
}

public class ActivityStatistics {
    private long totalVisits;
    private double avgVisitsPerPlayer;
    private long avgSessionLength;
    private int peakConcurrentPlayers;
    private Instant peakTime;
    private int dailyActiveUsers;
    private int weeklyActiveUsers;
    private int monthlyActiveUsers;
}

public class BlockStatistics {
    private long totalBlocksBroken;
    private long totalBlocksPlaced;
    private long netBlocksPlaced;
    private String mostBrokenBlock;
    private String mostPlacedBlock;
    private List<TopBuilderData> topBuilders;
}

public class LocationStatistics {
    private long totalDistanceTraveled;
    private long avgDistancePerPlayer;
    private List<BiomePopularityData> popularBiomes;
    private List<LocationPopularityData> popularLocations;
}

public class TimeStatistics {
    private Instant firstPlayerVisit;
    private Instant lastPlayerVisit;
    private long worldAge;
    private List<HourlyActivityData> activeHours;
}
```

## Implementation Strategy

### Phase 1: Core World Tracking

1. **Database Schema Implementation**
   - Create `rvnk_worlds` table with all metadata fields
   - Implement foreign key relationships with player world data
   - Create performance indexes for world queries

2. **Basic World Service**
   - Implement `WorldService` interface with CRUD operations
   - Create `WorldRepository` with database access
   - Develop `WorldData` entity and DTOs

3. **World Discovery Service**
   - Implement automatic world detection on server startup
   - Create world registration and synchronization logic
   - Handle world metadata extraction from Bukkit World objects

### Phase 2: Event Integration and Real-Time Updates

1. **Event Handler Implementation**
   - Create world load/unload event handlers
   - Implement player world change tracking
   - Add real-time player count updates

2. **Statistics Collection**
   - Implement scheduled world statistics collection
   - Create aggregation logic for player world data
   - Add performance monitoring for statistics updates

### Phase 3: Analytics and API Integration

1. **Advanced Analytics**
   - Implement comprehensive world statistics calculation
   - Create world comparison and ranking algorithms
   - Add trend analysis and historical data

2. **REST API Integration**
   - Create world management endpoints
   - Implement world analytics API endpoints
   - Add world-player correlation endpoints

3. **Performance Optimization**
   - Implement caching for frequently accessed world data
   - Optimize database queries for world analytics
   - Add connection pooling for world statistics collection

## Configuration Options

### World Tracking Configuration

```yaml
worldTracking:
  enabled: true
  
  discovery:
    autoRegisterNewWorlds: true
    syncOnStartup: true
    syncIntervalMinutes: 60
  
  statistics:
    enabled: true
    collectionIntervalMinutes: 5
    retainHistoryDays: 90
  
  analytics:
    cacheStatisticsDuration: 300  # 5 minutes
    popularLocationRadius: 50
    maxPopularLocations: 20
  
  worldDefaults:
    enableNewWorlds: true
    trackPlayerActivity: true
    collectBlockStatistics: true
    collectLocationStatistics: true
```

### World-Specific Configuration

```yaml
worlds:
  world:
    displayName: "Main Survival World"
    enabled: true
    trackingEnabled: true
    tags: ["survival", "main", "overworld"]
    metadata:
      description: "Primary survival world for all players"
      rules: ["No griefing", "Respect others"]
      features: ["Towns", "Economy", "Shops"]
  
  world_nether:
    displayName: "The Nether"
    enabled: true
    trackingEnabled: true
    tags: ["nether", "dangerous", "resources"]
    
  world_the_end:
    displayName: "The End"
    enabled: true
    trackingEnabled: true
    tags: ["end", "enderdragon", "endgame"]
```

## Testing Requirements

### Unit Testing

1. **WorldService Tests**
   - Test CRUD operations for world data
   - Verify world discovery and synchronization
   - Test world statistics calculations

2. **WorldRepository Tests**
   - Test database operations for world data
   - Verify complex queries for analytics
   - Test batch operations performance

### Integration Testing

1. **Event Integration Tests**
   - Test world load/unload event handling
   - Verify player world change tracking
   - Test real-time statistics updates

2. **API Integration Tests**
   - Test world management endpoints
   - Verify world analytics API responses
   - Test world-player correlation endpoints

### Performance Testing

1. **Statistics Collection Performance**
   - Test collection with large numbers of worlds
   - Verify performance with high player counts
   - Test database query optimization

2. **Analytics Performance**
   - Test world analytics calculation speed
   - Verify caching effectiveness
   - Test concurrent access patterns

## Success Criteria

1. **Functional Requirements**
   - All worlds automatically discovered and tracked
   - Real-time world status updates
   - Comprehensive world analytics and statistics
   - Efficient world-player data correlation

2. **Performance Requirements**
   - World statistics collection completes within 30 seconds
   - World analytics queries respond within 2 seconds
   - Real-time updates have minimal server performance impact
   - System scales to support 50+ tracked worlds

3. **Integration Requirements**
   - Seamless integration with existing PlayerWorld system
   - Compatible with WorldSwap and other world management plugins
   - RESTful API provides complete world management capabilities
   - Event-driven updates maintain data consistency

This world tracking architecture provides comprehensive world management capabilities while maintaining high performance and seamless integration with the existing PlayerWorld system.
