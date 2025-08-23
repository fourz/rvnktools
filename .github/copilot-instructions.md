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

*See detailed instructions: [VS Code Query Tasks & PowerShell Query Script](copilot-instructions.vscode-tasks.md)*

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