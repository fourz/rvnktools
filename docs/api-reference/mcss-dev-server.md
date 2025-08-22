# MC Server Soft (MCSS) API Reference

## Overview

MC Server Soft (MCSS) is a Windows-based Minecraft server management tool that provides a comprehensive REST API for remote server administration. This reference covers how to use the MCSS API for testing and troubleshooting RVNKTools and other Minecraft plugins.

**API Version**: v2 (API_2.4.0)  
**MCSS Version**: 13.7.0 and higher  
**Documentation**: https://docs.mcserversoft.com/apis/v2

## Getting Started

### Prerequisites

1. **MCSS Installation**: Install MC Server Soft on your development machine
2. **API Enablement**: Enable the Web API in MCSS settings (`File > Options`)
3. **API Key Creation**: Generate an API key in the 'Web Panel' section

### Authentication

All API calls require an API key header:

```http
GET /api/v2/servers HTTP/1.1
Host: localhost:25564
apiKey: YOUR_API_KEY
Content-Type: application/json
```

## Core Endpoints for Plugin Development

### Server Management

#### Get Server List
```http
GET /api/v2/servers
```

Returns all servers with their status, configuration, and metadata.

**Response Example**:
```json
[
  {
    "serverId": "69361e31-2ac8-43b5-9377-0cb5e40e75ac",
    "status": 0,
    "name": "Development Server",
    "description": "Plugin testing environment",
    "pathToFolder": "C:\\servers\\dev\\",
    "folderName": "dev",
    "type": "Paper",
    "javaAllocatedMemory": 2048,
    "javaStartupLine": "java -Xms1G -Xmx2G -jar paper.jar nogui"
  }
]
```

#### Get Server Details
```http
GET /api/v2/servers/{serverId}
```

Get detailed information about a specific server.

### Console Access and Command Execution

#### Get Server Console Output
```http
GET /api/v2/servers/{serverId}/console?AmountOfLines=50&Reversed=false
```

**Query Parameters**:
- `AmountOfLines`: Number of lines to retrieve (-1 for all)
- `takeFromBeginning`: Start from oldest lines (boolean)
- `Reversed`: Flip line order (boolean)

**Response Example**:
```json
[
  "[21:24:12] [Server thread/INFO]: [RVNKTools] RVNKTools v1.2.0-alpha enabling...",
  "[21:24:12] [Server thread/INFO]: [RVNKTools] Database connected successfully",
  "[21:24:12] [Server thread/INFO]: [RVNKTools] Commands registered: 5",
  "[21:24:12] [Server thread/INFO]: [RVNKTools] RVNKTools enabled successfully"
]
```

#### Execute Server Command
```http
POST /api/v2/servers/{serverId}/execute/command
Content-Type: application/json

{
  "command": "rvnktools reload"
}
```

#### Execute Multiple Commands
```http
POST /api/v2/servers/{serverId}/execute/commands
Content-Type: application/json

{
  "commands": [
    "save-all",
    "rvnktools reload",
    "say Plugin reloaded"
  ]
}
```

### Server Actions

#### Server Power Management
```http
POST /api/v2/servers/{serverId}/execute/action
Content-Type: application/json

{
  "action": 1
}
```

**Action Values**:
- `0`: InvalidOrEmpty
- `1`: Stop
- `2`: Start
- `3`: Kill
- `4`: Restart

### Server Statistics

#### Get Server Stats
```http
GET /api/v2/servers/{serverId}/stats
```

**Response Example**:
```json
{
  "latest": {
    "cpu": 15.2,
    "memoryUsed": 1024,
    "memoryLimit": 2048,
    "playersOnline": 3,
    "playerLimit": 20,
    "startDate": 1642780800000
  }
}
```

## Plugin Testing and Troubleshooting Use Cases

### 1. Plugin Reload Testing

```bash
# Test plugin reload sequence
curl -X POST http://localhost:25564/api/v2/servers/{serverId}/execute/commands \
  -H "apiKey: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "commands": [
      "plugman disable RVNKTools",
      "plugman enable RVNKTools",
      "rvnktools status"
    ]
  }'

# Check console for errors
curl -X GET "http://localhost:25564/api/v2/servers/{serverId}/console?AmountOfLines=20" \
  -H "apiKey: YOUR_API_KEY"
```

### 2. Configuration Testing

```bash
# Test configuration reload
curl -X POST http://localhost:25564/api/v2/servers/{serverId}/execute/command \
  -H "apiKey: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"command": "rvnktools reload"}'

# Check for configuration errors
curl -X GET "http://localhost:25564/api/v2/servers/{serverId}/console?AmountOfLines=10&Reversed=true" \
  -H "apiKey: YOUR_API_KEY"
```

### 3. Performance Monitoring

```bash
# Monitor server performance during plugin operations
curl -X GET http://localhost:25564/api/v2/servers/{serverId}/stats \
  -H "apiKey: YOUR_API_KEY"
```

### 4. Error Log Analysis

```bash
# Get recent console output for error analysis
curl -X GET "http://localhost:25564/api/v2/servers/{serverId}/console?AmountOfLines=100" \
  -H "apiKey: YOUR_API_KEY" | grep -i "error\|exception\|warn"
```

## Console Monitoring Patterns

### Real-time Console Monitoring

```bash
#!/bin/bash
# monitor-console.sh - Monitor console for specific patterns

SERVER_ID="your-server-id"
API_KEY="your-api-key"
MCSS_HOST="localhost:25564"

while true; do
  # Get last 5 lines
  RESPONSE=$(curl -s -X GET \
    "http://${MCSS_HOST}/api/v2/servers/${SERVER_ID}/console?AmountOfLines=5&Reversed=true" \
    -H "apiKey: ${API_KEY}")
  
  # Check for RVNKTools messages
  echo "$RESPONSE" | jq -r '.[]' | grep -i "rvnktools\|error\|exception"
  
  sleep 5
done
```

### Plugin Command Testing

```bash
# Test RVNKTools commands systematically
COMMANDS=(
  "rvnktools status"
  "rvnktools version"
  "rvnktools reload"
  "announce list"
  "link list"
)

for cmd in "${COMMANDS[@]}"; do
  echo "Testing: $cmd"
  curl -X POST http://localhost:25564/api/v2/servers/${SERVER_ID}/execute/command \
    -H "apiKey: ${API_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"command\": \"$cmd\"}"
  
  sleep 2
  
  # Check console for response
  curl -X GET "http://localhost:25564/api/v2/servers/${SERVER_ID}/console?AmountOfLines=3" \
    -H "apiKey: ${API_KEY}"
  
  echo "---"
done
```

## Permissions

The following permissions are relevant for plugin development:

- **viewConsole**: Required to read console output
- **useConsole**: Required to execute commands
- **useServerActions**: Required for server start/stop/restart
- **viewStats**: Required to monitor server performance

## PowerShell Integration

### Example PowerShell Script

```powershell
# Test-RVNKToolsAPI.ps1
param(
    [string]$ApiKey,
    [string]$ServerId,
    [string]$McssHost = "localhost:25564"
)

function Invoke-McssApi {
    param($Endpoint, $Method = "GET", $Body = $null)
    
    $headers = @{
        'apiKey' = $ApiKey
        'Content-Type' = 'application/json'
    }
    
    $uri = "http://$McssHost/api/v2$Endpoint"
    
    try {
        if ($Body) {
            Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body ($Body | ConvertTo-Json)
        } else {
            Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers
        }
    } catch {
        Write-Error "API call failed: $($_.Exception.Message)"
    }
}

# Test plugin reload
Write-Host "Testing RVNKTools reload..." -ForegroundColor Yellow
Invoke-McssApi -Endpoint "/servers/$ServerId/execute/command" -Method "POST" -Body @{
    command = "rvnktools reload"
}

# Check console for results
Start-Sleep -Seconds 2
Write-Host "Getting console output..." -ForegroundColor Yellow
$console = Invoke-McssApi -Endpoint "/servers/$ServerId/console?AmountOfLines=10"
$console | Where-Object { $_ -like "*RVNKTools*" } | ForEach-Object {
    Write-Host $_ -ForegroundColor Green
}

# Get server stats
Write-Host "Server statistics:" -ForegroundColor Yellow
$stats = Invoke-McssApi -Endpoint "/servers/$ServerId/stats"
Write-Host "Memory: $($stats.latest.memoryUsed)MB / $($stats.latest.memoryLimit)MB"
Write-Host "Players: $($stats.latest.playersOnline) / $($stats.latest.playerLimit)"
```

## Integration with RVNKTools Development

### Development Workflow

1. **Plugin Build**: Use VS Code tasks to build plugin
2. **Deploy**: Copy plugin to server directory
3. **Reload**: Use MCSS API to reload plugin via `plugman` or restart server
4. **Test**: Execute plugin commands via API
5. **Monitor**: Check console output for errors or success messages
6. **Debug**: Analyze console logs and server performance

### Automated Testing Pipeline

```yaml
# Example GitHub Actions workflow snippet
- name: Test Plugin with MCSS API
  run: |
    # Start MCSS server
    # Deploy plugin
    
    # Test plugin functionality
    curl -X POST http://localhost:25564/api/v2/servers/${{ env.SERVER_ID }}/execute/command \
      -H "apiKey: ${{ secrets.MCSS_API_KEY }}" \
      -H "Content-Type: application/json" \
      -d '{"command": "rvnktools status"}'
    
    # Verify no errors in console
    CONSOLE_OUTPUT=$(curl -s -X GET \
      "http://localhost:25564/api/v2/servers/${{ env.SERVER_ID }}/console?AmountOfLines=20" \
      -H "apiKey: ${{ secrets.MCSS_API_KEY }}")
    
    if echo "$CONSOLE_OUTPUT" | grep -i "error\|exception"; then
      exit 1
    fi
```

## Best Practices

### Error Handling

- Always check HTTP status codes
- Parse JSON responses for error information
- Monitor console output after API calls
- Use appropriate timeouts for long-running operations

### Performance Considerations

- Limit console output requests to necessary lines
- Use server stats endpoint to monitor resource usage
- Implement rate limiting in your scripts
- Cache server information when possible

### Security

- Keep API keys secure and rotate regularly
- Use HTTPS in production environments
- Implement proper permission scoping
- Monitor API access logs

## Common Error Codes

- **401 Unauthorized**: Invalid or missing API key
- **403 Forbidden**: Insufficient permissions
- **404 Not Found**: Server or resource not found
- **400 Bad Request**: Invalid request format or parameters

## Troubleshooting

### Console Output Issues

If console output appears incomplete:
1. Check `AmountOfLines` parameter
2. Verify server is running and accessible
3. Ensure proper permissions (`viewConsole`)
4. Check for console encoding issues

### Command Execution Failures

If commands fail to execute:
1. Verify `useConsole` permission
2. Check command syntax
3. Ensure server is in running state
4. Monitor console for command feedback

This MCSS API reference provides comprehensive tools for testing, monitoring, and troubleshooting RVNKTools and other Minecraft plugins in a development environment.
