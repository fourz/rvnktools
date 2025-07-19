# RVNKCore Copilot Instructions

These guidelines outline the coding standards, patterns, and best practices for developing RVNKCore and downstream plugins that utilize the RVNKCore API. Following these standards ensures consistency throughout the codebase and a seamless integration experience.

## General Directives

- **Follow SOLID principles for all component design.**
- **Ensure proper resource cleanup in all services and managers.**
- **Use asynchronous programming for potentially blocking operations.**
- **Create comprehensive tests for all core functionality.**
- **Document all public APIs with complete JavaDoc.**
- **Keep command implementations simple and focused.**
- **Use LogManager for all logging operations. Do not use System.out or direct logger instances.**

## Package Structure

```
org.fourz.rvnkcore/
├── api/                    # Public API interfaces
│   ├── service/            # Service interfaces
│   ├── model/              # Data transfer objects
│   ├── event/              # Event interfaces
│   └── exception/          # API exceptions
├── database/               # Database implementation
│   ├── connection/         # Connection management
│   ├── query/              # Query building
│   ├── repository/         # Data repositories
│   └── schema/             # Schema management
├── service/                # Service implementations
│   ├── registry/           # Service registry
│   ├── player/             # Player services
│   ├── config/             # Configuration services
│   └── ...                 # Other services
├── util/                   # Utility classes
│   ├── log/                # Logging framework
│   ├── concurrent/         # Concurrency utilities
│   └── ...                 # Other utilities
└── RVNKCore.java           # Main plugin class
```

## Coding Standards

### Naming Conventions

1. **Interfaces**: Prefixed with 'I' (e.g., `IPlayerService`)
2. **Implementation Classes**: Named after the interface without prefix (e.g., `PlayerService`)
3. **DTOs**: Suffixed with 'DTO' (e.g., `PlayerDTO`)
4. **Repositories**: Suffixed with 'Repository' (e.g., `PlayerRepository`)
5. **Service Classes**: Suffixed with 'Service' (e.g., `AnnouncementService`)
6. **Managers**: Suffixed with 'Manager' (e.g., `ConfigurationManager`)
7. **Factories**: Suffixed with 'Factory' (e.g., `ConnectionFactory`)
8. **Exceptions**: Suffixed with 'Exception' (e.g., `DatabaseException`)

### Documentation Standards

#### JavaDoc Requirements

All public APIs must have comprehensive JavaDoc, including:

- Class/interface purpose description
- Method descriptions explaining behavior (not implementation)
- Parameter documentation
- Return value documentation
- Exception documentation
- Usage examples for complex methods

```java
/**
 * Manages player data and metadata across plugins.
 * Provides a unified way to access player information regardless of the data source.
 * <p>
 * This service handles caching, persistence, and synchronization of player data.
 */
public interface IPlayerService {
    /**
     * Retrieves a player by UUID.
     * <p>
     * This method will first check the cache before querying the database.
     * If the player is online, the most current data is always returned.
     * 
     * @param id The UUID of the player to retrieve
     * @return A CompletableFuture containing the player data, or empty if not found
     * @throws IllegalArgumentException If id is null
     */
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID id);
    
    // More methods...
}
```

#### Package Documentation

All packages must include `package-info.java` with a description of the package purpose:

```java
/**
 * Provides service interfaces for the RVNKCore API.
 * <p>
 * These interfaces define the contract between RVNKCore and client plugins.
 * All service implementations adhere to these interfaces.
 */
package org.fourz.rvnkcore.api.service;
```

### Error Handling

#### Exception Hierarchy

```
- RVNKException (base exception for all RVNKCore exceptions)
  |- ServiceException (base for service-related exceptions)
     |- ServiceNotFoundException
     |- ServiceInitializationException
  |- DatabaseException (base for database-related exceptions)
     |- ConnectionException
     |- QueryException
     |- MigrationException
  |- ConfigException (base for configuration-related exceptions)
     |- ValidationException
     |- MigrationException
  |- APIException (base for API-related exceptions)
     |- VersionMismatchException
     |- PermissionDeniedException
```

#### Exception Handling Practices

1. **Use checked exceptions only when recovery is possible.**
2. **Use unchecked exceptions for programming errors.**
3. **Include meaningful context in exception messages.**
4. **Always log exceptions with appropriate level.**
5. **Use try-with-resources for all closeable resources.**

```java
public CompletableFuture<PlayerDTO> getPlayerById(UUID id) {
    Objects.requireNonNull(id, "Player ID cannot be null");
    
    return CompletableFuture.supplyAsync(() -> {
        try (Connection conn = connectionProvider.getConnection()) {
            // Query logic
            return playerDTO;
        } catch (SQLException e) {
            logger.error("Failed to retrieve player with ID: " + id, e);
            throw new DatabaseException("Could not retrieve player data", e);
        } catch (Exception e) {
            logger.error("Unexpected error retrieving player: " + id, e);
            throw new ServiceException("Unexpected error in player service", e);
        }
    });
}
```

### Asynchronous Programming

All potentially blocking operations (database, I/O, network) must use asynchronous programming:

1. **Use CompletableFuture for all async operations.**
2. **Handle exceptions in all async chains.**
3. **Avoid blocking the main thread.**
4. **Provide meaningful completion callbacks.**

```java
public CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements() {
    return CompletableFuture.supplyAsync(() -> {
        // Database query logic
        return announcements;
    }).exceptionally(ex -> {
        logger.error("Failed to retrieve active announcements", ex);
        return Collections.emptyList();
    });
}
```

### Dependency Injection

RVNKCore uses a lightweight service locator pattern through the ServiceRegistry:

```java
public class AnnouncementService implements IAnnouncementService {
    private final ConnectionProvider connectionProvider;
    private final AnnouncementRepository repository;
    private final LogManager logger;
    
    public AnnouncementService(ServiceRegistry registry) {
        this.connectionProvider = registry.getService(ConnectionProvider.class);
        this.repository = registry.getService(AnnouncementRepository.class);
        this.logger = LogManager.getInstance(RVNKCore.getInstance(), getClass());
    }
    
    // Implementation
}
```

## Logging Framework Standards

### LogManager Usage

The LogManager class is the **required** centralized logging system for all components:

1. **Declaration**: Always declare the logger as `private final LogManager logger;` in classes.
2. **Initialization**: Initialize with `this.logger = LogManager.getInstance(plugin, getClass());` in constructors.
3. **Method Usage**: Use appropriate severity methods: `logger.info()`, `logger.warning()`, `logger.error()`, `logger.debug()`.
4. **Error Reporting**: Always include the exception in error logs: `logger.error("Message", exception)`.
5. **No Direct Logger Access**: Never use `System.out.println()`, Bukkit logger, or Java logger directly.

```java
public class ExampleService implements IExampleService {
    private final LogManager logger;
    
    public ExampleService(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    public void performAction() {
        try {
            // Operation code
            logger.info("Action completed successfully");
        } catch (Exception e) {
            logger.error("Failed to perform action", e);
            throw new ServiceException("Action failed", e);
        }
    }
}
```

### DebugLogger for Performance Monitoring

For performance-critical code sections, use the DebugLogger implementation:

1. **Usage**: Use DebugLogger for components where performance monitoring is needed.
2. **Metrics**: Use `timeSection()` to profile code sections automatically.
3. **Conditional Logging**: Debug logs only appear when debug mode is enabled.

```java
public class PerformanceCriticalService {
    private final DebugLogger logger;
    
    public PerformanceCriticalService(Plugin plugin) {
        this.logger = new DebugLogger(plugin, getClass());
    }
    
    public Result processData(Data data) {
        try (AutoCloseable timer = logger.timeSection("processData")) {
            // Processing code
            return result;
        } catch (Exception e) {
            logger.error("Data processing failed", e);
            throw new ServiceException("Processing failed", e);
        }
    }
    
    public Map<String, Long> getPerformanceMetrics() {
        return logger.getPerformanceMetrics();
    }
}
```

### Message Formatting Standards

#### Console and Debug Messages

- **Informational**: Concise and clear descriptions of normal operations
- **Warnings**: Actionable warnings with clear context
- **Errors**: Detailed error messages with contextual information
- **Debug**: Detailed information useful during development
- **No Color Codes**: Never use color codes in console/log messages
- **No Emojis**: Never use emojis or special characters in logs
- **Clean Format**: Always use `ChatFormat.stripColors()` when logging player-visible text

```java
// Good logging examples
logger.info("Loaded 42 announcements from database");
logger.warning("Failed to load player preferences, using defaults");
logger.error("Database connection failed", exception);
logger.debug("Processing player login: " + player.getName() + " with permissions: " + permissions);
```

## Command Implementation Guidelines

RVNKCore commands should be simple and focused. Follow these guidelines for command implementation:

### Basic Command Structure

1. **Direct Implementation**: Implement `CommandExecutor` and `TabCompleter` interfaces directly
2. **Clear Purpose**: Each command should have a single, well-defined purpose
3. **Simple Validation**: Include basic permission and argument validation
4. **Helpful Messages**: Provide clear usage and error messages
5. **Clean Organization**: Keep command logic organized and readable

Example command implementation:

```java
public class CoreCommand implements CommandExecutor, TabCompleter {
    private final RVNKCore plugin;
    private final LogManager logger;
    
    public CoreCommand(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rvnkcore.command.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (sender.hasPermission("rvnkcore.command.reload")) {
                    handleReload(sender);
                }
                break;
            case "info":
                if (sender.hasPermission("rvnkcore.command.info")) {
                    showPluginInfo(sender);
                }
                break;
            default:
                showHelp(sender);
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("rvnkcore.command.reload")) completions.add("reload");
            if (sender.hasPermission("rvnkcore.command.info")) completions.add("info");
            return completions;
        }
        return Collections.emptyList();
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "RVNKCore Commands:");
        if (sender.hasPermission("rvnkcore.command.reload")) {
            sender.sendMessage(ChatColor.GOLD + "/rvnkcore reload " + ChatColor.WHITE + "- Reload configuration");
        }
        if (sender.hasPermission("rvnkcore.command.info")) {
            sender.sendMessage(ChatColor.GOLD + "/rvnkcore info " + ChatColor.WHITE + "- Show plugin information");
        }
    }
    
    private void handleReload(CommandSender sender) {
        try {
            // Reload logic
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully.");
            logger.info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration.");
            logger.error("Failed to reload configuration", e);
        }
    }
    
    private void showPluginInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "RVNKCore v" + plugin.getDescription().getVersion());
        // Additional info...
    }
}

## Database Access Patterns

### Repository Pattern

All database access should use the repository pattern:

```java
public class PlayerRepository extends BaseRepository<PlayerDTO, UUID> {
    
    public PlayerRepository(ConnectionProvider provider, QueryBuilder queryBuilder) {
        super(provider, queryBuilder, PlayerDTO.class);
    }
    
    @Override
    protected String getTableName() {
        return "players";
    }
    
    public CompletableFuture<List<PlayerDTO>> findByLastSeen(Timestamp since) {
        return executeQueryList(
            queryBuilder.select("*")
                      .from(getTableName())
                      .where("last_seen > ?", since)
                      .orderBy("last_seen", false),
            PlayerDTO.class
        );
    }
}
```

### Data Transfer Objects

Use DTOs for all data transfer between layers:

```java
public class PlayerDTO {
    private UUID id;
    private String username;
    private Timestamp firstJoin;
    private Timestamp lastSeen;
    private boolean banned;
    private Map<String, Object> metadata;
    
    // Getters and setters
    
    // Builder pattern for construction
    public static class Builder {
        private PlayerDTO dto = new PlayerDTO();
        
        public Builder id(UUID id) {
            dto.id = id;
            return this;
        }
        
        // Other builder methods
        
        public PlayerDTO build() {
            return dto;
        }
    }
}
```

### Transaction Management

Use transactions for multi-operation sequences:

```java
public CompletableFuture<Boolean> updatePlayerAndPreferences(PlayerDTO player, Map<String, String> preferences) {
    return transactionManager.inTransaction(() -> {
        return playerRepository.save(player)
            .thenCompose(savedPlayer -> {
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (Map.Entry<String, String> entry : preferences.entrySet()) {
                    futures.add(preferenceRepository.savePreference(
                        player.getId(), entry.getKey(), entry.getValue()));
                }
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> true);
            });
    });
}
```

## Service Development Guidelines

### Service Lifecycle

All services should implement a consistent lifecycle:

```java
public class MyService implements IMyService, AutoCloseable {
    private final LogManager logger;
    private boolean initialized = false;
    
    public MyService() {
        this.logger = LogManager.getInstance();
    }
    
    public void initialize() {
        if (initialized) return;
        
        // Initialization logic
        
        initialized = true;
        logger.info("MyService initialized successfully");
    }
    
    @Override
    public void close() {
        if (!initialized) return;
        
        // Cleanup logic
        
        initialized = false;
        logger.info("MyService shut down successfully");
    }
    
    // Service methods
}
```

### Service Registration

Services should be registered with the ServiceRegistry:

```java
// In RVNKCore main class
private void initializeServices() {
    // Create instances
    PlayerService playerService = new PlayerService();
    AnnouncementService announcementService = new AnnouncementService();
    
    // Initialize
    playerService.initialize();
    announcementService.initialize();
    
    // Register
    serviceRegistry.registerService(IPlayerService.class, playerService);
    serviceRegistry.registerService(IAnnouncementService.class, announcementService);
    
    logger.info("Core services initialized and registered");
}
```

## Logging Standards

### LogManager Usage

Use the LogManager for all logging:

```java
private final LogManager logger = LogManager.getInstance();

public void doSomething() {
    logger.debug("Starting operation with debug info");
    logger.info("Operation in progress");
    
    try {
        // Operation logic
    } catch (Exception e) {
        logger.error("Failed to perform operation", e);
        throw e;
    }
}
```

### Log Levels

Use appropriate log levels:

- **DEBUG**: Detailed information useful for debugging
- **INFO**: General information about system operation
- **WARNING**: Potential issues that don't prevent operation
- **ERROR**: Errors that prevent operation and require intervention

## Testing Guidelines

### Unit Testing

All core components must have unit tests:

```java
@Test
public void testPlayerServiceGetPlayer() {
    // Arrange
    UUID playerId = UUID.randomUUID();
    PlayerDTO expectedPlayer = new PlayerDTO.Builder()
        .id(playerId)
        .username("testplayer")
        .build();
    
    // Mock repository
    when(playerRepository.findById(playerId))
        .thenReturn(CompletableFuture.completedFuture(expectedPlayer));
    
    // Act
    PlayerDTO actualPlayer = playerService.getPlayer(playerId).join();
    
    // Assert
    assertEquals(expectedPlayer.getId(), actualPlayer.getId());
    assertEquals(expectedPlayer.getUsername(), actualPlayer.getUsername());
}
```

### Integration Testing

Integration tests should verify component interactions:

```java
@Test
public void testDatabaseServiceIntegration() {
    // Create test database connection
    ConnectionProvider provider = new SQLiteConnectionProvider(":memory:");
    
    // Create schema
    SchemaManager schemaManager = new SchemaManager(provider);
    schemaManager.createSchema();
    
    // Create repository
    PlayerRepository repository = new PlayerRepository(provider, new SQLiteQueryBuilder());
    
    // Create player
    PlayerDTO player = new PlayerDTO.Builder()
        .id(UUID.randomUUID())
        .username("testplayer")
        .build();
    
    // Save and retrieve
    repository.save(player).join();
    PlayerDTO retrieved = repository.findById(player.getId()).join();
    
    // Verify
    assertEquals(player.getId(), retrieved.getId());
    assertEquals(player.getUsername(), retrieved.getUsername());
}
```

## API Design Guidelines

### Interface Design

1. **Keep interfaces focused on a single responsibility.**
2. **Use method names that clearly express intent.**
3. **Provide sensible default methods where appropriate.**
4. **Design for extensibility without breaking changes.**

```java
public interface IAnnouncementService {
    // Core methods
    CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement);
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    CompletableFuture<List<AnnouncementDTO>> getAnnouncements(Optional<String> type);
    
    // Default convenience methods
    default CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements() {
        return getAnnouncements(Optional.empty());
    }
    
    default CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type) {
        return getAnnouncements(Optional.of(type));
    }
}
```

### Versioning

1. **Use semantic versioning for all APIs.**
2. **Deprecate legacy methods before removing them.**
3. **Provide migration paths for breaking changes.**
4. **Document version compatibility requirements.**

```java
/**
 * @deprecated As of version 2.0.0, replaced by {@link #newMethod()}
 * Will be removed in version 3.0.0
 */
@Deprecated
public CompletableFuture<String> oldMethod() {
    // Forward to new method
    return newMethod();
}

/**
 * New implementation introduced in version 2.0.0
 */
public CompletableFuture<String> newMethod() {
    // Implementation
}
```

## Resource Management

### Resource Cleanup

All resources must be properly cleaned up:

```java
public void shutdown() {
    // Close resources in reverse initialization order
    
    // 1. Shutdown scheduled tasks
    if (scheduledExecutor != null) {
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // 2. Close database connections
    if (connectionProvider != null) {
        connectionProvider.close();
    }
    
    // 3. Clear caches
    if (cache != null) {
        cache.invalidateAll();
    }
    
    logger.info("Resources cleaned up successfully");
}
```

### Memory Management

Be conscious of memory usage:

1. **Use soft references for caches.**
2. **Set appropriate cache size limits.**
3. **Clean up thread-local variables.**
4. **Avoid holding references to unused objects.**

```java
private final Cache<UUID, PlayerDTO> playerCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .softValues()
    .build();
```

## Plugin Integration Guidelines

### Plugin Registration

Plugins using RVNKCore should register themselves:

```java
public class MyPlugin extends JavaPlugin {
    private RVNKCoreAPI coreAPI;
    
    @Override
    public void onEnable() {
        // Get RVNKCore API
        Plugin corePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (corePlugin == null || !(corePlugin instanceof RVNKCore)) {
            getLogger().severe("RVNKCore not found or incompatible version!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        coreAPI = ((RVNKCore) corePlugin).getAPI();
        
        // Register plugin
        coreAPI.registerPlugin(this, "my-plugin", getDescription().getVersion());
        
        // Initialize with core services
        initializeWithCore();
    }
    
    private void initializeWithCore() {
        // Get required services
        IPlayerService playerService = coreAPI.getService(IPlayerService.class);
        IAnnouncementService announcementService = coreAPI.getService(IAnnouncementService.class);
        
        // Use services
        // ...
    }
}
```

### Service Consumption

Use the service registry to access core services:

```java
public class MyFeature {
    private final IPlayerService playerService;
    private final IAnnouncementService announcementService;
    
    public MyFeature(RVNKCoreAPI coreAPI) {
        this.playerService = coreAPI.getService(IPlayerService.class);
        this.announcementService = coreAPI.getService(IAnnouncementService.class);
    }
    
    public void doSomething(UUID playerId) {
        playerService.getPlayer(playerId)
            .thenAccept(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO player = playerOpt.get();
                    // Use player data
                }
            });
    }
}
```

## Configuration Guidelines

### Configuration Structure

Organize configuration files by feature:

```yaml
# Main config.yml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    # ...

services:
  player:
    cache-size: 1000
    cache-expiry: 30
  announcement:
    default-type: info
    broadcast-interval: 300

api:
  rest-enabled: true
  port: 8080
  auth-required: true
```

### Configuration Validation

Validate all configuration values:

```java
public class ServiceConfig {
    private final int cacheSize;
    private final int cacheExpiry;
    
    public ServiceConfig(ConfigurationSection section) {
        // Get values with validation
        this.cacheSize = validateCacheSize(section.getInt("cache-size", 1000));
        this.cacheExpiry = validateCacheExpiry(section.getInt("cache-expiry", 30));
    }
    
    private int validateCacheSize(int size) {
        if (size < 10) {
            logger.warning("Cache size too small, using minimum value of 10");
            return 10;
        }
        if (size > 10000) {
            logger.warning("Cache size too large, using maximum value of 10000");
            return 10000;
        }
        return size;
    }
    
    private int validateCacheExpiry(int minutes) {
        if (minutes < 1) {
            logger.warning("Cache expiry too small, using minimum value of 1 minute");
            return 1;
        }
        if (minutes > 1440) {
            logger.warning("Cache expiry too large, using maximum value of 1440 minutes (24 hours)");
            return 1440;
        }
        return minutes;
    }
    
    // Getters
}
```

## Performance Considerations

1. **Use connection pooling for database connections.**
2. **Implement caching for frequently accessed data.**
3. **Use batch operations for multiple database updates.**
4. **Minimize main thread blocking operations.**
5. **Profile performance-critical sections.**
6. **Use async operations for I/O-bound tasks.**

## Security Guidelines

1. **Validate all user input.**
2. **Use parameterized queries for database access.**
3. **Implement proper permission checks.**
4. **Sanitize data for display.**
5. **Protect sensitive configuration (passwords, keys).**
6. **Log security-relevant events.**

## Documentation Reference

For more detailed information, refer to:

- [API Documentation](api-documentation.md)
- [Database Architecture](database-architecture.md)
- [Service Development Guide](service-development.md)
- [Integration Guide](integration-guide.md)
