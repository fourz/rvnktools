# Feature Specification: Database Schema Design

**Feature ID**: 02-database-schema  
**Priority**: High  
**Dependencies**: Service Architecture Design  
**Implementation Phase**: Week 2

## Overview

This feature defines the comprehensive database schema design for RVNKCore announcements, supporting both MySQL and SQLite databases with optimized indexing, data integrity, and performance considerations.

## Database Schema

### Primary Tables

#### announcements

```sql
CREATE TABLE announcements (
    id VARCHAR(36) PRIMARY KEY,                                -- UUID primary key
    message TEXT NOT NULL,                                     -- Announcement content
    type VARCHAR(50) NOT NULL,                                 -- Announcement category/type
    active BOOLEAN DEFAULT 1,                                  -- Enable/disable flag
    world VARCHAR(100),                                        -- World restriction (NULL = all worlds)
    permission VARCHAR(100),                                   -- Required permission (NULL = no requirement)
    display_duration_seconds INT DEFAULT 5,                   -- Display duration for actionbar/title
    priority INT DEFAULT 1,                                   -- Display priority (higher = more important)
    created_by VARCHAR(36),                                   -- Creator UUID
    owner VARCHAR(100),                                        -- Owner name for legacy compatibility
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,           -- Creation timestamp
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Last update
    
    -- Legacy migration support
    recurrence BIGINT,                                        -- Recurrence interval in seconds
    recurrence_string VARCHAR(50),                            -- Original recurrence format
    date DATE,                                                -- Scheduled date (legacy)
    time TIME,                                                -- Scheduled time (legacy)
    imported BOOLEAN DEFAULT 0,                              -- Migration tracking flag
    
    -- Indexes for performance
    INDEX idx_announcements_active (active),
    INDEX idx_announcements_type (type),
    INDEX idx_announcements_world (world),
    INDEX idx_announcements_permission (permission),
    INDEX idx_announcements_created_at (created_at),
    INDEX idx_announcements_priority (priority),
    INDEX idx_announcements_type_world_active (type, world, active),
    INDEX idx_announcements_owner (owner)
);
```

#### announcement_types

```sql
CREATE TABLE announcement_types (
    id VARCHAR(50) PRIMARY KEY,                               -- Type identifier
    name VARCHAR(100) NOT NULL,                              -- Display name
    description TEXT,                                         -- Type description
    default_priority INT DEFAULT 1,                          -- Default priority for this type
    color VARCHAR(7) DEFAULT '#FFFFFF',                      -- Display color (hex)
    enabled BOOLEAN DEFAULT 1,                               -- Type enabled flag
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_announcement_types_enabled (enabled)
);
```

#### announcement_schedules

```sql
CREATE TABLE announcement_schedules (
    id VARCHAR(36) PRIMARY KEY,                              -- Schedule UUID
    announcement_id VARCHAR(36) NOT NULL,                    -- Reference to announcement
    schedule_type ENUM('interval', 'cron', 'once') NOT NULL, -- Schedule type
    interval_seconds BIGINT,                                 -- For interval scheduling
    cron_expression VARCHAR(100),                            -- For cron scheduling
    scheduled_time TIMESTAMP,                                -- For one-time scheduling
    last_executed TIMESTAMP,                                 -- Last execution time
    next_execution TIMESTAMP,                                -- Next execution time
    enabled BOOLEAN DEFAULT 1,                               -- Schedule enabled flag
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    INDEX idx_schedules_announcement (announcement_id),
    INDEX idx_schedules_next_execution (next_execution, enabled),
    INDEX idx_schedules_type (schedule_type)
);
```

#### player_announcement_preferences

```sql
CREATE TABLE player_announcement_preferences (
    player_uuid VARCHAR(36) NOT NULL,                        -- Player UUID
    announcement_type VARCHAR(50) NOT NULL,                  -- Type to disable
    disabled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,         -- When disabled
    
    PRIMARY KEY (player_uuid, announcement_type),
    FOREIGN KEY (announcement_type) REFERENCES announcement_types(id) ON DELETE CASCADE,
    INDEX idx_player_prefs_uuid (player_uuid),
    INDEX idx_player_prefs_type (announcement_type)
);
```

#### announcement_delivery_log

```sql
CREATE TABLE announcement_delivery_log (
    id VARCHAR(36) PRIMARY KEY,                              -- Log entry UUID
    announcement_id VARCHAR(36) NOT NULL,                    -- Reference to announcement
    player_uuid VARCHAR(36),                                 -- Target player (NULL = broadcast)
    delivery_method ENUM('chat', 'actionbar', 'title', 'subtitle') NOT NULL,
    delivered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,        -- Delivery timestamp
    success BOOLEAN DEFAULT 1,                               -- Delivery success flag
    error_message TEXT,                                       -- Error details if failed
    
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    INDEX idx_delivery_log_announcement (announcement_id),
    INDEX idx_delivery_log_player (player_uuid),
    INDEX idx_delivery_log_delivered_at (delivered_at),
    INDEX idx_delivery_log_success (success)
);
```

### SQLite Compatibility

For SQLite compatibility, the following adjustments are made:

```sql
-- SQLite version of announcements table
CREATE TABLE announcements (
    id TEXT PRIMARY KEY,
    message TEXT NOT NULL,
    type TEXT NOT NULL,
    active INTEGER DEFAULT 1,
    world TEXT,
    permission TEXT,
    display_duration_seconds INTEGER DEFAULT 5,
    priority INTEGER DEFAULT 1,
    created_by TEXT,
    owner TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    recurrence INTEGER,
    recurrence_string TEXT,
    date DATE,
    time TIME,
    imported INTEGER DEFAULT 0
);

-- SQLite indexes
CREATE INDEX idx_announcements_active ON announcements(active);
CREATE INDEX idx_announcements_type ON announcements(type);
CREATE INDEX idx_announcements_world ON announcements(world);
CREATE INDEX idx_announcements_permission ON announcements(permission);
CREATE INDEX idx_announcements_created_at ON announcements(created_at);
CREATE INDEX idx_announcements_priority ON announcements(priority);
CREATE INDEX idx_announcements_type_world_active ON announcements(type, world, active);
```

## Data Migration Schema

### Migration Tracking

```sql
CREATE TABLE schema_migrations (
    version VARCHAR(50) PRIMARY KEY,                         -- Migration version
    description TEXT,                                         -- Migration description
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,          -- Application timestamp
    checksum VARCHAR(64)                                      -- Migration checksum for integrity
);

-- Track YAML migration status
CREATE TABLE yaml_migration_status (
    file_path TEXT PRIMARY KEY,                              -- Original YAML file path
    file_checksum VARCHAR(64),                               -- File checksum
    migrated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,         -- Migration timestamp
    announcement_count INTEGER,                              -- Number of migrated announcements
    type_count INTEGER,                                      -- Number of migrated types
    success BOOLEAN DEFAULT 1,                              -- Migration success flag
    error_log TEXT                                           -- Error details if failed
);
```

## Performance Optimization

### Index Strategy

**Primary Lookup Indexes:**
- `announcements.id` (PRIMARY KEY) - Direct announcement lookup
- `announcements.active` - Filter active/inactive announcements
- `announcements.type` - Query by announcement category

**Query Optimization Indexes:**
- `announcements.type, world, active` (COMPOSITE) - Most common query pattern
- `announcements.created_at` - Chronological sorting and date range queries
- `announcements.priority` - Priority-based ordering

**Administrative Indexes:**
- `announcements.owner` - Legacy ownership queries
- `announcements.permission` - Permission-based filtering

### Connection Pool Configuration

```java
// HikariCP configuration for announcement workload
public static HikariConfig createAnnouncementPoolConfig() {
    HikariConfig config = new HikariConfig();
    
    // Announcement system is read-heavy with occasional writes
    config.setMaximumPoolSize(8);
    config.setMinimumIdle(2);
    config.setConnectionTimeout(5000);
    config.setIdleTimeout(300000);    // 5 minutes
    config.setMaxLifetime(1800000);   // 30 minutes
    
    // MySQL-specific optimizations
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.addDataSourceProperty("useServerPrepStmts", "true");
    
    return config;
}
```

## Schema Evolution Support

### Version 1.0 → 1.1 Migration

```sql
-- Add scheduling support
ALTER TABLE announcements ADD COLUMN schedule_id VARCHAR(36);
ALTER TABLE announcements ADD FOREIGN KEY (schedule_id) REFERENCES announcement_schedules(id);

-- Add delivery tracking
CREATE TABLE IF NOT EXISTS announcement_delivery_log (
    -- Table definition as above
);
```

### Version 1.1 → 1.2 Migration

```sql
-- Add analytics support
ALTER TABLE announcements ADD COLUMN view_count INTEGER DEFAULT 0;
ALTER TABLE announcements ADD COLUMN last_viewed TIMESTAMP;

-- Create analytics aggregation table
CREATE TABLE announcement_analytics (
    announcement_id VARCHAR(36),
    date DATE,
    view_count INTEGER DEFAULT 0,
    unique_viewers INTEGER DEFAULT 0,
    
    PRIMARY KEY (announcement_id, date),
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);
```

## Data Integrity Constraints

### Referential Integrity

```sql
-- Ensure announcement types exist before use
ALTER TABLE announcements 
ADD CONSTRAINT fk_announcements_type 
FOREIGN KEY (type) REFERENCES announcement_types(id);

-- Cascade deletions to maintain consistency
ALTER TABLE announcement_schedules 
ADD CONSTRAINT fk_schedules_announcement 
FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE;

-- Ensure player preferences reference valid types
ALTER TABLE player_announcement_preferences 
ADD CONSTRAINT fk_preferences_type 
FOREIGN KEY (announcement_type) REFERENCES announcement_types(id) ON DELETE CASCADE;
```

### Data Validation Constraints

```sql
-- Ensure valid priority values
ALTER TABLE announcements ADD CONSTRAINT chk_priority CHECK (priority >= 0);

-- Ensure valid display duration
ALTER TABLE announcements ADD CONSTRAINT chk_duration CHECK (display_duration_seconds > 0);

-- Ensure valid color format for types
ALTER TABLE announcement_types ADD CONSTRAINT chk_color CHECK (
    color IS NULL OR color REGEXP '^#[0-9A-Fa-f]{6}$'
);
```

## Legacy YAML Migration Mapping

### YAML to Database Field Mapping

```yaml
# Original YAML structure
announcements:
  - id: "welcome"
    text: "Welcome to the server!"
    type: "general"
    recurrence: "5m"
    permission: "rvnktools.receive.general"
    owner: "admin"
    date: "2024-01-15"
    time: "1400"
    imported: false
```

```sql
-- Corresponding database record
INSERT INTO announcements (
    id, message, type, recurrence, recurrence_string, 
    permission, owner, date, time, imported, active,
    display_duration_seconds, priority, created_at, updated_at
) VALUES (
    'welcome',
    'Welcome to the server!',
    'general',
    300,                          -- 5 minutes in seconds
    '5m',                         -- Original format
    'rvnktools.receive.general',
    'admin',
    '2024-01-15',
    '14:00:00',
    0,                            -- Not imported initially
    1,                            -- Active by default
    5,                            -- Default duration
    1,                            -- Default priority
    NOW(),
    NOW()
);
```

### Type Migration Mapping

```yaml
# Original YAML type structure
announce_types:
  - id: "general"
    displayName: "General Announcements"
    color: "&a"
    enabled: true
```

```sql
-- Corresponding database record
INSERT INTO announcement_types (
    id, name, description, color, enabled, default_priority
) VALUES (
    'general',
    'General Announcements',
    'General server announcements for all players',
    '#55FF55',                    -- Convert &a to hex
    1,                            -- true -> 1
    1                             -- Default priority
);
```

## Testing Schema

### Schema Validation Queries

```sql
-- Verify table structure
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'rvnktools'
AND TABLE_NAME IN ('announcements', 'announcement_types', 'announcement_schedules');

-- Verify indexes
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'rvnktools'
AND TABLE_NAME = 'announcements';

-- Verify foreign key constraints
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'rvnktools'
AND REFERENCED_TABLE_NAME IS NOT NULL;
```

### Performance Testing Queries

```sql
-- Test common query patterns
EXPLAIN SELECT * FROM announcements WHERE active = 1 AND type = 'general';
EXPLAIN SELECT * FROM announcements WHERE active = 1 AND type = 'general' AND world = 'survival';
EXPLAIN SELECT * FROM announcements WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY);
EXPLAIN SELECT a.*, at.name, at.color FROM announcements a JOIN announcement_types at ON a.type = at.id;
```

## Implementation Considerations

### Database-Specific Adaptations

**MySQL Specific:**
- Use `TIMESTAMP` with automatic update triggers
- Leverage foreign key constraints for data integrity
- Use `ENUM` types for restricted value sets
- Optimize with composite indexes

**SQLite Specific:**
- Use `INTEGER` instead of `BOOLEAN`
- Manual timestamp updates in application code
- Simplified constraint checking
- Reduced index complexity for better performance

### Migration Safety

1. **Backup Requirements**: Full database backup before any schema changes
2. **Rollback Procedures**: Maintain previous schema version for rollback capability
3. **Data Validation**: Verify data integrity after each migration step
4. **Performance Impact**: Monitor query performance before and after migrations

This comprehensive database schema provides the foundation for the RVNKCore announcements system while maintaining compatibility with existing YAML-based configurations and supporting future enhancements.
