# RVNKTools / RVNKCore: AI Assistant Instructions

@import ../../.claude/rules/java-plugin-build.md

---

## Project Overview

**RVNKCore** is the shared core library for the Ravenkraft plugin ecosystem. Extracted from RVNKTools (Feb 2026), deployed as standalone Bukkit plugin (`rvnkcore-*.jar`). All ecosystem plugins depend on it via `<scope>provided</scope>`.

**RVNKTools** components (announcements, permissions, utilities) are bundled inside RVNKCore.

**Tech Stack**: Java 17+, Paper/Spigot 1.20+, Maven, MySQL/SQLite, HikariCP

**Source**: `repos/rvnktools/toolkitplugin/`

---

## Build Commands

```bash
cd repos/rvnktools/toolkitplugin
mvn clean package              # Full build
mvn clean package -DskipTests  # Skip tests
```

**Output**: `target/rvnkcore.jar`

---

## Reference Materials

### Primary References

- **Graph Memory** — Plugin status and history: `open_nodes(["RVNKCore"])` or `open_nodes(["RVNKTools"])`
- **GitHub Issues** — Task tracking: `gh issue list --repo fourz/Ravenkaft-Dev --label "board:rvnkcore"`
- **[README.md](README.md)** — Project overview, architecture, features

### Standards (Parent Repo)

- [Coding Standards](../../docs/standard/coding-standards.md) — Java 17+ conventions
- [RVNKCore Integration Guide](../../docs/standard/rvnkcore-integration.md) — ServiceRegistry, Repository, DTO patterns
- [Database Patterns](../../docs/standard/database-patterns.md) — Repository pattern, HikariCP
- [REST API Standards](../../docs/standard/rest-api-standards.md) — Endpoint design, JSON structure
- [REST Endpoint Reference](../../docs/api/rest-endpoint-reference.md) — All 78 endpoints

### Copilot Instruction Modules

- **[copilot-instructions.md](.github/copilot-instructions.md)** — Main navigation hub
- **[copilot-instructions.commands.md](.github/copilot-instructions.commands.md)** — Command patterns
- **[copilot-instructions.logging.md](.github/copilot-instructions.logging.md)** — Logging standards

---

## Architecture

### Key Packages

```
org.fourz.rvnkcore
├── api/
│   ├── config/          # ApiConfig, dto/
│   ├── controller/      # PlayerController, WorldController, AnnouncementController,
│   │                    # BarterShopsController, LoreController, RVNKWorldsController
│   ├── model/           # DTOs (PlayerWorldDataDTO, WorldDataDTO, etc.)
│   │   ├── request/     # LocationUpdateRequest, GroupUpdateRequest
│   │   └── response/    # ApiResponse, ApiError, PlayerResponse, etc.
│   ├── security/        # AuthFilter (API-key + IP whitelist)
│   ├── server/jetty/    # CoreServer, ServletFactory, ServerSSLFactory
│   ├── service/         # Service interfaces (IBarterShopsApiService, etc.)
│   │   └── impl/        # ServletRegistrationServiceImpl
│   └── util/            # ApiUtils (shared HTTP helpers)
├── database/            # BaseRepository, connection providers, transactions
├── init/                # CoreServiceFactory, BundledComponentInitializer
├── service/
│   ├── announcement/    # DefaultAnnouncementService
│   └── registry/        # ServiceRegistry, DefaultServiceRegistry
├── validation/          # Validator, ValidationResult
└── util/log/            # LogManager
```

### Bundled RVNKTools Components

Registered in ServiceRegistry by `CoreServiceFactory`:
- AnnounceManager, LinkMaker, PermissionService, LogFilter
- LuckPermsIntegrationListener (optional), Economy/Vault (optional)

### REST API

- 78 endpoints across 7 controllers (4 native + 3 plugin-delegated)
- Auth: `X-API-Key` header via `AuthFilter` on `/v1/*`, `/bartershops/*`, `/lore/*`, `/rvnkworlds/*`
- Response envelope: `ApiResponse.success(data)` / `ApiResponse.error(code, message)`
- Plugin controllers resolve services lazily from ServiceRegistry

---

## Development Workflows

### Implementing a Feature

1. Check GitHub Issues for task: `gh issue list --repo fourz/Ravenkaft-Dev --label "board:rvnkcore"`
2. Follow RVNKCore architecture patterns (ServiceRegistry, Repository, async)
3. Implement, test, document
4. Build: `mvn clean package -DskipTests`
5. Deploy: `/rvnkdev-deploy` skill

### Adding a REST Endpoint

1. Add method to service interface (e.g., `IBarterShopsApiService`)
2. Implement in plugin's endpoint impl
3. Add routing in controller's `doGet`/`doPost`
4. Use `ApiUtils.sendJson/sendError` for responses
5. Update `docs/api/rest-endpoint-reference.md`

### Adding a Command

1. Check `copilot-instructions.commands.md` for patterns
2. Create command class extending CommandManager framework
3. Register in plugin command registry
4. Console support required (no player-only restrictions without justification)

---

## Patterns

### Service Framework

```java
ServiceRegistry.register(MyService.class, new MyServiceImpl());
MyService service = ServiceRegistry.get(MyService.class);
```

### Async Operations

```java
// NEVER block main thread
database.queryAsync(sql)
    .thenAccept(result -> processResult(result))
    .exceptionally(ex -> handleError(ex));
```

### REST Controller Response

```java
ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
ApiUtils.sendJson(resp, gson, 200, response);
```

---

## Status Tracking

- **Graph Memory**: `open_nodes(["RVNKCore"])` — plugin status, version, recent work
- **GitHub Issues**: `gh issue list --repo fourz/Ravenkaft-Dev --label "board:rvnkcore"` — open tasks
- **Parent Ecosystem**: See parent [CLAUDE.md](../../CLAUDE.md) for cross-project context

---

**Last Updated**: March 2026
