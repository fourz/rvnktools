# RVNKCore Per-World Player Tracking Requirements

**Document Version**: 2.0  
**Last Updated**: August 2, 2025  
**Status**: Implemented

## Purpose

This document defines the updated requirements for RVNKCore's player tracking system, now enhanced with comprehensive per-world data tracking. The system maintains both global player information and detailed world-specific data to support advanced features like worldswap teleportation and world-based analytics.

## Enhanced Architecture Overview

The per-world tracking system extends the existing player management architecture:

```text
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  WorldSwap Command │ Analytics │ REST API │ Player Tools    │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                             │
├─────────────────────┬───────────────────────────────────────┤
│   PlayerService     │        PlayerWorldService             │
│   (Global Data)     │        (Per-World Data)               │
│                     │                                       │
│ • Basic Info        │ • Location Tracking                   │
│ • Join/Leave        │ • Visit History                       │
│ • Total Playtime    │ • World-Specific Playtime             │
│ • Permission Groups │ • Death Tracking                      │
│ • Name History      │ • World Analytics                     │
└─────────────────────┴───────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Repository Layer                            │
├─────────────────────┬───────────────────────────────────────┤
│   PlayerRepository  │    PlayerWorldDataRepository          │
│   (rvnk_players)    │    (rvnk_player_world_data)          │
└─────────────────────┴───────────────────────────────────────┘
```

## Updated Data Models

### Enhanced PlayerDTO

The global player model now focuses on server-wide information:

```java
public class PlayerDTO {
    // Core identification
    private UUID id;
    private String currentName;
    private List<String> nameHistory;
    
    // Global activity tracking
    private Timestamp firstJoin;
    private Timestamp lastSeen;
    private String currentWorld;        // Currently active world
    private int timesJoined;
    private long totalPlaytimeSeconds;  // Aggregate across all worlds
    
    // Permission and group data
    private String primaryGroup;
    private List<String> groups;
    private boolean banned;
    
    // Extensible metadata
    private Map<String, Object> metadata;
}
```

**Key Changes from Legacy**:
- Removed world-specific location fields (lastX, lastY, lastZ, lastYaw, lastPitch)
- Added `currentWorld` to track active world
- `totalPlaytimeSeconds` now aggregates from all worlds

### New PlayerWorldDataDTO

Comprehensive per-world tracking model:

```java
public class PlayerWorldDataDTO {
    // Composite key
    private UUID playerId;
    private String worldName;
    
    // Location tracking
    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch;
    private String lastBiome;
    
    // Visit tracking
    private Timestamp firstVisit;
    private Timestamp lastVisit;
    private int visitCount;
    
    // Activity tracking
    private long playtimeSeconds;      // World-specific playtime
    private int deathCount;
    
    // Extensible metadata
    private Map<String, Object> metadata;
}
```

## Service Interface Requirements

### PlayerWorldService Interface

Comprehensive service for per-world player management:

```java
public interface PlayerWorldService {
    
    // Location Management
    CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName);
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, 
                                               double x, double y, double z, 
                                               float yaw, float pitch, String biome);
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, 
                                            double x, double y, double z, float yaw, float pitch);
    
    // Visit and History Management
    CompletableFuture<PlayerWorldDataDTO> recordJoin(UUID playerId, String worldName, 
                                                   double x, double y, double z, 
                                                   float yaw, float pitch, String biome);
    CompletableFuture<Void> recordLeave(UUID playerId, String worldName, long sessionSeconds);
    CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId);
    CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
    
    // Analytics and Reporting
    CompletableFuture<Long> getTotalPlaytime(UUID playerId);
    CompletableFuture<Long> getWorldPlaytime(UUID playerId, String worldName);
    CompletableFuture<List<PlayerWorldDataDTO>> getMostActiveWorlds(UUID playerId, int limit);
    CompletableFuture<Map<String, Integer>> getWorldVisitCounts(UUID playerId);
    
    // Death Tracking
    CompletableFuture<Void> recordDeath(UUID playerId, String worldName, 
                                      double x, double y, double z, String cause);
    CompletableFuture<Integer> getDeathCount(UUID playerId, String worldName);
    
    // Global Player Management (delegates to PlayerService)
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
}
```

## Database Schema Requirements

### Enhanced rvnk_players Table

Simplified global player tracking:

```sql
CREATE TABLE rvnk_players (
    id VARCHAR(36) PRIMARY KEY,
    current_name VARCHAR(16) NOT NULL,
    name_history TEXT,
    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    current_world VARCHAR(255),
    times_joined INTEGER DEFAULT 1,
    total_playtime_seconds BIGINT DEFAULT 0,
    primary_group VARCHAR(100),
    groups TEXT,
    banned BOOLEAN DEFAULT FALSE,
    metadata TEXT
);
```

**Schema Changes**:
- Removed location fields (last_x, last_y, last_z, last_yaw, last_pitch)
- Added `current_world` field
- Retained `total_playtime_seconds` as aggregate value

### New rvnk_player_world_data Table

Comprehensive per-world tracking:

```sql
CREATE TABLE rvnk_player_world_data (
    player_id VARCHAR(36) NOT NULL,
    world_name VARCHAR(255) NOT NULL,
    last_x DOUBLE DEFAULT 0.0,
    last_y DOUBLE DEFAULT 64.0,
    last_z DOUBLE DEFAULT 0.0,
    last_yaw FLOAT DEFAULT 0.0,
    last_pitch FLOAT DEFAULT 0.0,
    last_biome VARCHAR(100),
    first_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    visit_count INTEGER DEFAULT 1,
    playtime_seconds BIGINT DEFAULT 0,
    death_count INTEGER DEFAULT 0,
    metadata TEXT,
    PRIMARY KEY (player_id, world_name),
    FOREIGN KEY (player_id) REFERENCES rvnk_players(id) ON DELETE CASCADE
);

-- Performance indexes
CREATE INDEX idx_player_world_last_visit ON rvnk_player_world_data(player_id, last_visit);
CREATE INDEX idx_world_activity ON rvnk_player_world_data(world_name, last_visit);
CREATE INDEX idx_player_playtime ON rvnk_player_world_data(player_id, playtime_seconds);
```

## Event Handling Requirements

### Enhanced PlayerTrackingListener

Updated event handling for dual tracking:

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    // Global tracking
    CompletableFuture<Void> globalUpdate = playerService.getPlayer(player.getUniqueId())
        .thenCompose(playerOpt -> {
            if (playerOpt.isEmpty()) {
                // Create new global player record
                return createNewPlayer(player);
            } else {
                // Update existing global data
                return updateExistingPlayer(player, playerOpt.get());
            }
        });
    
    // Per-world tracking
    CompletableFuture<Void> worldUpdate = playerWorldService.updatePlayerLocation(
        player.getUniqueId(), player.getWorld().getName(),
        player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
        player.getLocation().getYaw(), player.getLocation().getPitch(),
        player.getLocation().getBlock().getBiome().name()
    );
    
    // Wait for both operations
    CompletableFuture.allOf(globalUpdate, worldUpdate)
        .whenComplete((result, throwable) -> {
            // Handle completion or errors
        });
}
```

### Required Event Mappings

- **PlayerJoinEvent**: Update global data + record world visit
- **PlayerQuitEvent**: Calculate session time + update last locations
- **PlayerMoveEvent**: Rate-limited per-world location updates
- **PlayerChangedWorldEvent**: Record world transitions
- **PlayerDeathEvent**: Increment world-specific death count

## WorldSwap Command Requirements

### Functional Requirements

1. **Command Syntax**: `/worldswap <world_name>` and `/ws <world_name>`
2. **Permission**: `rvnktools.command.worldswap`
3. **Validation**: World existence and player permissions
4. **Behavior**:
   - First visit: Teleport to world spawn
   - Return visit: Teleport to last known location
   - Error handling: Graceful fallback to spawn

### Implementation Requirements

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    // Validation
    if (!(sender instanceof Player)) return false;
    if (args.length != 1) return false;
    
    Player player = (Player) sender;
    String targetWorldName = args[0];
    World targetWorld = Bukkit.getWorld(targetWorldName);
    
    if (targetWorld == null) {
        player.sendMessage("§cWorld '" + targetWorldName + "' does not exist.");
        return true;
    }
    
    // Get last known location
    playerWorldService.getLastKnownLocation(player.getUniqueId(), targetWorldName)
        .thenAccept(locationOpt -> {
            if (locationOpt.isPresent()) {
                // Return visit - use last location
                teleportToLastLocation(player, targetWorld, locationOpt.get());
            } else {
                // First visit - use spawn
                teleportToSpawn(player, targetWorld);
            }
        })
        .exceptionally(throwable -> {
            // Error handling - fallback to spawn
            fallbackToSpawn(player, targetWorld, throwable);
            return null;
        });
    
    return true;
}
```

## Performance Requirements

### Rate Limiting

- Location updates: Maximum once per 30 seconds per player
- Prevents database spam from frequent movement events
- Configurable rate limiting intervals

### Async Operations

- All service methods return CompletableFuture
- Non-blocking player experience
- Proper error handling with fallbacks

### Database Optimization

- Composite primary key (player_id, world_name)
- Strategic indexes for common queries
- Foreign key constraints for data integrity
- Efficient upsert operations for location updates

### Memory Management

- Session-based playtime calculation
- Minimal in-memory caching for rate limiting
- Cleanup of inactive sessions

## Migration Requirements

### From Legacy Single-World System

1. **Schema Migration**: 
   - Move location data from rvnk_players to rvnk_player_world_data
   - Preserve existing playtime as world-specific data

2. **Service Updates**:
   - Replace direct PlayerService location methods
   - Implement PlayerWorldService delegation pattern

3. **Backward Compatibility**:
   - Maintain existing PlayerService interface
   - Deprecate location-specific methods

This enhanced per-world tracking system provides the foundation for advanced server features while maintaining performance, data integrity, and backward compatibility.
