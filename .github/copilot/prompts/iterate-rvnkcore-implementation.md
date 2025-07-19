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

### 4. Code Quality Requirements
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
7. **Error-free compilation** and successful plugin build
8. **Server restart capability** after resolving all implementation issues

## Implementation Guidelines

### Architecture Patterns
- Use Repository pattern for data access
- Implement Service layer for business logic
- Apply Bridge pattern for legacy integration
- Follow DTO pattern for data transfer
- Use Builder pattern for complex object construction

### Async Programming Standards
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
