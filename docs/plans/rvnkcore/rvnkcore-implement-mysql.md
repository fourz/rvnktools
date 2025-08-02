# RVNKCore MySQL Implementation Plan

**Document Version**: 1.0  
**Date**: August 2, 2025  
**Status**: Implementation Plan  
**Meta Prompt**: Designed for use with `iterate-rvnkcore-implementation.md`

## Executive Summary

This document provides a comprehensive implementation plan for adding MySQL support with HikariCP connection pooling to RVNKCore. With Phase 1 Foundation at 90% completion, MySQL implementation is the **critical blocker** preventing production deployments and Phase 2 progression.

## Current Context Analysis

### Implementation Status
- **✅ SQLite Implementation**: Complete and operational
- **✅ Core Infrastructure**: Database layer, service framework, REST API
- **❌ MySQL Implementation**: Missing - blocking production deployments
- **❌ Schema Migration**: Missing - blocking existing installation upgrades

### Critical Dependencies
- RVNKCore Phase 1 foundation is 90% complete
- SQLiteConnectionProvider serves as implementation template
- Existing legacy MySQL implementation in `MySQLDataConnector` provides reference
- Production deployments require MySQL for scalability

## Implementation Objectives

### Primary Goals
1. **MySQL ConnectionProvider Implementation**: Production-ready connection pooling with HikariCP
2. **Configuration Integration**: Seamless integration with existing configuration framework
3. **Schema Migration Foundation**: Version management and data migration utilities
4. **Testing and Validation**: Comprehensive testing of MySQL implementation

### Success Criteria
- ✅ MySQL connections established with HikariCP pooling
- ✅ Configuration validation and SSL/TLS support
- ✅ Schema creation and migration framework operational
- ✅ Integration tests passing for both SQLite and MySQL
- ✅ Production deployment ready

## Implementation Plan

### Phase 1: Foundation Setup (Days 1-2)

#### Day 1: Dependency Integration and Basic Implementation

**Step 1.1: Maven Dependencies**
```xml
<!-- Add to toolkitplugin/pom.xml dependencies section -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>

<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

**Step 1.2: Configuration Model**
```java
// Create: org.fourz.rvnkcore.database.config.DatabaseConfig
public class DatabaseConfig {
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean useSSL;
    private String connectionParameters;
    
    // Connection Pool Settings
    private int maxConnections = 10;
    private int minIdleConnections = 2;
    private long connectionTimeoutMs = 30000;
    private long idleTimeoutMs = 600000;
    private long maxLifetimeMs = 1800000;
    private long leakDetectionMs = 60000;
    
    // Builder pattern implementation
    // Validation methods
    // SSL configuration
}
```

**Step 1.3: MySQL ConnectionProvider Skeleton**
```java
// Create: org.fourz.rvnkcore.database.connection.MySQLConnectionProvider
public class MySQLConnectionProvider implements ConnectionProvider {
    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final LogManager logger;
    private final ReentrantLock initializationLock = new ReentrantLock();
    private volatile boolean initialized = false;
    
    // Constructor, initialization, connection methods
    // Health monitoring and validation
    // Graceful shutdown procedures
}
```

#### Day 2: Core Implementation and Configuration

**Step 2.1: HikariCP Configuration**
```java
private void initializeDataSource() {
    HikariConfig hikariConfig = new HikariConfig();
    
    // Connection URL building with SSL support
    hikariConfig.setJdbcUrl(buildConnectionUrl());
    hikariConfig.setUsername(config.getUsername());
    hikariConfig.setPassword(config.getPassword());
    
    // Connection Pool Optimization
    hikariConfig.setMaximumPoolSize(config.getMaxConnections());
    hikariConfig.setMinimumIdle(config.getMinIdleConnections());
    hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
    hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
    hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
    
    // Performance Settings
    hikariConfig.setLeakDetectionThreshold(config.getLeakDetectionMs());
    hikariConfig.setCachePrepStmts(true);
    hikariConfig.setPrepStmtCacheSize(250);
    hikariConfig.setPrepStmtCacheSqlLimit(2048);
    hikariConfig.setUseServerPrepStmts(true);
    
    // MySQL-specific optimizations
    hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
    hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
    
    this.dataSource = new HikariDataSource(hikariConfig);
}
```

**Step 2.2: Configuration Integration**
```java
// Extend existing configuration to support MySQL
// Integration with application.properties
// Environment variable support
// Configuration validation and error handling
```

### Phase 2: Schema Management (Days 3-4)

#### Day 3: Migration Framework Foundation

**Step 3.1: Schema Version Management**
```java
// Create: org.fourz.rvnkcore.database.migration.SchemaManager
public class SchemaManager {
    public static final String VERSION_TABLE = "rvnkcore_schema_version";
    
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    
    public CompletableFuture<Integer> getCurrentSchemaVersion();
    public CompletableFuture<Void> createVersionTable();
    public CompletableFuture<Void> updateSchemaVersion(int version);
    public CompletableFuture<List<Migration>> getPendingMigrations();
    public CompletableFuture<Void> executeMigration(Migration migration);
}
```

**Step 3.2: Migration Script Framework**
```java
// Create migration interface and implementation
public interface Migration {
    int getVersion();
    String getDescription();
    String getUpScript();
    String getDownScript();
    boolean requiresData();
}

public class SqlMigration implements Migration {
    // SQL-based migration implementation
    // Support for both SQLite and MySQL dialects
}
```

#### Day 4: Data Migration and Testing

**Step 4.1: Legacy Data Migration**
```java
// Create utilities for migrating from existing RVNKTools data
// Support for announcement data, player preferences, etc.
// Validation and rollback capabilities
```

**Step 4.2: Integration Testing**
```java
// Test MySQL connectivity
// Validate connection pooling behavior
// Test schema creation and migration
// Performance benchmarking
```

### Phase 3: Testing and Integration (Day 5)

#### Comprehensive Testing Strategy

**Unit Tests**
- MySQL ConnectionProvider functionality
- Configuration validation
- Connection pooling behavior
- Error handling and recovery

**Integration Tests**
- Database operations with MySQL
- Schema migration execution
- Service integration with MySQL backend
- Performance under concurrent load

**Validation Tests**
- SSL/TLS connection security
- Connection leak detection
- Failover and recovery scenarios
- Memory usage and resource cleanup

## Implementation Specifications

### MySQL ConnectionProvider Requirements

**File**: `org.fourz.rvnkcore.database.connection.MySQLConnectionProvider`

```java
public class MySQLConnectionProvider implements ConnectionProvider {
    
    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final LogManager logger;
    private final ReentrantLock initializationLock = new ReentrantLock();
    private volatile boolean initialized = false;
    
    public MySQLConnectionProvider(DatabaseConfig config, Plugin plugin) {
        this.config = validateConfig(config);
        this.logger = LogManager.getInstance(plugin);
        initializeDataSource();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        ensureInitialized();
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(5)) {
                throw new SQLException("Connection validation failed");
            }
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to obtain MySQL connection", e);
            throw new DatabaseException("MySQL connection failed", e);
        }
    }
    
    @Override
    public boolean isValid() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection testConn = dataSource.getConnection()) {
            return testConn.isValid(5);
        } catch (SQLException e) {
            logger.warning("MySQL connection validation failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down MySQL connection pool");
            dataSource.close();
            initialized = false;
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
    
    private String buildConnectionUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://")
           .append(config.getHost())
           .append(":").append(config.getPort())
           .append("/").append(config.getDatabase());
        
        // Add SSL and other parameters
        List<String> params = new ArrayList<>();
        params.add("useSSL=" + config.isUseSSL());
        params.add("serverTimezone=UTC");
        params.add("characterEncoding=UTF-8");
        params.add("autoReconnect=true");
        params.add("failOverReadOnly=false");
        params.add("maxReconnects=3");
        
        if (config.getConnectionParameters() != null) {
            params.add(config.getConnectionParameters());
        }
        
        url.append("?").append(String.join("&", params));
        return url.toString();
    }
    
    private DatabaseConfig validateConfig(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Database configuration cannot be null");
        }
        
        if (config.getHost() == null || config.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Database host cannot be null or empty");
        }
        
        if (config.getDatabase() == null || config.getDatabase().trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Database username cannot be null or empty");
        }
        
        // Additional validation logic
        return config;
    }
    
    private void ensureInitialized() throws SQLException {
        if (!initialized) {
            initializationLock.lock();
            try {
                if (!initialized) {
                    initializeDataSource();
                }
            } finally {
                initializationLock.unlock();
            }
        }
    }
}
```

### Configuration Integration

**application.properties Integration**
```properties
# MySQL Database Configuration
database.type=mysql
database.mysql.host=localhost
database.mysql.port=3306
database.mysql.database=rvnktools
database.mysql.username=rvnkuser
database.mysql.password=secure_password
database.mysql.useSSL=true
database.mysql.connectionParameters=allowPublicKeyRetrieval=true

# Connection Pool Configuration
database.mysql.pool.maxConnections=20
database.mysql.pool.minIdleConnections=5
database.mysql.pool.connectionTimeoutMs=30000
database.mysql.pool.idleTimeoutMs=600000
database.mysql.pool.maxLifetimeMs=1800000
database.mysql.pool.leakDetectionMs=60000
```

## Meta Prompting Integration

### Usage with iterate-rvnkcore-implementation.md

This document is designed to work as a **focused implementation guide** alongside the broader `iterate-rvnkcore-implementation.md` prompt. Use this pattern:

**Primary Prompt**: `iterate-rvnkcore-implementation.md`
**Focused Prompt**: This document (`rvnkcore-implement-mysql.md`)

### Meta Prompt Pattern

```markdown
# Meta Prompt Instructions

1. **Context Loading**: Load both documents for comprehensive context
2. **Priority Assignment**: This MySQL implementation takes IMMEDIATE priority
3. **Task Sequencing**: Complete MySQL implementation before other Phase 1 tasks
4. **Integration Points**: Ensure compatibility with existing SQLite implementation
5. **Testing Requirements**: Validate both database backends work seamlessly

## Prompt Chaining Strategy

**Step 1**: Review current implementation status
**Step 2**: Focus on MySQL implementation (this document)
**Step 3**: Return to broader iteration tasks (iterate-rvnkcore-implementation.md)
**Step 4**: Integration testing and validation
```

### Implementation Commands

```bash
# Development workflow using this plan
1. Load context from both documents
2. Execute MySQL implementation plan (Days 1-5)
3. Test and validate implementation
4. Update roadmap and status documents
5. Proceed with broader RVNKCore iteration tasks
```

## Risk Assessment and Mitigation

### High-Risk Areas

**Connection Pool Management**
- Risk: Resource leaks or connection exhaustion
- Mitigation: Comprehensive testing, monitoring, proper cleanup

**Configuration Complexity**
- Risk: Incorrect configuration leading to connection failures
- Mitigation: Validation framework, clear error messages, examples

**Schema Migration**
- Risk: Data loss during migration
- Mitigation: Backup procedures, rollback capabilities, validation

### Dependencies

**External Dependencies**
- HikariCP library stability and compatibility
- MySQL JDBC driver compatibility
- Network connectivity and database availability

**Internal Dependencies**
- Existing ConnectionProvider interface compatibility
- Service registry integration
- Configuration framework extension

## Success Metrics

### Implementation Metrics
- [ ] MySQL connections established successfully
- [ ] Connection pooling operational with configured limits
- [ ] SSL/TLS connections working securely
- [ ] Schema migration framework functional
- [ ] All existing tests pass with MySQL backend
- [ ] Performance benchmarks meet requirements

### Quality Metrics
- [ ] Code coverage >80% for new MySQL components
- [ ] Documentation complete with examples
- [ ] Integration tests cover critical paths
- [ ] Error handling comprehensive and user-friendly
- [ ] Memory usage within acceptable limits
- [ ] Connection leaks eliminated

## Completion Checklist

### Phase 1: Foundation (Days 1-2)
- [ ] HikariCP and MySQL dependencies added to pom.xml
- [ ] DatabaseConfig class implemented with validation
- [ ] MySQLConnectionProvider skeleton created
- [ ] Basic configuration integration working
- [ ] Initial connectivity tests passing

### Phase 2: Schema Management (Days 3-4)
- [ ] SchemaManager implementation complete
- [ ] Migration framework operational
- [ ] Version tracking table created
- [ ] Legacy data migration utilities implemented
- [ ] Migration testing complete

### Phase 3: Testing and Integration (Day 5)
- [ ] Unit tests implemented and passing
- [ ] Integration tests covering MySQL operations
- [ ] Performance benchmarks completed
- [ ] Documentation updated
- [ ] Production readiness validated

### Final Validation
- [ ] Both SQLite and MySQL backends working
- [ ] Service registry properly integrated
- [ ] Configuration validation robust
- [ ] Error handling comprehensive
- [ ] Ready for Phase 2 RVNKCore development

## Next Steps After Completion

1. **Update Roadmap**: Mark MySQL implementation as complete
2. **Phase 2 Preparation**: Begin business service implementation
3. **Documentation**: Update all relevant documentation
4. **Testing**: Comprehensive integration testing
5. **Deployment**: Prepare for production deployment scenarios

---

**Document Status**: Ready for Implementation  
**Priority**: CRITICAL - Blocking Phase 2  
**Estimated Completion**: 5 days  
**Dependencies**: RVNKCore Phase 1 Foundation (90% complete)
