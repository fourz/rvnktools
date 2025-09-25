# RVNK Plugin Ecosystem Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the RVNK plugin ecosystem, including RVNKTools, RVNKCore, RVNKLore, RVNKQuests, and any other RVNK plugins.

## Metamake Project Management Integration

*See detailed instructions: [Metamake Project Management](copilot-instructions.metamake.md)*

When the user explicitly requests metamake functionality using phrases like "use metamake to..." or "with metamake", activate the integrated project management capabilities.

## Core Directives

- **Use the CommandManager framework for all commands. Do not create standalone command executors.**
- **Follow SOLID principles when adding new features or refactoring existing code.**
- **Ensure proper resource cleanup in all managers and services.**
- **Implement RVNKCore patterns when working on core functionality extraction.**
- **Use service interfaces for all business logic across all RVNK plugins**
- **Implement services through the ServiceRegistry pattern for dependency injection**
- **Use the Repository pattern for all data access across the ecosystem**
- **Use DTOs for data transfer between layers and across plugin boundaries**
- **Create clean, versioned API interfaces for all plugin interactions**
- **Use RVNKCore's event system for cross-plugin communication**

## Asynchronous Programming Guidelines

### When to Use Async (CompletableFuture)
- Database operations (SELECT, INSERT, UPDATE, DELETE)
- External API calls and web requests
- File I/O operations (reading/writing config files)
- Long-running computations (>50ms)

### When NOT to Use Async
- In-memory operations (cache lookups, Map/List operations)
- Simple validation (null checks, format validation)
- Configuration access (already-loaded values)
- Event handlers (already on appropriate threads)
- Command responses (users expect immediate feedback)

### Service Interface Pattern

**Naming Conventions:**
- **Do not use the `I` prefix for interfaces.** Use `PlayerService` instead of `IPlayerService`.
- **Service interfaces should use descriptive names ending with `Service`, `Repository`, or `Manager` as appropriate.** 
  - Examples: `PlayerService`, `AnnouncementService`, `WorldService`, `EconomyService`.
- **Implementation classes should use a clear suffix such as `Default`, `Sql`, or another specific descriptor.**
  - Examples: `DefaultPlayerService`, `SqlPlayerService`, `CorePlayerService`.

*See examples: [Service Interface Pattern](copilot-instructions.examples.md#service-interface-pattern)*

### Command Framework Integration

- Validate synchronously (permissions, args, format)
- Use async for database/API operations
- Provide immediate feedback to users
- Handle async results with proper error messages

**Important**: Command responses must be immediate to provide user feedback, but long-running database/API operations within commands should be wrapped in `CompletableFuture` to avoid blocking the main thread.

*See examples: [Command Framework Integration](copilot-instructions.examples.md#command-framework-integration)*


When metamake is invoked, reference the `metamake/prompts/` directory for specialized prompts and use the `metamake/template/` directory for project structure templates.

### Performance Rules
- Don't async operations that take <10ms
- Batch operations instead of individual async calls
- Consider thread pool limits
- **For caching strategies**: See [Performance and Monitoring](#performance-and-monitoring) section

## Plugin Architecture

All RVNK plugins should follow a consistent architecture:

*See examples: [Standard Plugin Structure](copilot-instructions.examples.md#standard-plugin-structure)*

### Dependencies and Standards

- **Declare RVNKCore as a dependency** in plugin.yml for all RVNK plugins
- **Use ServiceRegistry** to obtain dependencies rather than direct instantiation
- **Implement proper service lifecycle** with initialization and cleanup phases
- **Handle missing dependencies gracefully** with appropriate fallback behavior
- **For configuration management**: See [Configuration Standards](#configuration-standards) section
- **For error handling**: See [Error Handling and Resilience](#error-handling-and-resilience) section
- **For caching strategies**: See [Performance and Monitoring](#performance-and-monitoring) section

*See examples: [Plugin Dependencies Configuration](copilot-instructions.examples.md#plugin-dependencies-configuration)*

### Error Handling and Resilience

- **Implement circuit breaker patterns** for external service calls
- **Provide meaningful error messages** with actionable information for administrators
- **Log errors with appropriate context** including player IDs, operation details, and stack traces
- **Handle missing dependencies gracefully** 
- **Implement proper exception chaining** to preserve stack trace information
- **Use custom exception types** for domain-specific error conditions

*See examples: [Error Handling and Resilience](copilot-instructions.examples.md#error-handling-and-resilience)*

### Performance and Monitoring

- **Use DebugLogger as needed** for debugging only
- **Implement caching strategies** for frequently accessed data:
  - Use caching to reduce database calls
  - Implement proper cache invalidation strategies
  - Consider memory usage vs. performance trade-offs
  - Use connection pooling for external resources
- **Monitor async operation completion** and log performance metrics
- **Use connection pooling** through RVNKCore for database operations

*See examples: [Performance and Monitoring](copilot-instructions.examples.md#performance-and-monitoring)*

### Configuration Standards

- **Use YAML for all configuration files** with consistent naming conventions
- **Implement configuration validation** with clear error messages
- **Support configuration reloading** without server restart where possible
- **Document all configuration options** with examples and default values

*See examples: [Configuration Standards](copilot-instructions.examples.md#configuration-standards)*

### Testing Requirements

- **Write integration tests** for all async operations
- **Mock external dependencies** using RVNKCore's testing framework
- **Test cross-plugin compatibility** when implementing shared features
- **Validate performance** under concurrent load for database operations

### Documentation Standards

- **Document all public APIs** with complete JavaDoc including since tags
- **Provide configuration examples** in README files
- **Create integration guides** for server administrators
- **Maintain changelog** with version compatibility information

This comprehensive set of standards ensures consistency, performance, and maintainability across the entire RVNK plugin ecosystem while emphasizing asynchronous operations and proper data layer abstraction.

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Explain the class's purpose and responsibility in the system
- Note important design patterns or architectural decisions
- Focus on "why" over implementation details

*See examples: [Class Documentation](copilot-instructions.examples.md#class-documentation)*

#### Method Documentation

- Describe purpose and behavior, not implementation
- Document parameters and return values
- Note exceptions that may be thrown
- Include examples for complex methods

*See examples: [Method Documentation](copilot-instructions.examples.md#method-documentation)*

### Code Comments
- Comment on "why" not "what" - explain reasoning behind code
- Place comments above the code they describe
- Keep comments concise and meaningful
- Explain complex logic, business rules, or non-obvious decisions

## Message Formatting Standards

*See detailed instructions: [Message Formatting Standards](copilot-instructions.formatting.md)*

## Logging and Debug Standards

*See detailed instructions: [Logging and Debug Standards](copilot-instructions.logging.md)*

### Console Output Guidelines

- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- **Do not use ChatFormat for logger output**
- For all command output to console (outside of logger), use `ChatFormat.stripColors()` to ensure clean output

## Command Framework Guidelines

*See detailed instructions: [Command Framework Guidelines](copilot-instructions.commands.md)*

## Resource Management

- Initialize resources in proper order during onEnable
- Clean up resources in reverse order during onDisable
- Use try-with-resources for closeable resources
- Cancel tasks and unregister listeners properly
- Implement shutdown methods in manager classes

## Development Workflow

### Multi-Server Development Environment

**RVNK Dev (Local)**: Local development server using MCSS API
- **Status**: ✅ **OPERATIONAL** - Maintains all existing functionality and shortcuts
- **Usage**: Primary development, testing, debugging
- **Key Bindings**: `Ctrl+Shift+-` (build & copy), `Ctrl+Shift+/` (restart)

**RVNK Test (SparkedHost)**: Remote test server using SparkedHost MCP tools
- **Status**: ✅ **OPERATIONAL** - New integration for production-like testing
- **Usage**: Final testing, validation, staging deployments
- **Key Bindings**: `Ctrl+Shift++` (build, copy to test, restart via MCP)

### VS Code Tasks (Command Palette)

**Primary Development Tasks (RVNK Dev - Local):**
- **Build & Deploy**: Complete automated sequence (Build → Copy → Restart → Validation)
- **Build Plugin**: Maven compile and package (`mvn clean package`)
- **Copy to Server**: Copy JAR to development server
- **Restart Server**: Full server restart via MCSS API
- **Reload Server**: Plugin reload without full restart (faster alternative)

**New Test Server Tasks (RVNK Test - SparkedHost):**
- **Build & Deploy to Test**: Complete sequence using SparkedHost MCP tools
- **Copy to Test Server**: Deploy JAR to RVNK Test server via SparkedHost MCP
- **Restart Test Server**: Remote server restart using SparkedHost MCP server management

**Server Query Tasks:**
- **Query Console - Recent**: Last 50 console lines with color-coded formatting
- **Query Console - Errors Only**: Filter ERROR/WARN messages from recent logs
- **Query Console - Plugin Messages**: RVNKTools-specific log entries from last 100 lines
- **Query Console - Extended**: Last 500 lines for comprehensive debugging
- **Query Server Status**: Server state, name, type, and memory information
- **Query Server Statistics**: Real-time CPU, memory, player count, uptime metrics
- **Send Server Command**: Interactive command execution with custom input
- **RVNKTools Debug**: Execute `rvnktools debug` for comprehensive plugin status

**Database Management Tasks:**
- **Clean MySQL Database - DEV**: Interactive database cleanup with confirmation
- **List MySQL Tables - DEV**: List all tables without modifications
- **Clean SQLite Database - DEV**: Remove local SQLite database files

**Usage Guidelines:**
- **RVNK Dev (Local)**: Use **Build & Deploy** for complete development cycle with validation
- **RVNK Test (SparkedHost)**: Use **Build & Deploy to Test** for production-like testing
- Use granular tasks (Build Plugin, Copy to Server, etc.) for targeted operations
- Database cleanup tasks support both interactive and force modes

**Key Bindings for Development Workflow:**
- `Ctrl+Shift+-`: Build and copy to RVNK Dev (local development server)
- `Ctrl+Shift+/`: Restart RVNK Dev server via MCSS API
- `Ctrl+Shift++`: **NEW** - Build, copy to RVNK Test, restart via SparkedHost MCP tools

### SparkedHost MCP Server Operations

**⚠️ PRODUCTION SAFETY**: Never operate on Ravenkraft (production server). Only use RVNK Test server for development operations.

**Server Configuration:**
- **RVNK Test Server**: `serverId: "b2bc4d7e"` `serverName: "RVNK Test"` (✅ Safe for all operations)
- **Ravenkraft Server**: `serverId: "140324c4"` `serverName: "Ravenkraft"` (🔒 READ-ONLY - Status queries only)

**Quick Reference MCP Commands:**

**Server Management (RVNK Test Only):**
- **Restart**: `mcp_sparkedhost_restart-server` 
- **Console Monitoring**: Use `mcp_sparkedhost_send-console-command` + `mcp_sparkedhost_get-file-contents` (/logs/latest.log)
- **Send Command**: `mcp_sparkedhost_send-console-command`
- **Plugin List**: `mcp_sparkedhost_get-plugin-list`

**File Operations (Both Servers):**
- **List Files**: `mcp_sparkedhost_list-files` (optional directory parameter)
- **Get File**: `mcp_sparkedhost_get-file-contents` 

**Common File Paths:**
- `/logs/latest.log` - Current server log
- `/plugins/` - Plugin directory listing  
- `/server.properties` - Server configuration
- `/plugins/RVNKTools/config.yml` - Plugin configuration

**Console Monitoring Pattern:**
- **Console streaming is currently broken** - `mcp_sparkedhost_console-stream` fails with "Unknown error"
- **Working alternative**: Use `mcp_sparkedhost_send-console-command` followed by `mcp_sparkedhost_get-file-contents` on `/logs/latest.log`
- **Best practice**: Send command → wait 2-3 seconds → fetch log file to see output

#### Console Monitoring MCP Workflow

**Real-Time Console Monitoring (Workaround):**
```javascript
// Console streaming is currently broken, use this alternative:
// 1. Send a test command to generate log entry
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "say Monitoring console..."
})

// 2. Fetch latest log to see recent activity (wait 2-3 seconds)
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  filePath: "/logs/latest.log"  // Shows recent console output
})
```

**Console Command Execution:**
```javascript
// Execute server commands remotely
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "rvnktools debug"  // or any server command
})

// Common monitoring commands
mcp_sparkedhost_send-console-command({ command: "plugins" })
mcp_sparkedhost_send-console-command({ command: "tps" })
mcp_sparkedhost_send-console-command({ command: "list" })
mcp_sparkedhost_send-console-command({ command: "version" })
```

**Plugin Status Monitoring:**
```javascript
// Get installed plugin list
mcp_sparkedhost_get-plugin-list({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test"
})

// Check specific plugin files
mcp_sparkedhost_list-files({
  serverId: "b2bc4d7e", 
  directory: "/plugins"
})
```

#### Build & Upload Sequence MCP Workflow Reference

**Complete Development Deployment Pipeline:**

**Phase 1: Local Build**
```powershell
# Build plugin using Maven
mvn clean package -f toolkitplugin/pom.xml

# Verify JAR creation
Get-ChildItem -Path "toolkitplugin\target" -Filter "*.jar" | 
  Where-Object { $_.Name -notlike '*original*' -and $_.Name -notlike '*sources*' }
```

**Phase 2: Upload to Test Server**
```javascript
// Upload built JAR to test server
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  localFilePath: "c:\\tools\\rvnktools\\toolkitplugin\\target\\rvnktools-1.3.0-alpha.jar",
  filePath: "/plugins/RVNKTools.jar"
})

// Alternative: Upload with content (for config files)
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  content: "# Configuration content here",
  filePath: "/plugins/RVNKTools/config.yml"
})
```

**Phase 3: Server Restart & Validation**
```javascript
// Restart test server to apply changes
mcp_sparkedhost_restart-server({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test" 
})

// Monitor restart process (wait 10-15 seconds after restart)
// Console stream is broken, use log file monitoring instead:
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  filePath: "/logs/latest.log"  // Check for startup messages
})

// Validate plugin loaded successfully
mcp_sparkedhost_get-plugin-list({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test"
})

// Test plugin functionality
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  command: "rvnktools debug"
})
```

**Phase 4: Configuration Verification**
```javascript
// Verify configuration files
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  filePath: "/plugins/RVNKTools/config.yml"
})

// Check log files for errors
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  filePath: "/logs/latest.log"
})
```

**Error Handling & Troubleshooting:**
```javascript
// Check for upload errors
if (uploadResult.includes("ERROR")) {
  // Retry upload or check file permissions
  mcp_sparkedhost_list-files({ directory: "/plugins" })
}

// Monitor for plugin errors during startup  
// Console stream broken - use log file monitoring:
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  filePath: "/logs/latest.log"
})

// Check plugin dependencies
mcp_sparkedhost_send-console-command({ command: "version RVNKTools" })
```

**Batch Operations:**
```javascript
// Multiple file upload (configurations, data files)
const files = [
  { local: "config.yml", remote: "/plugins/RVNKTools/config.yml" },
  { local: "announcements.yml", remote: "/plugins/RVNKTools/announcements.yml" }
];

files.forEach(file => {
  mcp_sparkedhost_upload-file({
    serverId: "b2bc4d7e",
    localFilePath: file.local,
    filePath: file.remote
  });
});
```

### Integrated Development Workflow: Local + Test Server

**Dual-Server Development Strategy:**

**RVNK Dev (Local) - Primary Development:**
- **Purpose**: Rapid iteration, debugging, initial testing
- **Tools**: MCSS API, PowerShell scripts, VS Code tasks
- **Workflow**: Build → Copy → Restart → Validate (2-5 seconds)
- **Usage**: 80% of development time for quick iterations

**RVNK Test (SparkedHost) - Production Validation:**
- **Purpose**: Final testing, production-like environment validation  
- **Tools**: SparkedHost MCP server operations
- **Workflow**: Build → Upload → Restart → Monitor (30-60 seconds)
- **Usage**: 20% of development time for staging deployment

**Recommended Development Cycle:**
1. **Local Development**: Use RVNK Dev for rapid iteration (5-10 cycles)
2. **Test Server Validation**: Deploy to RVNK Test for final validation (1 cycle)
3. **Production Deployment**: Manual deployment to production server

### PowerShell Query Scripts

**Console Queries (`query-server-DEV.ps1`):**
```powershell
# Console queries (1-500 lines or "all")
.\query-server-DEV.ps1 console 50                    # Recent 50 lines
.\query-server-DEV.ps1 console 100 -ErrorsOnly       # Errors/warnings only
.\query-server-DEV.ps1 console 100 -FilterText "RVNKTools"  # Plugin-specific

# Server information
.\query-server-DEV.ps1 status    # Basic server info
.\query-server-DEV.ps1 stats     # Performance metrics
.\query-server-DEV.ps1 info      # Detailed configuration

# Remote command execution
.\query-server-DEV.ps1 command "rvnktools debug"     # Plugin status
.\query-server-DEV.ps1 command "plugin list"         # Installed plugins
```

**MCP Test Server Operations (Complementary to Local Dev):**
```javascript
// Test server console monitoring (mirrors local dev queries)
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  command: "rvnktools debug"  // Equivalent to PowerShell query
})

// Test server plugin validation
mcp_sparkedhost_get-plugin-list({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test"    // Equivalent to local plugin check
})

// Test server performance monitoring  
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  command: "tps"             // Server performance equivalent
})
```

**Database Management (`clean-mysqldb-DEV.ps1`):**
```powershell
.\clean-mysqldb-DEV.ps1 -ListOnly    # List tables only
.\clean-mysqldb-DEV.ps1              # Interactive cleanup
.\clean-mysqldb-DEV.ps1 -Force       # Force cleanup without prompt
```

**Advanced Query Features:**
- Flexible line counts (1-500 or "all"), advanced filtering (-ErrorsOnly, -FilterText)
- Color-coded output (Green=INFO, Yellow=WARN, Red=ERROR), real-time access (1-2s response)
- Zero context switching from VS Code, complete MySQL database management

*See comprehensive documentation: [VS Code Query Tasks & PowerShell Query Script](copilot-instructions.vscode-tasks.md)*

## Documentation and Reference Structure

### Primary Documentation Files

- **README.md**: Main project description, features overview, and architectural principles
- **ROADMAP.md**: Current implementation status, development priorities, and timelines
- **RVNKCore README.md**: `toolkitplugin/src/main/java/org/fourz/rvnkcore/README.md` - Core implementation status and architecture

### Reference Documentation (Parse As Needed)

The following documentation should be referenced only when relevant to specific prompts:

#### Core Architecture and Migration
- `docs/requirements/rvnkcore-outline.md` - Core architecture overview
- `docs/requirements/rvnkcore-implementation-status.md` - Detailed implementation status
- `docs/requirements/announcemanager-migration-requirements.md` - Announcement system migration plan
- `docs/implementation/announcement-architecture-evolution.md` - Architecture comparison guide

#### Data Schema and Database
- `docs/rvnkcore-mysql-implementation.md` - MySQL integration details
- `docs/rvnktools-datalayer-diagram.md` - Data layer architecture diagrams
- `docs/requirements/rvnkcore-database.md` - Database implementation requirements

#### API and Service Documentation
- `docs/requirements/rvnkcore-api.md` - API design specifications
- `docs/requirements/rvnkcore-service.md` - Service layer requirements
- `docs/api/rvnkcore-java.md` - Java API documentation
- `docs/api/rvnkcore-httprest.md` - REST API documentation

#### Configuration and Migration Guides
- `docs/configuration-migration-summary.md` - Configuration migration overview
- `docs/rvnkcore-configuration-migration.md` - Detailed configuration migration
- `docs/configuration-validation-summary.md` - Configuration validation guide

#### Implementation Plans and Testing
- `docs/plans/rvnkcore/` - Detailed implementation plans
- `docs/tests/` - Testing documentation and status
- `docs/requirements/rvnkcore-action-plan.md` - Action plan and priorities

### Documentation Usage Guidelines

- **README files contain project descriptions and current status**
- **ROADMAP files contain implementation status and timelines** 
- **Reference docs should be parsed only when specific technical details are needed**
- **Migration documentation contains comprehensive transition plans**
- **API documentation provides interface specifications and examples**

### Status Information Location

- **Implementation Status**: Use ROADMAP.md files for current progress and priorities
- **Project Description**: Use README.md files for feature overview and architecture
- **Technical Details**: Reference specific docs/ files only when needed for implementation
- **Migration Plans**: Use migration-specific documentation for transition requirements