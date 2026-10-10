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
- [REST Endpoint Reference](../../docs/api/rest-endpoint-reference.md) — 75 implemented endpoints (10 controllers)

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
│   ├── npc/             # NPC bridge: NpcBridge, NpcKeys, UnavailableNpcService (#2213)
│   │   ├── citizens/    # Citizens adapter - loaded ONLY when Citizens is enabled
│   │   ├── harness/     # NPC harness (#2248): spec parser, planner, verifier, executor - no Citizens types
│   │   └── papi/        # %rvnknpc_*% - loaded ONLY when PlaceholderAPI is enabled
│   ├── region/          # IRegionService, RegionBridge, RegionArgs, Cuboid (#2248)
│   │   └── worldguard/  # WorldGuard adapter - loaded ONLY when WorldGuard is enabled
│   └── registry/        # ServiceRegistry, DefaultServiceRegistry
├── validation/          # Validator, ValidationResult
└── util/log/            # LogManager
```

### Bundled RVNKTools Components

Registered in ServiceRegistry by `CoreServiceFactory`:
- AnnounceManager, LinkMaker, PermissionService, LogFilter
- LuckPermsIntegrationListener (optional), Economy/Vault (optional)

### REST API

- 75 endpoints across 10 controllers (7 native + 3 plugin-delegated)
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

`ServiceRegistry` is an **instance** owned by RVNKCore, not a static API — there is no
`ServiceRegistry.register(...)` / `ServiceRegistry.get(...)`.

```java
// Register (inside RVNKCore, or a plugin holding the core instance)
RVNKCore.getInstance().getServiceRegistry()
        .registerService(MyService.class, new MyServiceImpl());

// Hard dependency: throws if RVNKCore is not initialised
MyService service = RVNKCore.getInstance().getService(MyService.class);

// Soft dependency: returns null when RVNKCore or the service is absent
MyService maybe = RVNKCore.getServiceSafe(MyService.class);
```

### NPC bridge (#2213, since 1.5.99-alpha)

RVNKCore owns the NPC contract; Citizens sits behind it. Consumers (RVNKQuests, RVNKEvents) depend
only on `INpcService` and `RvnkNpcInteractEvent` and reference an NPC by its **RVNK key**, never by a
Citizens id. Keys are lower-case `[a-z0-9_-]{1,48}`, unique per server, and staff set them with
`/rvnk npc tag <key> [npcId]` (perms `rvnkcore.npc.*`).

```java
// Listen: fired on the main thread, only for NPCs that carry a key. Cancellable - a cancel also
// cancels the Citizens click (right-click interaction and the NPC's /npc command actions).
@EventHandler(ignoreCancelled = true)
public void onNpc(RvnkNpcInteractEvent event) {
    if (event.getClickType() == RvnkNpcInteractEvent.ClickType.RIGHT
            && event.getNpcKey().equals("harbour_master")) {
        Player player = event.getPlayer();
        // advance a TALK_TO objective ...
    }
}

// Query: always registered. Without Citizens it is an "unavailable" stand-in that returns empty
// and never throws, so check isAvailable() instead of null.
INpcService npcs = RVNKCore.getServiceSafe(INpcService.class);
if (npcs != null && npcs.isAvailable()) {
    npcs.findByKey("harbour_master").ifPresent(ref -> ref.getLocation());
}
```

- **Classloading guard**: every Citizens class is in `service/npc/citizens/`, every PlaceholderAPI
  class in `service/npc/papi/`. `NpcBridge` reaches them only after `isPluginEnabled(...)` is true.
  Never import either package from anywhere else — `NpcBridgeClassLoadingTest` fails if you do.
- **Persistence**: the key is Citizens persistent metadata `rvnk-key` in each server's `saves.yml`.
- **Placeholders** (registered only when PlaceholderAPI is enabled): `%rvnknpc_last_key%`,
  `%rvnknpc_last_name%`, `%rvnknpc_last_ago_seconds%` (`""` / `""` / `-1` when none). In memory,
  bounded to 1024 players, empty after a restart.

### NPC harness and region tool (#2248, since 1.5.100-alpha)

Console-safe NPC place/edit (`/rvnk npc create|move|rename|remove|skin|lookclose|pose|hold|protected|nameplate`),
WorldGuard protect zones (`/rvnk npc protect`), an idempotent YAML spec (`plugins/RVNKCore/npc/<spec>.yml`,
`/rvnk npc apply|verify|export`), and `/rvnk region define|flag|remove|info` from explicit corners. Perms
`rvnkcore.npc.admin`, `rvnkcore.region.admin`. Full reference, spec schema and the TFAH sample:
[toolkitplugin/docs/api/npc-harness.md](toolkitplugin/docs/api/npc-harness.md).

- **Never dispatch `/npc` or `/rg` from code.** Console `/npc create` NPEs without `--at`; `/rg define` needs a
  WorldEdit selection. Use `NpcHarness` (Citizens API) and `IRegionService` (WorldGuard API).
- **Same classloading guard**: WorldGuard/WorldEdit classes only in `service/region/worldguard/`; the
  planner, verifier and parser in `service/npc/harness/` must stay free of Citizens and WorldGuard types
  so they stay unit-testable. `NpcBridgeClassLoadingTest` hides `com.sk89q` too.

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
