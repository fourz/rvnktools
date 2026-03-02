# RVNKTools: AI Assistant Instructions

@import ../../.claude/rules/archon-workflow.md
@import ../../.claude/rules/java-plugin-build.md

---

## Archon Project IDs

**Related Projects**:

- **RVNKCore**: `7785e125-4468-44e2-a86c-2fef668fce48`
  - Core data layer extraction from RVNKTools
  - Service framework architecture, database abstraction
  - Access: `find_projects(project_id="7785e125-4468-44e2-a86c-2fef668fce48")`

- **Ravenkraft Dev**: `4787f505-e92e-474d-ba54-f5ac7993ccfe` (parent ecosystem)
  - Knowledge base sync: coding standards, architecture patterns
  - Cross-project context: plugin ecosystem, web integration
  - Access: `find_documents(project_id="4787f505-e92e-474d-ba54-f5ac7993ccfe")`

---

## Project Overview

**RVNKTools** is a comprehensive Minecraft server plugin providing administration tools, announcements, permissions, and utilities. Currently undergoing major refactor to extract core functionality into **RVNKCore**.

**Tech Stack**: Java 17+, Paper/Spigot 1.20+, Maven, MySQL/SQLite, HikariCP

**Current Focus**: RVNKCore extraction - centralized data layer, service framework, API interfaces

---

## Reference Materials

### Primary References (Always Check First)

- **GraphQL-Memdock** — For RVNKCore/RVNKTools status and history: `search_nodes("RVNKCore")` or `search_nodes("RVNKTools")`
- **[README.md](README.md)** — Project overview, architecture diagrams, features
- **[.github/copilot-instructions.md](.github/copilot-instructions.md)** — Instruction file navigation hub

### Copilot Instruction Modules

- **[copilot-instructions.md](.github/copilot-instructions.md)** — Main navigation hub
- **[copilot-instructions.commands.md](.github/copilot-instructions.commands.md)** — Command implementation patterns
- **[copilot-instructions.examples.md](.github/copilot-instructions.examples.md)** — Code examples, patterns
- **[copilot-instructions.formatting.md](.github/copilot-instructions.formatting.md)** — Code style, formatting
- **[copilot-instructions.logging.md](.github/copilot-instructions.logging.md)** — Logging standards
- **[copilot-instructions.metamake.md](.github/copilot-instructions.metamake.md)** — Metamake framework usage

### Archon Documentation

- **[archon/README.md](archon/README.md)** — Project documentation index
- **[archon/docs/](archon/docs/)** — Detailed specifications
- **[archon/PRPs/](archon/PRPs/)** — Product Requirements Plans

### RVNKCore Board Documents (Internal Implementation)

Documents on RVNKTools/RVNKCore board (`7785e125-4468-44e2-a86c-2fef668fce48`):
- **RVNKCore ServiceRegistry Reference** - Service interface contracts, registration patterns
- **RVNKTools Bundling Clarification** - Target architecture (RVNKTools bundled in RVNKCore)
- **Privacy-Focused Teleport Tracking** - WorldSwap command, player location tracking
- **Servlet Registration API** - REST endpoint registration patterns

### Parent Ecosystem Standards (Cross-cutting)

Access via Archon RAG or parent repo:

- **Coding Standards**: `rag_search_knowledge_base(query="Java coding standards")`
- **RVNKCore Integration**: `rag_search_knowledge_base(query="RVNKCore ServiceRegistry")`
- **Repository Pattern**: `rag_search_knowledge_base(query="Repository pattern database")`
- **REST API Standards**: `rag_search_knowledge_base(query="REST API standards")`

Parent board documents (`4787f505-e92e-474d-ba54-f5ac7993ccfe`):
- [Coding Standards](../../docs/standard/coding-standards.md) - Java 17+ conventions (general)
- [RVNKCore Integration Guide](../../docs/standard/rvnkcore-integration.md) - Integration concepts (general)
- [Database Patterns](../../docs/standard/database-patterns.md) - Repository pattern, HikariCP

---

## Development Workflows

### Implementing a New Feature

1. **Task Management**: Check Archon for current tasks or create new task
2. **Research**: Use Archon RAG to search for similar patterns
3. **Design**: Follow RVNKCore architecture patterns
4. **Implement**: Use ServiceRegistry, Repository pattern, async operations
5. **Test**: Write unit tests, integration tests
6. **Document**: Add Javadoc, update README
7. **Complete**: Mark task as review/done in Archon

### RVNKCore Migration Task

1. **Identify**: Component to extract (service, repository, model)
2. **Design**: Interface in RVNKCore, implementation in RVNKTools
3. **Extract**: Move shared code to RVNKCore module
4. **Refactor**: Update RVNKTools to use RVNKCore dependency
5. **Test**: Verify functionality preserved
6. **Document**: Update architecture docs

### Adding a New Command

1. **Check**: copilot-instructions.commands.md for patterns
2. **Create**: Command class extending CommandManager framework
3. **Register**: Add to plugin command registry
4. **Permissions**: Define in plugin.yml and LuckPerms
5. **Console Support**: Ensure command works from console (no player-only restrictions)
6. **Test**: Manual testing, automated tests

---

## Architecture Patterns

### Service Framework

```java
// Register service
ServiceRegistry.register(MyService.class, new MyServiceImpl());

// Get service
MyService service = ServiceRegistry.get(MyService.class);
```

### Repository Pattern

```java
// All data access through repositories
PlayerRepository repo = ServiceRegistry.get(PlayerRepository.class);
CompletableFuture<PlayerData> future = repo.findByUuid(uuid);
```

### Async Operations

```java
// NEVER block main thread
database.queryAsync(sql)
    .thenAccept(result -> processResult(result))
    .exceptionally(ex -> handleError(ex));
```

---

## Decision-Making Guidelines

### Autonomous Actions (Can proceed without approval)

- Code implementation following established patterns
- Test creation for new functions
- Documentation updates
- Refactoring for clarity
- Bug fixes with clear root cause

### Constraints (Ask first)

- Creating new services or repositories
- Changing database schema
- Modifying public APIs
- Adding new dependencies
- Breaking changes to existing commands

### Quality Gates (Must pass before completion)

- All tests passing
- Code follows Java conventions
- Javadoc for public methods
- No blocking operations on main thread
- Console command support where applicable

---

## Best Practices

### Working with Claude

**DO:**

- Use Archon MCP server for task management
- Follow ServiceRegistry and Repository patterns
- Use CompletableFuture for async operations
- Check copilot-instructions modules for patterns
- Reference Archon RAG for ecosystem standards

**DON'T:**

- Block main thread with I/O operations
- Create player-only commands without justification
- Ignore RVNKCore architecture patterns
- Hardcode credentials or sensitive data
- Skip task updates in Archon

### Before Starting Work

1. **Check Archon**: Verify task availability and priority
2. **Check ROADMAP.md**: Understand current project status
3. **Research**: Use Archon RAG for relevant patterns
4. **Review**: Check copilot-instructions modules
5. **Understand**: Read architecture documentation

---

## Status Tracking

**For current project status and RVNKCore extraction progress:**

- **[ROADMAP.md](ROADMAP.md)** — Primary source for all status information

**For task management and issue tracking:**

- **Archon MCP Board** — Live task management system

**For ecosystem-wide standards:**

- **Ravenkraft Dev Parent** — `find_documents(project_id="4787f505-e92e-474d-ba54-f5ac7993ccfe")`

---

**Last Updated**: January 2026

**Note**: This file is for AI guidance and project context. For transient project status, see ROADMAP.md and Archon only.
