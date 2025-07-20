# RVNKCore Implementation Iteration Prompt

## Objective
Continue iterating on the RVNKCore architectural refactor, focusing on completing the player core example and resolving any implementation issues while following the project roadmap.

## Current Context
- **Branch**: `derek/dev-core`
- **Phase**: RVNKCore Foundation Implementation
- **Target**: Working player core example for comprehensive tracking
- **Date**: July 19, 2025

## Primary Tasks

### 1. Roadmap Status Update
- Review current implementation status in `./ROADMAP.md`
- Update completion status for implemented components
- Mark completed items with ✅ and proper documentation
- Identify remaining gaps or issues

### 2. Directory Structure & Implementation
- Follow the established RVNKCore architecture patterns
- Create any missing directory structures following `org.fourz.rvnkcore.*` pattern
- Implement missing components identified in the roadmap
- Ensure proper package organization and separation of concerns

### 3. Player Core Example Focus
Implement comprehensive player tracking system with:
- **Player seen status**: First seen, last seen, times joined tracking
- **Name history**: Track username changes over time with history preservation
- **Last location**: World, coordinates, automatic updates with rate limiting
- **Rank and groups**: Permission system integration and group tracking
- **Async operations**: All database operations using CompletableFuture
- **Event-driven updates**: Real-time tracking via Bukkit event listeners

### 4. Web Integration & REST API Planning
Consider web integration requirements for each plugin type when designing data structures:

#### Plugins Requiring Web Integration (REST API Required)
- **RVNKShops**: Full CRUD operations for shop management, pricing, inventory
  - Shop listings, item catalogs, transaction history, pricing management
  - Customer-facing web interface for browsing and purchasing
- **RVNKTools**: Announcement management and server administration
  - Web-based announcement creation, scheduling, and management
  - Server statistics and monitoring dashboard
- **RVNKLore**: Lore content management and discovery system
  - Web-based lore browser, search, and content management
  - Community-driven lore creation and sharing platform

#### Plugins for Internal Use Only (Java API Only)
- **RVNKQuests**: Quest system for in-game progression
  - Complex state management, player progress tracking
  - Real-time quest updates and completion handling
- **RVNKWorlds**: World management and teleportation
  - Server-side world generation and management
  - Location-based operations and world state

### 4. REST API Infrastructure Migration *(Critical Priority)*

Migrate and refactor API components from RVNKTools to RVNKCore:

#### Core API Package Structure

- **Create `org.fourz.rvnkcore.api.server` package hierarchy**
  - `org.fourz.rvnkcore.api.server.jetty` - Jetty server components
  - `org.fourz.rvnkcore.api.config` - Configuration management
  - `org.fourz.rvnkcore.api.security` - Authentication and security
  - `org.fourz.rvnkcore.api.model.request` - Request DTOs
  - `org.fourz.rvnkcore.api.model.response` - Response DTOs

#### Migration Components *(Iterative Borrowing from RVNKTools)*

- **Jetty Server Infrastructure**
  - Migrate `JettyServer.java` → `org.fourz.rvnkcore.api.server.jetty.RVNKCoreServer`
  - Port HTTP/HTTPS configuration and SSL/TLS support
  - Extract reusable server lifecycle management
- **Configuration Framework**
  - Migrate `RestConfig.java` → `org.fourz.rvnkcore.api.config.ApiConfig`
  - Implement centralized configuration with environment-specific overrides
  - Add validation and runtime configuration updates
- **Security Components**
  - Migrate `ApiKeyAuthFilter.java` → `org.fourz.rvnkcore.api.security.AuthFilter`
  - Port `KeyStoreGenerator.java` and `KeyStoreImporter.java`
  - Implement role-based access control for different plugin types
- **Player API Controller**
  - Create `org.fourz.rvnkcore.api.controller.PlayerController`
  - Implement comprehensive REST endpoints using PlayerService
  - Add pagination, filtering, and search capabilities

#### REST API Endpoint Implementation

Design and implement REST endpoints that mirror internal service operations:

```java
// Player API endpoints in org.fourz.rvnkcore.api.controller.PlayerController
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {
    
    private final PlayerService playerService;
    
    // Core CRUD operations
    @GetMapping
    public CompletableFuture<PagedResponse<PlayerResponse>> getAllPlayers(
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "50") int limit);
    
    @GetMapping("/online")
    public CompletableFuture<List<PlayerResponse>> getOnlinePlayers();
    
    @GetMapping("/recent")
    public CompletableFuture<List<PlayerResponse>> getRecentPlayers(
        @RequestParam(defaultValue = "24") int hours);
    
    @GetMapping("/{uuid}")
    public CompletableFuture<PlayerResponse> getPlayer(@PathVariable UUID uuid);
    
    @GetMapping("/name/{name}")
    public CompletableFuture<PlayerResponse> getPlayerByName(@PathVariable String name);
    
    @GetMapping("/group/{group}")
    public CompletableFuture<List<PlayerResponse>> getPlayersByGroup(@PathVariable String group);
    
    @GetMapping("/search")
    public CompletableFuture<List<PlayerResponse>> searchPlayers(@RequestParam String name);
    
    @GetMapping("/count")
    public CompletableFuture<CountResponse> getPlayerCount();
    
    // Update operations
    @PutMapping("/{uuid}/location")
    public CompletableFuture<StatusResponse> updatePlayerLocation(
        @PathVariable UUID uuid, @RequestBody LocationUpdateRequest request);
    
    @PutMapping("/{uuid}/groups")
    public CompletableFuture<StatusResponse> updatePlayerGroups(
        @PathVariable UUID uuid, @RequestBody GroupUpdateRequest request);
}
```

### 5. Code Quality Requirements

- Follow SOLID principles and service-oriented architecture
- Use async programming patterns for all data operations
- Implement proper error handling with ServiceException framework
- Follow established logging patterns with LogManager
- Ensure thread safety for concurrent operations
- Add comprehensive JavaDoc documentation

### 5. Integration & Testing
- Ensure seamless integration with existing RVNKTools plugin
- Register event listeners properly in main plugin class
- Test service discovery and dependency injection
- Resolve any compilation or runtime errors
- Verify database operations work correctly

### 6. Performance Considerations
- Implement caching strategies for frequently accessed data
- Use rate limiting for location updates to prevent database spam
- Monitor async operation completion and performance
- Ensure proper resource cleanup and lifecycle management

## Expected Deliverables

1. **Updated ROADMAP.md** with current implementation status
2. **Complete service implementation** with PlayerService, PlayerRepository, and PlayerDTO
3. **Working event listeners** for real-time player tracking
4. **Service registry system** with proper dependency injection
5. **Database integration** with SQLite provider and query builder
6. **Integration bridge** (RVNKCoreBootstrap) for legacy compatibility
7. **REST API foundation** for web-enabled plugins (shops, tools, lore)
8. **RVNKCore API Infrastructure** (`org.fourz.rvnkcore.api.*` package structure)
   - Migrated Jetty server components from RVNKTools
   - Configuration management with ApiConfig
   - Security framework with AuthFilter and key management
   - Player REST API controller with comprehensive endpoints
9. **Web integration planning** with CRUD operation mapping for external access
10. **Error-free compilation** and successful plugin build
11. **Server restart capability** after resolving all implementation issues

## Implementation Guidelines

### Architecture Patterns
- Use Repository pattern for data access
- Implement Service layer for business logic
- Apply Bridge pattern for legacy integration
- Follow DTO pattern for data transfer
- Use Builder pattern for complex object construction

### Async Programming Standards-
```java
// All database operations MUST use CompletableFuture
public CompletableFuture<PlayerDTO> getPlayer(UUID playerId) {
    return repository.findById(playerId)
        .thenApply(this::enrichPlayerData)
        .exceptionally(ex -> {
            logger.error("Failed to retrieve player: " + playerId, ex);
            throw new ServiceException("Player retrieval failed", ex);
        });
}
```

### Service Integration Pattern
```java
// Services should be accessed through ServiceRegistry
IPlayerService playerService = coreBootstrap.getService(IPlayerService.class);
```

### REST API Architecture Pattern
```java
// For plugins requiring web integration, mirror data structures to REST endpoints
@RestController
@RequestMapping("/api/v1/shops")
public class ShopController {
    
    private final ShopService shopService;
    
    // CRUD operations mirroring internal service methods
    @GetMapping
    public CompletableFuture<List<ShopDTO>> getAllShops() {
        return shopService.getAllShops();
    }
    
    @PostMapping
    public CompletableFuture<ShopDTO> createShop(@RequestBody ShopCreateRequest request) {
        return shopService.createShop(request.toDTO());
    }
    
    @PutMapping("/{id}")
    public CompletableFuture<ShopDTO> updateShop(@PathVariable UUID id, @RequestBody ShopUpdateRequest request) {
        return shopService.updateShop(id, request.toDTO());
    }
    
    @DeleteMapping("/{id}")
    public CompletableFuture<Void> deleteShop(@PathVariable UUID id) {
        return shopService.deleteShop(id);
    }
}
```

### Event Handling Standards
```java
// Event handlers should be async and non-blocking
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    playerService.updatePlayerJoin(player.getUniqueId(), player.getName())
        .whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("Failed to update player join", ex);
            }
        });
}
```

## Success Criteria

1. **All compilation errors resolved** - Plugin builds successfully
2. **Service discovery working** - Services can be retrieved from registry
3. **Database operations functional** - Player data can be stored and retrieved
4. **Event listeners active** - Player activity is tracked in real-time
5. **Integration complete** - RVNKCore works seamlessly with RVNKTools
6. **Server restart successful** - Plugin loads and functions properly in test environment

## Troubleshooting Checklist

- [ ] Check all import statements are correct
- [ ] Verify interface implementations are complete
- [ ] Ensure proper constructor parameters
- [ ] Validate service registration in bootstrap
- [ ] Confirm database schema exists
- [ ] Test async operation completion
- [ ] Verify event listener registration
- [ ] Check LogManager initialization

## Final Step
After completing all implementation work and resolving errors:
- Execute "Restart Server" task to deploy and test the updated plugin
- Monitor logs for any runtime issues
- Verify player tracking functionality works as expected
