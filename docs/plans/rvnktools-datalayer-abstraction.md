# RVNKCore Data Layer Abstraction Plan

*Last Updated: July 21, 2025*

This document outlines the plan for separating RVNKTools into distinct components with a centralized data layer (RVNKCore) that can be shared across multiple plugins.

## 1. Overview and Goals

### Primary Objectives

1. **Decouple Data Layer**: Isolate database and API components into a core module (RVNKCore)
2. **Create Clean Interfaces**: Establish well-defined interfaces for all plugin interactions
3. **Enable Cross-Plugin Data Access**: Allow multiple plugins to access shared data in a consistent manner
4. **Maintain Backward Compatibility**: Ensure existing RVNKTools functionality continues to work
5. **Prepare for Future Plugins**: Build a foundation that future plugins can leverage

### Target Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                          RVNKCore                              │
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ Database    │  │ API Layer   │  │ Shared Services         │ │
│  │ Layer       │◀─┤             │◀─┤                         │ │
│  │             │  │             │  │ - Player Registry       │ │
│  │ - Connection│  │ - REST API  │  │ - Permission Manager    │ │
│  │ - Schema    │  │ - Java API  │  │ - Configuration Manager │ │
│  │ - Repository│  │             │  │ - Logging Framework     │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
                           ▲
                           │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│   RVNKTools     │ │  RVNKLore   │ │   RVNKQuests    │
│                 │ │             │ │                 │
│ - Announcements │ │ - Items     │ │ - Quest System  │
│ - Hat Manager   │ │ - Lore      │ │ - Rewards       │
│ - Link System   │ │ - Collections│ │ - Objectives    │
└─────────────────┘ └─────────────┘ └─────────────────┘
```

## 2. Current Architecture Assessment

### Existing Components in RVNKTools

1. **Command Framework**: The recently completed CommandManager framework
2. **Data Storage**: SQLite support with plans for MySQL
3. **Utility Services**: Logging, configuration, messaging
4. **Feature Components**: Announcements, hat management, link creation
5. **Integration Points**: PlaceholderAPI, Multiverse, etc.

### Current Challenges

1. **Tight Coupling**: Features are directly integrated with data access
2. **Limited Abstraction**: Minimal separation between layers
3. **Component Isolation**: Services are not clearly defined or isolated
4. **Cross-Plugin Sharing**: No mechanism for sharing data between plugins
5. **Configuration Management**: Each feature manages its own configuration

## 3. RVNKCore Architecture Design

### 3.1 Core Components

#### Database Layer

The database layer will be based on the framework outlined in `docs/examples/rvnk-database-provider-framework.md` with the following enhancements:

1. **Connection Management**:
   - Support for both SQLite and MySQL
   - Connection pooling via HikariCP
   - Health monitoring and reconnection
   - Transaction management

2. **Repository Framework**:
   - Base repository classes for common operations
   - Repository registration system
   - Automatic mapping between database and DTOs
   - Query builder for dialect-agnostic SQL

3. **Schema Management**:
   - Version-controlled schema definitions
   - Automatic schema validation
   - Migration support for schema changes
   - Table prefix support for multi-plugin deployments

#### API Layer

1. **Java API**:
   - Core interfaces for all major components
   - Service registry for plugin communication
   - Event system for cross-plugin notification
   - Annotation-based service discovery

2. **REST API**:
   - Embedded HTTP server (NanoHTTPD)
   - Authentication and authorization
   - JSON-based data exchange
   - Rate limiting and security controls

#### Shared Services

1. **Player Registry**:
   - Centralized player tracking
   - Cross-server synchronization
   - Player metadata storage
   - Player session management

2. **Permission Manager**:
   - Unified permission system
   - Integration with LuckPerms
   - Permission inheritance
   - Dynamic permission assignment

3. **Configuration Manager**:
   - Centralized configuration storage
   - Type-safe configuration access
   - Auto-reloading configuration
   - Configuration versioning

4. **Logging Framework**:
   - Enhanced logging capabilities
   - Log level configuration
   - Log rotation and retention
   - Performance logging for critical sections

### 3.2 Interface Design

All components will expose clean interfaces that define their behavior. This enables:

1. Mock implementations for testing
2. Alternative implementations for specific needs
3. Clear contracts between components
4. Documentation of expected behavior

Example interface for Player Registry:
```java
public interface PlayerRegistry {
    /**
     * Gets a player by UUID.
     * 
     * @param id The player's UUID
     * @return A CompletableFuture containing the player data, or null if not found
     */
    CompletableFuture<PlayerData> getPlayer(UUID id);
    
    /**
     * Gets a player by name.
     * 
     * @param name The player's name
     * @return A CompletableFuture containing the player data, or null if not found
     */
    CompletableFuture<PlayerData> getPlayerByName(String name);
    
    /**
     * Updates player data.
     * 
     * @param player The player data to update
     * @return A CompletableFuture containing the updated player data
     */
    CompletableFuture<PlayerData> updatePlayer(PlayerData player);
    
    /**
     * Registers a callback for player events.
     * 
     * @param eventType The type of event to listen for
     * @param callback The callback to invoke when the event occurs
     * @return A registration handle that can be used to unregister the callback
     */
    RegistrationHandle onPlayerEvent(PlayerEventType eventType, Consumer<PlayerEvent> callback);
}
```

## 4. Migration Strategy

### 4.1 Phase 1: Preparation (Current Phase)

1. **Architectural Planning**:
   - Document current architecture
   - Define target architecture
   - Identify dependencies and interfaces
   - Create migration roadmap

2. **Codebase Analysis**:
   - Identify core components for extraction
   - Document existing interfaces and patterns
   - Analyze data access patterns
   - Identify cross-cutting concerns

3. **Test Harness Development**:
   - Create testing framework
   - Establish baseline performance metrics
   - Define success criteria
   - Create automated tests for core functionality

### 4.2 Phase 2: Core Framework Development

1. **Database Framework**:
   - Implement ConnectionProvider interfaces
   - Create QueryBuilder and QueryExecutor
   - Develop Repository base classes
   - Build schema management tools

2. **Service Framework**:
   - Develop service registry
   - Create service discovery mechanism
   - Implement event system
   - Build dependency injection framework

3. **Configuration Framework**:
   - Create ConfigurationManager
   - Implement configuration versioning
   - Build migration tools for config changes
   - Add validation for configurations

4. **Logging Framework**:
   - Enhance LogManager
   - Add performance logging
   - Implement log routing
   - Create debug logging tools

### 4.3 Phase 3: Feature Migration

1. **Announcement System**:
   - Extract data models and repositories
   - Create service interfaces
   - Refactor existing implementation
   - Update command handlers

2. **Hat Manager**:
   - Extract data models and repositories
   - Create service interfaces
   - Refactor existing implementation
   - Update command handlers

3. **Link System**:
   - Extract data models and repositories
   - Create service interfaces
   - Refactor existing implementation
   - Update command handlers

4. **Integration Points**:
   - Refactor PlaceholderAPI integration
   - Update Multiverse integration
   - Enhance Vault integration
   - Extract integration interfaces

### 4.4 Phase 4: Plugin Separation

1. **RVNKCore Plugin**:
   - Create standalone plugin structure
   - Implement plugin lifecycle management
   - Add service initialization sequence
   - Create dependency management

2. **RVNKTools Plugin**:
   - Update to depend on RVNKCore
   - Refactor to use core services
   - Update configuration management
   - Add compatibility layer if needed

3. **Documentation and Examples**:
   - Create developer guides
   - Update user documentation
   - Provide example implementations
   - Document migration paths

## 5. Technical Implementation Details

### 5.1 Core Service Registry

The central component of RVNKCore will be the `ServiceRegistry` which manages all services:

```java
public class ServiceRegistry {
    private static ServiceRegistry instance;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    
    private ServiceRegistry() {
        // Private constructor for singleton
    }
    
    public static synchronized ServiceRegistry getInstance() {
        if (instance == null) {
            instance = new ServiceRegistry();
        }
        return instance;
    }
    
    public <T> void registerService(Class<T> serviceClass, T implementation) {
        services.put(serviceClass, implementation);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) services.get(serviceClass);
    }
    
    public <T> boolean hasService(Class<T> serviceClass) {
        return services.containsKey(serviceClass);
    }
    
    public void shutdown() {
        // Orderly shutdown of all services
        for (Object service : services.values()) {
            if (service instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) service).close();
                } catch (Exception e) {
                    // Log error
                }
            }
        }
        services.clear();
    }
}
```

### 5.2 Data Transfer Objects

DTOs will serve as the primary data exchange format between layers:

```java
public class PlayerDTO {
    private UUID id;
    private String username;
    private Timestamp lastSeen;
    private Timestamp firstJoin;
    private boolean isBanned;
    private Map<String, Object> metadata;
    
    // Getters and setters
}

public class AnnouncementDTO {
    private String id;
    private String message;
    private String type;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp expiresAt;
    private Map<String, Object> metadata;
    
    // Getters and setters
}
```

### 5.3 Repository Base Class

A base repository class will provide common functionality:

```java
public abstract class BaseRepository<T, ID> {
    protected final ConnectionProvider connectionProvider;
    protected final QueryBuilder queryBuilder;
    protected final QueryExecutor queryExecutor;
    protected final LogManager logger;
    protected final Class<T> entityClass;
    
    protected BaseRepository(ConnectionProvider connectionProvider, 
                           QueryBuilder queryBuilder,
                           QueryExecutor queryExecutor,
                           LogManager logger,
                           Class<T> entityClass) {
        this.connectionProvider = connectionProvider;
        this.queryBuilder = queryBuilder;
        this.queryExecutor = queryExecutor;
        this.logger = logger;
        this.entityClass = entityClass;
    }
    
    public CompletableFuture<T> findById(ID id) {
        return queryExecutor.executeQuery(
            queryBuilder.select("*")
                      .from(getTableName())
                      .where(getIdColumnName() + " = ?", id),
            entityClass
        );
    }
    
    public CompletableFuture<List<T>> findAll() {
        return queryExecutor.executeQueryList(
            queryBuilder.select("*").from(getTableName()),
            entityClass
        );
    }
    
    protected abstract String getTableName();
    
    protected String getIdColumnName() {
        return "id";
    }
}
```

### 5.4 Plugin Integration

RVNKCore will provide a simple integration point for plugins:

```java
public class RVNKCore {
    private static RVNKCore instance;
    private final Plugin plugin;
    private final ServiceRegistry serviceRegistry;
    private final LogManager logger;
    private boolean initialized = false;
    
    private RVNKCore(Plugin plugin) {
        this.plugin = plugin;
        this.serviceRegistry = ServiceRegistry.getInstance();
        this.logger = LogManager.getInstance(plugin);
    }
    
    public static synchronized RVNKCore getInstance(Plugin plugin) {
        if (instance == null) {
            instance = new RVNKCore(plugin);
        }
        return instance;
    }
    
    public void initialize() {
        if (initialized) return;
        
        // Initialize core services
        initializeDatabase();
        initializeServices();
        
        initialized = true;
        logger.info("RVNKCore initialized successfully");
    }
    
    public <T> T getService(Class<T> serviceClass) {
        return serviceRegistry.getService(serviceClass);
    }
    
    private void initializeDatabase() {
        // Initialize database connection and repositories
    }
    
    private void initializeServices() {
        // Initialize core services
    }
}
```

## 6. API Standardization Guidelines

To ensure consistency across all components using the RVNKCore API, the following standards should be enforced:

### 6.1 Naming Conventions

1. **Interfaces**: Prefixed with 'I' (e.g., `IPlayerService`)
2. **Implementation Classes**: Named after the interface without prefix (e.g., `PlayerService`)
3. **DTOs**: Suffixed with 'DTO' (e.g., `PlayerDTO`)
4. **Repositories**: Suffixed with 'Repository' (e.g., `PlayerRepository`)
5. **Service Classes**: Suffixed with 'Service' (e.g., `AnnouncementService`)
6. **Managers**: Suffixed with 'Manager' (e.g., `ConfigurationManager`)

### 6.2 Method Signatures

1. **Asynchronous Operations**: All potentially blocking operations must return `CompletableFuture`
2. **Exception Handling**: Use checked exceptions only when recovery is possible
3. **Null Handling**: Use Optional for values that may be absent
4. **Parameter Validation**: All public methods must validate parameters
5. **Return Values**: Never return null from non-Optional methods

### 6.3 Documentation Requirements

1. **Interface Documentation**: All interfaces must have complete JavaDoc
2. **Method Documentation**: All public methods must include:
   - Purpose description
   - Parameter descriptions
   - Return value description
   - Exception descriptions
   - Usage examples for complex methods
3. **Class Documentation**: All classes must have an overview comment
4. **Package Documentation**: All packages must have package-info.java

### 6.4 Error Handling

1. **Use Specific Exceptions**: Create and use domain-specific exceptions
2. **Log All Exceptions**: All caught exceptions must be logged
3. **Provide Context**: Include relevant context in exception messages
4. **Return Error Objects**: Return error information rather than throwing exceptions when appropriate

### 6.5 Versioning

1. **API Versions**: Major changes must increase the API version
2. **Deprecation**: Methods should be deprecated before removal
3. **Compatibility**: Backward compatibility must be maintained within a major version
4. **Documentation**: Changes must be documented in changelogs

## 7. Implementation Roadmap

### 7.1 Immediate Next Steps (Q3 2025)

1. **Core Framework Design** (3 weeks)
   - Finalize interface definitions
   - Create class hierarchy diagrams
   - Document dependencies
   - Define component interactions

2. **Database Layer Implementation** (4 weeks)
   - Implement ConnectionProvider
   - Create QueryBuilder
   - Develop Repository framework
   - Build schema management

3. **Service Framework Development** (3 weeks)
   - Create ServiceRegistry
   - Implement event system
   - Build dependency injection
   - Develop service lifecycle management

### 7.2 Medium-Term Goals (Q4 2025)

1. **Feature Migration** (6 weeks)
   - Extract announcement system
   - Refactor hat manager
   - Migrate link system
   - Update integration points

2. **API Development** (4 weeks)
   - Create REST API
   - Implement authentication
   - Build API documentation
   - Develop client libraries

3. **Testing and Optimization** (4 weeks)
   - Create comprehensive test suite
   - Perform load testing
   - Optimize critical paths
   - Fix identified issues

### 7.3 Long-Term Goals (Q1-Q2 2026)

1. **Plugin Separation** (8 weeks)
   - Create RVNKCore plugin
   - Update RVNKTools to use core
   - Develop example plugins
   - Create migration guides

2. **Advanced Features** (12 weeks)
   - Implement cross-server synchronization
   - Add advanced analytics
   - Develop administration dashboard
   - Create extensibility framework

## 8. Success Metrics

1. **Code Quality**:
   - Reduced cyclomatic complexity
   - Increased test coverage
   - Fewer dependencies between components
   - Improved maintainability scores

2. **Performance**:
   - Reduced server impact
   - Faster operation times
   - Lower memory usage
   - Improved startup time

3. **Developer Experience**:
   - Reduced time to implement new features
   - Fewer bugs in new implementations
   - Positive developer feedback
   - Increased adoption by other plugins

4. **User Experience**:
   - No regression in functionality
   - Improved reliability
   - New capabilities enabled by the framework
   - Positive user feedback

## 9. Risks and Mitigations

### 9.1 Identified Risks

1. **Breaking Changes**: API changes may break existing functionality
   - **Mitigation**: Maintain backward compatibility, provide migration paths

2. **Performance Impact**: Abstraction may introduce overhead
   - **Mitigation**: Benchmark critical paths, optimize bottlenecks

3. **Complexity Increase**: More abstraction can increase complexity
   - **Mitigation**: Thorough documentation, clear examples

4. **Deployment Challenges**: Multiple plugins increase deployment complexity
   - **Mitigation**: Simplified deployment tools, clear instructions

5. **Resource Usage**: Additional services may increase resource requirements
   - **Mitigation**: Optimize resource usage, make features configurable

### 9.2 Contingency Plans

1. **Reversion Strategy**: Ability to revert to monolithic implementation
2. **Phased Rollout**: Gradual deployment to limit impact
3. **Feature Toggles**: Ability to disable problematic components
4. **Monitoring Plan**: Early detection of issues in production

## 10. Conclusion

The RVNKCore data layer abstraction project represents a significant architectural enhancement that will provide long-term benefits for the RVNK plugin ecosystem. By creating a centralized data and service layer, we can:

1. **Improve Maintainability**: Clearer separation of concerns
2. **Enable New Capabilities**: Cross-plugin data sharing
3. **Enhance Performance**: Optimized data access patterns
4. **Support Scalability**: Foundation for multi-plugin ecosystem

The implementation strategy balances immediate needs with long-term goals, providing a clear path forward while maintaining existing functionality. By following the defined standards and guidelines, we can ensure a consistent, high-quality implementation across all components.

---

## Appendix A: API Implementation Checklist

When implementing new components for the RVNKCore API, ensure compliance with the following checklist:

- [ ] **Interface Defined**: Clear interface with complete JavaDoc
- [ ] **Implementation Created**: Implementation of all interface methods
- [ ] **Unit Tests Written**: Tests for all public methods
- [ ] **Error Handling**: Comprehensive exception handling
- [ ] **Logging**: Appropriate logging throughout
- [ ] **Performance Considerations**: No blocking operations on main thread
- [ ] **Resource Management**: Proper cleanup of resources
- [ ] **Thread Safety**: Implementation is thread-safe
- [ ] **Documentation**: Complete documentation with examples
- [ ] **Versioning**: API version clearly indicated

## Appendix B: Example Interface Implementation

```java
/**
 * Provides centralized management of announcements across the server.
 * Handles scheduling, targeting, and delivery of announcements to players.
 */
public interface IAnnouncementService {
    /**
     * Creates a new announcement.
     * 
     * @param announcement The announcement data to create
     * @return A CompletableFuture containing the created announcement with ID assigned
     * @throws IllegalArgumentException if announcement is null or has invalid fields
     */
    CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Retrieves an announcement by ID.
     * 
     * @param id The unique identifier of the announcement
     * @return A CompletableFuture containing the announcement, or empty if not found
     * @throws IllegalArgumentException if id is null
     */
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    
    /**
     * Updates an existing announcement.
     * 
     * @param announcement The announcement data to update
     * @return A CompletableFuture containing the updated announcement
     * @throws IllegalArgumentException if announcement is null, has invalid fields, or doesn't exist
     */
    CompletableFuture<AnnouncementDTO> updateAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Deletes an announcement by ID.
     * 
     * @param id The unique identifier of the announcement to delete
     * @return A CompletableFuture containing true if deleted, false if not found
     * @throws IllegalArgumentException if id is null
     */
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    
    /**
     * Lists all announcements, optionally filtered by type and status.
     * 
     * @param type Optional filter for announcement type
     * @param activeOnly If true, only return active announcements
     * @return A CompletableFuture containing a list of matching announcements
     */
    CompletableFuture<List<AnnouncementDTO>> listAnnouncements(
            Optional<String> type, boolean activeOnly);
    
    /**
     * Broadcasts an announcement to matching players.
     * 
     * @param announcementId The ID of the announcement to broadcast
     * @param target Optional target selector for specific players
     * @return A CompletableFuture containing the number of players who received the announcement
     * @throws IllegalArgumentException if announcementId is null or doesn't exist
     */
    CompletableFuture<Integer> broadcastAnnouncement(String announcementId, Optional<String> target);
    
    /**
     * Registers a listener for announcement events.
     * 
     * @param listener The listener to notify of events
     * @return A registration handle that can be used to unregister the listener
     * @throws IllegalArgumentException if listener is null
     */
    RegistrationHandle registerListener(AnnouncementEventListener listener);
}
```

## Appendix C: Repository Implementation Example

```java
/**
 * Repository for managing announcement data in the database.
 */
public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, String> {
    
    public AnnouncementRepository(ConnectionProvider connectionProvider,
                               QueryBuilder queryBuilder,
                               QueryExecutor queryExecutor,
                               LogManager logger) {
        super(connectionProvider, queryBuilder, queryExecutor, logger, AnnouncementDTO.class);
    }
    
    @Override
    protected String getTableName() {
        return "announcements";
    }
    
    /**
     * Finds announcements by type.
     * 
     * @param type The announcement type to search for
     * @return A CompletableFuture containing matching announcements
     */
    public CompletableFuture<List<AnnouncementDTO>> findByType(String type) {
        return queryExecutor.executeQueryList(
            queryBuilder.select("*")
                      .from(getTableName())
                      .where("type = ?", type),
            AnnouncementDTO.class
        );
    }
    
    /**
     * Finds active announcements.
     * 
     * @return A CompletableFuture containing active announcements
     */
    public CompletableFuture<List<AnnouncementDTO>> findActive() {
        return queryExecutor.executeQueryList(
            queryBuilder.select("*")
                      .from(getTableName())
                      .where("active = ? AND (expires_at IS NULL OR expires_at > ?)", 
                            true, new Timestamp(System.currentTimeMillis())),
            AnnouncementDTO.class
        );
    }
    
    /**
     * Saves an announcement (creates or updates).
     * 
     * @param announcement The announcement to save
     * @return A CompletableFuture containing the saved announcement
     */
    public CompletableFuture<AnnouncementDTO> save(AnnouncementDTO announcement) {
        if (announcement.getId() == null) {
            // Create new
            announcement.setId(UUID.randomUUID().toString());
            
            // Build insert query
            QueryBuilder query = queryBuilder.insert(getTableName())
                .columns("id", "message", "type", "active", "created_at", "expires_at", "metadata")
                .values(announcement.getId(), announcement.getMessage(), announcement.getType(),
                       announcement.isActive(), announcement.getCreatedAt(),
                       announcement.getExpiresAt(), serializeMetadata(announcement.getMetadata()));
                       
            return queryExecutor.executeInsert(query)
                .thenApply(inserted -> inserted > 0 ? announcement : null);
        } else {
            // Update existing
            QueryBuilder query = queryBuilder.update(getTableName())
                .set("message", announcement.getMessage())
                .set("type", announcement.getType())
                .set("active", announcement.isActive())
                .set("expires_at", announcement.getExpiresAt())
                .set("metadata", serializeMetadata(announcement.getMetadata()))
                .where("id = ?", announcement.getId());
                
            return queryExecutor.executeUpdate(query)
                .thenApply(updated -> updated > 0 ? announcement : null);
        }
    }
    
    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            logger.error("Failed to serialize metadata", e);
            return null;
        }
    }
}
```
