# RVNKCore HTTP REST API Documentation

**Version**: 1.0.0 (Planned)  
**Status**: Under Development  
**Base URL**: `http://localhost:8080/api/v1`

## Overview

The RVNKCore HTTP REST API provides external access to the RVNK plugin ecosystem data and services. This RESTful interface allows web applications, external tools, and third-party integrations to interact with player data, server statistics, and administrative functions.

**Note**: The HTTP REST API is currently in the planning phase as part of the RVNKCore roadmap. This documentation outlines the planned implementation.

## Authentication

All API endpoints require authentication via API keys or bearer tokens.

```http
Authorization: Bearer YOUR_API_TOKEN
```

API keys can be generated through the in-game admin interface or server console.

## Base Response Format

All API responses follow a consistent JSON structure:

```json
{
  "success": true,
  "data": {},
  "message": "Operation completed successfully",
  "timestamp": "2025-07-19T21:30:00Z",
  "version": "1.0.0"
}
```

### Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "PLAYER_NOT_FOUND",
    "message": "Player with ID 12345 not found",
    "details": {}
  },
  "timestamp": "2025-07-19T21:30:00Z",
  "version": "1.0.0"
}
```

## Player API Endpoints

### Get Player by UUID

```http
GET /api/v1/players/{uuid}
```

**Parameters:**
- `uuid` (path) - Player UUID

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "12345678-1234-1234-1234-123456789abc",
    "currentName": "PlayerName",
    "nameHistory": ["OldName1", "OldName2"],
    "firstJoin": "2025-01-15T10:30:00Z",
    "lastSeen": "2025-07-19T21:25:00Z",
    "location": {
      "world": "world",
      "x": 100.5,
      "y": 64.0,
      "z": -50.25
    },
    "primaryGroup": "default",
    "groups": ["default", "vip"],
    "banned": false
  }
}
```

### Get Player by Name

```http
GET /api/v1/players/by-name/{name}
```

**Parameters:**
- `name` (path) - Current player name

**Response:** Same as Get Player by UUID

### Search Players

```http
GET /api/v1/players/search?q={query}&limit={limit}&offset={offset}
```

**Query Parameters:**
- `q` (required) - Search query (name pattern)
- `limit` (optional) - Maximum results (default: 50, max: 100)
- `offset` (optional) - Pagination offset (default: 0)

**Response:**
```json
{
  "success": true,
  "data": {
    "players": [
      {
        "id": "12345678-1234-1234-1234-123456789abc",
        "currentName": "PlayerName",
        "lastSeen": "2025-07-19T21:25:00Z",
        "primaryGroup": "default"
      }
    ],
    "total": 150,
    "limit": 50,
    "offset": 0
  }
}
```

### Get Recent Players

```http
GET /api/v1/players/recent?hours={hours}&limit={limit}
```

**Query Parameters:**
- `hours` (optional) - Hours to look back (default: 24)
- `limit` (optional) - Maximum results (default: 50)

**Response:**
```json
{
  "success": true,
  "data": {
    "players": [
      {
        "id": "12345678-1234-1234-1234-123456789abc",
        "currentName": "PlayerName",
        "lastSeen": "2025-07-19T21:25:00Z",
        "location": {
          "world": "world",
          "x": 100.5,
          "y": 64.0,
          "z": -50.25
        }
      }
    ],
    "count": 25
  }
}
```

### Get Players by Group

```http
GET /api/v1/players/groups/{group}?limit={limit}&offset={offset}
```

**Parameters:**
- `group` (path) - Permission group name

**Query Parameters:**
- `limit` (optional) - Maximum results (default: 50)
- `offset` (optional) - Pagination offset (default: 0)

**Response:** Similar to Search Players

## Server API Endpoints

### Get Server Statistics

```http
GET /api/v1/server/stats
```

**Response:**
```json
{
  "success": true,
  "data": {
    "onlinePlayers": 15,
    "maxPlayers": 100,
    "totalRegisteredPlayers": 2500,
    "serverVersion": "1.21",
    "pluginVersion": "1.1-alpha",
    "uptime": 86400000,
    "worlds": ["world", "world_nether", "world_the_end", "event"],
    "performance": {
      "tps": 19.95,
      "memoryUsed": "2.5GB",
      "memoryMax": "8GB"
    }
  }
}
```

### Get Online Players

```http
GET /api/v1/server/online
```

**Response:**
```json
{
  "success": true,
  "data": {
    "players": [
      {
        "uuid": "12345678-1234-1234-1234-123456789abc",
        "name": "PlayerName",
        "world": "world",
        "gameMode": "SURVIVAL",
        "displayName": "§6PlayerName"
      }
    ],
    "count": 15
  }
}
```

## Administrative API Endpoints

### Update Player Location

```http
PUT /api/v1/admin/players/{uuid}/location
```

**Request Body:**
```json
{
  "world": "world",
  "x": 100.5,
  "y": 64.0,
  "z": -50.25
}
```

**Response:**
```json
{
  "success": true,
  "message": "Player location updated successfully"
}
```

### Ban/Unban Player

```http
POST /api/v1/admin/players/{uuid}/ban
DELETE /api/v1/admin/players/{uuid}/ban
```

**POST Request Body:**
```json
{
  "reason": "Violation of server rules",
  "duration": 86400000,
  "moderator": "AdminName"
}
```

## WebSocket Events (Planned)

Real-time events for live updates:

### Connection

```javascript
const ws = new WebSocket('ws://localhost:8080/api/v1/events');
ws.onopen = () => {
  // Send authentication
  ws.send(JSON.stringify({
    type: 'auth',
    token: 'YOUR_API_TOKEN'
  }));
};
```

### Event Types

- `player.join` - Player joins the server
- `player.quit` - Player leaves the server
- `player.chat` - Player chat message
- `server.start` - Server startup
- `server.stop` - Server shutdown

### Event Format

```json
{
  "type": "player.join",
  "timestamp": "2025-07-19T21:30:00Z",
  "data": {
    "player": {
      "uuid": "12345678-1234-1234-1234-123456789abc",
      "name": "PlayerName"
    },
    "world": "world"
  }
}
```

## Rate Limiting

API requests are rate-limited to prevent abuse:

- **Standard endpoints**: 100 requests per minute per IP
- **Search endpoints**: 30 requests per minute per IP
- **Administrative endpoints**: 50 requests per minute per API key

Rate limit headers are included in responses:

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642694400
```

## Error Codes

| Code | Description |
|------|-------------|
| `INVALID_API_KEY` | API key is missing or invalid |
| `RATE_LIMIT_EXCEEDED` | Too many requests |
| `PLAYER_NOT_FOUND` | Player does not exist |
| `INVALID_PARAMETERS` | Request parameters are invalid |
| `SERVER_ERROR` | Internal server error |
| `MAINTENANCE_MODE` | Server is in maintenance mode |

## SDK Examples

### JavaScript/Node.js

```javascript
const RVNKCoreAPI = require('@rvnk/core-api');

const client = new RVNKCoreAPI({
  baseURL: 'http://localhost:8080/api/v1',
  apiKey: 'your-api-key'
});

// Get player data
const player = await client.players.getByUUID('12345678-1234-1234-1234-123456789abc');
console.log(player.currentName);

// Search players
const results = await client.players.search('Player', { limit: 10 });
results.players.forEach(p => console.log(p.currentName));
```

### Python

```python
import rvnkcore

client = rvnkcore.Client(
    base_url='http://localhost:8080/api/v1',
    api_key='your-api-key'
)

# Get player data
player = client.players.get_by_uuid('12345678-1234-1234-1234-123456789abc')
print(player.current_name)

# Get recent players
recent = client.players.get_recent(hours=24)
for player in recent.players:
    print(f"{player.current_name} - {player.last_seen}")
```

### cURL Examples

```bash
# Get player by UUID
curl -H "Authorization: Bearer YOUR_API_TOKEN" \
     "http://localhost:8080/api/v1/players/12345678-1234-1234-1234-123456789abc"

# Search players
curl -H "Authorization: Bearer YOUR_API_TOKEN" \
     "http://localhost:8080/api/v1/players/search?q=Player&limit=10"

# Get server stats
curl -H "Authorization: Bearer YOUR_API_TOKEN" \
     "http://localhost:8080/api/v1/server/stats"
```

## Implementation Status

- ✅ **Planned**: API specification complete
- 🔄 **In Progress**: Core infrastructure development
- ⏳ **Pending**: REST endpoint implementation
- ⏳ **Pending**: WebSocket event system
- ⏳ **Pending**: Authentication system
- ⏳ **Pending**: Rate limiting implementation

## Configuration

The REST API will be configurable through `api.yml`:

```yaml
api:
  enabled: true
  host: "0.0.0.0"
  port: 8080
  ssl:
    enabled: false
    keystore: "keystore.jks"
    password: "changeme"
  
  authentication:
    type: "api-key"  # or "jwt"
    expiration: 86400000  # 24 hours
  
  rate-limiting:
    enabled: true
    requests-per-minute: 100
    
  cors:
    enabled: true
    origins: ["*"]
    
  endpoints:
    players: true
    server: true
    admin: true
```

---

**Note**: This API is part of the RVNKCore roadmap and is currently under development. Implementation details may change based on technical requirements and feedback.
