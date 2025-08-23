# World Tracking System Documentation

## Overview

The RVNKCore World Tracking System provides comprehensive monitoring and management of Minecraft server worlds, automatically registering world metadata and maintaining tracking information in the database.

## Architecture

### Components

- **WorldTrackingListener**: Event-driven listener for world lifecycle management
- **WorldService**: Service interface for world operations
- **WorldDTO**: Data transfer object containing world metadata
- **WorldController**: REST API endpoints for world management

### Event Handling

The `WorldTrackingListener` handles three main world lifecycle events:

1. **WorldInitEvent**: World initialization (pre-loading phase)
2. **WorldLoadEvent**: World fully loaded and ready for use
3. **WorldUnloadEvent**: World being unloaded (marked as inactive)

### Database Schema

The world tracking system uses the `rvnk_worlds` table with comprehensive metadata:

```sql
CREATE TABLE rvnk_worlds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world_name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    world_type TEXT,
    environment TEXT NOT NULL,
    generator TEXT,
    generator_settings TEXT,
    seed BIGINT,
    spawn_x REAL,
    spawn_y REAL,
    spawn_z REAL,
    world_border_size REAL,
    world_border_center_x REAL,
    world_border_center_z REAL,
    difficulty TEXT,
    game_rules TEXT,
    weather_clear BOOLEAN DEFAULT 1,
    time_of_day BIGINT DEFAULT 0,
    total_chunks_loaded INTEGER DEFAULT 0,
    total_entities INTEGER DEFAULT 0,
    total_tile_entities INTEGER DEFAULT 0,
    allow_animals BOOLEAN DEFAULT 1,
    allow_monsters BOOLEAN DEFAULT 1,
    pvp_enabled BOOLEAN DEFAULT 1,
    world_folder_path TEXT,
    world_size_bytes BIGINT DEFAULT 0,
    last_backup_timestamp TIMESTAMP,
    backup_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT 1,
    created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_playtime_seconds BIGINT DEFAULT 0,
    unique_players_visited INTEGER DEFAULT 0,
    current_players_online INTEGER DEFAULT 0,
    max_players_recorded INTEGER DEFAULT 0,
    updated_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Features

### Automatic World Registration

When a world is loaded, the system automatically:

1. **Detects world load events** via `WorldLoadEvent`
2. **Extracts metadata** from the Bukkit `World` object
3. **Registers or updates** world information in the database
4. **Marks the world as active** for tracking purposes

### World Lifecycle Management

- **Initialization Tracking**: Pre-registers worlds during initialization
- **Load Registration**: Complete registration when world is fully loaded
- **Unload Handling**: Marks worlds as inactive (preserves data)
- **Startup Sync**: Synchronizes all loaded worlds at plugin startup

### REST API Integration

The world tracking system provides comprehensive REST endpoints:

- `GET /api/v1/worlds` - List all worlds with metadata
- `GET /api/v1/worlds/active` - Get active worlds only
- `GET /api/v1/worlds/with-players` - Get worlds with online players
- `GET /api/v1/worlds/statistics` - Get world statistics
- `GET /api/v1/worlds/{worldName}` - Get specific world details

## Usage

### Automatic Operation

The world tracking system operates automatically once registered:

```java
// Automatic registration in RVNKTools.java
WorldTrackingListener worldTracker = new WorldTrackingListener(this, rvnkCore);
getServer().getPluginManager().registerEvents(worldTracker, this);

// Sync existing worlds at startup
worldTracker.syncAllLoadedWorlds();
```

### Programmatic Access

Access world information through the service layer:

```java
WorldService worldService = rvnkCore.getService(WorldService.class);

// Get all worlds
CompletableFuture<List<WorldDTO>> allWorlds = worldService.getAllWorlds();

// Get active worlds only
CompletableFuture<List<WorldDTO>> activeWorlds = worldService.getActiveWorlds();

// Register a world manually
World bukkitWorld = Bukkit.getWorld("world_name");
CompletableFuture<Void> registration = worldService.registerWorld(bukkitWorld);
```

### REST API Access

Query world information via HTTP:

```bash
# Get all worlds
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://localhost:8081/api/v1/worlds

# Get worlds with players
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://localhost:8081/api/v1/worlds/with-players

# Get world statistics
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://localhost:8081/api/v1/worlds/statistics
```

## Integration Points

### Player World Correlation

The world tracking system integrates with the PlayerWorld tracking system to provide:

- **World-Player relationships**: Track which players have visited which worlds
- **Correlation data**: Combined world metadata with player-specific information
- **Activity tracking**: Monitor player activity across different worlds

### Performance Considerations

- **Async Operations**: All database operations use CompletableFuture
- **Event-Driven Updates**: Real-time tracking with minimal performance impact
- **Efficient Queries**: Optimized database schema with proper indexing
- **Rate Limiting**: Prevents excessive database updates

## Configuration

World tracking operates automatically but can be configured via RVNKCore settings:

```yaml
# config-core.yml
world_tracking:
  enabled: true
  auto_register: true
  sync_on_startup: true
  track_inactive_worlds: true
```

## Troubleshooting

### Common Issues

1. **World not registered**: Check if WorldTrackingListener is properly registered
2. **Database errors**: Verify database schema and connection
3. **Missing worlds**: Run manual sync via `syncAllLoadedWorlds()`
4. **Inactive worlds**: Check if worlds were properly unloaded

### Debug Information

Enable debug logging to monitor world tracking:

```java
logger.debug("World registration events will be logged in debug mode");
```

### Manual Operations

Force world registration or sync:

```java
WorldTrackingListener listener = new WorldTrackingListener(plugin, rvnkCore);
listener.syncAllLoadedWorlds(); // Sync all loaded worlds
```

## Future Enhancements

- **World backup integration**: Automatic backup tracking
- **Performance metrics**: World-specific performance monitoring  
- **World templates**: Save and restore world configurations
- **Cross-server sync**: Multi-server world tracking (BungeeCord)
- **Advanced analytics**: World usage statistics and insights

## See Also

- [PlayerWorld Tracking Documentation](player-world-tracking.md)
- [RVNKCore Service Architecture](rvnkcore-service.md)
- [REST API Documentation](rvnkcore-httprest.md)
- [Database Schema Reference](rvnkcore-database.md)
