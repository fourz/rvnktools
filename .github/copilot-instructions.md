# RVNK Plugin Ecosystem Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the RVNK plugin ecosystem, including RVNKTools, RVNKCore, RVNKLore, RVNKQuests, and any other RVNK plugins.

## Metamake Project Management Integration

When the user explicitly requests metamake functionality using phrases like "use metamake to..." or "with metamake", activate the integrated project management capabilities:

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

### Metamake Capabilities Available

- **Project Planning and Organization**: Structure complex features, refactoring tasks, and development phases
- **Document-Based Project Management**: Create structured project documentation with templates and validation checklists
- **Implementation Roadmaps**: Break down large architectural changes (like RVNKCore integration) into manageable phases
- **Quality Assurance Frameworks**: Generate validation checklists and testing procedures for plugin features
- **Cross-Plugin Architecture Planning**: Coordinate development across RVNK ecosystem plugins

### Metamake Project Structure

Metamake projects are organized in the `metamake/projects/` directory with this structure:

```text
metamake/projects/XX-project-name/
├── README.md              # Project overview and purpose
├── ROADMAP.md             # Implementation status and timelines
├── COPILOT-INSTRUCTIONS.md # Project-specific guidance
├── project-details.md     # Context, workflow, and objectives
├── features/              # Feature specifications
├── implementation/        # Step-by-step implementation guides
├── validation/           # Testing and quality assurance checklists
└── docs/                 # Supporting documentation
```

### RVNK-Specific Metamake Context

**Project Domain**: Minecraft Plugin Ecosystem Development
**Technology Stack**: Java/Maven, Spigot/Paper API, MySQL/SQLite, Jetty REST API, YAML Configuration
**Current Focus**: RVNKCore architectural refactor and plugin ecosystem consolidation

**Key Project Areas for Metamake Integration:**
- RVNKCore Phase 1/Phase 2 implementation planning
- Announcement system migration (YAML → Database)
- REST API framework expansion
- Cross-plugin service integration
- Database layer abstraction completion
- Web integration strategy implementation

**Documentation Integration:**
- Main documentation: `docs/` (requirements, implementation, API references)
- Project status: `README.md` and `ROADMAP.md` files
- Architecture guides: `docs/implementation/` and `docs/requirements/`
- API documentation: `docs/api/` for Java and REST endpoints

### Usage Examples

```text
"Use metamake to plan the Phase 2 RVNKCore implementation"
"With metamake, create a validation checklist for the announcement service migration"
"Use metamake to organize the cross-plugin REST API integration project"
"With metamake, structure the MySQL ConnectionProvider implementation project"
```

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

- **Use the RVNK exception hierarchy** for consistent error handling across all plugins
- **Implement circuit breaker patterns** for external service calls
- **Provide meaningful error messages** with actionable information for administrators
- **Log errors with appropriate context** including player IDs, operation details, and stack traces
- **Handle missing dependencies gracefully** with appropriate fallback behavior
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

## Legacy Support and Migration

### Backward Compatibility

- **Maintain compatibility** for at least 2 major versions
- **Provide deprecation warnings** with clear migration paths
- **Support legacy configuration formats** during transition periods
- **Test with existing server setups** before releasing breaking changes

### Data Migration

- **Implement automatic data migration** from legacy formats
- **Provide manual migration tools** for complex scenarios
- **Backup data** before performing migrations
- **Validate data integrity** after migration completion

*See examples: [Data Migration Service](copilot-instructions.examples.md#data-migration-service)*

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

### Player-Facing Messages

- Use `ChatFormat` for all player-facing command output, including messages, titles, and action bars.
- Use standardized message prefixes:

  - `&c▶` for usage instructions and command help
  - `&6⚙` for operations in progress
  - `&a✓` for success messages
  - `&c✖` for error messages
  - `&e⚠` for warnings
  - `&7␣␣␣` for additional information or tips (three spaces after)

## Logging

### LogManager Standard
- Use `LogManager` for all info, warning, and error logging in plugin code
- Declare as `private final LogManager logger;` and initialize with `LogManager.getInstance(plugin);`
- Use `logger.info()`, `logger.warning()`, `logger.error(message, exception)` for all logging
- Do not use `System.out.println()` or direct logger calls
- Reserve `Debug` class for debug-level or trace logging only

### Console and Debug Messages
- Use the designated logging system for all console output
- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- **Do not use ChatFormat for logger output**
- For all command output to console (outside of logger), use `ChatFormat.stripColors()` to ensure clean output
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

*See examples: [LogManager Usage](copilot-instructions.examples.md#logmanager-usage)*

## Command Framework Guidelines

Follow the CommandManager framework for all commands:

1. **Extend BaseCommand** (part of CommandManager framework) for new commands
2. **Register through CommandManager**: `commandManager.registerCommand(new MyCommand(plugin));`
3. **Use subcommands** where appropriate: `registerSubCommand("subcommand", new MySubCommand(plugin));`
4. **Implement tab completion** with `getMatchingSubCommands(sender, args[0])`

*See examples: [Command Framework Examples](copilot-instructions.examples.md#command-framework-examples)*

## Resource Management

- Initialize resources in proper order during onEnable
- Clean up resources in reverse order during onDisable
- Use try-with-resources for closeable resources
- Cancel tasks and unregister listeners properly
- Implement shutdown methods in manager classes

## Development Workflow

Use VS Code tasks for development:

- **Build Plugin**: `mvn clean package` (builds the plugin JAR)
- **Copy to Server**: Copy JAR to dev server
- **Restart Server**: Build and full server restart on dev server

### Server Query System Integration

The project includes a comprehensive MCSS API-based query system for seamless server interaction during development. Use these capabilities for debugging, monitoring, and development workflow optimization:

#### VS Code Query Tasks (Available via Command Palette)

**Server Query Tasks:**
- **Query Console - Recent**: Get last 25 console lines with color-coded formatting
- **Query Console - Errors Only**: Filter only ERROR/WARN messages from last 50 lines
- **Query Console - Plugin Messages**: Show only RVNKTools-related log entries from last 100 lines  
- **Query Console - Extended**: Get last 300 console lines for comprehensive debugging context
- **Query Server Status**: Get server running state, name, type, and memory info
- **Query Server Statistics**: Get real-time CPU, memory usage, player count, uptime
- **Query Server Info**: Get detailed server configuration and setup information
- **Send Server Command**: Interactive command execution with custom input prompt
- **RVNKTools Debug**: Execute `rvnktools debug` command for comprehensive plugin status

**Build and Deployment Tasks (Sequential Control):**
- **Build Plugin**: Compile and package plugin JAR using Maven
- **Copy to Server**: Copy built JAR to development server plugins folder
- **ServerCleanup**: Remove existing plugin files and folders from server as needed
- **Restart Server**: Full server restart via MCSS API
- **Clean&Restart Server**: Combined folder cleanup and restart sequence
Note: Tasks run independently for granular control in agentic debugging workflows. Call sequentially as needed.

**Database Management Tasks:**
- **Clean MySQL Database - DEV**: Interactive database cleanup with confirmation prompt
- **List MySQL Tables - DEV**: List all tables in development database without modifications
- **Force Clean MySQL Database - DEV**: Database cleanup without confirmation (use with caution)
- **Set MySQL Password**: Configure secure environment variable for database access
- **Verify MySQL Password**: Check if database credentials are properly configured
- **Clean SQLite Database - DEV**: Remove local SQLite database files
- **List SQLite Files - DEV**: List SQLite database files without removal

#### PowerShell Query Script (`query-server-DEV.ps1`)

For advanced queries and copilot agentic usage, execute the PowerShell script directly:

```powershell
# Console queries with flexible parameters (1-300 lines or "all")
.\query-server-DEV.ps1 console [1-300|all] [-ErrorsOnly] [-PluginOnly] [-FilterText "text"] [-Reversed] [-NoTimestamp] [-Raw]

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

# Environment variable for password (optional)
$env:RVNK_MYSQL_PASSWORD = "your_password"
```

#### Query System Features for Copilot Agents

- **Flexible Line Counts**: Support for 1-300 lines or "all" for complete history
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
- **Extended Console Access**: Up to 300 lines for comprehensive debugging context

#### Usage Examples for Development Workflow

```powershell
# Post-deployment verification
.\query-server-DEV.ps1 console 25 -PluginOnly

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
# Test all RVNKCore APIs
.\Test-RestRVNKCoreAPI.ps1 -Tests all

# Test specific API categories
.\Test-RestRVNKCoreAPI.ps1 -Tests player        # Player management APIs
.\Test-RestRVNKCoreAPI.ps1 -Tests playerworld   # Player-world correlation APIs
.\Test-RestRVNKCoreAPI.ps1 -Tests world         # World management APIs  
.\Test-RestRVNKCoreAPI.ps1 -Tests announcement  # Announcement system APIs

# Advanced testing options
.\Test-RestRVNKCoreAPI.ps1 -Tests world -HttpsOnly -Detail          # HTTPS only with detailed output
.\Test-RestRVNKCoreAPI.ps1 -Tests all -IgnoreSSLErrors -Detail      # Full test suite with SSL bypass
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