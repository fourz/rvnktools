# RVNK VS Code Command Palette and Development Tools

## Development Workflow

Use VS Code tasks for development. Most tasks are **granular** for precise control, except for the complete deployment sequence:

### Complete Deployment Sequence

- **Build & Deploy**: Complete automated sequence - Build Plugin → Copy to Server → Restart Server → Wait for startup validation

### Granular Development Tasks

- **Build Plugin**: `mvn clean package` (builds the plugin JAR only)
- **Copy to Server**: Copy JAR to dev server (requires build first)
- **Restart Server**: Full server restart on dev server (independent action)
- **Reload Server**: Plugin reload without full restart (faster alternative)

**Usage Pattern**: Use granular tasks for targeted development workflows, use "Build & Deploy" for complete code-to-server deployment with validation.

### Server Query System Integration

The project includes a comprehensive MCSS API-based query system for seamless server interaction during development. Use these capabilities for debugging, monitoring, and development workflow optimization:

#### VS Code Query Tasks (Available via Command Palette)

**Server Query Tasks:**

- **Query Console - Recent**: Get last 50 console lines with color-coded formatting
- **Query Console - Errors Only**: Filter only ERROR/WARN messages from last 50 lines
- **Query Console - Plugin Messages**: Show only RVNKTools-related log entries from last 100 lines  
- **Query Console - Extended**: Get last 500 console lines for comprehensive debugging context
- **Query Server Status**: Get server running state, name, type, and memory info
- **Query Server Statistics**: Get real-time CPU, memory usage, player count, uptime
- **Query Server Info**: Get detailed server configuration and setup information
- **Send Server Command**: Interactive command execution with custom input prompt
- **RVNKTools Debug**: Execute `rvnktools debug` command for comprehensive plugin status

**Build and Deployment Tasks (Granular Control):**

- **Build & Deploy**: Complete automated sequence (Build → Copy → Restart → Validation)
- **Build Plugin**: Compile and package plugin JAR using Maven
- **Copy to Server**: Copy built JAR to development server plugins folder
- **ServerCleanup**: Remove existing plugin files and folders from server as needed
- **Restart Server**: Full server restart via MCSS API
- **Reload Server**: Plugin reload without full restart (faster alternative)

**Usage Guidelines**: 

- Use **Build & Deploy** for complete development cycle with automatic validation
- Use individual tasks for targeted operations (build-only, copy-only, restart-only)
- Tasks execute independently for precise workflow control in debugging scenarios

**Database Management Tasks:**

- **Clean MySQL Database - DEV**: Interactive database cleanup with confirmation prompt
- **List MySQL Tables - DEV**: List all tables in development database without modifications
- **Force Clean MySQL Database - DEV**: Database cleanup without confirmation (use with caution)
- **Clean SQLite Database - DEV**: Remove local SQLite database files
- **List SQLite Files - DEV**: List SQLite database files without removal

#### PowerShell Query Script (`query-server-DEV.ps1`)

For advanced queries and copilot agentic usage, execute the PowerShell script directly:

```powershell
# Console queries with flexible parameters (1-500 lines or "all")
.\query-server-DEV.ps1 console [1-500|all] [-ErrorsOnly] [-PluginOnly] [-FilterText "text"] [-Reversed] [-NoTimestamp] [-Raw]

# Server information queries  
.\query-server-DEV.ps1 status    # Server state and basic info
.\query-server-DEV.ps1 stats     # Performance metrics (CPU, memory, players, uptime)
.\query-server-DEV.ps1 info      # Complete server configuration details

# Server command execution
.\query-server-DEV.ps1 command "rvnktools debug"    # Execute server commands remotely
.\query-server-DEV.ps1 command "plugin list"        # List installed plugins
```

#### MySQL Database Management Script (`clean-mysqldb-DEV.ps1`)

For development database management and schema reset scenarios:

```powershell
# List all tables in development database
.\clean-mysqldb-DEV.ps1 -ListOnly

# Interactive cleanup with confirmation prompt
.\clean-mysqldb-DEV.ps1

# Force cleanup without confirmation (use with caution)
.\clean-mysqldb-DEV.ps1 -Force

# Password is automatically retrieved from project.json configuration and environment variable is set for future use
```

#### Query System Features for Copilot Agents

- **Flexible Line Counts**: Support for 1-500 lines or "all" for complete history
- **Advanced Filtering**: 
  - `-ErrorsOnly`: Show only ERROR and WARN level messages
  - `-PluginOnly`: Show only plugin-related messages  
  - `-FilterText "keyword"`: Filter logs containing specific text
  - `-Reversed`: Show newest entries first
  - `-NoTimestamp`: Remove timestamp formatting for parsing
  - `-Raw`: Unformatted output for programmatic processing
- **Color-Coded Output**: Green (INFO), Yellow (WARN), Red (ERROR), Gray (DEBUG)
- **Real-Time Access**: 1-2 second response time for all query types
- **Zero Context Switching**: Query server without leaving VS Code environment
- **Server Command Execution**: Execute commands remotely via MCSS API
- **MySQL Database Management**: Complete database cleanup and table listing capabilities
- **Extended Console Access**: Up to 500 lines for comprehensive debugging context

#### Usage Examples for Development Workflow

```powershell
# Post-deployment verification
.\query-server-DEV.ps1 console 50 -PluginOnly

# Error debugging after code changes  
.\query-server-DEV.ps1 console 100 -ErrorsOnly -FilterText "database"

# Performance monitoring during testing
.\query-server-DEV.ps1 stats

# Complete plugin startup sequence analysis
.\query-server-DEV.ps1 console all -FilterText "RVNKTools" -NoTimestamp

# Database management examples
.\clean-mysqldb-DEV.ps1 -ListOnly                    # List all tables
.\clean-mysqldb-DEV.ps1                              # Interactive cleanup
.\clean-mysqldb-DEV.ps1 -Force                       # Force cleanup without prompt

# Server command execution
.\query-server-DEV.ps1 command "rvnktools reload"    # Reload plugin configuration
.\query-server-DEV.ps1 command "plugin list"         # List all installed plugins
```

**Location**: All query scripts located in `.vscode/` directory
**Reference Documentation**: `.vscode/MCSS-Query-Tasks-Instructions.md` for complete usage guide

### Testing and Troubleshooting Tools

#### MC Server Soft (MCSS) API Integration

For comprehensive testing and debugging, utilize the MCSS API for real-time server interaction:

- **Console Monitoring**: Use MCSS API to read server console output in real-time
- **Command Execution**: Execute plugin commands remotely via REST API
- **Performance Monitoring**: Track server performance during plugin operations
- **Error Analysis**: Programmatically search console logs for errors and exceptions

**Reference Documentation**: `docs/api-reference/mcss-dev-server.md`

#### RVNKCore API Testing Infrastructure

For comprehensive REST API testing and validation of RVNKCore endpoints, utilize the PowerShell-based testing framework:

**Execute RVNKCore API Call Task:**

- **Task Name**: "Execute RVNKCore API Call" (VS Code Command Palette → Tasks: Run Task)
- **Purpose**: Interactive API testing with custom parameters for debugging and validation
- **Usage**: Provides guided parameter input for testing specific API endpoints

**PowerShell API Test Script:**

- **Location**: `tests/scripts/posh/Test-RestRVNKCoreAPI.ps1`
- **Configuration**: Auto-loads from `.vscode/project.json` for URLs and API keys
- **Protocols**: Tests both HTTP (8080) and HTTPS (8081) endpoints

**API Test Categories:**

```powershell
# Test all RVNKCore APIs (test for https by default)
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests all

# Test specific API categories
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests player        # Player management APIs
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests playerworld   # Player-world correlation APIs
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests world         # World management APIs  
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests announcement  # Announcement system APIs

# Advanced testing options 
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests world -Detail          # HTTPS only with detailed output
.\Test-RestRVNKCoreAPI.ps1 -HttpsOnly -Tests all -IgnoreSSLErrors -Detail      # Full test suite with SSL bypass
```

**Key API Endpoints Tested:**

- **Player APIs**: Player retrieval, search, location updates, group management
- **World APIs**: World metadata, statistics, environment filtering, player correlation  
- **Player-World APIs**: World visit tracking, location history, playtime statistics
- **Announcement APIs**: CRUD operations, bulk management, filtering, metrics

**Testing Features:**

- **Comprehensive Coverage**: Tests 30+ API endpoints across 4 major categories
- **Dual Protocol Support**: Validates both HTTP and HTTPS implementations
- **Detailed Analysis**: Request/response logging with `-Detail` flag for debugging
- **Error Handling**: Validates proper HTTP status codes and error responses
- **Performance Metrics**: Response time analysis and success/failure reporting
- **SSL Configuration**: Self-signed certificate support for development environments

**Reference Documentation**: `.vscode/tasks-rvnkcore-api.md` for comprehensive usage guide and debugging workflows
