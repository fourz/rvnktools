# RVNKCore Announcements API - Implementation Scaffolding Guide

This document provides comprehensive scaffolding instructions for implementing the RVNKCore Announcements API, including step-by-step setup, code structure, and integration patterns.

## Project Overview

**Project**: 07-rvnkcore-announcements-api  
**Purpose**: Comprehensive announcement service implementation with database migration, REST API, and cross-plugin integration  
**Status**: Implementation Phase  
**Dependencies**: RVNKCore, MySQL/SQLite, Jetty HTTP Server

## Implementation Phases

### Phase 1: Core Service Infrastructure

**Duration**: 2-3 days  
**Priority**: Critical  
**Deliverables**: AnnouncementService interface and implementation

#### 1.1 Service Interface Design

Create the core service interface in RVNKCore:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/service/AnnouncementService.java
public interface AnnouncementService extends Service {
    // Async CRUD operations
    CompletableFuture<List<Announcement>> getActiveAnnouncements();
    CompletableFuture<Optional<Announcement>> getAnnouncement(String id);
    CompletableFuture<Announcement> createAnnouncement(CreateAnnouncementRequest request);
    CompletableFuture<Announcement> updateAnnouncement(String id, UpdateAnnouncementRequest request);
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    
    // Cache management
    CompletableFuture<Void> refreshCache();
    CacheStats getCacheStatistics();
    
    // Player-specific operations
    CompletableFuture<List<Announcement>> getAnnouncementsForPlayer(UUID playerId);
    CompletableFuture<Void> markAnnouncementRead(String announcementId, UUID playerId);
    
    // Admin operations
    CompletableFuture<List<Announcement>> getAllAnnouncements();
    CompletableFuture<Void> toggleAnnouncementActive(String id);
}
```

#### 1.2 Data Transfer Objects (DTOs)

Create comprehensive DTOs for the service layer:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/dto/announcement/
public record Announcement(
    String id,
    String title,
    String message,
    String type,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime expiresAt,
    Map<String, Object> metadata
) {}

public record CreateAnnouncementRequest(
    String title,
    String message,
    String type,
    boolean active,
    LocalDateTime expiresAt,
    Map<String, Object> metadata
) {}

public record UpdateAnnouncementRequest(
    Optional<String> title,
    Optional<String> message,
    Optional<String> type,
    Optional<Boolean> active,
    Optional<LocalDateTime> expiresAt,
    Optional<Map<String, Object>> metadata
) {}
```

#### 1.3 Service Implementation

Create the default service implementation:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/service/impl/DefaultAnnouncementService.java
@Component
public class DefaultAnnouncementService implements AnnouncementService {
    
    private final AnnouncementRepository repository;
    private final LogManager logger;
    private final Cache<String, List<Announcement>> announcementCache;
    private final EventManager eventManager;
    
    public DefaultAnnouncementService(
            AnnouncementRepository repository,
            LogManager logger,
            CacheManager cacheManager,
            EventManager eventManager) {
        this.repository = repository;
        this.logger = logger;
        this.announcementCache = cacheManager.createCache("announcements", Duration.ofMinutes(30));
        this.eventManager = eventManager;
    }
    
    @Override
    public CompletableFuture<List<Announcement>> getActiveAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return announcementCache.get("active", () -> {
                    logger.info("Cache miss - loading active announcements from database");
                    return repository.findActiveAnnouncements();
                });
            } catch (Exception e) {
                logger.error("Failed to load active announcements", e);
                throw new ServiceException("Failed to load announcements", e);
            }
        });
    }
    
    // Implementation continues...
}
```

### Phase 2: Data Access Layer

**Duration**: 2-3 days  
**Priority**: Critical  
**Deliverables**: Repository pattern implementation, database schema

#### 2.1 Repository Interface

Create the repository interface following the established pattern:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/repository/AnnouncementRepository.java
public interface AnnouncementRepository extends Repository<Announcement, String> {
    
    // Basic CRUD inherited from Repository<T, ID>
    
    // Custom queries
    List<Announcement> findActiveAnnouncements();
    List<Announcement> findByType(String type);
    List<Announcement> findExpiringBefore(LocalDateTime dateTime);
    Optional<Announcement> findByTitleIgnoreCase(String title);
    
    // Player-specific queries
    List<Announcement> findUnreadForPlayer(UUID playerId);
    boolean isAnnouncementReadByPlayer(String announcementId, UUID playerId);
    void markAsReadByPlayer(String announcementId, UUID playerId);
    
    // Admin queries
    Page<Announcement> findAllPaginated(Pageable pageable);
    long countByActive(boolean active);
    List<Announcement> findRecentlyCreated(int limit);
}
```

#### 2.2 SQL Repository Implementation

Create the SQL-based repository implementation:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/repository/impl/SqlAnnouncementRepository.java
@Component
public class SqlAnnouncementRepository implements AnnouncementRepository {
    
    private final ConnectionProvider connectionProvider;
    private final RowMapper<Announcement> announcementMapper;
    private final LogManager logger;
    
    private static final String SELECT_BASE = 
        "SELECT id, title, message, type, active, created_at, updated_at, expires_at, metadata " +
        "FROM rvnk_announcements";
    
    public SqlAnnouncementRepository(ConnectionProvider connectionProvider, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        this.announcementMapper = new AnnouncementRowMapper();
    }
    
    @Override
    public List<Announcement> findActiveAnnouncements() {
        String sql = SELECT_BASE + " WHERE active = 1 AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY created_at DESC";
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            List<Announcement> announcements = new ArrayList<>();
            while (rs.next()) {
                announcements.add(announcementMapper.mapRow(rs));
            }
            
            logger.debug("Found {} active announcements", announcements.size());
            return announcements;
            
        } catch (SQLException e) {
            logger.error("Failed to find active announcements", e);
            throw new RepositoryException("Database query failed", e);
        }
    }
    
    // Additional methods...
}
```

#### 2.3 Database Schema

Define the database schema for announcements:

```sql
-- Location: toolkitplugin/src/main/resources/sql/announcements-schema.sql

-- Main announcements table
CREATE TABLE IF NOT EXISTS rvnk_announcements (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'general',
    active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    metadata JSON,
    
    INDEX idx_active_expires (active, expires_at),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
);

-- Player read tracking table
CREATE TABLE IF NOT EXISTS rvnk_announcement_reads (
    announcement_id VARCHAR(36),
    player_uuid VARCHAR(36),
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (announcement_id, player_uuid),
    FOREIGN KEY (announcement_id) REFERENCES rvnk_announcements(id) ON DELETE CASCADE,
    
    INDEX idx_player_uuid (player_uuid),
    INDEX idx_read_at (read_at)
);

-- Audit table for announcement changes
CREATE TABLE IF NOT EXISTS rvnk_announcement_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    announcement_id VARCHAR(36),
    action VARCHAR(20) NOT NULL,
    old_values JSON,
    new_values JSON,
    changed_by VARCHAR(36),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (announcement_id) REFERENCES rvnk_announcements(id) ON DELETE CASCADE,
    INDEX idx_announcement_id (announcement_id),
    INDEX idx_changed_at (changed_at)
);
```

### Phase 3: REST API Implementation

**Duration**: 3-4 days  
**Priority**: High  
**Deliverables**: HTTP/HTTPS endpoints, authentication, documentation

#### 3.1 REST Controller

Create the REST API controller using Jetty:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/rest/AnnouncementController.java
@RestController("/api/v1/announcements")
public class AnnouncementController {
    
    private final AnnouncementService announcementService;
    private final AuthenticationService authService;
    private final LogManager logger;
    
    public AnnouncementController(AnnouncementService announcementService, 
                                AuthenticationService authService, 
                                LogManager logger) {
        this.announcementService = announcementService;
        this.authService = authService;
        this.logger = logger;
    }
    
    @GET
    @Path("/active")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> getActiveAnnouncements(@Context HttpServletRequest request) {
        return announcementService.getActiveAnnouncements()
            .thenApply(announcements -> {
                logger.debug("Returning {} active announcements", announcements.size());
                return Response.ok(new AnnouncementListResponse(announcements)).build();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get active announcements", throwable);
                return Response.serverError()
                    .entity(new ErrorResponse("Internal server error"))
                    .build();
            });
    }
    
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> getAnnouncement(@PathParam("id") String id) {
        return announcementService.getAnnouncement(id)
            .thenApply(optionalAnnouncement -> {
                if (optionalAnnouncement.isPresent()) {
                    return Response.ok(optionalAnnouncement.get()).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Announcement not found"))
                        .build();
                }
            });
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    @RequiresPermission("rvnk.announcements.create")
    public CompletionStage<Response> createAnnouncement(
            CreateAnnouncementRequest request,
            @Context HttpServletRequest httpRequest) {
        
        return authService.validateRequest(httpRequest)
            .thenCompose(authContext -> {
                return announcementService.createAnnouncement(request);
            })
            .thenApply(announcement -> {
                logger.info("Created announcement: {}", announcement.id());
                return Response.status(Response.Status.CREATED)
                    .entity(announcement)
                    .build();
            })
            .exceptionally(this::handleException);
    }
    
    // Additional endpoints...
    
    private Response handleException(Throwable throwable) {
        logger.error("API error", throwable);
        if (throwable instanceof ValidationException) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(throwable.getMessage()))
                .build();
        } else if (throwable instanceof AuthenticationException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Authentication required"))
                .build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Internal server error"))
                .build();
        }
    }
}
```

#### 3.2 API Response DTOs

Create API-specific response objects:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/dto/
public record AnnouncementListResponse(
    List<Announcement> announcements,
    int total,
    long timestamp
) {
    public AnnouncementListResponse(List<Announcement> announcements) {
        this(announcements, announcements.size(), System.currentTimeMillis());
    }
}

public record ErrorResponse(
    String error,
    String message,
    long timestamp
) {
    public ErrorResponse(String message) {
        this("API_ERROR", message, System.currentTimeMillis());
    }
}

public record SuccessResponse(
    String message,
    Object data,
    long timestamp
) {
    public SuccessResponse(String message) {
        this(message, null, System.currentTimeMillis());
    }
}
```

### Phase 4: Migration System

**Duration**: 2-3 days  
**Priority**: High  
**Deliverables**: YAML to database migration, rollback capability

#### 4.1 Migration Service

Create the migration service implementation:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/migration/AnnouncementMigrationService.java
@Component
public class AnnouncementMigrationService {
    
    private final AnnouncementRepository repository;
    private final YamlConfigManager yamlManager;
    private final LogManager logger;
    
    public AnnouncementMigrationService(
            AnnouncementRepository repository,
            YamlConfigManager yamlManager,
            LogManager logger) {
        this.repository = repository;
        this.yamlManager = yamlManager;
        this.logger = logger;
    }
    
    public CompletableFuture<MigrationResult> migrateFromYaml(Path yamlFilePath, MigrationOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting announcement migration from: {}", yamlFilePath);
            
            try {
                // Load YAML data
                YamlConfiguration config = yamlManager.loadConfig(yamlFilePath);
                List<YamlAnnouncement> yamlAnnouncements = parseYamlAnnouncements(config);
                
                logger.info("Found {} announcements in YAML file", yamlAnnouncements.size());
                
                // Validate data before migration
                ValidationResult validationResult = validateYamlData(yamlAnnouncements);
                if (!validationResult.isValid()) {
                    throw new MigrationException("YAML validation failed: " + validationResult.getErrors());
                }
                
                // Create backup if requested
                if (options.createBackup()) {
                    createMigrationBackup(yamlFilePath);
                }
                
                // Perform migration
                List<Announcement> migratedAnnouncements = new ArrayList<>();
                int successCount = 0;
                int errorCount = 0;
                
                for (YamlAnnouncement yamlAnnouncement : yamlAnnouncements) {
                    try {
                        Announcement announcement = convertYamlToAnnouncement(yamlAnnouncement);
                        repository.save(announcement);
                        migratedAnnouncements.add(announcement);
                        successCount++;
                        
                        logger.debug("Migrated announcement: {}", announcement.title());
                        
                    } catch (Exception e) {
                        logger.error("Failed to migrate announcement: {}", yamlAnnouncement.getTitle(), e);
                        errorCount++;
                        
                        if (!options.continueOnError()) {
                            throw new MigrationException("Migration failed for announcement: " + yamlAnnouncement.getTitle(), e);
                        }
                    }
                }
                
                logger.info("Migration completed - Success: {}, Errors: {}", successCount, errorCount);
                
                return new MigrationResult(
                    successCount,
                    errorCount,
                    migratedAnnouncements,
                    Duration.between(Instant.now(), Instant.now())
                );
                
            } catch (Exception e) {
                logger.error("Migration failed", e);
                throw new MigrationException("Migration failed", e);
            }
        });
    }
    
    private List<YamlAnnouncement> parseYamlAnnouncements(YamlConfiguration config) {
        List<YamlAnnouncement> announcements = new ArrayList<>();
        
        ConfigurationSection announcementsSection = config.getConfigurationSection("announcements");
        if (announcementsSection != null) {
            for (String key : announcementsSection.getKeys(false)) {
                ConfigurationSection announcementSection = announcementsSection.getConfigurationSection(key);
                if (announcementSection != null) {
                    YamlAnnouncement yamlAnnouncement = parseYamlAnnouncement(key, announcementSection);
                    announcements.add(yamlAnnouncement);
                }
            }
        }
        
        return announcements;
    }
    
    // Additional methods...
}
```

### Phase 5: Integration and Testing

**Duration**: 3-4 days  
**Priority**: Critical  
**Deliverables**: Cross-plugin integration, comprehensive testing

#### 5.1 Service Registration

Register the service with RVNKCore's ServiceRegistry:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/RVNKCorePlugin.java
@Override
public void onEnable() {
    try {
        // Initialize components
        ConnectionProvider connectionProvider = createConnectionProvider();
        LogManager logger = LogManager.getInstance(this);
        
        // Create repository
        AnnouncementRepository repository = new SqlAnnouncementRepository(connectionProvider, logger);
        
        // Create service
        AnnouncementService announcementService = new DefaultAnnouncementService(
            repository, logger, getCacheManager(), getEventManager());
        
        // Register service
        serviceRegistry.registerService(AnnouncementService.class, announcementService);
        
        // Initialize REST API
        if (config.getBoolean("api.rest.enabled", true)) {
            AnnouncementController controller = new AnnouncementController(
                announcementService, getAuthenticationService(), logger);
            restApiManager.registerController(controller);
        }
        
        logger.info("RVNKCore Announcements API initialized successfully");
        
    } catch (Exception e) {
        logger.error("Failed to initialize RVNKCore Announcements API", e);
        throw new RuntimeException("Initialization failed", e);
    }
}
```

#### 5.2 RVNKTools Integration

Integrate with RVNKTools for command interface:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnktools/commands/AnnouncementCommand.java
public class AnnouncementCommand extends BaseCommand {
    
    private final ServiceRegistry serviceRegistry;
    private final LogManager logger;
    
    public AnnouncementCommand(RVNKToolsPlugin plugin) {
        super(plugin, "announcement", "Manage server announcements", "rvnk.announcements");
        this.serviceRegistry = plugin.getServiceRegistry();
        this.logger = LogManager.getInstance(plugin);
        
        registerSubCommand("list", new ListAnnouncementsSubCommand(plugin));
        registerSubCommand("create", new CreateAnnouncementSubCommand(plugin));
        registerSubCommand("delete", new DeleteAnnouncementSubCommand(plugin));
        registerSubCommand("toggle", new ToggleAnnouncementSubCommand(plugin));
        registerSubCommand("migrate", new MigrateAnnouncementsSubCommand(plugin));
    }
    
    @Override
    protected void executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAnnouncementStatus(sender);
            return;
        }
        
        super.executeCommand(sender, args);
    }
    
    private void showAnnouncementStatus(CommandSender sender) {
        try {
            AnnouncementService service = serviceRegistry.getService(AnnouncementService.class);
            if (service == null) {
                sender.sendMessage(ChatFormat.error("Announcement service is not available"));
                return;
            }
            
            service.getActiveAnnouncements().thenAccept(announcements -> {
                sender.sendMessage(ChatFormat.success("Active announcements: " + announcements.size()));
                
                CacheStats stats = service.getCacheStatistics();
                sender.sendMessage(ChatFormat.info("Cache hit rate: " + String.format("%.2f%%", stats.hitRate() * 100)));
            });
            
        } catch (Exception e) {
            logger.error("Failed to show announcement status", e);
            sender.sendMessage(ChatFormat.error("Failed to load announcement status"));
        }
    }
}
```

## Implementation Timeline

### Week 1: Core Infrastructure

- **Days 1-2**: Service interface and DTOs
- **Days 3-4**: Repository implementation
- **Days 5-6**: Database schema and migration scripts
- **Day 7**: Basic testing and validation

### Week 2: API and Migration

- **Days 1-3**: REST API implementation
- **Days 4-5**: Migration service implementation  
- **Days 6-7**: Authentication and security

### Week 3: Integration and Testing

- **Days 1-2**: RVNKTools command integration
- **Days 3-4**: Comprehensive testing
- **Days 5-6**: Performance optimization
- **Day 7**: Documentation and deployment

## Quality Gates

### Code Quality Requirements

- **Test Coverage**: Minimum 80% for service and repository layers
- **Performance**: API response time < 200ms for cached data
- **Security**: All admin endpoints protected with authentication
- **Documentation**: Complete JavaDoc for all public APIs

### Validation Checkpoints

- [ ] Service integration tests pass
- [ ] Database schema validation complete
- [ ] REST API endpoints tested with Postman/curl
- [ ] Migration validation with real YAML files
- [ ] Cross-plugin integration verified
- [ ] Performance benchmarks meet requirements

## Deployment Strategy

### Development Environment

1. **Database Setup**: Create `rvnkcore_dev` database
2. **Schema Migration**: Run schema creation scripts
3. **Service Testing**: Use VS Code tasks for validation
4. **API Testing**: Use PowerShell test scripts

### Production Deployment

1. **Backup Current State**: YAML files and existing data
2. **Database Migration**: Run production migration scripts
3. **Service Deployment**: Deploy new service implementation
4. **Validation Testing**: Run full validation suite
5. **Rollback Plan**: Prepared rollback procedures if needed

## Risk Mitigation

### High-Risk Areas

**Data Migration**: Risk of data loss during YAML to database migration

- **Mitigation**: Comprehensive backup procedures and rollback capability

**Service Performance**: Risk of performance degradation with large datasets

- **Mitigation**: Caching strategy and database indexing

**API Security**: Risk of unauthorized access to admin endpoints

- **Mitigation**: Robust authentication and permission validation

### Monitoring and Alerts

- **Database Connection Pool**: Monitor connection usage
- **API Response Times**: Track performance metrics
- **Error Rates**: Monitor service error rates
- **Cache Performance**: Track hit rates and memory usage

This comprehensive scaffolding guide provides the foundation for implementing the RVNKCore Announcements API with proper testing, validation, and integration patterns.
