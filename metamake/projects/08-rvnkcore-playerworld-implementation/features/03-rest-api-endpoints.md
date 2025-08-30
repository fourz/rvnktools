# REST API Endpoints Specification

**Feature ID**: 03-rest-api-endpoints  
**Phase**: REST API Layer  
**Prerequisites**: Service layer implemented, Jetty server configured

## PlayerWorld REST API Architecture

### API Base Structure

The PlayerWorld API extends the existing RVNKCore REST API server with new endpoints for per-world player data management:

```
Base URL: http://localhost:8080/api/v1 (HTTP)
Base URL: https://localhost:8081/api/v1 (HTTPS)
Authentication: X-API-Key header required
Content-Type: application/json
```

### Endpoint Categories

#### 1. Player World Data Endpoints

These endpoints provide access to world-specific player data:

**GET /players/{uuid}/worlds**
- **Purpose**: Get all world data for a specific player
- **Response**: Array of PlayerWorldDataDTO objects
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worlds`

**GET /players/{uuid}/worlds/{world}**
- **Purpose**: Get player data for a specific world
- **Response**: Single PlayerWorldDataDTO object
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worlds/world`

**GET /players/{uuid}/worlds/{world}/location**
- **Purpose**: Get last known location for player in specific world
- **Response**: Location object with coordinates and metadata
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worlds/world/location`

#### 2. Player Analytics Endpoints

These endpoints provide analytics and statistics:

**GET /players/{uuid}/analytics**
- **Purpose**: Get comprehensive analytics for a player
- **Response**: PlayerAnalyticsDTO with total playtime, most active worlds, etc.
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/analytics`

**GET /players/{uuid}/analytics/playtime**
- **Purpose**: Get total playtime across all worlds
- **Response**: Playtime data with breakdown by world
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/analytics/playtime`

**GET /players/{uuid}/analytics/activity**
- **Purpose**: Get player activity patterns and statistics
- **Response**: Activity metrics including session data
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/analytics/activity`

#### 3. World Management Endpoints

These endpoints provide comprehensive world data and management capabilities:

**GET /worlds**
- **Purpose**: Get list of all tracked worlds
- **Response**: Array of WorldDTO objects with metadata
- **Query Parameters**: `?type=NORMAL&environment=NETHER&enabled=true`
- **Example**: `GET /api/v1/worlds?type=NORMAL&enabled=true`

**GET /worlds/{world}**
- **Purpose**: Get detailed information about a specific world
- **Response**: Complete WorldDTO with all metadata and statistics
- **Example**: `GET /api/v1/worlds/world`

**PUT /worlds/{world}**
- **Purpose**: Update world metadata and configuration
- **Request Body**: WorldUpdateDTO with changed fields
- **Response**: Updated WorldDTO
- **Example**: `PUT /api/v1/worlds/world`

**POST /worlds/{world}/sync**
- **Purpose**: Synchronize world data from server (refresh metadata)
- **Response**: Synchronization result with updated statistics
- **Example**: `POST /api/v1/worlds/world/sync`

**GET /worlds/{world}/analytics**
- **Purpose**: Get comprehensive analytics for a specific world
- **Response**: WorldAnalyticsDTO with player counts, activity metrics
- **Example**: `GET /api/v1/worlds/world/analytics`

**GET /worlds/{world}/players**
- **Purpose**: Get all players who have visited a world
- **Query Parameters**: `?active=true&minPlaytime=3600&sortBy=playtime&order=desc`
- **Response**: Array of player summaries with world-specific data
- **Example**: `GET /api/v1/worlds/world/players?active=true`

**GET /worlds/{world}/players/active**
- **Purpose**: Get currently active players in a world
- **Response**: Array of online player data with current locations
- **Example**: `GET /api/v1/worlds/world/players/active`

**GET /worlds/{world}/statistics**
- **Purpose**: Get detailed world statistics and metrics
- **Response**: WorldStatisticsDTO with comprehensive metrics
- **Example**: `GET /api/v1/worlds/world/statistics`

#### 4. World Analytics Endpoints

These endpoints provide world-centric analytics:

**GET /worlds/{world}/analytics**
- **Purpose**: Get analytics for a specific world
- **Response**: WorldAnalyticsDTO with player counts, activity metrics
- **Example**: `GET /api/v1/worlds/world/analytics`

**GET /worlds/{world}/players**
- **Purpose**: Get all players who have visited a world
- **Response**: Array of player summaries with world-specific data
- **Example**: `GET /api/v1/worlds/world/players`

**GET /worlds/{world}/players/active**
- **Purpose**: Get currently active players in a world
- **Response**: Array of online player data
- **Example**: `GET /api/v1/worlds/world/players/active`

#### 4. WorldSwap Integration Endpoints

These endpoints support WorldSwap plugin integration:

**GET /players/{uuid}/worldswap/eligible**
- **Purpose**: Get worlds player is eligible to swap to
- **Response**: Array of world names with access status
- **Example**: `GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worldswap/eligible`

**POST /players/{uuid}/worldswap/validate**
- **Purpose**: Validate a proposed world swap
- **Request Body**: `{ "fromWorld": "world", "toWorld": "world_nether" }`
- **Response**: Validation result with permissions and cooldowns
- **Example**: `POST /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worldswap/validate`

**POST /players/{uuid}/worldswap/record**
- **Purpose**: Record a completed world swap
- **Request Body**: `{ "fromWorld": "world", "toWorld": "world_nether", "method": "command" }`
- **Response**: Success confirmation
- **Example**: `POST /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worldswap/record`

### Data Transfer Objects (DTOs)

#### PlayerWorldDataDTO

```json
{
  "playerId": "94c37976-5134-40b0-9e03-722ae6664fea",
  "worldName": "world",
  "firstVisit": "2024-01-15T10:30:00Z",
  "lastSeen": "2024-01-20T15:45:30Z",
  "visitCount": 15,
  "totalPlaytimeMs": 7200000,
  "lastX": 100.5,
  "lastY": 64.0,
  "lastZ": -200.3,
  "lastYaw": 45.0,
  "lastPitch": 0.0,
  "biome": "PLAINS",
  "deathCount": 2,
  "mobKills": 45,
  "blocksBroken": 150,
  "blocksPlaced": 75
}
```

#### PlayerAnalyticsDTO

```json
{
  "playerId": "94c37976-5134-40b0-9e03-722ae6664fea",
  "totalPlaytimeMs": 14400000,
  "totalWorlds": 3,
  "mostActiveWorld": "world",
  "averageSessionLength": 1800000,
  "worldPlaytimes": {
    "world": 7200000,
    "world_nether": 3600000,
    "world_the_end": 3600000
  },
  "visitCounts": {
    "world": 15,
    "world_nether": 8,
    "world_the_end": 3
  },
  "firstVisit": "2024-01-15T10:30:00Z",
  "lastSeen": "2024-01-20T15:45:30Z"
}
```

#### WorldAnalyticsDTO

```json
{
  "worldName": "world",
  "totalPlayers": 127,
  "activePlayersLast7Days": 45,
  "averagePlaytimePerPlayer": 5400000,
  "totalVisits": 890,
  "averageVisitsPerPlayer": 7.0,
  "mostActivePlayer": "94c37976-5134-40b0-9e03-722ae6664fea",
  "peakConcurrentPlayers": 12,
  "lastActivity": "2024-01-20T15:45:30Z",
  "popularLocations": [
    {
      "x": 0,
      "y": 64,
      "z": 0,
      "visitCount": 67,
      "description": "Spawn area"
    }
  ]
}
```

#### LocationDTO

```json
{
  "world": "world",
  "x": 100.5,
  "y": 64.0,
  "z": -200.3,
  "yaw": 45.0,
  "pitch": 0.0,
  "biome": "PLAINS",
  "lastUpdated": "2024-01-20T15:45:30Z"
}
```

#### WorldDTO

```json
{
  "worldName": "world",
  "displayName": "Main World",
  "worldType": "NORMAL",
  "environment": "NORMAL",
  "worldPath": "/server/worlds/world",
  "difficulty": "NORMAL",
  "gameMode": "SURVIVAL",
  "seed": 123456789012345,
  "spawnLocation": {
    "x": 0,
    "y": 64,
    "z": 0
  },
  "worldBorder": {
    "size": 29999984,
    "centerX": 0.0,
    "centerZ": 0.0
  },
  "settings": {
    "allowAnimals": true,
    "allowMonsters": true,
    "allowPvp": true,
    "keepSpawnInMemory": true,
    "autoSave": true
  },
  "status": {
    "isLoaded": true,
    "isEnabled": true,
    "currentPlayerCount": 5,
    "lastLoaded": "2024-01-20T10:00:00Z"
  },
  "statistics": {
    "totalVisits": 1250,
    "totalPlaytime": 45600000,
    "uniquePlayerCount": 127,
    "createdDate": "2023-12-01T00:00:00Z"
  },
  "tags": ["survival", "main", "overworld"],
  "metadata": {
    "description": "Primary survival world",
    "rules": ["No griefing", "Respect others"],
    "features": ["Towns", "Economy", "Shops"]
  }
}
```

#### WorldUpdateDTO

```json
{
  "displayName": "Updated World Name",
  "difficulty": "HARD",
  "gameMode": "SURVIVAL",
  "settings": {
    "allowAnimals": true,
    "allowMonsters": true,
    "allowPvp": false,
    "keepSpawnInMemory": true,
    "autoSave": true
  },
  "isEnabled": true,
  "tags": ["survival", "main", "pvp-disabled"],
  "metadata": {
    "description": "Updated world description",
    "rules": ["No griefing", "Respect others", "No PvP"],
    "features": ["Towns", "Economy", "Shops", "Safe Zone"]
  }
}
```

#### WorldStatisticsDTO

```json
{
  "worldName": "world",
  "playerStatistics": {
    "totalPlayers": 127,
    "activePlayers": 45,
    "currentPlayers": 5,
    "avgPlaytimePerPlayer": 358740,
    "topPlayers": [
      {
        "playerId": "94c37976-5134-40b0-9e03-722ae6664fea",
        "playerName": "wizardofire",
        "playtime": 7200000,
        "visitCount": 15,
        "lastSeen": "2024-01-20T15:45:30Z"
      }
    ]
  },
  "activityStatistics": {
    "totalVisits": 1250,
    "avgVisitsPerPlayer": 9.8,
    "avgSessionLength": 1800000,
    "peakConcurrentPlayers": 12,
    "peakTime": "2024-01-19T20:30:00Z",
    "dailyActiveUsers": 8,
    "weeklyActiveUsers": 45,
    "monthlyActiveUsers": 89
  },
  "blockStatistics": {
    "totalBlocksBroken": 125000,
    "totalBlocksPlaced": 98000,
    "netBlocksPlaced": -27000,
    "mostBrokenBlock": "STONE",
    "mostPlacedBlock": "COBBLESTONE",
    "topBuilders": [
      {
        "playerId": "94c37976-5134-40b0-9e03-722ae6664fea",
        "blocksPlaced": 5000,
        "blocksBroken": 7500
      }
    ]
  },
  "locationStatistics": {
    "totalDistanceTraveled": 2500000,
    "avgDistancePerPlayer": 19685,
    "popularBiomes": [
      {"biome": "PLAINS", "visitCount": 450},
      {"biome": "FOREST", "visitCount": 320},
      {"biome": "MOUNTAINS", "visitCount": 180}
    ],
    "popularLocations": [
      {
        "x": 0, "y": 64, "z": 0,
        "visitCount": 67,
        "description": "Spawn area"
      },
      {
        "x": 200, "y": 70, "z": -150,
        "visitCount": 23,
        "description": "Player town"
      }
    ]
  },
  "timeStatistics": {
    "firstPlayerVisit": "2023-12-01T10:00:00Z",
    "lastPlayerVisit": "2024-01-20T15:45:30Z",
    "worldAge": 7257600,
    "activeHours": [
      {"hour": 18, "playerCount": 8},
      {"hour": 19, "playerCount": 12},
      {"hour": 20, "playerCount": 10}
    ]
  }
}
```

### Query Parameters and Filtering

#### Common Query Parameters

**Pagination**
- `page`: Page number (default: 1)
- `size`: Items per page (default: 50, max: 200)
- `sort`: Sort field (default: varies by endpoint)
- `order`: Sort order (asc/desc, default: desc)

**Time Filtering**
- `since`: Filter data since timestamp (ISO 8601)
- `until`: Filter data until timestamp (ISO 8601)
- `period`: Predefined time period (1d, 7d, 30d, 90d)

**Data Filtering**
- `active`: Filter active players only (true/false)
- `minPlaytime`: Minimum playtime in milliseconds
- `world`: Filter by specific world name
- `includeInactive`: Include inactive players (default: false)

#### Example Queries

**Get active players in world with pagination:**
```
GET /api/v1/worlds/world/players?active=true&page=1&size=20&sort=lastSeen&order=desc
```

**Get player analytics for last 30 days:**
```
GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/analytics?period=30d
```

**Get world data with minimum playtime filter:**
```
GET /api/v1/players/94c37976-5134-40b0-9e03-722ae6664fea/worlds?minPlaytime=3600000
```

### HTTP Status Codes and Error Handling

#### Success Responses
- **200 OK**: Successful GET request
- **201 Created**: Successful POST request creating new resource
- **204 No Content**: Successful DELETE request
- **304 Not Modified**: Resource not modified since last request

#### Client Error Responses
- **400 Bad Request**: Invalid request parameters or body
- **401 Unauthorized**: Missing or invalid API key
- **403 Forbidden**: Insufficient permissions
- **404 Not Found**: Resource not found
- **429 Too Many Requests**: Rate limit exceeded

#### Server Error Responses
- **500 Internal Server Error**: Unexpected server error
- **502 Bad Gateway**: Service dependency error
- **503 Service Unavailable**: Service temporarily unavailable

#### Error Response Format

```json
{
  "error": {
    "code": "PLAYER_NOT_FOUND",
    "message": "Player with UUID 94c37976-5134-40b0-9e03-722ae6664fea not found",
    "details": {
      "uuid": "94c37976-5134-40b0-9e03-722ae6664fea",
      "timestamp": "2024-01-20T15:45:30Z",
      "requestId": "req-12345"
    }
  }
}
```

### Rate Limiting and Caching

#### Rate Limiting
- **Default Limits**: 1000 requests per hour per API key
- **Analytics Endpoints**: 100 requests per hour (more expensive)
- **Headers**: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

#### Caching Strategy
- **Player Data**: Cache for 5 minutes
- **Analytics Data**: Cache for 15 minutes
- **Location Data**: Cache for 1 minute
- **World Statistics**: Cache for 30 minutes
- **Headers**: `Cache-Control`, `ETag`, `Last-Modified`

### Security Considerations

#### API Key Authentication
- API keys must be included in `X-API-Key` header
- Keys should have appropriate permissions for requested resources
- Rate limiting applies per API key

#### Data Access Control
- Players can only access their own detailed data
- Anonymous access limited to public statistics
- Administrative endpoints require elevated permissions

#### Input Validation
- All UUIDs validated for correct format
- World names validated against existing worlds
- Coordinate values validated for reasonable ranges
- Request body size limits enforced

### Performance Optimization

#### Database Query Optimization
- Indexed queries for common access patterns
- Batch operations for bulk data requests
- Connection pooling for high concurrency
- Query result caching for expensive operations

#### Response Optimization
- JSON response compression (gzip)
- Partial response support (field selection)
- Conditional requests (ETag/Last-Modified)
- Streaming responses for large datasets

---

**Integration Notes:**
- All endpoints integrate with existing RVNKCore authentication system
- Consistent with existing API patterns and error handling
- Supports both HTTP and HTTPS protocols
- Compatible with existing monitoring and logging systems
- Extensible for future PlayerWorld feature additions
