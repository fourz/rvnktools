# RVNKTools Copilot Instructions

**Parent Hub**: See Ravenkraft-Dev CLAUDE.md for complete ecosystem standards.

## Tool Discovery

**Server Management**: `mcp_rvnkdev-minec_*` tools (console, files, state, db)
**Live Testing**: `/rvnktest [health|services|db|plugins|run all]`
**Agents**: Browse `.claude/agents/` for specialized workflows
**Skills**: Browse `.claude/skills/` for domain capabilities
**Rules Import**: Use `@import ../../.claude/rules/<rule>.md` for shared directives

## Archon Integration

**Board**: `7785e125-4468-44e2-a86c-2fef668fce48` (RVNKCore/RVNKTools)
**Workflow**: `find_tasks()` → `manage_task("update", status="doing")` → implement → `status="done"`

## Plugin-Specific Standards

### Core Directives
- Use CommandManager framework for all commands (no standalone executors)
- Implement ServiceRegistry pattern for dependency injection
- Use Repository pattern for all data access
- Follow SOLID principles; ensure proper resource cleanup

### RVNKCore Services Provided
- `IPlayerService`, `IWorldService`, `IAnnouncementService`
- `ServiceRegistry` for cross-plugin service discovery
- Database abstraction (MySQL/SQLite via HikariCP)

### Message Prefixes
- `&c▶` usage | `&6⚙` progress | `&a✓` success | `&c✖` error | `&e⚠` warning

### Logging
Use `LogManager.getInstance(plugin, "ClassName")` from RVNKCore. Never use `System.out.println()`.

## References

- **Coding Standards**: `docs/standard/coding-standards.md`
- **Architecture Patterns**: `docs/architecture/shared-patterns.md`
- **RVNKCore Integration**: `docs/standard/rvnkcore-integration.md`
- **Database Patterns**: `docs/standard/database-patterns.md`
