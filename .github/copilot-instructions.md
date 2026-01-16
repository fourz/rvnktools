# RVNK Plugin Ecosystem Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the RVNK plugin ecosystem, including RVNKTools, RVNKCore, RVNKLore, RVNKQuests, and any other RVNK plugins.

## Metamake Project Management Integration

*See detailed instructions: [Metamake Project Management](copilot-instructions.metamake.md)*

When the user explicitly requests metamake functionality using phrases like "use metamake to..." or "with metamake", activate the integrated project management capabilities.

## Archon Project Management Integration

When the user explicitly requests Archon functionality, activate the Archon MCP-based project management capabilities:

### Archon Capabilities Available

- **Task Management**: Create, update, and track development tasks with status workflows (todo → doing → review → done)
- **Project Organization**: Structure and manage projects with documents, features, and versioning
- **Knowledge Base Integration**: RAG-powered search for code examples and documentation
- **Document Management**: Create and maintain project specifications, design docs, notes, and API documentation
- **Version Management**: Track project component versions and changes

### Archon MCP Tools

**Task Management:**
- `mcp_archon_find_tasks` - Search and list tasks with filtering (status, project, assignee)
- `mcp_archon_manage_task` - Create, update, or delete tasks with detailed tracking

**Project Management:**
- `mcp_archon_find_projects` - Search and retrieve project information
- `mcp_archon_manage_project` - Create, update, or delete projects

**Knowledge Base:**
- `mcp_archon_rag_search_knowledge_base` - Search documentation and knowledge base (keep queries 2-5 keywords)
- `mcp_archon_rag_search_code_examples` - Find relevant code examples
- `mcp_archon_rag_read_full_page` - Retrieve complete page content
- `mcp_archon_rag_get_available_sources` - List available knowledge sources for filtering

**Document Management:**
- `mcp_archon_find_documents` - Search project documents (spec/design/note/prp/api/guide)
- `mcp_archon_manage_document` - Create, update, or delete project documents

**System Management:**
- `mcp_archon_health_check` - Check Archon MCP server health and uptime
- `mcp_archon_session_info` - Get active session information
- `mcp_archon_get_project_features` - Retrieve project feature tracking

### Archon Usage Patterns

**Task-Driven Development Workflow:**
1. Check current tasks: `mcp_archon_find_tasks(filter_by="status", filter_value="todo")`
2. Start task: `mcp_archon_manage_task("update", task_id="...", status="doing")`
3. Research phase: `mcp_archon_rag_search_knowledge_base(query="...")` and `mcp_archon_rag_search_code_examples(query="...")`
4. Implementation: Code based on research findings
5. Mark for review: `mcp_archon_manage_task("update", task_id="...", status="review")`
6. Complete: `mcp_archon_manage_task("update", task_id="...", status="done")`

**Knowledge Base Search Best Practices:**
- **Keep queries SHORT and FOCUSED (2-5 keywords)** - Vector search works best with concise terms
- **Good**: `"vector search pgvector"`, `"React useState"`, `"authentication JWT"`
- **Bad**: `"how to implement vector search with pgvector in PostgreSQL for semantic similarity"`
- **Filter by source**: Use `mcp_archon_rag_get_available_sources()` then provide `source_id` parameter

**Task Status Flow:**
- `todo` → `doing` → `review` → `done`
- Only ONE task in 'doing' status at a time
- Use 'review' for completed work awaiting validation

**Project Documentation:**
- Document types: spec, design, note, prp, api, guide
- Use structured JSON content for complex documentation
- Tag documents for easy filtering and discovery

### RVNK-Specific Archon Context

**Current Focus Areas:**
- RVNKCore architectural refactor and plugin ecosystem consolidation
- Announcement system migration (YAML → Database)
- REST API framework expansion
- Cross-plugin service integration
- Database layer abstraction

**Usage Examples:**
```text
"Use Archon to track the Phase 2 RVNKCore implementation tasks"
"With Archon, search for REST API authentication patterns"
"Use Archon to create a design document for the announcement service migration"
"With Archon, find all todo tasks assigned to me"
```

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

### Claude Code AI Assistant Integration

**Project-Level Instructions:**
- **CLAUDE.md**: [`shared/derek/CLAUDE.md`](../shared/derek/CLAUDE.md) - Central AI assistant instructions for Ravenkraft Dev
  - Task-driven development workflow with Archon MCP integration
  - Research patterns and knowledge base integration
  - Documentation and project standards

**Claude Code Components (Parent Repo):**
- **Commands**: [`shared/derek/.claude/commands/`](../shared/derek/.claude/commands/) - Custom slash commands
- **Agents**: [`shared/derek/.claude/agents/`](../shared/derek/.claude/agents/) - Specialized AI agents (@archon-manager, @java-architect, @minecraft-rvnk-dev, etc.)
- **Skills**: [`shared/derek/.claude/skills/`](../shared/derek/.claude/skills/) - Reusable skill modules
- **Skillsets**: [`shared/derek/.claude/skillsets.config.json`](../shared/derek/.claude/skillsets.config.json) - Skillset configuration and management

**RVNKDev Development Skillsets:**
- **rvnkdev-dev**: RVNK Minecraft plugin development with remote deployment support
- **rvnkdev-mcp**: RVNKDev MCP server tool usage and patterns (see [`shared/derek/.claude/skills/rvnkdev-mcp.md`](../shared/derek/.claude/skills/rvnkdev-mcp.md))
- **rvnkdev-mcss**: Local MCSS server management (see [`shared/derek/.claude/skills/mcss-api.md`](../shared/derek/.claude/skills/mcss-api.md), [`mcss-scripts.md`](../shared/derek/.claude/skills/mcss-scripts.md), [`mcss-config.md`](../shared/derek/.claude/skills/mcss-config.md))
- **rvnkdev-admin**: Ravenkraft server administration via MCP tools

**Development Environment:**
- **RVNK Dev (Local)**: Local development server using MCSS API for rapid iteration
- **RVNK Test (SparkedHost)**: Remote test server using SparkedHost MCP tools for production-like testing
- **Key Bindings**: `Ctrl+Shift+-` (build & copy), `Ctrl+Shift+/` (restart local), `Ctrl+Shift++` (deploy to test)

**Workflow Reference:**
- VS Code tasks and PowerShell scripts for server operations
- MCP-based deployment pipelines for test server
- Database management and console monitoring tools

*See full details in parent repository CLAUDE.md and skillset documentation*

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