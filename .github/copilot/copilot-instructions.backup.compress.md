# RVNK Plugin Ecosystem Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the RVNK plugin ecosystem, including RVNKTools, RVNKCore, RVNKLore, RVNKQuests, and any other RVNK plugins.

## General Directives

- **Use the CommandManager framework for all commands. Do not create standalone command executors.**
- **Follow SOLID principles when adding new features or refactoring existing code.**
- **Ensure proper resource cleanup in all managers and services.**
- **Implement RVNKCore patterns when working on core functionality extraction.**
- **Use asynchronous programming for all data operations and API calls.**

## Asynchronous Data and API Layer Standards

### Core Principles

- **All database operations MUST use CompletableFuture** to prevent blocking the main thread
- **All external API calls MUST be asynchronous** using CompletableFuture or similar patterns
- **Service interfaces MUST return CompletableFuture** for any potentially blocking operations
- **Repository methods MUST be async** with proper error handling and logging
- **Configuration loading and saving MUST be asynchronous** when involving I/O operations

### Database Operations

```java
// REQUIRED: All database operations use CompletableFuture
public CompletableFuture<PlayerDTO> getPlayer(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> {
        try (Connection conn = connectionProvider.getConnection()) {
            // Database query logic
            return playerDTO;
        } catch (SQLException e) {
            logger.error("Failed to retrieve player: " + playerId, e);
            throw new DatabaseException("Player retrieval failed", e);
        }
    });
}

// REQUIRED: Chain async operations properly
public CompletableFuture<AnnouncementDTO> saveAnnouncement(AnnouncementDTO announcement) {
    return validateAnnouncement(announcement)
        .thenCompose(validated -> repository.save(validated))
        .thenCompose(saved -> updateCache(saved))
        .exceptionally(ex -> {
            logger.error("Failed to save announcement", ex);
            throw new ServiceException("Save operation failed", ex);
        });
}
```

### API Integration

```java
// REQUIRED: External API calls must be async
public CompletableFuture<EconomyResponse> processPayment(UUID player, double amount) {
    return CompletableFuture.supplyAsync(() -> {
        // Vault/Economy API interaction
        return economyService.withdrawPlayer(player, amount);
    }).exceptionally(ex -> {
        logger.error("Payment processing failed for player: " + player, ex);
        return new EconomyResponse(0, 0, ResponseType.FAILURE, "Payment failed");
    });
}
```

## RVNKCore Integration Guidelines

As all RVNK plugins integrate with RVNKCore, follow these additional guidelines:

### Service Pattern

- **Use service interfaces** for all business logic across all RVNK plugins
- **Implement services through the ServiceRegistry pattern** for dependency injection
- **Keep services focused on single responsibilities** following SOLID principles
- **Register services with RVNKCore** for cross-plugin access and dependency management

```java
// Service interface in RVNKCore API
public interface PlayerService {
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    CompletableFuture<List<PlayerDTO>> getOnlinePlayers();
}

// Service implementation
public class DefaultPlayerService implements PlayerService {
    private final PlayerRepository repository;
    private final LogManager logger;
    
    public PlayerService(ServiceRegistry registry) {
        this.repository = registry.getService(PlayerRepository.class);
        this.logger = LogManager.getInstance(RVNKCore.getInstance().getPlugin(), getClass());
    }
}
```

### Database Access

- **Use the Repository pattern** for all data access across the ecosystem
- **Implement async operations with CompletableFuture** for all database interactions
- **Use DTOs for data transfer** between layers and across plugin boundaries
- **Leverage RVNKCore's ConnectionProvider** for consistent database access

```java
// Repository implementation using RVNKCore infrastructure
public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, Long> {
    
    public AnnouncementRepository(ConnectionProvider provider, QueryBuilder builder) {
        super(provider, builder, AnnouncementDTO.class);
    }
    
    public CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements() {
        return executeQueryList(
            queryBuilder.select("*")
                       .from("announcements")
                       .where("active = ? AND expires_at > ?", true, Timestamp.valueOf(LocalDateTime.now()))
                       .orderBy("created_at", false)
        );
    }
}
```

### API Design

- **Create clean, versioned API interfaces** for all plugin interactions
- **Document all public APIs with complete JavaDoc** including examples and error conditions
- **Use event-driven integration points** for loose coupling between plugins
- **Implement backward compatibility** for API changes across versions

```java
/**
 * API for managing player lore and item data across the RVNK ecosystem.
 * 
 * This service provides centralized access to player lore, custom items,
 * and related metadata that can be shared across plugins.
 * 
 * @since 1.0.0
 */
public interface ILoreService {
    /**
     * Retrieves lore items for a specific player.
     * 
     * @param playerId The UUID of the player
     * @param category The lore category to filter by (optional)
     * @return CompletableFuture containing list of lore items
     * @throws ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<List<LoreItemDTO>> getPlayerLore(UUID playerId, String category);
}
```

### Cross-Plugin Event System

- **Use RVNKCore's event system** for cross-plugin communication
- **Implement event listeners** for responding to ecosystem-wide changes
- **Fire events asynchronously** to prevent blocking plugin operations
- **Provide event cancellation** where appropriate for plugin interaction

```java
// Event definition
public class PlayerLoreUpdatedEvent extends RVNKEvent {
    private final UUID playerId;
    private final LoreItemDTO loreItem;
    
    public PlayerLoreUpdatedEvent(UUID playerId, LoreItemDTO loreItem) {
        this.playerId = playerId;
        this.loreItem = loreItem;
    }
}

// Event firing (async)
public CompletableFuture<Void> fireLoreUpdateEvent(UUID playerId, LoreItemDTO item) {
    return CompletableFuture.runAsync(() -> {
        PlayerLoreUpdatedEvent event = new PlayerLoreUpdatedEvent(playerId, item);
        eventBus.fireEvent(event);
    });
}
```

### Migration Strategy

- **Maintain backward compatibility** during transition to RVNKCore
- **Create migration utilities** for existing data when upgrading plugins
## Ecosystem-Wide Standards

### Plugin Architecture

All RVNK plugins should follow a consistent architecture:

```
Plugin Root/
├── api/                    # Public plugin APIs
├── service/               # Business logic services
├── repository/            # Data access layer
├── command/              # Command implementations
├── listener/             # Event listeners
├── config/               # Configuration management
└── integration/          # Third-party integrations
```

### Dependency Management

- **Declare RVNKCore as a dependency** in plugin.yml for all RVNK plugins
- **Use ServiceRegistry** to obtain dependencies rather than direct instantiation
- **Implement proper service lifecycle** with initialization and cleanup phases
- **Handle missing dependencies gracefully** with appropriate fallback behavior

```yaml
# plugin.yml example
depend: [RVNKCore]
softdepend: [RVNKTools, RVNKLore, PlaceholderAPI, Vault]
```

### Error Handling and Resilience

- **Use the RVNK exception hierarchy** for consistent error handling
- **Implement circuit breaker patterns** for external service calls
- **Provide meaningful error messages** with actionable information for administrators
- **Log errors with appropriate context** including player IDs, operation details, and stack traces

```java
public CompletableFuture<Result> performOperation(UUID playerId) {
    return serviceCall(playerId)
        .handle((result, ex) -> {
            if (ex != null) {
                logger.error("Operation failed for player: " + playerId, ex);
                // Return default result or throw appropriate exception
                return Result.failure("Operation temporarily unavailable");
            }
            return result;
        });
}
```

### Performance and Monitoring

- **Use DebugLogger for performance-critical sections** across all plugins
- **Implement caching strategies** for frequently accessed data
- **Monitor async operation completion** and log performance metrics
- **Use connection pooling** through RVNKCore for database operations

```java
public class PerformanceMonitoredService {
    private final DebugLogger logger;
    
    public CompletableFuture<Data> getData(String key) {
        try (AutoCloseable timer = logger.timeSection("getData")) {
            return cache.get(key)
                .orElseGet(() -> repository.findByKey(key)
                    .thenApply(data -> {
                        cache.put(key, data);
                        return data;
                    }));
        }
    }
}
```

### Configuration Standards

- **Use YAML for all configuration files** with consistent naming conventions
- **Implement configuration validation** with clear error messages
- **Support configuration reloading** without server restart where possible
- **Document all configuration options** with examples and default values

```yaml
# Standard configuration structure
plugin-name:
  database:
    enabled: true
    type: shared  # Use RVNKCore shared database
  
  features:
    feature-name:
      enabled: true
      options:
        option1: value1
        option2: value2
  
  integration:
    placeholder-api: true
    vault: true
```

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

```java
public class DataMigrationService {
    public CompletableFuture<MigrationResult> migrateFromLegacy() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Backup existing data
                backupService.createBackup("pre-migration");
                
                // 2. Migrate data to new format
                List<LegacyData> legacyData = legacyRepository.findAll();
                List<NewData> migratedData = convertToNewFormat(legacyData);
                
                // 3. Validate migration
                validateMigration(legacyData, migratedData);
                
                // 4. Save migrated data
                newRepository.saveAll(migratedData);
                
                return MigrationResult.success(migratedData.size());
            } catch (Exception e) {
                logger.error("Migration failed", e);
                return MigrationResult.failure(e.getMessage());
            }
        });
    }
}
```

This comprehensive set of standards ensures consistency, performance, and maintainability across the entire RVNK plugin ecosystem while emphasizing asynchronous operations and proper data layer abstraction.

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Explain the class's purpose and responsibility in the system
- Note important design patterns or architectural decisions
- Focus on "why" over implementation details

```java
/**
 * Manages lore item creation and distribution with configurable properties.
 * Acts as the central registry for all custom items within the lore system.
 */
```

#### Method Documentation

- Describe purpose and behavior, not implementation
- Document parameters and return values
- Note exceptions that may be thrown
- Include examples for complex methods

```java
/**
 * Retrieves lore content based on provided entity type and identifier.
 * Handles fallback behavior when specific lore isn't available.
 *
 * @param entityType The type of entity to retrieve lore for
 * @param identifier Unique identifier within the entity type
 * @return The lore content or default text if none found
 * @throws IllegalArgumentException If entityType is null
 */
```

### Code Comments

- Comment on "why" less "what" - explain reasoning behind code
- Place comments above the code they describe
- Keep comments concise and meaningful
- Use TODO and FIXME sparingly and with clear descriptions
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

### Console and Debug Messages

- Use the designated logging system for all console output
- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- **Do not use ChatFormat for logger output**
- For all command output to console (outside of logger), use `ChatFormat.stripColors()` to ensure clean output
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

## Logging Manager Standard

- Use the persistent `LogManager` class for all info, warning, and error logging in plugin code.
- Always declare the property as `private final LogManager logger;` (or `private LogManager logger;` if not final).
- Initialize with `this.logger = LogManager.getInstance(plugin);` in constructors.
- Use `logger.info(message)`, `logger.warning(message)`, and `logger.error(message, exception)` for all logging.
- Do not use `System.out.println()`, direct logger calls, or custom logger fields for these log levels.
- Use the property name `logger` for all `LogManager` usages to ensure consistency across the codebase.
- Reserve the `Debug` class for debug-level or trace logging only.

**Example:**

```java
private final LogManager logger;

public MyClass(RVNKLore plugin) {
    this.logger = LogManager.getInstance(plugin);
}

public void doSomething() {
    logger.info("Something happened");
    logger.warning("A warning");
    logger.error("An error occurred", exception);
}
```

## Command Framework Guidelines

Follow the CommandManager framework for all commands:

1. Extend `BaseCommand` for new commands:
```java
public class MyCommand extends BaseCommand {
    public MyCommand(RVNKTools plugin) {
        super(plugin, "commandname", 
              "Command description", 
              "/commandname <arg>",
              "rvnktools.command.permission");
    }
}
```

2. Register commands through CommandManager:
```java
commandManager.registerCommand(new MyCommand(plugin));
```

3. Use subcommands where appropriate:
```java
registerSubCommand("subcommand", new MySubCommand(plugin));
```

4. Implement proper tab completion:
```java
@Override
public List<String> tabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
        return getMatchingSubCommands(sender, args[0]);
    }
    return Collections.emptyList();
}
```

## Resource Management

- Initialize resources in proper order during onEnable
- Clean up resources in reverse order during onDisable
- Use try-with-resources for closeable resources
- Cancel tasks and unregister listeners properly
- Implement shutdown methods in manager classes

## Plugin Architecture

### Core Components

1. **CommandManager**
   - Central command registration and handling
   - Permission management
   - Tab completion support

2. **AnnounceManager**
   - Announcement scheduling and delivery
   - YAML configuration integration
   - PlaceholderAPI support

3. **LinkMaker**
   - Link creation and management
   - Click handling
   - Permission integration

4. **Integration Support**
   - Economy (Vault)
   - Permissions (LuckPerms)
   - PlaceholderAPI
   - Multiverse

### Best Practices

1. **Command Implementation**
   - Use CommandManager framework
   - Follow consistent error handling
   - Implement proper permissions
   - Support tab completion

2. **Configuration Management**
   - Use typed configuration objects
   - Validate configuration on load
   - Support live reloading
   - Handle missing/invalid values

3. **Event Handling**
   - Register listeners properly
   - Keep handlers focused
   - Consider performance impact
   - Clean up on disable

## Performance Considerations

- Use async tasks for I/O operations
- Implement caching where appropriate
- Batch operations when possible
- Monitor resource usage
- Clean up resources promptly

## Development Workflow

Use VS Code tasks for development:

- **Build Plugin**: `mvn clean package` (builds the plugin JAR)
- **Copy to Server**: Copy JAR to dev server
- **Restart Server**: Build and full server restart on dev server
- **Reload Server**: Build and plugin reload only via api call

### Testing and Troubleshooting Tools

#### MC Server Soft (MCSS) API Integration

For comprehensive testing and debugging, utilize the MCSS API for real-time server interaction:

- **Console Monitoring**: Use MCSS API to read server console output in real-time
- **Command Execution**: Execute plugin commands remotely via REST API
- **Performance Monitoring**: Track server performance during plugin operations
- **Error Analysis**: Programmatically search console logs for errors and exceptions

**Reference Documentation**: `docs/api-reference/mcss-dev-server.md`

**Key MCSS API Endpoints**:
- `GET /api/v2/servers/{serverId}/console` - Read console output
- `POST /api/v2/servers/{serverId}/execute/command` - Execute single commands
- `POST /api/v2/servers/{serverId}/execute/commands` - Execute multiple commands
- `GET /api/v2/servers/{serverId}/stats` - Monitor server performance

**Example Usage**:
```bash
# Test plugin reload via MCSS API
curl -X POST http://localhost:25564/api/v2/servers/{serverId}/execute/command \
  -H "apiKey: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"command": "rvnktools reload"}'

# Monitor console for errors
curl -X GET "http://localhost:25564/api/v2/servers/{serverId}/console?AmountOfLines=20" \
  -H "apiKey: YOUR_API_KEY" | grep -i "error\|exception"
```

This integration enables automated testing, continuous monitoring, and rapid troubleshooting during plugin development.

## Testing Guidelines

- Test commands with various input combinations
- Verify permission handling
- Check resource cleanup
- Test integration points
- Validate configuration handling

## Documentation Reference

For detailed information, refer to:

- [README.md](../README.md) - Project overview and features
- [ROADMAP.md](../ROADMAP.md) - Development roadmap and priorities

## Service Interface and Implementation Naming Conventions

- **Do not use the `I` prefix for interfaces.**
  - Example: Use `PlayerService` instead of `IPlayerService`.
- **Service interfaces should use descriptive names ending with `Service`, `Repository`, or `Manager` as appropriate.**
  - Example: `PlayerService`, `AnnouncementService`, `WorldService`, `EconomyService`.
- **Implementation classes should use a clear suffix such as `Default`, `Sql`, or another specific descriptor.**
  - Example: `DefaultPlayerService`, `SqlPlayerService`, `CorePlayerService`.
- **All references, imports, and documentation should reflect these conventions for consistency.**
- **This applies to all plugin modules and shared RVNKCore services.**

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
```java
public interface PlayerService {
    // Async: Database operations
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<List<PlayerDTO>> searchPlayers(String namePattern);
    
    // Sync: Cache/memory operations
    Optional<PlayerDTO> getCachedPlayer(UUID playerId);
    boolean isPlayerOnline(UUID playerId);
    
    // Sync: Simple validation
    boolean isValidPlayerName(String name);
}
```

### Command Framework Integration
- Validate synchronously (permissions, args, format)
- Use async for database/API operations
- Provide immediate feedback to users
- Handle async results with proper error messages

```java
@Override
protected void execute(CommandSender sender, String[] args) {
    // Sync validation first
    if (!hasPermission(sender, "permission")) {
        sender.sendMessage("No permission");
        return;
    }
    
    // Then async work
    service.doWork(args)
        .thenAccept(result -> sender.sendMessage("Success: " + result))
        .exceptionally(ex -> {
            logger.error("Operation failed", ex);
            sender.sendMessage("Operation failed");
            return null;
        });
}
```

### Performance Rules
- Don't async operations that take <10ms
- Use caching to reduce database calls
- Batch operations instead of individual async calls
- Consider thread pool limits