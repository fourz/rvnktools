# Event Integration Specification

**Feature ID**: 04-event-integration  
**Phase**: Bukkit Event Integration  
**Prerequisites**: Service layer implemented, event system configured

## Bukkit Event Integration Architecture

### Overview

The PlayerWorld system integrates with Bukkit's event system to provide real-time tracking of player activities across multiple worlds. This integration captures essential player actions and updates the database asynchronously.

### Event Handler Strategy

#### Core Design Principles

1. **Non-blocking Operations**: All database updates are asynchronous to prevent server lag
2. **Rate Limiting**: Location updates are rate-limited to prevent database spam
3. **Error Resilience**: Event handler failures don't crash the server
4. **Performance Optimized**: Minimal processing time in event handlers
5. **Session Management**: Accurate session tracking for playtime calculations

### Primary Event Handlers

#### PlayerJoinEvent Handler

**Purpose**: Initialize player session and record world entry

**Triggered When**: Player joins the server

**Implementation Logic**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String worldName = player.getWorld().getName();
    Location location = player.getLocation();
    
    // Start player session asynchronously
    playerWorldService.recordJoin(playerId, worldName, location)
        .exceptionally(throwable -> {
            logger.error("Failed to record player join for " + playerId, throwable);
            return null;
        });
}
```

**Data Updates**:
- Global player data (last seen, times joined, current name)
- World-specific data (visit count, first visit timestamp)
- Session initiation (start time, starting location)
- Location data (current coordinates, biome)

#### PlayerQuitEvent Handler

**Purpose**: Complete player session and finalize playtime calculations

**Triggered When**: Player leaves the server

**Implementation Logic**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String worldName = player.getWorld().getName();
    
    // End player session and update playtime asynchronously
    playerWorldService.recordLeave(playerId, worldName)
        .exceptionally(throwable -> {
            logger.error("Failed to record player quit for " + playerId, throwable);
            return null;
        });
}
```

**Data Updates**:
- Session completion (end time, total session duration)
- Total playtime accumulation
- Final location update
- Global player last seen timestamp

#### PlayerMoveEvent Handler

**Purpose**: Track significant player movement and location changes

**Triggered When**: Player moves (with filtering for significant movement)

**Implementation Logic**:
```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerMove(PlayerMoveEvent event) {
    // Only process significant movement (block changes)
    if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
        return;
    }
    
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    Location newLocation = event.getTo();
    
    // Update location with rate limiting (non-blocking)
    CompletableFuture.runAsync(() -> {
        playerWorldService.updatePlayerLocation(playerId, newLocation);
    }).exceptionally(throwable -> {
        // Log but don't interrupt gameplay
        logger.debug("Failed to update location for " + playerId, throwable);
        return null;
    });
}
```

**Data Updates**:
- Current location coordinates (X, Y, Z, yaw, pitch)
- Current biome information
- Last seen timestamp
- Rate-limited to prevent excessive database writes

#### PlayerChangedWorldEvent Handler

**Purpose**: Track world transitions and manage cross-world sessions

**Triggered When**: Player moves between worlds (including portals, commands, etc.)

**Implementation Logic**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String fromWorld = event.getFrom().getName();
    String toWorld = player.getWorld().getName();
    Location newLocation = player.getLocation();
    
    // Record world transition asynchronously
    playerWorldService.recordWorldChange(playerId, fromWorld, toWorld)
        .thenRun(() -> {
            // Initialize new world session
            return playerWorldService.recordJoin(playerId, toWorld, newLocation);
        })
        .exceptionally(throwable -> {
            logger.error("Failed to record world change for " + playerId, throwable);
            return null;
        });
}
```

**Data Updates**:
- Complete previous world session
- Start new world session
- Update visit counts for destination world
- Record world transition event

### Secondary Event Handlers

#### PlayerDeathEvent Handler

**Purpose**: Track player deaths in specific worlds

**Implementation**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    UUID playerId = player.getUniqueId();
    String worldName = player.getWorld().getName();
    
    // Increment death count for this world
    CompletableFuture.runAsync(() -> {
        worldDataRepository.incrementDeathCount(playerId, worldName);
    }).exceptionally(throwable -> {
        logger.error("Failed to record death for " + playerId, throwable);
        return null;
    });
}
```

#### EntityDeathEvent Handler

**Purpose**: Track mob kills by players

**Implementation**:
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onEntityDeath(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) return;
    
    UUID playerId = killer.getUniqueId();
    String worldName = killer.getWorld().getName();
    
    // Increment mob kill count for this world
    CompletableFuture.runAsync(() -> {
        worldDataRepository.incrementMobKills(playerId, worldName);
    }).exceptionally(throwable -> {
        logger.error("Failed to record mob kill for " + playerId, throwable);
        return null;
    });
}
```

#### BlockBreakEvent Handler

**Purpose**: Track blocks broken by players in each world

**Implementation**:
```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String worldName = player.getWorld().getName();
    
    // Increment blocks broken count
    CompletableFuture.runAsync(() -> {
        worldDataRepository.incrementBlocksBroken(playerId, worldName);
    }).exceptionally(throwable -> {
        logger.error("Failed to record block break for " + playerId, throwable);
        return null;
    });
}
```

#### BlockPlaceEvent Handler

**Purpose**: Track blocks placed by players in each world

**Implementation**:
```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String worldName = player.getWorld().getName();
    
    // Increment blocks placed count
    CompletableFuture.runAsync(() -> {
        worldDataRepository.incrementBlocksPlaced(playerId, worldName);
    }).exceptionally(throwable -> {
        logger.error("Failed to record block place for " + playerId, throwable);
        return null;
    });
}
```

### Event Handler Configuration

#### Event Priority Strategy

- **MONITOR Priority**: Use for data collection (doesn't modify gameplay)
- **ignoreCancelled = true**: Only process successful events
- **Async Processing**: All database operations are asynchronous

#### Performance Optimization

**Rate Limiting Configuration**:
```yaml
playerworld:
  event-handling:
    location-update-interval: 30000  # 30 seconds between location updates
    block-activity-batch-size: 10    # Batch block break/place events
    session-cleanup-interval: 300000 # 5 minutes
```

**Event Filtering**:
- Movement events only processed for block-level changes
- Location updates respect rate limiting per player
- Block events can be batched for performance

### Error Handling and Resilience

#### Exception Management

```java
private void handleEventError(String eventType, UUID playerId, Throwable error) {
    // Log error without disrupting gameplay
    logger.error("Failed to process " + eventType + " for player " + playerId, error);
    
    // Optional: Send to monitoring system
    if (monitoringService != null) {
        monitoringService.recordEventError(eventType, playerId, error);
    }
    
    // Never throw exceptions back to Bukkit
}
```

#### Circuit Breaker Pattern

```java
private boolean isServiceHealthy = true;
private long lastFailureTime = 0;
private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1 minute

private boolean canProcessEvent() {
    if (isServiceHealthy) {
        return true;
    }
    
    // Check if we should try again
    if (System.currentTimeMillis() - lastFailureTime > CIRCUIT_BREAKER_TIMEOUT) {
        isServiceHealthy = true;
        return true;
    }
    
    return false;
}
```

### Session Management Integration

#### Active Session Tracking

The event system integrates with session management to provide accurate playtime calculations:

1. **Session Start**: PlayerJoinEvent or PlayerChangedWorldEvent
2. **Session Updates**: Periodic location updates during PlayerMoveEvent
3. **Session End**: PlayerQuitEvent or PlayerChangedWorldEvent (leaving world)

#### Session Data Structure

```java
public class PlayerSession {
    private UUID playerId;
    private String worldName;
    private long startTime;
    private Location startLocation;
    private Location lastKnownLocation;
    private long lastUpdateTime;
    
    // Calculate current session duration
    public long getCurrentDuration() {
        return System.currentTimeMillis() - startTime;
    }
}
```

### WorldSwap Integration

#### WorldSwap Event Support

The event system provides hooks for WorldSwap plugin integration:

```java
// Custom event for WorldSwap operations
public class WorldSwapEvent extends PlayerEvent {
    private String fromWorld;
    private String toWorld;
    private WorldSwapMethod method;
    
    // Event details and cancellation support
}

@EventHandler
public void onWorldSwap(WorldSwapEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    
    // Record WorldSwap-specific data
    playerWorldService.recordWorldSwap(
        playerId, 
        event.getFromWorld(), 
        event.getToWorld(), 
        event.getMethod().name()
    );
}
```

### Monitoring and Diagnostics

#### Event Processing Metrics

```java
public class EventMetrics {
    private long totalEventsProcessed;
    private long successfulEvents;
    private long failedEvents;
    private double averageProcessingTime;
    private Map<String, Long> eventTypeCounts;
    
    // Metric collection and reporting
}
```

#### Health Checks

```java
public boolean isEventSystemHealthy() {
    return isServiceHealthy 
        && sessionManager.isHealthy()
        && playerWorldService.isHealthy()
        && (failedEvents / totalEventsProcessed) < 0.05; // Less than 5% failure rate
}
```

---

**Implementation Notes:**
- All event handlers are asynchronous to prevent server lag
- Rate limiting prevents database overload from frequent events  
- Error handling ensures event failures don't crash the server
- Session management provides accurate playtime calculations
- WorldSwap integration enables seamless world transitions
- Comprehensive monitoring enables performance optimization
