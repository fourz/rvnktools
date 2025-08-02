# RVNKCore World-Based Player Tracking Requirements

**Document Version**: 2.0  
**Last Updated**: August 2, 2025  
**Status**: Updated for World-Based Implementation

## Purpose

This document defines the enhanced player tracking requirements for RVNKCore, implementing per-world player data tracking to support advanced world management features including the 'worldswap' teleport command and comprehensive world-specific analytics.

## Architecture Overview

The world-based player tracking system separates global player data from world-specific data, enabling precise location tracking, playtime analysis, and world-specific statistics while maintaining performance through rate limiting and intelligent caching.

```text
┌─────────────────────────────────────────────────────────────┐
│                    Global Player Data                       │
│  • Player Identity (UUID, Names, History)                  │
│  • Global Statistics (Total Playtime, Times Joined)        │
│  • Current World Location                                   │
│  • Permission Groups & Ban Status                          │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                 World-Specific Player Data                  │
├─────────────────┬─────────────────┬─────────────────────────┤
│   World Alpha   │   World Beta    │      World Gamma        │
│                 │                 │                         │
│ • Last Location │ • Last Location │ • Last Location         │
│ • Visit History │ • Visit History │ • Visit History         │
│ • Playtime      │ • Playtime      │ • Playtime              │
│ • Deaths        │ • Deaths        │ • Deaths                │
│ • World Data    │ • World Data    │ • World Data            │
└─────────────────┴─────────────────┴─────────────────────────┘
```

## Database Schema

### Primary Tables

#### rvnk_players (Global Player Data)
```sql
CREATE TABLE rvnk_players (
    id TEXT PRIMARY KEY,                    -- Player UUID
    current_name TEXT NOT NULL,             -- Current player name
    name_history TEXT DEFAULT '',           -- Comma-separated name history
    first_join TIMESTAMP NOT NULL,          -- First time player joined
    last_seen TIMESTAMP NOT NULL,           -- Last activity timestamp
    current_world TEXT,                     -- Current world location
    times_joined INTEGER DEFAULT 1,         -- Total join count
    total_playtime_seconds BIGINT DEFAULT 0,-- Total playtime across all worlds
    primary_group TEXT DEFAULT 'default',   -- Primary permission group
    groups TEXT DEFAULT '',                 -- All permission groups
    banned BOOLEAN DEFAULT FALSE,           -- Ban status
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### rvnk_player_world_data (World-Specific Data)
```sql
CREATE TABLE rvnk_player_world_data (
    player_id TEXT NOT NULL,               -- Player UUID (FK)
    world_name TEXT NOT NULL,              -- World identifier
    first_visit TIMESTAMP NOT NULL,        -- First visit to this world
    last_visit TIMESTAMP NOT NULL,         -- Most recent visit
    visit_count INTEGER DEFAULT 1,         -- Number of times visited
    playtime_seconds BIGINT DEFAULT 0,     -- Playtime in this world
    last_x REAL DEFAULT 0,                 -- Last X coordinate
    last_y REAL DEFAULT 0,                 -- Last Y coordinate
    last_z REAL DEFAULT 0,                 -- Last Z coordinate
    last_yaw REAL DEFAULT 0,               -- Last view direction (yaw)
    last_pitch REAL DEFAULT 0,             -- Last view direction (pitch)
    last_biome TEXT,                       -- Last known biome
    death_count INTEGER DEFAULT 0,         -- Deaths in this world
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, world_name)    -- Composite primary key
);
```

### Performance Indexes
```sql
-- Player queries
CREATE INDEX idx_players_name_history ON rvnk_players(current_name, name_history);
CREATE INDEX idx_players_last_seen ON rvnk_players(last_seen);
CREATE INDEX idx_players_current_world ON rvnk_players(current_world);

-- World data queries
CREATE INDEX idx_player_world_data_player ON rvnk_player_world_data(player_id);
CREATE INDEX idx_player_world_data_world ON rvnk_player_world_data(world_name);
CREATE INDEX idx_player_world_data_last_visit ON rvnk_player_world_data(last_visit);
CREATE INDEX idx_player_world_data_playtime ON rvnk_player_world_data(playtime_seconds);
```

## Service Architecture

### PlayerWorldService Interface
The core service interface providing comprehensive player and world data management:

#### Global Player Management
- `getPlayer(UUID)` - Retrieve global player data
- `getPlayerByName(String)` - Find player by current name
- `recordPlayerJoin(UUID, String, String)` - Track player login
- `recordPlayerQuit(UUID, long)` - Track player logout with session duration

#### World-Specific Management
- `getPlayerWorldData(UUID, String)` - Get player data for specific world
- `getLastKnownLocation(UUID, String)` - Core method for worldswap functionality
- `recordWorldChange(UUID, String, String, coords...)` - Track world transitions
- `updatePlayerLocation(UUID, String, coords...)` - Update location with rate limiting
- `recordPlayerDeath(UUID, String)` - Track deaths per world

#### Analytics and Statistics
- `getWorldVisitors(String)` - All players who visited a world
- `getRecentWorldVisitors(String, int)` - Recent visitors within timeframe
- `getWorldTotalPlaytime(String)` - Aggregate playtime for world
- `getPlayerMostVisitedWorlds(UUID, int)` - Player's favorite worlds

#### Worldswap Command Support
- `getPlayerVisitedWorlds(UUID)` - List of worlds player has visited
- `hasPlayerVisitedWorld(UUID, String)` - Validation for worldswap permission
- `getPlayerPreviousWorld(UUID, String)` - Previous world for "back" functionality

## Key Features

### 1. Worldswap Teleport Command
The primary driver for this enhancement, enabling players to teleport between worlds they have previously visited while remembering their exact last location in each world.

**Command Examples:**
- `/worldswap [world]` - Teleport to last known location in specified world
- `/worldswap back` - Return to previous world
- `/worldswap list` - Show all visited worlds with visit counts and playtime

**Implementation Requirements:**
- Validate player has visited target world
- Retrieve last known coordinates, yaw, and pitch
- Ensure target location is safe (not in void/lava)
- Update world transition tracking
- Rate limit to prevent abuse

### 2. Performance Optimizations

#### Rate Limiting for Location Updates
- Location updates limited to once per 30 seconds per player per world
- Prevents excessive database writes during normal movement
- Immediate updates during world changes/teleports

#### Session Tracking
- In-memory tracking of active player sessions
- Efficient playtime calculation on logout
- Automatic cleanup of disconnected players

#### Intelligent Caching
- Recently accessed world data cached in memory
- Cache invalidation on data updates
- Configurable cache size and expiration

### 3. World Analytics

#### Per-World Statistics
- Total unique visitors
- Active/recent visitors (configurable timeframe)
- Aggregate playtime across all players
- Death statistics and hotspots
- Popular locations (most visited coordinates)

#### Player Journey Analysis
- World visitation patterns
- Playtime distribution across worlds
- World preference rankings
- Cross-world activity correlation

## API Enhancements

### REST API Endpoints

#### Player Endpoints (Enhanced)
```
GET /api/players/{uuid}                    # Global player data
GET /api/players/{uuid}/worlds             # All world data for player
GET /api/players/{uuid}/worlds/{world}     # Specific world data
GET /api/players/by-name/{name}            # Find player by name
```

#### New World Data Endpoints
```
GET /api/worlds/{world}/players            # All players who visited world
GET /api/worlds/{world}/players/recent     # Recent visitors
GET /api/worlds/{world}/statistics         # World statistics
GET /api/worlds/{world}/playtime           # Total playtime in world
```

#### Worldswap Support Endpoints
```
GET /api/players/{uuid}/visited-worlds     # Worlds player has visited
GET /api/players/{uuid}/location/{world}   # Last location in world
POST /api/players/{uuid}/worldswap         # Record worldswap event
```

### Response Models

#### Enhanced PlayerResponse
```json
{
  "uuid": "player-uuid",
  "name": "PlayerName",
  "online": true,
  "firstSeen": "2025-01-01T00:00:00",
  "lastSeen": "2025-08-02T12:00:00",
  "timesJoined": 42,
  "currentWorld": "world_nether",
  "totalPlaytimeMinutes": 3600,
  "groups": ["default", "builder"],
  "nameHistory": ["OldName1", "OldName2"],
  "visitedWorlds": ["world", "world_nether", "world_the_end"]
}
```

#### PlayerWorldDataResponse
```json
{
  "playerId": "player-uuid",
  "playerName": "PlayerName",
  "worldName": "world_nether",
  "firstVisit": "2025-01-15T10:30:00",
  "lastVisit": "2025-08-02T12:00:00",
  "visitCount": 15,
  "playtimeSeconds": 7200,
  "lastX": -123.456,
  "lastY": 64.0,
  "lastZ": 789.012,
  "lastYaw": 180.0,
  "lastPitch": 0.0,
  "lastBiome": "nether_wastes",
  "deathCount": 3,
  "worldSpecificData": {
    "customKey": "customValue"
  }
}
```

## Migration Strategy

### Phase 1: Schema Migration
1. Add new `rvnk_player_world_data` table
2. Update `rvnk_players` table structure
3. Migrate existing location data to world-specific records
4. Create performance indexes

### Phase 2: Service Implementation
1. Implement `PlayerWorldDataRepository`
2. Update `PlayerRepository` for new schema
3. Implement `PlayerWorldService` with both repositories
4. Add rate limiting and caching mechanisms

### Phase 3: API Updates
1. Update existing player endpoints
2. Add new world-specific endpoints
3. Implement worldswap command support endpoints
4. Update response models and documentation

### Phase 4: Command Implementation
1. Implement worldswap command with tab completion
2. Add world validation and safety checks
3. Implement world list and statistics commands
4. Add admin commands for world data management

## Configuration Options

### Rate Limiting
```yaml
player_tracking:
  location_update_interval_seconds: 30
  max_cached_players: 1000
  cache_expiration_minutes: 30
  
worldswap:
  enabled: true
  cooldown_seconds: 5
  require_visited: true
  safety_checks: true
```

### Performance Tuning
```yaml
database:
  batch_update_size: 100
  connection_pool_size: 10
  query_timeout_seconds: 30
  
analytics:
  recent_visitor_hours: 24
  statistics_cache_minutes: 15
  world_ranking_limit: 50
```

## Security Considerations

### Data Privacy
- Player location data considered sensitive
- API access requires authentication
- Rate limiting prevents data mining
- Optional data anonymization for analytics

### World Access Control
- Worldswap requires previous visitation
- Integration with permission systems
- Configurable world blacklists
- Admin override capabilities

### Performance Protection
- Rate limiting prevents database overload
- Maximum query result limits
- Automatic cleanup of old data
- Connection pool management

## Testing Requirements

### Unit Tests
- Repository layer CRUD operations
- Service layer business logic
- Rate limiting mechanisms
- Data migration scripts

### Integration Tests
- Database schema validation
- API endpoint functionality
- Worldswap command behavior
- Performance under load

### Load Testing
- Concurrent player location updates
- Mass worldswap operations
- Large world visitor queries
- Database performance benchmarks

## Monitoring and Metrics

### Key Performance Indicators
- Average world change processing time
- Database query performance
- Cache hit rates
- API response times

### Alerting Thresholds
- High database connection usage
- Slow query detection
- Cache performance degradation
- Unusual player activity patterns

This enhanced world-based player tracking system provides the foundation for advanced world management features while maintaining high performance and scalability for large Minecraft servers.
