# RVNKCore API Testing Tasks for VS Code

This document describes the available VS Code tasks for testing the RVNKCore REST API and their usage within the development workflow.

## Overview

The RVNKCore API testing infrastructure provides comprehensive PowerShell-based testing for all RVNKCore REST API endpoints, including Player, World, Player-World correlation, and Announcement APIs. This system integrates with VS Code tasks for seamless debugging and development workflow.

## Available Tasks

### Execute RVNKCore API Call

**Task Name**: "Execute RVNKCore API Call"
**Location**: VS Code Command Palette → Tasks: Run Task → "Execute RVNKCore API Call"
**Purpose**: Interactive API testing with custom parameters for debugging and validation

**Usage Example**:
- Open Command Palette (`Ctrl+Shift+P`)
- Type "Tasks: Run Task"
- Select "Execute RVNKCore API Call"
- Provide custom test parameters when prompted

### PowerShell Script Direct Usage

**Script Location**: `tests/scripts/posh/Test-RestRVNKCoreAPI.ps1`
**Purpose**: Comprehensive API testing suite with multiple test categories and detailed analysis

## API Test Categories

### 1. Player API Tests
**Purpose**: Test player data retrieval, search, and management endpoints
**Usage**: `.\Test-RestRVNKCoreAPI.ps1 -Tests player`

**Endpoints Tested**:
- `/api/v1/players` - Get all players
- `/api/v1/players/online` - Get online players
- `/api/v1/players/{uuid}` - Get player by UUID
- `/api/v1/player/name/{name}` - Get player by name
- `/api/v1/player/name/{name}/history` - Get player name history
- `/api/v1/players/group/{group}` - Get players by group
- `/api/v1/players/search` - Search players
- `/api/v1/players/count` - Get player count
- PUT operations for player location and groups

### 2. Player-World API Tests
**Purpose**: Test player world correlation and location tracking
**Usage**: `.\Test-RestRVNKCoreAPI.ps1 -Tests playerworld`

**Endpoints Tested**:
- `/api/v1/players/{uuid}/worlds` - Get player world data
- `/api/v1/players/{uuid}/worlds/{world}` - Get player data for specific world
- `/api/v1/players/{uuid}/worlds/{world}/location` - Get player last known location
- `/api/v1/players/{uuid}/worlds/visited` - Get player visited worlds
- `/api/v1/players/{uuid}/worlds/stats` - Get player world statistics

### 3. World API Tests
**Purpose**: Test world metadata, statistics, and management endpoints
**Usage**: `.\Test-RestRVNKCoreAPI.ps1 -Tests world`

**Endpoints Tested**:
- `/api/v1/worlds` - Get all worlds
- `/api/v1/worlds/active` - Get active worlds
- `/api/v1/worlds/with-players` - Get worlds with players
- `/api/v1/worlds/{worldName}` - Get world by name
- `/api/v1/worlds/environment/{environment}` - Get worlds by environment
- `/api/v1/worlds/player/{playerUuid}` - Get worlds for specific player
- `/api/v1/worlds/correlation/{playerUuid}` - Get world-player correlation
- `/api/v1/worlds/statistics` - Get world statistics
- `/api/v1/worlds/recent` - Get recently accessed worlds

### 4. Announcement API Tests
**Purpose**: Test announcement creation, management, and bulk operations
**Usage**: `.\Test-RestRVNKCoreAPI.ps1 -Tests announcement`

**Endpoints Tested**:
- `/api/v1/announcements` - CRUD operations for announcements
- `/api/v1/announcements/{id}` - Individual announcement management
- `/api/v1/announcements/type/{type}` - Filter by announcement type
- `/api/v1/announcements/world/{world}` - Filter by world
- `/api/v1/announcements/group/{group}` - Filter by group
- `/api/v1/announcements/search` - Advanced search
- `/api/v1/announcements/bulk` - Bulk operations
- `/api/v1/announcements/count` - Get announcement count
- `/api/v1/announcements/metrics` - Get announcement metrics

## Usage Examples

### Basic Testing Commands

```powershell
# Test all APIs (both HTTP and HTTPS)
.\Test-RestRVNKCoreAPI.ps1 -Tests all

# Test only World API with HTTPS
.\Test-RestRVNKCoreAPI.ps1 -Tests world -HttpsOnly

# Test Player World API with detailed output
.\Test-RestRVNKCoreAPI.ps1 -Tests playerworld -HttpOnly -Detail

# Test announcements only with detailed debugging
.\Test-RestRVNKCoreAPI.ps1 -Tests announcement -HttpOnly -Detail
```

### Advanced Testing Options

```powershell
# Custom API endpoints and keys
.\Test-RestRVNKCoreAPI.ps1 -HttpUrl "http://localhost:8080" -ApiKey "custom-key" -Tests world

# Ignore SSL certificate errors for testing
.\Test-RestRVNKCoreAPI.ps1 -Tests all -IgnoreSSLErrors -HttpsOnly

# Detailed analysis with comprehensive output
.\Test-RestRVNKCoreAPI.ps1 -Tests all -Detail -IgnoreSSLErrors
```

### Manual API Testing Examples

```powershell
# HTTP GET request for player data
Invoke-WebRequest http://localhost:8080/api/v1/player/name/wizardofire -Headers @{"X-API-Key"="your-api-key"}

# HTTPS GET request for world data
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest https://localhost:8081/api/v1/worlds -Headers @{"X-API-Key"="your-api-key"} -SkipCertificateCheck
```

## Configuration Management

### Project.json Configuration

The test script automatically loads configuration from `.vscode/project.json`:

```json
{
  "RVNKCoreAPI": {
    "httpUrl": "http://localhost:8080",
    "httpsUrl": "https://localhost:8081",
    "apiKey": "your-secure-api-key"
  }
}
```

### Environment Variables

Alternative configuration through environment variables:
- `RVNKCORE_HTTP_URL`
- `RVNKCORE_HTTPS_URL`
- `RVNKCORE_API_KEY`

## Debugging and Development Workflow

### 1. Development Cycle Integration

```powershell
# Build and deploy plugin
# Then test API functionality
.\Test-RestRVNKCoreAPI.ps1 -Tests world -HttpOnly -Detail

# Check specific endpoint after code changes
.\Test-RestRVNKCoreAPI.ps1 -Tests playerworld -HttpsOnly
```

### 2. Error Analysis

**Detailed Output Mode**: Use `-Detail` flag for comprehensive request/response logging:
- Request URI, method, headers, and body
- Response data with formatted JSON
- Error details with stack traces
- Performance and timing information

### 3. SSL Certificate Handling

For development servers with self-signed certificates:
```powershell
.\Test-RestRVNKCoreAPI.ps1 -Tests all -IgnoreSSLErrors -HttpsOnly
```

### 4. CI/CD Integration

**Exit Codes**:
- `0`: All tests passed
- `1`: One or more tests failed

**Example CI Usage**:
```powershell
.\Test-RestRVNKCoreAPI.ps1 -Tests all -HttpOnly
if ($LASTEXITCODE -ne 0) { 
    Write-Error "API tests failed"
    exit 1 
}
```

## Test Data Configuration

### Player Test Data
- **Test Player UUID**: `94c37976-5134-40b0-9e03-722ae6664fea`
- **Test Player Name**: `wizardofire`
- **Test Group**: `default`
- **Search Query**: `wizard`

### World Test Data
- **Primary Test World**: `world`
- **Alternative World**: `world_nether`
- **Test Environment**: `NORMAL`
- **World Query Limit**: `5`

### Announcement Test Data
- **Test Content**: `"Welcome to our test server! This is a test announcement."`
- **Test Type**: `BROADCAST`
- **Test World**: `world`
- **Test Tags**: `["test", "api", "welcome"]`

## Output Analysis

### Test Result Format

```
[PASS] [HTTP] Get All Worlds : Retrieved 5 worlds successfully
[FAIL] [HTTPS] Get Player by Name : Failed to retrieve player by name
```

### Summary Report

```
HTTP Tests - Passed: 15, Failed: 0
HTTPS Tests - Passed: 14, Failed: 1
Total Passed: 29
Total Failed: 1
```

### Detailed Error Reporting

When tests fail, detailed error information includes:
- Test name and protocol (HTTP/HTTPS)
- Error message and exception details
- Request information (URI, method, headers)
- Response data (when available)

## Integration with VS Code Copilot

### Copilot Usage Context

**For API Debugging**:
1. Use "Execute RVNKCore API Call" task for interactive testing
2. Run specific test categories to validate changes
3. Use `-Detail` flag for comprehensive debugging output
4. Check console logs during testing for server-side analysis

**For Development Workflow**:
1. Build plugin with "Build Plugin" task
2. Deploy with "Copy to Server" task
3. Test API functionality with RVNKCore API tests
4. Check server logs with console query tasks

### Common Debugging Scenarios

**World Registration Issues**:
```powershell
.\Test-RestRVNKCoreAPI.ps1 -Tests world -HttpOnly -Detail
```

**Player Tracking Problems**:
```powershell
.\Test-RestRVNKCoreAPI.ps1 -Tests playerworld -HttpsOnly -Detail
```

**Announcement System Testing**:
```powershell
.\Test-RestRVNKCoreAPI.ps1 -Tests announcement -HttpOnly -Detail
```

## Security Considerations

### API Key Management
- Store API keys securely in project.json
- Never commit API keys to version control
- Use different keys for development/production
- Rotate keys regularly

### SSL/TLS Configuration
- Use HTTPS in production environments
- Validate SSL certificates in production
- Use `-IgnoreSSLErrors` only for development
- Configure proper certificate chains

## Performance Testing

### Load Testing Considerations
- Use bulk operations for performance testing
- Monitor response times with `-Detail` output
- Test under concurrent load scenarios
- Validate database performance during bulk operations

### Metrics Collection
- Track API response times
- Monitor success/failure rates
- Analyze endpoint usage patterns
- Validate resource utilization

This comprehensive testing framework ensures robust API functionality and supports efficient debugging workflows for RVNKCore development.
