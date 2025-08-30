# Database Implementation Guide

**Guide ID**: 01-database-implementation-guide  
**Implementation Phase**: 1  
**Prerequisites**: RVNKCore Database Layer, ConnectionProvider

## Overview

This guide provides step-by-step instructions for implementing the enhanced database schema for per-world player tracking, including table creation, indexing, and migration strategies.

## Implementation Steps

### Step 1: Database Schema Creation

#### Enhanced rvnk_players Table

Create the enhanced players table with global focus:

```java
// File: DatabaseSetup.java - Add to createTables method
private void createPlayersTable(Connection connection) throws SQLException {
    String createPlayersSQL = """
        CREATE TABLE IF NOT EXISTS rvnk_players (
            id VARCHAR(36) PRIMARY KEY,
            current_name VARCHAR(16) NOT NULL,
            name_history TEXT DEFAULT '[]',
            first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            current_world VARCHAR(255),
            times_joined INTEGER DEFAULT 1,
            total_playtime_seconds BIGINT DEFAULT 0,
            primary_group VARCHAR(100) DEFAULT 'default',
            groups TEXT DEFAULT '[]',
            banned BOOLEAN DEFAULT FALSE,
            metadata TEXT DEFAULT '{}',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
        
    try (PreparedStatement stmt = connection.prepareStatement(createPlayersSQL)) {
        stmt.executeUpdate();
        logger.info("Enhanced rvnk_players table created successfully");
    }
}
```

#### rvnk_player_world_data Table

Create the new world-specific tracking table:

```java
private void createPlayerWorldDataTable(Connection connection) throws SQLException {
    String createWorldDataSQL = """
        CREATE TABLE IF NOT EXISTS rvnk_player_world_data (
            player_id VARCHAR(36) NOT NULL,
            world_name VARCHAR(255) NOT NULL,
            last_x DOUBLE DEFAULT 0.0,
            last_y DOUBLE DEFAULT 64.0,
            last_z DOUBLE DEFAULT 0.0,
            last_yaw FLOAT DEFAULT 0.0,
            last_pitch FLOAT DEFAULT 0.0,
            last_biome VARCHAR(100),
            first_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            visit_count INTEGER DEFAULT 1,
            playtime_seconds BIGINT DEFAULT 0,
            death_count INTEGER DEFAULT 0,
            metadata TEXT DEFAULT '{}',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, world_name),
            FOREIGN KEY (player_id) REFERENCES rvnk_players(id) ON DELETE CASCADE
        )
        """;
        
    try (PreparedStatement stmt = connection.prepareStatement(createWorldDataSQL)) {
        stmt.executeUpdate();
        logger.info("rvnk_player_world_data table created successfully");
    }
}
```

### Step 2: Index Creation

#### Critical Performance Indexes

Implement comprehensive indexing strategy:

```java
private void createPerformanceIndexes(Connection connection) throws SQLException {
    String[] indexes = {
        // Player-focused indexes
        "CREATE INDEX IF NOT EXISTS idx_players_current_name ON rvnk_players(current_name)",
        "CREATE INDEX IF NOT EXISTS idx_players_last_seen ON rvnk_players(last_seen DESC)",
        "CREATE INDEX IF NOT EXISTS idx_players_current_world ON rvnk_players(current_world)",
        "CREATE INDEX IF NOT EXISTS idx_players_total_playtime ON rvnk_players(total_playtime_seconds DESC)",
        
        // World data indexes
        "CREATE INDEX IF NOT EXISTS idx_player_world_last_visit ON rvnk_player_world_data(player_id, last_visit DESC)",
        "CREATE INDEX IF NOT EXISTS idx_world_players ON rvnk_player_world_data(world_name, last_visit DESC)",
        "CREATE INDEX IF NOT EXISTS idx_world_activity ON rvnk_player_world_data(world_name, visit_count DESC)",
        "CREATE INDEX IF NOT EXISTS idx_player_playtime ON rvnk_player_world_data(player_id, playtime_seconds DESC)",
        "CREATE INDEX IF NOT EXISTS idx_world_total_playtime ON rvnk_player_world_data(world_name, playtime_seconds DESC)",
        
        // Analytics indexes
        "CREATE INDEX IF NOT EXISTS idx_recent_visitors ON rvnk_player_world_data(last_visit DESC, world_name)",
        "CREATE INDEX IF NOT EXISTS idx_player_visit_count ON rvnk_player_world_data(player_id, visit_count DESC)"
    };
    
    for (String indexSQL : indexes) {
        try (PreparedStatement stmt = connection.prepareStatement(indexSQL)) {
            stmt.executeUpdate();
            logger.debug("Index created: " + indexSQL);
        }
    }
    
    logger.info("All performance indexes created successfully");
}
```

### Step 3: Migration Implementation

#### Legacy Data Migration

Implement comprehensive migration from existing schema:

```java
public class PlayerWorldMigration {
    
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    
    /**
     * Migrates existing player location data to world-specific records.
     */
    public void migrateLegacyData() throws SQLException {
        logger.info("Starting legacy data migration to per-world tracking");
        
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            
            try {
                // Step 1: Add current_world column if it doesn't exist
                addCurrentWorldColumn(connection);
                
                // Step 2: Migrate location data to world-specific records
                migrateLocationData(connection);
                
                // Step 3: Update schema to remove location columns
                removeLocationColumns(connection);
                
                connection.commit();
                logger.info("Legacy data migration completed successfully");
                
            } catch (SQLException e) {
                connection.rollback();
                logger.error("Migration failed, rolling back changes", e);
                throw e;
            }
        }
    }
    
    private void addCurrentWorldColumn(Connection connection) throws SQLException {
        // Check if current_world column exists
        String checkColumnSQL = """
            SELECT COUNT(*) FROM pragma_table_info('rvnk_players') 
            WHERE name = 'current_world'
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(checkColumnSQL);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next() && rs.getInt(1) == 0) {
                // Column doesn't exist, add it
                String addColumnSQL = "ALTER TABLE rvnk_players ADD COLUMN current_world VARCHAR(255)";
                try (PreparedStatement addStmt = connection.prepareStatement(addColumnSQL)) {
                    addStmt.executeUpdate();
                    logger.info("Added current_world column to rvnk_players table");
                }
            }
        }
    }
    
    private void migrateLocationData(Connection connection) throws SQLException {
        String migrationSQL = """
            INSERT INTO rvnk_player_world_data (
                player_id, world_name, last_x, last_y, last_z, 
                last_yaw, last_pitch, first_visit, last_visit, 
                playtime_seconds, visit_count
            )
            SELECT 
                id,
                COALESCE(current_world, 'world') as world_name,
                COALESCE(last_x, 0.0),
                COALESCE(last_y, 64.0),
                COALESCE(last_z, 0.0),
                COALESCE(last_yaw, 0.0),
                COALESCE(last_pitch, 0.0),
                first_join,
                last_seen,
                total_playtime_seconds,
                times_joined
            FROM rvnk_players 
            WHERE id NOT IN (
                SELECT DISTINCT player_id FROM rvnk_player_world_data
            )
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(migrationSQL)) {
            int migrated = stmt.executeUpdate();
            logger.info("Migrated location data for " + migrated + " player records");
        }
    }
    
    private void removeLocationColumns(Connection connection) throws SQLException {
        // Note: SQLite doesn't support DROP COLUMN directly
        // This would need to be implemented differently for MySQL
        String[] columnsToRemove = {"last_x", "last_y", "last_z", "last_yaw", "last_pitch"};
        
        for (String column : columnsToRemove) {
            String checkColumnSQL = """
                SELECT COUNT(*) FROM pragma_table_info('rvnk_players') 
                WHERE name = ?
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(checkColumnSQL)) {
                stmt.setString(1, column);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        logger.info("Location column " + column + " still exists - manual removal required");
                    }
                }
            }
        }
    }
}
```

### Step 4: Database Validation

#### Schema Validation

Implement comprehensive validation of the new schema:

```java
public class SchemaValidator {
    
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    
    /**
     * Validates the database schema meets requirements.
     */
    public ValidationResult validateSchema() throws SQLException {
        ValidationResult result = new ValidationResult();
        
        try (Connection connection = connectionProvider.getConnection()) {
            result.addCheck("Players table structure", validatePlayersTable(connection));
            result.addCheck("World data table structure", validateWorldDataTable(connection));
            result.addCheck("Foreign key constraints", validateForeignKeys(connection));
            result.addCheck("Performance indexes", validateIndexes(connection));
            result.addCheck("Data integrity", validateDataIntegrity(connection));
        }
        
        return result;
    }
    
    private boolean validatePlayersTable(Connection connection) throws SQLException {
        String validateSQL = """
            SELECT COUNT(*) FROM pragma_table_info('rvnk_players') 
            WHERE name IN ('id', 'current_name', 'current_world', 'total_playtime_seconds')
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(validateSQL);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int requiredColumns = rs.getInt(1);
                boolean valid = requiredColumns >= 4;
                if (valid) {
                    logger.info("Players table structure validated successfully");
                } else {
                    logger.error("Players table missing required columns");
                }
                return valid;
            }
        }
        return false;
    }
    
    private boolean validateWorldDataTable(Connection connection) throws SQLException {
        String validateSQL = """
            SELECT COUNT(*) FROM pragma_table_info('rvnk_player_world_data')
            WHERE name IN ('player_id', 'world_name', 'last_x', 'last_y', 'last_z', 
                          'visit_count', 'playtime_seconds')
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(validateSQL);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int requiredColumns = rs.getInt(1);
                boolean valid = requiredColumns >= 7;
                if (valid) {
                    logger.info("World data table structure validated successfully");
                } else {
                    logger.error("World data table missing required columns");
                }
                return valid;
            }
        }
        return false;
    }
    
    private boolean validateIndexes(Connection connection) throws SQLException {
        String[] requiredIndexes = {
            "idx_players_current_name",
            "idx_players_last_seen",
            "idx_player_world_last_visit",
            "idx_world_players"
        };
        
        int foundIndexes = 0;
        for (String indexName : requiredIndexes) {
            String checkIndexSQL = """
                SELECT COUNT(*) FROM sqlite_master 
                WHERE type = 'index' AND name = ?
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(checkIndexSQL)) {
                stmt.setString(1, indexName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        foundIndexes++;
                    }
                }
            }
        }
        
        boolean valid = foundIndexes >= requiredIndexes.length;
        if (valid) {
            logger.info("All required indexes validated successfully");
        } else {
            logger.error("Missing required indexes: found " + foundIndexes + " of " + requiredIndexes.length);
        }
        return valid;
    }
}
```

## Configuration Integration

### Database Configuration

Integrate with RVNKCore configuration system:

```java
public class PlayerWorldDatabaseConfig {
    
    private boolean migrationEnabled = true;
    private boolean validateSchemaOnStartup = true;
    private int migrationBatchSize = 1000;
    private boolean createIndexesAsync = false;
    
    public void loadConfig(ConfigurationSection config) {
        this.migrationEnabled = config.getBoolean("migration.enabled", true);
        this.validateSchemaOnStartup = config.getBoolean("validation.on_startup", true);
        this.migrationBatchSize = config.getInt("migration.batch_size", 1000);
        this.createIndexesAsync = config.getBoolean("indexes.create_async", false);
    }
    
    // Getters and setters
}
```

### Initialization Sequence

Proper initialization order in RVNKCore:

```java
public void initializePlayerWorldDatabase() throws SQLException {
    logger.info("Initializing PlayerWorld database schema");
    
    // 1. Create base schema
    DatabaseSetup setup = new DatabaseSetup(connectionProvider, plugin);
    setup.initializeDatabase();
    
    // 2. Run migrations if enabled
    if (config.isMigrationEnabled()) {
        PlayerWorldMigration migration = new PlayerWorldMigration(connectionProvider, plugin);
        migration.migrateLegacyData();
    }
    
    // 3. Validate schema if enabled
    if (config.isValidateSchemaOnStartup()) {
        SchemaValidator validator = new SchemaValidator(connectionProvider, plugin);
        ValidationResult result = validator.validateSchema();
        
        if (!result.isValid()) {
            throw new SQLException("Schema validation failed: " + result.getErrorSummary());
        }
    }
    
    logger.info("PlayerWorld database initialization completed successfully");
}
```

## Performance Testing

### Load Testing Scripts

Create performance validation scripts:

```java
@Test
public void testDatabasePerformance() throws SQLException {
    // Test 1: Single player world data retrieval
    long startTime = System.currentTimeMillis();
    Optional<PlayerWorldDataDTO> result = repository.findByPlayerAndWorld(testPlayerId, "world").join();
    long queryTime = System.currentTimeMillis() - startTime;
    
    assertThat(queryTime).isLessThan(100); // Sub-100ms requirement
    assertThat(result).isPresent();
    
    // Test 2: World visitor list retrieval
    startTime = System.currentTimeMillis();
    List<PlayerWorldDataDTO> visitors = repository.findWorldVisitors("world", 50).join();
    queryTime = System.currentTimeMillis() - startTime;
    
    assertThat(queryTime).isLessThan(200); // Sub-200ms for larger queries
    assertThat(visitors).isNotEmpty();
    
    // Test 3: Player world history
    startTime = System.currentTimeMillis();
    List<PlayerWorldDataDTO> history = repository.findAllByPlayer(testPlayerId).join();
    queryTime = System.currentTimeMillis() - startTime;
    
    assertThat(queryTime).isLessThan(150);
    assertThat(history).isNotEmpty();
}
```

## Error Handling and Recovery

### Migration Error Recovery

Implement robust error recovery:

```java
public class MigrationRecovery {
    
    /**
     * Attempts to recover from failed migration.
     */
    public void recoverFromFailedMigration() throws SQLException {
        logger.warning("Attempting migration recovery");
        
        try (Connection connection = connectionProvider.getConnection()) {
            // Check if tables are in inconsistent state
            boolean playersTableExists = checkTableExists(connection, "rvnk_players");
            boolean worldDataTableExists = checkTableExists(connection, "rvnk_player_world_data");
            
            if (playersTableExists && !worldDataTableExists) {
                // Incomplete migration - recreate world data table
                createPlayerWorldDataTable(connection);
                logger.info("Recreated missing world data table");
            }
            
            // Check for orphaned data and clean up
            cleanupOrphanedData(connection);
            
        } catch (SQLException e) {
            logger.error("Migration recovery failed", e);
            throw e;
        }
    }
    
    private void cleanupOrphanedData(Connection connection) throws SQLException {
        String cleanupSQL = """
            DELETE FROM rvnk_player_world_data 
            WHERE player_id NOT IN (SELECT id FROM rvnk_players)
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(cleanupSQL)) {
            int cleaned = stmt.executeUpdate();
            if (cleaned > 0) {
                logger.info("Cleaned up " + cleaned + " orphaned world data records");
            }
        }
    }
}
```

## Implementation Checklist

### Schema Creation
- [ ] Create enhanced rvnk_players table structure
- [ ] Implement rvnk_player_world_data table with composite key
- [ ] Add all required foreign key constraints
- [ ] Create comprehensive index strategy
- [ ] Validate schema structure meets requirements

### Migration Implementation
- [ ] Develop legacy data migration scripts
- [ ] Implement rollback procedures for safety
- [ ] Add migration validation and integrity checks
- [ ] Test migration with sample legacy data
- [ ] Create error recovery mechanisms

### Performance Optimization
- [ ] Benchmark query performance with indexes
- [ ] Validate index usage in execution plans
- [ ] Test concurrent access patterns
- [ ] Optimize batch operation performance
- [ ] Monitor storage space and growth patterns

### Integration and Validation
- [ ] Integrate with RVNKCore initialization sequence
- [ ] Add configuration management for migration settings
- [ ] Implement comprehensive schema validation
- [ ] Create performance testing suite
- [ ] Add monitoring and alerting for database operations

This database implementation provides the robust foundation required for comprehensive per-world player tracking while maintaining high performance and data integrity standards.
