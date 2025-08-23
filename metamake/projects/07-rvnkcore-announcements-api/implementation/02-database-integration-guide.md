# Implementation Guide: Database Integration

**Guide ID**: 02-database-integration-guide  
**Related Feature**: [Database Schema Design](../features/02-database-schema.md)  
**Prerequisites**: Service layer implemented, RVNKCore database connection established  
**Estimated Time**: 2-3 days

## Overview

This guide provides comprehensive instructions for implementing the database integration layer for RVNKCore announcements, including repository pattern implementation, query optimization, and migration support.

## Prerequisites

## Prerequisites

### Required Infrastructure

- RVNKCore database connection established (MySQL or SQLite with YAML fallback)
- Service layer implementation completed
- Repository base classes available
- Database connection pooling configured via HikariCP

### Configuration Framework

**Database Configuration Strategy:**

1. **Primary Database**: MySQL or SQLite based on server configuration
2. **YAML Fallback**: Automatic fallback to YAML when database unavailable
3. **Configuration Detection**: Runtime database availability checking
4. **Migration Support**: Seamless transition between storage types

```yaml
# config-core.yml - Database configuration
database:
  # Primary database settings
  type: "mysql"  # or "sqlite" 
  enabled: true
  
  # MySQL configuration
  mysql:
    host: "localhost"
    port: 3306
    database: "rvnktools"
    username: "rvnk_user"
    # Password via environment variable: RVNK_MYSQL_PASSWORD
  
  # SQLite configuration  
  sqlite:
    filename: "rvnktools.db"
    path: "plugins/RVNKCore/data/"
  
  # Fallback configuration
  fallback:
    enabled: true
    fallback_to: "yaml"  # yaml, memory, or disabled
    yaml_config: "announcements.yml"
    fallback_timeout_seconds: 30
    
# Connection pool settings
connection_pool:
  minimum_idle: 2
  maximum_pool_size: 10
  connection_timeout: 5000
  idle_timeout: 300000
  max_lifetime: 1800000
```

### Schema Requirements

```sql
-- Announcements table structure
CREATE TABLE announcements (
    id VARCHAR(36) PRIMARY KEY,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT 1,
    world VARCHAR(100),
    permission VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    display_duration_seconds INT DEFAULT 5,
    priority INT DEFAULT 1,
    created_by VARCHAR(36),
    INDEX idx_active (active),
    INDEX idx_type (type),
    INDEX idx_world (world),
    INDEX idx_permission (permission),
    INDEX idx_created_at (created_at)
);
```

## Step 1: Repository Interface Implementation

### 1.1 Review AnnouncementRepository Interface

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/database/repository/AnnouncementRepository.java

public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, String> {
    
    // Required constructor
    public AnnouncementRepository(ConnectionProvider connectionProvider, 
                                QueryBuilder queryBuilder, 
                                JavaPlugin plugin) {
        super(connectionProvider, queryBuilder, plugin, 
              "announcements", AnnouncementDTO.class);
    }
    
    // Core CRUD operations inherited from BaseRepository:
    // - CompletableFuture<String> save(AnnouncementDTO entity)
    // - CompletableFuture<Optional<AnnouncementDTO>> findById(String id)
    // - CompletableFuture<List<AnnouncementDTO>> findAll()
    // - CompletableFuture<Void> update(AnnouncementDTO entity)
    // - CompletableFuture<Boolean> deleteById(String id)
    
    // Custom query methods to implement:
    CompletableFuture<List<AnnouncementDTO>> findByActive(boolean active);
    CompletableFuture<List<AnnouncementDTO>> findByType(String type);
    CompletableFuture<List<AnnouncementDTO>> findByWorld(String world);
    CompletableFuture<List<AnnouncementDTO>> findByPermission(String permission);
    CompletableFuture<List<AnnouncementDTO>> findActiveByTypeAndWorld(String type, String world);
    CompletableFuture<Long> countByType(String type);
    CompletableFuture<List<AnnouncementDTO>> findRecentAnnouncements(int limit);
    CompletableFuture<Void> updateActiveStatus(String id, boolean active);
    CompletableFuture<Integer> deleteByType(String type);
}
```

### 1.2 Implement Custom Query Methods

```java
// Custom finder methods using QueryBuilder
@Override
public CompletableFuture<List<AnnouncementDTO>> findByActive(boolean active) {
    return CompletableFuture.supplyAsync(() -> {
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = queryBuilder.select()
                .from(tableName)
                .where("active = ?")
                .orderBy("created_at DESC")
                .build();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setBoolean(1, active);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSetToEntity(rs));
                    }
                    
                    logger.debug("Found " + results.size() + " announcements with active=" + active);
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find announcements by active status", e);
            throw new RepositoryException("Database query failed", e);
        }
    }, asyncExecutor);
}

@Override
public CompletableFuture<List<AnnouncementDTO>> findByType(String type) {
    if (type == null || type.trim().isEmpty()) {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    return CompletableFuture.supplyAsync(() -> {
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = queryBuilder.select()
                .from(tableName)
                .where("type = ? AND active = 1")
                .orderBy("priority DESC, created_at DESC")
                .build();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type.trim());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSetToEntity(rs));
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find announcements by type: " + type, e);
            throw new RepositoryException("Database query failed", e);
        }
    }, asyncExecutor);
}

@Override
public CompletableFuture<List<AnnouncementDTO>> findActiveByTypeAndWorld(String type, String world) {
    return CompletableFuture.supplyAsync(() -> {
        try (Connection connection = connectionProvider.getConnection()) {
            StringBuilder whereClause = new StringBuilder("active = 1");
            List<Object> parameters = new ArrayList<>();
            
            if (type != null && !type.trim().isEmpty()) {
                whereClause.append(" AND type = ?");
                parameters.add(type.trim());
            }
            
            if (world != null && !world.trim().isEmpty()) {
                whereClause.append(" AND (world IS NULL OR world = ?)");
                parameters.add(world.trim());
            }
            
            String sql = queryBuilder.select()
                .from(tableName)
                .where(whereClause.toString())
                .orderBy("priority DESC, created_at DESC")
                .build();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSetToEntity(rs));
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find announcements by type and world", e);
            throw new RepositoryException("Database query failed", e);
        }
    }, asyncExecutor);
}
```

### 1.3 Implement Batch Operations

```java
// Batch operations for improved performance
public CompletableFuture<Void> batchUpdateActiveStatus(List<String> ids, boolean active) {
    if (ids.isEmpty()) {
        return CompletableFuture.completedFuture(null);
    }
    
    return CompletableFuture.runAsync(() -> {
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            
            String sql = queryBuilder.update(tableName)
                .set("active = ?, updated_at = CURRENT_TIMESTAMP")
                .where("id = ?")
                .build();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (String id : ids) {
                    stmt.setBoolean(1, active);
                    stmt.setString(2, id);
                    stmt.addBatch();
                }
                
                int[] results = stmt.executeBatch();
                connection.commit();
                
                int updated = Arrays.stream(results).sum();
                logger.info("Batch updated " + updated + " announcements active status to " + active);
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to batch update announcement active status", e);
            throw new RepositoryException("Batch update failed", e);
        }
    }, asyncExecutor);
}

public CompletableFuture<List<String>> batchInsert(List<AnnouncementDTO> announcements) {
    if (announcements.isEmpty()) {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    return CompletableFuture.supplyAsync(() -> {
        List<String> insertedIds = new ArrayList<>();
        
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            
            String sql = queryBuilder.insert(tableName)
                .values("id", "message", "type", "active", "world", "permission", 
                       "display_duration_seconds", "priority", "created_by", 
                       "created_at", "updated_at")
                .build();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                Timestamp now = new Timestamp(System.currentTimeMillis());
                
                for (AnnouncementDTO announcement : announcements) {
                    String id = announcement.getId() != null ? announcement.getId() : UUID.randomUUID().toString();
                    
                    stmt.setString(1, id);
                    stmt.setString(2, announcement.getMessage());
                    stmt.setString(3, announcement.getType());
                    stmt.setBoolean(4, announcement.isActive());
                    stmt.setString(5, announcement.getWorld());
                    stmt.setString(6, announcement.getPermission());
                    stmt.setInt(7, announcement.getDisplayDurationSeconds());
                    stmt.setInt(8, announcement.getPriority());
                    stmt.setString(9, announcement.getCreatedBy());
                    stmt.setTimestamp(10, now);
                    stmt.setTimestamp(11, now);
                    
                    stmt.addBatch();
                    insertedIds.add(id);
                }
                
                int[] results = stmt.executeBatch();
                connection.commit();
                
                int inserted = Arrays.stream(results).sum();
                logger.info("Batch inserted " + inserted + " announcements");
                
                return insertedIds;
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to batch insert announcements", e);
            throw new RepositoryException("Batch insert failed", e);
        }
    }, asyncExecutor);
}
```

## Step 2: DTO and Entity Mapping

### 2.1 Enhance AnnouncementDTO

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/dto/AnnouncementDTO.java

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDTO {
    private String id;
    private String message;
    private String type;
    private boolean active = true;
    private String world;
    private String permission;
    private int displayDurationSeconds = 5;
    private int priority = 1;
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Validation methods
    public boolean isValid() {
        return message != null && !message.trim().isEmpty() &&
               type != null && !type.trim().isEmpty() &&
               displayDurationSeconds > 0 &&
               priority >= 0;
    }
    
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        
        if (message == null || message.trim().isEmpty()) {
            errors.add("Message cannot be null or empty");
        } else if (message.length() > 1000) {
            errors.add("Message cannot exceed 1000 characters");
        }
        
        if (type == null || type.trim().isEmpty()) {
            errors.add("Type cannot be null or empty");
        } else if (type.length() > 50) {
            errors.add("Type cannot exceed 50 characters");
        }
        
        if (world != null && world.length() > 100) {
            errors.add("World name cannot exceed 100 characters");
        }
        
        if (permission != null && permission.length() > 100) {
            errors.add("Permission cannot exceed 100 characters");
        }
        
        if (displayDurationSeconds <= 0) {
            errors.add("Display duration must be positive");
        }
        
        if (priority < 0) {
            errors.add("Priority cannot be negative");
        }
        
        return errors;
    }
    
    // Helper methods for database operations
    public AnnouncementDTO withId(String newId) {
        return this.toBuilder().id(newId).build();
    }
    
    public AnnouncementDTO withTimestamps() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return this.toBuilder()
            .createdAt(this.createdAt != null ? this.createdAt : now)
            .updatedAt(now)
            .build();
    }
}
```

### 2.2 Implement ResultSet Mapping

```java
// In AnnouncementRepository class
@Override
protected AnnouncementDTO mapResultSetToEntity(ResultSet rs) throws SQLException {
    return AnnouncementDTO.builder()
        .id(rs.getString("id"))
        .message(rs.getString("message"))
        .type(rs.getString("type"))
        .active(rs.getBoolean("active"))
        .world(rs.getString("world"))
        .permission(rs.getString("permission"))
        .displayDurationSeconds(rs.getInt("display_duration_seconds"))
        .priority(rs.getInt("priority"))
        .createdBy(rs.getString("created_by"))
        .createdAt(rs.getTimestamp("created_at"))
        .updatedAt(rs.getTimestamp("updated_at"))
        .build();
}

// Helper method for parameter mapping
private void mapEntityToStatement(PreparedStatement stmt, AnnouncementDTO entity, boolean includeId) throws SQLException {
    int paramIndex = 1;
    
    if (includeId) {
        stmt.setString(paramIndex++, entity.getId());
    }
    
    stmt.setString(paramIndex++, entity.getMessage());
    stmt.setString(paramIndex++, entity.getType());
    stmt.setBoolean(paramIndex++, entity.isActive());
    stmt.setString(paramIndex++, entity.getWorld());
    stmt.setString(paramIndex++, entity.getPermission());
    stmt.setInt(paramIndex++, entity.getDisplayDurationSeconds());
    stmt.setInt(paramIndex++, entity.getPriority());
    stmt.setString(paramIndex++, entity.getCreatedBy());
    
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (entity.getCreatedAt() != null) {
        stmt.setTimestamp(paramIndex++, entity.getCreatedAt());
    } else {
        stmt.setTimestamp(paramIndex++, now);
    }
    stmt.setTimestamp(paramIndex, now); // updated_at always current
}
```

## Step 3: Database Schema Migration

### 3.1 Schema Migration Service

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/database/migration/AnnouncementSchemaMigration.java

public class AnnouncementSchemaMigration implements SchemaMigration {
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private static final String MIGRATION_VERSION = "1.0.0";
    
    public AnnouncementSchemaMigration(ConnectionProvider connectionProvider, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
    }
    
    @Override
    public String getVersion() {
        return MIGRATION_VERSION;
    }
    
    @Override
    public String getDescription() {
        return "Create announcements table with indexes";
    }
    
    @Override
    public void migrate() throws MigrationException {
        try (Connection connection = connectionProvider.getConnection()) {
            // Check if table already exists
            if (tableExists(connection, "announcements")) {
                logger.info("Announcements table already exists, skipping creation");
                updateExistingSchema(connection);
                return;
            }
            
            // Create the table
            createAnnouncementsTable(connection);
            createIndexes(connection);
            
            logger.info("Announcements table created successfully with version " + MIGRATION_VERSION);
            
        } catch (SQLException e) {
            logger.error("Failed to migrate announcements schema", e);
            throw new MigrationException("Schema migration failed", e);
        }
    }
    
    private void createAnnouncementsTable(Connection connection) throws SQLException {
        String createTableSQL = """
            CREATE TABLE announcements (
                id VARCHAR(36) PRIMARY KEY,
                message TEXT NOT NULL,
                type VARCHAR(50) NOT NULL,
                active BOOLEAN DEFAULT 1,
                world VARCHAR(100),
                permission VARCHAR(100),
                display_duration_seconds INT DEFAULT 5,
                priority INT DEFAULT 1,
                created_by VARCHAR(36),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            logger.debug("Created announcements table");
        }
    }
    
    private void createIndexes(Connection connection) throws SQLException {
        String[] indexes = {
            "CREATE INDEX idx_announcements_active ON announcements(active)",
            "CREATE INDEX idx_announcements_type ON announcements(type)",
            "CREATE INDEX idx_announcements_world ON announcements(world)",
            "CREATE INDEX idx_announcements_permission ON announcements(permission)",
            "CREATE INDEX idx_announcements_created_at ON announcements(created_at)",
            "CREATE INDEX idx_announcements_priority ON announcements(priority)",
            "CREATE INDEX idx_announcements_type_world_active ON announcements(type, world, active)"
        };
        
        try (Statement stmt = connection.createStatement()) {
            for (String indexSQL : indexes) {
                stmt.execute(indexSQL);
            }
            logger.debug("Created " + indexes.length + " indexes for announcements table");
        }
    }
    
    private void updateExistingSchema(Connection connection) throws SQLException {
        // Check for and add any missing columns
        List<String> existingColumns = getTableColumns(connection, "announcements");
        
        if (!existingColumns.contains("display_duration_seconds")) {
            addColumn(connection, "announcements", "display_duration_seconds", "INT DEFAULT 5");
        }
        
        if (!existingColumns.contains("priority")) {
            addColumn(connection, "announcements", "priority", "INT DEFAULT 1");
        }
        
        if (!existingColumns.contains("created_by")) {
            addColumn(connection, "announcements", "created_by", "VARCHAR(36)");
        }
    }
    
    private void addColumn(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            logger.info("Added column " + columnName + " to " + tableName);
        }
    }
}
```

### 3.2 Migration Runner Integration

```java
// In RVNKCore bootstrap
public void runDatabaseMigrations() {
    try {
        MigrationRunner migrationRunner = new MigrationRunner(connectionProvider, logger);
        
        // Add announcement schema migration
        migrationRunner.addMigration(new AnnouncementSchemaMigration(connectionProvider, logger));
        
        // Run all pending migrations
        migrationRunner.migrate();
        
        logger.info("Database migrations completed successfully");
        
    } catch (Exception e) {
        logger.error("Database migration failed", e);
        throw new DatabaseInitializationException("Migration failed", e);
    }
}
```

## Step 4: Query Optimization and Performance

### 4.1 Connection Pool Configuration

```java
// Optimize connection pool settings for announcement queries
public class AnnouncementConnectionPoolConfig {
    
    public static void configurePool(HikariConfig config) {
        // Announcements are read-heavy with occasional writes
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        
        // Optimize for announcement queries
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
    }
}
```

### 4.2 Query Performance Monitoring

```java
// Add performance monitoring to repository methods
public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, String> {
    private final MetricRegistry metrics = new MetricRegistry();
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> findByActive(boolean active) {
        Timer.Context context = metrics.timer("announcements.query.findByActive").time();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Query execution code here
                List<AnnouncementDTO> results = executeQuery(/* query parameters */);
                
                // Log slow queries
                long duration = context.stop();
                if (duration > TimeUnit.MILLISECONDS.toNanos(100)) {
                    logger.warning("Slow query detected: findByActive took " + 
                        TimeUnit.NANOSECONDS.toMillis(duration) + "ms");
                }
                
                return results;
            } catch (Exception e) {
                metrics.counter("announcements.query.errors").inc();
                throw e;
            }
        }, asyncExecutor);
    }
    
    public QueryPerformanceStats getPerformanceStats() {
        return QueryPerformanceStats.builder()
            .totalQueries(metrics.getCounters().values().stream()
                .mapToLong(Counter::getCount).sum())
            .averageQueryTime(metrics.getTimers().get("announcements.query.findByActive")
                .getMeanRate())
            .errorCount(metrics.counter("announcements.query.errors").getCount())
            .build();
    }
}
```

## Step 5: Testing Database Integration

### 5.1 Repository Unit Tests

```java
@TestMethodOrder(OrderAnnotation.class)
class AnnouncementRepositoryTest {
    
    private static TestDatabaseSetup testDb;
    private static AnnouncementRepository repository;
    
    @BeforeAll
    static void setUpDatabase() throws Exception {
        testDb = new TestDatabaseSetup();
        testDb.initializeTestDatabase();
        
        ConnectionProvider connectionProvider = testDb.getConnectionProvider();
        QueryBuilder queryBuilder = new QueryBuilder();
        LogManager logger = LogManager.getInstance(null);
        
        repository = new AnnouncementRepository(connectionProvider, queryBuilder, null);
        
        // Run schema migration
        AnnouncementSchemaMigration migration = new AnnouncementSchemaMigration(
            connectionProvider, logger);
        migration.migrate();
    }
    
    @Test
    @Order(1)
    void testSaveAndFindById() throws Exception {
        // Create test announcement
        AnnouncementDTO announcement = AnnouncementDTO.builder()
            .id("test-1")
            .message("Test announcement")
            .type("general")
            .active(true)
            .world("world")
            .priority(5)
            .build();
        
        // Save
        String savedId = repository.save(announcement).get(5, TimeUnit.SECONDS);
        assertThat(savedId).isEqualTo("test-1");
        
        // Find by ID
        Optional<AnnouncementDTO> found = repository.findById("test-1").get(5, TimeUnit.SECONDS);
        assertThat(found).isPresent();
        assertThat(found.get().getMessage()).isEqualTo("Test announcement");
        assertThat(found.get().getPriority()).isEqualTo(5);
    }
    
    @Test
    @Order(2)
    void testFindByActive() throws Exception {
        // Create active and inactive announcements
        repository.save(AnnouncementDTO.builder()
            .id("active-1").message("Active 1").type("general").active(true).build()).get();
        repository.save(AnnouncementDTO.builder()
            .id("inactive-1").message("Inactive 1").type("general").active(false).build()).get();
        
        // Find active only
        List<AnnouncementDTO> active = repository.findByActive(true).get(5, TimeUnit.SECONDS);
        assertThat(active).hasSize(2); // test-1 from previous test + active-1
        assertThat(active).allMatch(AnnouncementDTO::isActive);
        
        // Find inactive only
        List<AnnouncementDTO> inactive = repository.findByActive(false).get(5, TimeUnit.SECONDS);
        assertThat(inactive).hasSize(1);
        assertThat(inactive.get(0).getId()).isEqualTo("inactive-1");
    }
    
    @Test
    @Order(3)
    void testBatchOperations() throws Exception {
        // Prepare batch data
        List<AnnouncementDTO> batch = Arrays.asList(
            AnnouncementDTO.builder().message("Batch 1").type("batch").active(true).build(),
            AnnouncementDTO.builder().message("Batch 2").type("batch").active(true).build(),
            AnnouncementDTO.builder().message("Batch 3").type("batch").active(true).build()
        );
        
        // Batch insert
        List<String> insertedIds = repository.batchInsert(batch).get(5, TimeUnit.SECONDS);
        assertThat(insertedIds).hasSize(3);
        
        // Verify inserted
        List<AnnouncementDTO> batchResults = repository.findByType("batch").get(5, TimeUnit.SECONDS);
        assertThat(batchResults).hasSize(3);
        
        // Batch update active status
        repository.batchUpdateActiveStatus(insertedIds, false).get(5, TimeUnit.SECONDS);
        
        // Verify updated
        List<AnnouncementDTO> afterUpdate = repository.findByType("batch").get(5, TimeUnit.SECONDS);
        assertThat(afterUpdate).isEmpty(); // findByType only returns active announcements
    }
    
    @Test
    void testQueryPerformance() throws Exception {
        // Create performance test data
        List<AnnouncementDTO> testData = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            testData.add(AnnouncementDTO.builder()
                .message("Performance test " + i)
                .type(i % 10 == 0 ? "priority" : "general")
                .active(i % 3 != 0)
                .world(i % 5 == 0 ? "special" : "world")
                .priority(i % 100)
                .build());
        }
        
        // Batch insert test data
        repository.batchInsert(testData).get(10, TimeUnit.SECONDS);
        
        // Measure query performance
        long startTime = System.currentTimeMillis();
        List<AnnouncementDTO> results = repository.findActiveByTypeAndWorld("priority", "special")
            .get(5, TimeUnit.SECONDS);
        long queryTime = System.currentTimeMillis() - startTime;
        
        assertThat(queryTime).isLessThan(100); // Should complete within 100ms
        assertThat(results).isNotEmpty();
        
        // Verify query performance statistics
        QueryPerformanceStats stats = repository.getPerformanceStats();
        assertThat(stats.getTotalQueries()).isGreaterThan(0);
    }
    
    @AfterAll
    static void cleanUp() throws Exception {
        if (testDb != null) {
            testDb.cleanup();
        }
    }
}
```

### 5.2 Integration Testing with Service Layer

```java
@TestMethodOrder(OrderAnnotation.class) 
class ServiceRepositoryIntegrationTest {
    
    private static RVNKCoreTestBootstrap testBootstrap;
    private static AnnouncementService service;
    private static AnnouncementRepository repository;
    
    @BeforeAll
    static void setUp() throws Exception {
        testBootstrap = new RVNKCoreTestBootstrap();
        testBootstrap.initializeTestEnvironment();
        
        ServiceRegistry registry = testBootstrap.getServiceRegistry();
        service = registry.getService(AnnouncementService.class).orElseThrow();
        repository = testBootstrap.getRepository(AnnouncementRepository.class);
    }
    
    @Test
    void testServiceRepositoryConsistency() throws Exception {
        // Create via service
        AnnouncementDTO announcement = AnnouncementDTO.builder()
            .message("Integration test")
            .type("integration")
            .active(true)
            .build();
        
        String id = service.createAnnouncement(announcement).get(5, TimeUnit.SECONDS);
        
        // Verify via repository
        Optional<AnnouncementDTO> fromRepo = repository.findById(id).get(5, TimeUnit.SECONDS);
        assertThat(fromRepo).isPresent();
        assertThat(fromRepo.get().getMessage()).isEqualTo("Integration test");
        
        // Verify via service
        Optional<AnnouncementDTO> fromService = service.getAnnouncement(id).get(5, TimeUnit.SECONDS);
        assertThat(fromService).isPresent();
        assertThat(fromService.get()).isEqualTo(fromRepo.get());
    }
    
    @Test
    void testCacheRepositorySync() throws Exception {
        // Create announcement
        AnnouncementDTO announcement = AnnouncementDTO.builder()
            .message("Cache sync test")
            .type("cache")
            .active(true)
            .build();
        
        String id = service.createAnnouncement(announcement).get(5, TimeUnit.SECONDS);
        
        // Get via service (should populate cache)
        service.getAnnouncement(id).get();
        
        // Update directly in repository
        AnnouncementDTO updated = announcement.toBuilder()
            .id(id)
            .message("Updated via repository")
            .build();
        repository.update(updated).get(5, TimeUnit.SECONDS);
        
        // Service should eventually reflect the change (cache TTL)
        // For testing, we can force cache eviction or wait for TTL
        Thread.sleep(100); // Small delay for async processing
        
        Optional<AnnouncementDTO> fromService = service.getAnnouncement(id).get(5, TimeUnit.SECONDS);
        // This test depends on cache implementation - may need adjustment
    }
}
```

## Step 6: Completion Checklist

### Implementation Verification

- [ ] **Repository Pattern**: All CRUD operations implemented with proper async handling
- [ ] **Custom Queries**: All finder methods working with proper indexing
- [ ] **Batch Operations**: Bulk insert/update operations optimized for performance
- [ ] **Schema Migration**: Table creation and updates handled automatically
- [ ] **Connection Pooling**: Database connections properly managed and optimized
- [ ] **Performance Monitoring**: Query performance tracked and slow queries logged

### Testing Verification

- [ ] **Unit Tests**: All repository methods covered with >90% code coverage
- [ ] **Integration Tests**: Service-repository integration verified
- [ ] **Performance Tests**: Query performance meets requirements (<100ms)
- [ ] **Migration Tests**: Schema migration tested with various scenarios
- [ ] **Error Handling**: Database errors properly caught and handled

### Documentation Verification

- [ ] **Schema Documentation**: Database schema documented with relationships
- [ ] **Query Documentation**: Complex queries documented with performance notes
- [ ] **Migration Guide**: Clear instructions for schema updates
- [ ] **Performance Guide**: Database tuning recommendations documented

## Next Steps

After completing the database integration:

1. **Proceed to REST API Development** - Follow [REST API Development Guide](03-rest-api-guide.md)
2. **Implement YAML Migration** - Follow [Migration Framework Guide](04-migration-framework-guide.md)
3. **Set Up Legacy Compatibility** - Follow [Legacy Support Guide](05-legacy-support-guide.md)

This database integration layer provides the foundation for all announcement data operations and establishes patterns for other RVNKCore database interactions.
