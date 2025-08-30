# RVNKCore Database Requirements

**Document Version**: 1.0  
**Last Updated**: August 1, 2025  
**Status**: Draft - Subject to Revision

## Purpose

This document defines the database architecture requirements for RVNKCore, including the abstraction layer, connection management, query building framework, and data persistence strategies. The database layer serves as the foundation for all data operations across the RVNK plugin ecosystem.

## Database Architecture Overview

RVNKCore implements a simplified, performance-focused database architecture that provides clean abstraction without unnecessary complexity:

```text
┌─────────────────────────┐
│      Service Layer      │
│                         │
│  Business Logic         │
│  Data Validation        │
│  Transaction Management │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   Repository Layer      │────▶│    Database Layer       │
│                         │     │                         │
│  BaseRepository         │     │  ConnectionProvider     │
│  Entity Repositories    │     │  QueryBuilder           │
│  CRUD Operations        │     │  Query Execution        │
└─────────────────────────┘     └─────────────────────────┘
             │                               │
             ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│      DTO Layer          │     │   Database Drivers      │
│                         │     │                         │
│  Data Transfer Objects  │     │  MySQL (HikariCP)       │
│  Builder Patterns       │     │  SQLite (Single Conn)   │
│  Validation Rules       │     │  Schema Management      │
└─────────────────────────┘     └─────────────────────────┘
```

## Core Database Components

### Connection Management Framework

#### ConnectionProvider Interface

```java
package org.fourz.rvnkcore.database.connection;

/**
 * Database connection abstraction interface.
 * Provides unified access to different database implementations.
 */
public interface ConnectionProvider extends AutoCloseable {
    /**
     * Gets a database connection from the pool or creates a new one.
     * 
     * @return Active database connection
     * @throws SQLException if connection cannot be established
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Validates that the connection provider is operational.
     * 
     * @return true if connections can be provided, false otherwise
     */
    boolean isValid();
    
    /**
     * Returns the database type this provider supports.
     */
    DatabaseType getDatabaseType();
    
    /**
     * Gets current connection statistics for monitoring.
     */
    ConnectionStats getConnectionStats();
    
    /**
     * Closes all connections and cleans up resources.
     */
    @Override
    void close();
}
```

#### Database Type Support

**SQLite Implementation Requirements:**

```java
public class SQLiteConnectionProvider implements ConnectionProvider {
    private final String databasePath;
    private final boolean enableWAL;
    private final int busyTimeout;
    private Connection connection;
    
    // Configuration requirements:
    // - WAL mode for better concurrency
    // - Busy timeout for write conflicts
    // - Connection pooling not required (single connection)
    // - Auto-schema creation on first connect
}
```

**MySQL Implementation Requirements:**

```java
public class MySQLConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;
    private final DatabaseConfig config;
    
    // Configuration requirements:
    // - HikariCP connection pooling
    // - Connection pool sizing based on server load
    // - Connection validation and health checks
    // - Automatic reconnection on failure
    // - SSL support for secure connections
}
```

#### Connection Configuration

**SQLite Configuration:**

```yaml
database:
  sqlite:
    file: "rvnkcore.db"
    wal-mode: true
    busy-timeout: 5000
    cache-size: 2000
    auto-vacuum: "incremental"
    synchronous: "normal"
```

**MySQL Configuration:**

```yaml
database:
  mysql:
    host: "localhost"
    port: 3306
    database: "rvnkcore"
    username: "rvnk_user"
    password: "secure_password"
    
    # HikariCP Settings
    pool:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      
    # Connection Properties
    properties:
      useSSL: true
      serverTimezone: "UTC"
      characterEncoding: "utf8mb4"
      useUnicode: true
```

### Query Building Framework

#### QueryBuilder Interface

```java
package org.fourz.rvnkcore.database.query;

/**
 * Database-agnostic query building interface.
 * Provides fluent API for constructing SQL queries.
 */
public interface QueryBuilder {
    // SELECT Operations
    QueryBuilder select(String... columns);
    QueryBuilder from(String table);
    QueryBuilder where(String condition, Object... params);
    QueryBuilder and(String condition, Object... params);
    QueryBuilder or(String condition, Object... params);
    QueryBuilder orderBy(String column, boolean ascending);
    QueryBuilder groupBy(String... columns);
    QueryBuilder having(String condition, Object... params);
    QueryBuilder limit(int limit);
    QueryBuilder offset(int offset);
    
    // JOIN Operations
    QueryBuilder innerJoin(String table, String condition);
    QueryBuilder leftJoin(String table, String condition);
    QueryBuilder rightJoin(String table, String condition);
    
    // INSERT Operations
    QueryBuilder insert(String table);
    QueryBuilder columns(String... columns);
    QueryBuilder values(Object... values);
    QueryBuilder onConflict(String resolution); // Database-specific
    
    // UPDATE Operations
    QueryBuilder update(String table);
    QueryBuilder set(String column, Object value);
    QueryBuilder set(Map<String, Object> columnValues);
    
    // DELETE Operations
    QueryBuilder delete();
    QueryBuilder deleteFrom(String table);
    
    // Query Building
    String build();
    Object[] getParameters();
    QueryType getQueryType();
    
    // Validation
    boolean isValid();
    List<String> getValidationErrors();
}
```

#### Database Dialect Support

**MySQL Query Builder:**

```java
public class MySQLQueryBuilder implements QueryBuilder {
    // MySQL-specific implementations:
    // - LIMIT/OFFSET syntax
    // - ON DUPLICATE KEY UPDATE for upserts
    // - MySQL-specific functions and operators
    // - Proper identifier quoting with backticks
    // - TIMESTAMP and datetime handling
}
```

**SQLite Query Builder:**

```java
public class SQLiteQueryBuilder implements QueryBuilder {
    // SQLite-specific implementations:
    // - LIMIT/OFFSET syntax
    // - INSERT OR REPLACE for upserts
    // - SQLite-specific functions
    // - Proper identifier quoting with brackets
    // - Date/time as text handling
}
```

### Schema Management Framework

#### SchemaQueryBuilder Interface

```java
package org.fourz.rvnkcore.database.schema;

/**
 * Database schema creation and management interface.
 */
public interface SchemaQueryBuilder {
    // Table Operations
    SchemaQueryBuilder createTable(String tableName);
    SchemaQueryBuilder ifNotExists();
    SchemaQueryBuilder dropTable(String tableName);
    SchemaQueryBuilder dropTableIfExists(String tableName);
    
    // Column Definitions
    SchemaQueryBuilder addColumn(String name, ColumnType type);
    SchemaQueryBuilder addColumn(String name, ColumnType type, ColumnConstraint... constraints);
    SchemaQueryBuilder primaryKey(String... columns);
    SchemaQueryBuilder foreignKey(String column, String referencedTable, String referencedColumn);
    
    // Indexes
    SchemaQueryBuilder createIndex(String indexName, String table, String... columns);
    SchemaQueryBuilder createUniqueIndex(String indexName, String table, String... columns);
    SchemaQueryBuilder dropIndex(String indexName);
    
    // Constraints
    SchemaQueryBuilder addConstraint(String name, String definition);
    SchemaQueryBuilder dropConstraint(String name);
    
    // Build and Execute
    String build();
    List<String> buildAll(); // For multi-statement operations
}
```

#### Schema Versioning Requirements

```java
package org.fourz.rvnkcore.database.schema;

/**
 * Database schema version management.
 */
public interface SchemaManager {
    /**
     * Gets the current schema version from the database.
     */
    CompletableFuture<Integer> getCurrentVersion();
    
    /**
     * Updates the database schema to the target version.
     */
    CompletableFuture<Void> migrateToVersion(int targetVersion);
    
    /**
     * Creates all tables if they don't exist (fresh install).
     */
    CompletableFuture<Void> createSchema();
    
    /**
     * Validates the current schema against expected structure.
     */
    CompletableFuture<SchemaValidationResult> validateSchema();
    
    /**
     * Gets all available migration scripts.
     */
    List<SchemaMigration> getAvailableMigrations();
}
```

### Repository Pattern Framework

#### BaseRepository Abstract Class

```java
package org.fourz.rvnkcore.database.repository;

/**
 * Base repository providing common CRUD operations.
 * All entity repositories should extend this class.
 */
public abstract class BaseRepository<T, ID> {
    protected final ConnectionProvider connectionProvider;
    protected final QueryBuilder queryBuilder;
    protected final Class<T> entityClass;
    protected final LogManager logger;
    
    // Abstract methods to be implemented by concrete repositories
    protected abstract String getTableName();
    protected abstract String getIdColumn();
    protected abstract T mapResultSet(ResultSet rs) throws SQLException;
    protected abstract Map<String, Object> mapToColumns(T entity);
    
    // Common CRUD operations
    public CompletableFuture<Optional<T>> findById(ID id) { /* implementation */ }
    public CompletableFuture<List<T>> findAll() { /* implementation */ }
    public CompletableFuture<T> save(T entity) { /* implementation */ }
    public CompletableFuture<Void> deleteById(ID id) { /* implementation */ }
    public CompletableFuture<Boolean> existsById(ID id) { /* implementation */ }
    public CompletableFuture<Long> count() { /* implementation */ }
    
    // Batch operations
    public CompletableFuture<List<T>> saveAll(List<T> entities) { /* implementation */ }
    public CompletableFuture<Void> deleteAll(List<ID> ids) { /* implementation */ }
    
    // Query operations
    protected CompletableFuture<List<T>> findByQuery(QueryBuilder query) { /* implementation */ }
    protected CompletableFuture<Optional<T>> findOneByQuery(QueryBuilder query) { /* implementation */ }
    protected CompletableFuture<Integer> executeUpdate(QueryBuilder query) { /* implementation */ }
}
```

#### Entity-Specific Repository Implementations

**PlayerRepository Requirements:**

```java
public class PlayerRepository extends BaseRepository<PlayerDTO, UUID> {
    
    @Override
    protected String getTableName() { return "rvnk_players"; }
    
    @Override
    protected String getIdColumn() { return "id"; }
    
    // Player-specific query methods
    public CompletableFuture<Optional<PlayerDTO>> findByName(String playerName);
    public CompletableFuture<List<PlayerDTO>> findByNamePattern(String pattern);
    public CompletableFuture<List<PlayerDTO>> findRecentPlayers(int hoursAgo);
    public CompletableFuture<List<PlayerDTO>> findByGroup(String groupName);
    public CompletableFuture<List<PlayerDTO>> findOnlinePlayers();
    
    // Activity tracking methods
    public CompletableFuture<Void> updateLastSeen(UUID playerId, LocalDateTime timestamp);
    public CompletableFuture<Void> updateLocation(UUID playerId, String world, 
                                                 double x, double y, double z);
    public CompletableFuture<Void> addNameToHistory(UUID playerId, String name);
    public CompletableFuture<Void> updateGroups(UUID playerId, String primaryGroup, 
                                               List<String> allGroups);
}
```

**AnnouncementRepository Requirements:**

```java
public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, String> {
    
    @Override
    protected String getTableName() { return "rvnk_announcements"; }
    
    @Override
    protected String getIdColumn() { return "id"; }
    
    // Announcement-specific query methods
    public CompletableFuture<List<AnnouncementDTO>> findActiveAnnouncements();
    public CompletableFuture<List<AnnouncementDTO>> findByCategory(String category);
    public CompletableFuture<List<AnnouncementDTO>> findScheduledBefore(LocalDateTime time);
    public CompletableFuture<List<AnnouncementDTO>> findByPriority(int priority);
    
    // Scheduling methods
    public CompletableFuture<Void> updateScheduledTime(String id, LocalDateTime scheduledTime);
    public CompletableFuture<Void> markAsDelivered(String id, LocalDateTime deliveredAt);
}
```

## Database Schema Requirements

### Core Tables Structure

#### Players Table

```sql
CREATE TABLE rvnk_players (
    id VARCHAR(36) PRIMARY KEY,           -- UUID as string
    current_name VARCHAR(16) NOT NULL,    -- Current player name
    name_history TEXT,                    -- JSON array of name history
    first_seen TIMESTAMP NOT NULL,       -- When player first joined
    last_seen TIMESTAMP NOT NULL,        -- Last activity timestamp
    times_joined INTEGER DEFAULT 1,      -- Number of times joined
    last_world VARCHAR(64),               -- Last known world
    last_x DOUBLE PRECISION,              -- Last X coordinate
    last_y DOUBLE PRECISION,              -- Last Y coordinate
    last_z DOUBLE PRECISION,              -- Last Z coordinate
    primary_group VARCHAR(32),            -- Primary permission group
    all_groups TEXT,                      -- JSON array of all groups
    metadata TEXT,                        -- JSON object for additional data
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_players_name ON rvnk_players(current_name);
CREATE INDEX idx_players_last_seen ON rvnk_players(last_seen);
CREATE INDEX idx_players_primary_group ON rvnk_players(primary_group);
CREATE INDEX idx_players_world ON rvnk_players(last_world);
```

#### Announcements Table

```sql
CREATE TABLE rvnk_announcements (
    id VARCHAR(36) PRIMARY KEY,           -- UUID as string
    title VARCHAR(255) NOT NULL,          -- Announcement title
    message TEXT NOT NULL,                -- Announcement content
    category VARCHAR(64),                 -- Announcement category
    enabled BOOLEAN DEFAULT true,         -- Whether announcement is active
    priority INTEGER DEFAULT 0,          -- Display priority
    scheduled_at TIMESTAMP,               -- When to display (null = immediate)
    delivered_at TIMESTAMP,               -- When it was delivered
    properties TEXT,                      -- JSON object for additional properties
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_announcements_enabled ON rvnk_announcements(enabled);
CREATE INDEX idx_announcements_category ON rvnk_announcements(category);
CREATE INDEX idx_announcements_scheduled ON rvnk_announcements(scheduled_at);
CREATE INDEX idx_announcements_priority ON rvnk_announcements(priority);
```

#### Schema Version Table

```sql
CREATE TABLE rvnk_schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255)
);

-- Insert initial version
INSERT INTO rvnk_schema_version (version, description) 
VALUES (1, 'Initial RVNKCore schema');
```

### Data Migration Requirements

#### Migration Framework

```java
package org.fourz.rvnkcore.database.migration;

/**
 * Interface for database migration scripts.
 */
public interface SchemaMigration {
    /**
     * Gets the version this migration targets.
     */
    int getTargetVersion();
    
    /**
     * Gets a description of this migration.
     */
    String getDescription();
    
    /**
     * Executes the migration.
     */
    CompletableFuture<Void> migrate(ConnectionProvider provider);
    
    /**
     * Rolls back the migration if possible.
     */
    CompletableFuture<Void> rollback(ConnectionProvider provider);
    
    /**
     * Checks if this migration can be rolled back.
     */
    boolean isRollbackSupported();
}
```

#### Legacy Data Migration

**RVNKTools Data Migration Requirements:**

- Migration of announcement data from YAML to database
- Preservation of existing player join messages and preferences
- Migration of link data and click tracking
- Preservation of player permission group associations

**Migration Validation:**

- Pre-migration data backup creation
- Post-migration data integrity validation
- Rollback capability for failed migrations
- Detailed migration logging and error reporting

## Performance Requirements

### Database Performance Targets

**Query Performance:**

- Simple queries (single table, indexed): < 10ms
- Complex queries (joins, aggregation): < 100ms
- Bulk operations (1000+ records): < 2 seconds
- Schema operations: < 5 seconds

**Connection Management:**

- Connection acquisition: < 100ms
- Connection pool efficiency: > 95% hit rate
- Connection leak detection and prevention
- Graceful handling of connection failures

**Concurrent Access:**

- Support for 500+ concurrent connections (MySQL)
- Proper transaction isolation levels
- Deadlock detection and resolution
- Connection pooling optimization

### Optimization Strategies

#### Indexing Strategy

**Primary Indexes:**

- All primary keys with clustered indexes
- Foreign key columns for join performance
- Frequently queried columns (names, timestamps, groups)
- Composite indexes for multi-column queries

**Query Optimization:**

- Query plan analysis and optimization
- Prepared statement caching
- Connection pooling optimization
- Database-specific optimizations (MySQL vs SQLite)

#### Caching Strategy

**Query Result Caching:**

- Frequently accessed player data
- Static configuration data
- Announcement schedules and content
- Permission group mappings

**Cache Invalidation:**

- Time-based expiration for dynamic data
- Event-driven invalidation for data changes
- Manual cache clearing for administrative operations
- Cache warming strategies for high-frequency data

## Monitoring and Health Checks

### Database Health Monitoring

```java
package org.fourz.rvnkcore.database.health;

/**
 * Database health monitoring and metrics collection.
 */
public interface DatabaseHealthService {
    /**
     * Performs a basic health check on the database connection.
     */
    CompletableFuture<HealthCheckResult> performHealthCheck();
    
    /**
     * Gets current database performance metrics.
     */
    DatabaseMetrics getCurrentMetrics();
    
    /**
     * Gets connection pool statistics.
     */
    ConnectionPoolMetrics getConnectionPoolMetrics();
    
    /**
     * Performs a deep health check including query performance.
     */
    CompletableFuture<DetailedHealthReport> performDetailedHealthCheck();
}
```

### Metrics Collection

**Performance Metrics:**

- Query execution times and counts
- Connection pool utilization
- Database lock contention
- Transaction rollback rates

**Error Monitoring:**

- Connection failures and timeouts
- Query errors and exceptions
- Schema validation failures
- Migration errors and rollbacks

### Backup and Recovery

#### Backup Requirements

**SQLite Backup:**

- Automatic daily backups
- WAL checkpoint before backup
- Backup rotation (keep 7 days)
- Compression for storage efficiency

**MySQL Backup:**

- Integration with existing MySQL backup solutions
- Point-in-time recovery capability
- Backup validation and testing
- Cross-server backup replication

#### Recovery Procedures

**Disaster Recovery:**

- Automated backup restoration
- Data integrity validation post-recovery
- Minimal downtime recovery procedures
- Documentation for manual recovery steps

This database architecture provides a solid foundation for the RVNKCore system while maintaining simplicity and performance. The requirements will be refined as implementation progresses and real-world usage patterns are observed.
