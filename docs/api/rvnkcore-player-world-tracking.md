# RVNKCore Per-World Player Tracking API Documentation

**Version**: 1.1-alpha  
**Last Updated**: August 2, 2025  
**Status**: Implemented

## Overview

The RVNKCore per-world player tracking system provides comprehensive location and activity tracking for players across multiple worlds. This enables features like the worldswap teleport command and detailed analytics for player behavior across different server environments.

## Core Components

### PlayerWorldDataDTO

Represents per-world player data including location, playtime, and visit history.

```java
/**
 * Data Transfer Object for per-world player tracking.
 * Each instance represents a player's data for a specific world.
 */
public class PlayerWorldDataDTO {
    private UUID playerId;              // Player's unique ID
    private String worldName;           // World identifier
    private double lastX, lastY, lastZ; // Last known coordinates
    private float lastYaw, lastPitch;   // Last known view direction
    private String lastBiome;           // Last known biome
    private Timestamp firstVisit;       // First time visiting this world
    private Timestamp lastVisit;        // Most recent visit
    private int visitCount;             // Total number of visits
    private long playtimeSeconds;       // Total playtime in this world
    private int deathCount;             // Deaths in this world
    private Map<String, Object> metadata; // Extensible metadata
}
```

### PlayerWorldService

Primary service interface for per-world tracking operations.

```java
/**
 * Service for comprehensive player management with world-specific tracking.
 */
public interface PlayerWorldService {
    
    // World-specific location tracking
    CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName);
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, 
                                               double x, double y, double z, 
                                               float yaw, float pitch, String biome);
    CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, 
                                            double x, double y, double z, float yaw, float pitch);
    
    // Visit and playtime tracking
    CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId);
    CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
    CompletableFuture<Long> getTotalPlaytime(UUID playerId);
    CompletableFuture<Long> getWorldPlaytime(UUID playerId, String worldName);
    
    // Death tracking
    CompletableFuture<Void> recordDeath(UUID playerId, String worldName, 
                                      double x, double y, double z, String cause);
    CompletableFuture<Integer> getDeathCount(UUID playerId, String worldName);
    
    // Analytics and reporting
    CompletableFuture<List<PlayerWorldDataDTO>> getMostActiveWorlds(UUID playerId, int limit);
    CompletableFuture<Map<String, Integer>> getWorldVisitCounts(UUID playerId);
}
```

### Database Schema

The per-world tracking uses the `rvnk_player_world_data` table:

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
```

Performance indexes:
- `idx_player_world_last_visit` on (player_id, last_visit)
- `idx_world_activity` on (world_name, last_visit)

## REST API Enhancements

### Updated PlayerResponse

The PlayerResponse model now includes world-related information:

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "name": "PlayerName",
  "online": true,
  "firstSeen": "2025-08-01T10:30:00Z",
  "lastSeen": "2025-08-02T15:45:00Z",
  "timesJoined": 42,
  "currentWorld": "world_survival",
  "totalPlaytimeMinutes": 12450,
  "groups": ["default", "builder"],
  "nameHistory": ["OldName", "PlayerName"],
  "visitedWorlds": ["world", "world_survival", "world_creative", "world_event"]
}
```

### New PlayerWorldDataResponse

Dedicated response model for per-world data:

```json
{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "worldName": "world_survival",
  "lastLocation": {
    "x": 125.5,
    "y": 64.0,
    "z": -89.3,
    "yaw": 45.0,
    "pitch": 0.0,
    "biome": "PLAINS"
  },
  "firstVisit": "2025-07-15T09:30:00Z",
  "lastVisit": "2025-08-02T15:45:00Z",
  "visitCount": 23,
  "playtimeMinutes": 4320,
  "deathCount": 7,
  "metadata": {
    "favoriteSpot": "base_coordinates",
    "lastStructure": "village"
  }
}
```

## REST API Endpoints

### Player World Data Endpoints

#### Get Last Known Location
```http
GET /api/v1/players/{uuid}/worlds/{worldName}/location
```

Returns the player's last known location in the specified world.

**Response**: `PlayerWorldDataResponse`

#### Get Player World History
```http
GET /api/v1/players/{uuid}/worlds
```

Returns all world data for a player.

**Query Parameters**:
- `sortBy`: `visitCount`, `playtime`, `lastVisit` (default: `lastVisit`)
- `order`: `asc`, `desc` (default: `desc`)
- `limit`: Maximum results (default: 50)

**Response**: Array of `PlayerWorldDataResponse`

#### Get World Statistics
```http
GET /api/v1/players/{uuid}/worlds/stats
```

Returns aggregated statistics across all worlds.

**Response**:
```json
{
  "totalWorlds": 4,
  "totalPlaytimeMinutes": 12450,
  "totalDeaths": 23,
  "mostVisitedWorld": "world_survival",
  "favoriteWorld": {
    "name": "world_survival",
    "playtimePercentage": 65.2
  }
}
```

## WorldSwap Command Implementation

The worldswap command demonstrates the per-world tracking capabilities:

### Usage
```
/worldswap <world_name>
/ws <world_name>
```

### Behavior
1. **First-time visit**: Teleports to world spawn
2. **Return visit**: Teleports to last known location with welcome message
3. **Error handling**: Graceful fallback to spawn location
4. **Validation**: Checks world existence and player permissions

### Implementation Example
```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    Player player = (Player) sender;
    String targetWorldName = args[0];
    
    PlayerWorldService service = coreBootstrap.getService(PlayerWorldService.class);
    
    service.getLastKnownLocation(player.getUniqueId(), targetWorldName)
        .thenAccept(locationOpt -> {
            if (locationOpt.isPresent()) {
                // Teleport to last location
                PlayerWorldDataDTO worldData = locationOpt.get();
                Location targetLocation = new Location(targetWorld,
                    worldData.getLastX(), worldData.getLastY(), worldData.getLastZ(),
                    worldData.getLastYaw(), worldData.getLastPitch());
                
                player.teleport(targetLocation);
                player.sendMessage("Welcome back! Visit #" + worldData.getVisitCount());
            } else {
                // First time - teleport to spawn
                player.teleport(targetWorld.getSpawnLocation());
                player.sendMessage("Welcome to " + targetWorldName + " for the first time!");
            }
        });
}
```

## Event Integration

The system automatically tracks player activity through event listeners:

### PlayerJoinEvent
- Updates global player data (current world, last seen)
- Records world visit with location tracking

### PlayerMoveEvent
- Rate-limited location updates (30-second intervals)
- Comprehensive coordinate and view direction tracking

### PlayerChangedWorldEvent
- Records world transitions with from/to world tracking
- Updates visit counts and location data

### PlayerQuitEvent
- Calculates session playtime
- Updates last known location and visit timestamp

## Performance Considerations

### Rate Limiting
- Location updates limited to once per 30 seconds per player
- Prevents database spam from frequent movement

### Async Operations
- All database operations use CompletableFuture
- Non-blocking player experience

### Indexing Strategy
- Composite primary key (player_id, world_name)
- Strategic indexes for common query patterns
- Foreign key constraints for data integrity

### Caching
- Session-based tracking for playtime calculation
- In-memory rate limiting state

## Usage Examples

### Getting Player's World History
```java
PlayerWorldService service = serviceRegistry.getService(PlayerWorldService.class);

service.getPlayerWorldHistory(playerId)
    .thenAccept(worldData -> {
        worldData.forEach(world -> {
            System.out.println("World: " + world.getWorldName() + 
                             ", Visits: " + world.getVisitCount() +
                             ", Playtime: " + world.getPlaytimeSeconds() / 3600 + "h");
        });
    });
```

### Implementing Custom World Analytics
```java
service.getMostActiveWorlds(playerId, 5)
    .thenAccept(topWorlds -> {
        player.sendMessage("Your top 5 worlds:");
        topWorlds.forEach(world -> {
            long hours = world.getPlaytimeSeconds() / 3600;
            player.sendMessage("- " + world.getWorldName() + ": " + hours + " hours");
        });
    });
```

### Creating Custom Teleport Commands
```java
// Custom /home command that uses world tracking
service.getLastKnownLocation(playerId, "world_survival")
    .thenAccept(locationOpt -> {
        if (locationOpt.isPresent()) {
            PlayerWorldDataDTO homeData = locationOpt.get();
            // Create location and teleport
            Location home = new Location(world, homeData.getLastX(), 
                                       homeData.getLastY(), homeData.getLastZ());
            player.teleport(home);
        }
    });
```

## Migration from Legacy System

For existing plugins using simple location tracking:

### Before (Legacy)
```java
// Old approach - limited data
player.updateLastLocation(world, x, y, z);
```

### After (Per-World Tracking)
```java
// New approach - comprehensive tracking
playerWorldService.updatePlayerLocation(playerId, worldName, x, y, z, yaw, pitch, biome)
    .thenRun(() -> {
        // Location updated with full context
    });
```

## Best Practices

### Service Usage
1. Always use async operations with CompletableFuture
2. Handle Optional results gracefully
3. Implement proper error handling with fallbacks

### Performance
1. Leverage rate limiting for frequent operations
2. Use batch operations for multiple updates
3. Consider caching for frequently accessed data

### Data Integrity
1. Validate world existence before operations
2. Handle edge cases (deleted worlds, corrupted data)
3. Implement proper cleanup for removed players

This per-world tracking system provides a solid foundation for advanced server features while maintaining performance and data integrity.
