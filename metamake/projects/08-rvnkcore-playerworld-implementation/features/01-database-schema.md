# Database Schema Feature Specification

**Feature ID**: 01-database-schema  
**Priority**: Critical  
**Dependencies**: RVNKCore Database Layer  
**Implementation Phase**: 1

## Overview

This feature implements the enhanced database schema for per-world player tracking, separating global player data from world-specific information to enable comprehensive world management and analytics capabilities.

## Database Schema Design

### Enhanced rvnk_players Table

**Purpose**: Global player data focusing on server-wide information

```sql
CREATE TABLE rvnk_players (
    id VARCHAR(36) PRIMARY KEY,              -- Player UUID
    current_name VARCHAR(16) NOT NULL,       -- Current player name
    name_history TEXT,                       -- JSON array of previous names
    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- First server join
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- Last activity
    current_world VARCHAR(255),              -- Currently active world
    times_joined INTEGER DEFAULT 1,          -- Total join count
    total_playtime_seconds BIGINT DEFAULT 0, -- Aggregate playtime across worlds
    primary_group VARCHAR(100),              -- Primary permission group
    groups TEXT,                             -- Comma-separated group list
    banned BOOLEAN DEFAULT FALSE,            -- Ban status
    metadata TEXT                            -- JSON metadata storage
);
```

**Key Changes from Legacy Schema:**
- Removed location fields (last_x, last_y, last_z, last_yaw, last_pitch)
- Added `current_world` field for active world tracking
- Retained `total_playtime_seconds` as aggregate value
- Enhanced `name_history` with proper JSON structure

### New rvnk_worlds Table

**Purpose**: Comprehensive world metadata and configuration tracking

```sql
CREATE TABLE rvnk_worlds (
    world_name VARCHAR(255) PRIMARY KEY,     -- World identifier
    display_name VARCHAR(255),               -- Human-readable world name
    world_type VARCHAR(100) NOT NULL,        -- World type (NORMAL, NETHER, END, FLAT, etc.)
    environment VARCHAR(50) NOT NULL,        -- Environment (NORMAL, NETHER, THE_END)
    world_path TEXT,                         -- File system path to world folder
    difficulty VARCHAR(20),                  -- Difficulty setting (PEACEFUL, EASY, NORMAL, HARD)
    game_mode VARCHAR(20),                   -- Default game mode (SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR)
    seed BIGINT,                             -- World generation seed
    spawn_x INTEGER DEFAULT 0,               -- World spawn X coordinate
    spawn_y INTEGER DEFAULT 64,              -- World spawn Y coordinate
    spawn_z INTEGER DEFAULT 0,               -- World spawn Z coordinate
    border_size DOUBLE,                      -- World border size
    border_center_x DOUBLE DEFAULT 0,        -- World border center X
    border_center_z DOUBLE DEFAULT 0,        -- World border center Z
    allow_animals BOOLEAN DEFAULT TRUE,      -- Animals spawn setting
    allow_monsters BOOLEAN DEFAULT TRUE,     -- Monsters spawn setting
    allow_pvp BOOLEAN DEFAULT TRUE,          -- PvP enabled setting
    keep_spawn_in_memory BOOLEAN DEFAULT TRUE, -- Keep spawn chunks loaded
    auto_save BOOLEAN DEFAULT TRUE,          -- Auto-save world setting
    is_loaded BOOLEAN DEFAULT FALSE,         -- Currently loaded status
    is_enabled BOOLEAN DEFAULT TRUE,         -- World enabled for players
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- World creation date
    last_loaded TIMESTAMP,                   -- Last time world was loaded
    player_count INTEGER DEFAULT 0,          -- Current player count
    total_visits BIGINT DEFAULT 0,           -- Total historical visits
    total_playtime BIGINT DEFAULT 0,         -- Aggregate playtime across all players
    metadata TEXT,                           -- Additional JSON metadata
    tags TEXT                                -- Comma-separated tags for categorization
);
```

### New rvnk_player_world_data Table

**Purpose**: Comprehensive per-world player tracking

```sql
CREATE TABLE rvnk_player_world_data (
    player_id VARCHAR(36) NOT NULL,          -- Player UUID (FK)
    world_name VARCHAR(255) NOT NULL,        -- World identifier (FK)
    last_x DOUBLE DEFAULT 0.0,               -- Last known X coordinate
    last_y DOUBLE DEFAULT 64.0,              -- Last known Y coordinate
    last_z DOUBLE DEFAULT 0.0,               -- Last known Z coordinate
    last_yaw FLOAT DEFAULT 0.0,              -- Last view direction (yaw)
    last_pitch FLOAT DEFAULT 0.0,            -- Last view direction (pitch)
    last_biome VARCHAR(100),                 -- Last known biome
    first_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- First visit timestamp
    last_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- Most recent visit
    visit_count INTEGER DEFAULT 1,           -- Number of visits to world
    playtime_seconds BIGINT DEFAULT 0,       -- Playtime in this world
    death_count INTEGER DEFAULT 0,           -- Deaths in this world
    blocks_broken BIGINT DEFAULT 0,          -- Blocks broken in this world
    blocks_placed BIGINT DEFAULT 0,          -- Blocks placed in this world
    distance_traveled BIGINT DEFAULT 0,      -- Total distance traveled (in blocks)
    achievements_unlocked INTEGER DEFAULT 0, -- Achievements earned in this world
    last_death_location TEXT,               -- JSON: last death coordinates
    metadata TEXT,                           -- World-specific JSON metadata
    PRIMARY KEY (player_id, world_name),     -- Composite primary key
    FOREIGN KEY (player_id) REFERENCES rvnk_players(id) ON DELETE CASCADE,
    FOREIGN KEY (world_name) REFERENCES rvnk_worlds(world_name) ON DELETE CASCADE
);
```

**Schema Features:**
- Composite primary key enables efficient player/world lookups
- Foreign key constraint ensures data integrity
- Cascade delete maintains consistency when players are removed
- Comprehensive location tracking including biome information

### Performance Indexes

**Critical Performance Indexes:**

```sql
-- Player-focused queries
CREATE INDEX idx_players_current_name ON rvnk_players(current_name);
CREATE INDEX idx_players_last_seen ON rvnk_players(last_seen);
CREATE INDEX idx_players_current_world ON rvnk_players(current_world);
CREATE INDEX idx_players_total_playtime ON rvnk_players(total_playtime_seconds);

-- World metadata queries
CREATE INDEX idx_worlds_type ON rvnk_worlds(world_type);
CREATE INDEX idx_worlds_environment ON rvnk_worlds(environment);
CREATE INDEX idx_worlds_enabled ON rvnk_worlds(is_enabled, is_loaded);
CREATE INDEX idx_worlds_player_count ON rvnk_worlds(player_count);
CREATE INDEX idx_worlds_total_visits ON rvnk_worlds(total_visits);
CREATE INDEX idx_worlds_created_date ON rvnk_worlds(created_date);
CREATE INDEX idx_worlds_last_loaded ON rvnk_worlds(last_loaded);

-- Player world data queries
CREATE INDEX idx_player_world_last_visit ON rvnk_player_world_data(player_id, last_visit);
CREATE INDEX idx_world_players ON rvnk_player_world_data(world_name, last_visit);
CREATE INDEX idx_world_activity ON rvnk_player_world_data(world_name, visit_count);
CREATE INDEX idx_player_playtime ON rvnk_player_world_data(player_id, playtime_seconds);
CREATE INDEX idx_world_total_playtime ON rvnk_player_world_data(world_name, playtime_seconds);
CREATE INDEX idx_player_deaths ON rvnk_player_world_data(player_id, death_count);
CREATE INDEX idx_world_deaths ON rvnk_player_world_data(world_name, death_count);
CREATE INDEX idx_player_blocks ON rvnk_player_world_data(player_id, blocks_broken, blocks_placed);
CREATE INDEX idx_world_blocks ON rvnk_player_world_data(world_name, blocks_broken, blocks_placed);
```

**Index Usage Patterns:**
- `idx_players_current_name`: Player lookup by name
- `idx_players_last_seen`: Recent activity queries
- `idx_players_current_world`: Players in specific world
- `idx_worlds_type`: Filter worlds by type (NORMAL, NETHER, etc.)
- `idx_worlds_environment`: Environment-based world queries
- `idx_worlds_enabled`: Active/loaded world filtering
- `idx_player_world_last_visit`: Player's world activity timeline
- `idx_world_players`: World visitor lists and recent activity
- `idx_world_activity`: World popularity and usage statistics
- `idx_player_blocks`: Player building/mining statistics
- `idx_world_blocks`: World modification analytics

## Data Types and Constraints

### Data Type Specifications

**Coordinate Storage:**
- Location coordinates: `DOUBLE` precision for accurate positioning
- View angles: `FLOAT` precision sufficient for yaw/pitch values
- Biome storage: `VARCHAR(100)` accommodates all Minecraft biome names

**Temporal Data:**
- All timestamps use `TIMESTAMP` with default `CURRENT_TIMESTAMP`
- Playtime stored as `BIGINT` in seconds for precision and range
- Visit counts as `INTEGER` with reasonable upper bounds

**Text and Metadata:**
- Player names: `VARCHAR(16)` matching Minecraft constraints
- World names: `VARCHAR(255)` for flexibility with custom worlds
- Metadata: `TEXT` fields for JSON storage with future extensibility

### Constraint Validation

**Business Rules:**
- Player names must be non-empty and follow Minecraft naming conventions
- World names must be valid identifiers without special characters
- Coordinates must be within reasonable Minecraft world boundaries
- Playtime and visit counts must be non-negative values

**Data Integrity:**
- Foreign key constraints ensure referential integrity
- Cascade delete prevents orphaned world data
- Composite primary key prevents duplicate player/world combinations
- Default values provide sensible starting points for new records

## Migration Strategy

### Legacy Schema Migration

**Phase 1: Schema Addition**
1. Create new `rvnk_player_world_data` table alongside existing structure
2. Add `current_world` column to existing `rvnk_players` table
3. Create all performance indexes
4. Validate schema integrity

**Phase 2: Data Migration**

```sql
-- First, populate worlds table with current world data
INSERT INTO rvnk_worlds (world_name, display_name, world_type, environment)
SELECT DISTINCT 
    current_world,
    current_world as display_name,
    'NORMAL' as world_type,
    'NORMAL' as environment
FROM rvnk_players 
WHERE current_world IS NOT NULL
ON DUPLICATE KEY UPDATE world_name = world_name;

-- Add common world environments
INSERT INTO rvnk_worlds (world_name, display_name, world_type, environment)
VALUES 
    ('world_nether', 'The Nether', 'NORMAL', 'NETHER'),
    ('world_the_end', 'The End', 'NORMAL', 'THE_END')
ON DUPLICATE KEY UPDATE world_name = world_name;

-- Migrate existing location data to world-specific records
INSERT INTO rvnk_player_world_data (
    player_id, world_name, last_x, last_y, last_z, 
    last_yaw, last_pitch, first_visit, last_visit, 
    playtime_seconds
)
SELECT 
    id, 
    COALESCE(current_world, 'world') as world_name,
    last_x, last_y, last_z, last_yaw, last_pitch,
    first_join, last_seen,
    total_playtime_seconds
FROM rvnk_players 
WHERE last_x IS NOT NULL;

-- Update world statistics based on migrated data
UPDATE rvnk_worlds w 
SET 
    total_visits = (
        SELECT COALESCE(SUM(visit_count), 0)
        FROM rvnk_player_world_data pwd 
        WHERE pwd.world_name = w.world_name
    ),
    total_playtime = (
        SELECT COALESCE(SUM(playtime_seconds), 0)
        FROM rvnk_player_world_data pwd 
        WHERE pwd.world_name = w.world_name
    );
```

**Phase 3: Schema Cleanup**
1. Remove location columns from `rvnk_players` table
2. Update application code to use new schema
3. Validate data migration completeness
4. Remove migration scripts and temporary code

### Rollback Procedures

**Data Backup Strategy:**
- Complete database backup before migration
- Export existing player location data
- Create rollback scripts to restore original schema
- Test rollback procedures in development environment

**Rollback Implementation:**
```sql
-- Rollback script to restore location fields to rvnk_players
ALTER TABLE rvnk_players ADD COLUMN last_x DOUBLE;
ALTER TABLE rvnk_players ADD COLUMN last_y DOUBLE;
ALTER TABLE rvnk_players ADD COLUMN last_z DOUBLE;
ALTER TABLE rvnk_players ADD COLUMN last_yaw FLOAT;
ALTER TABLE rvnk_players ADD COLUMN last_pitch FLOAT;

-- Restore location data from world-specific table
UPDATE rvnk_players p 
SET (last_x, last_y, last_z, last_yaw, last_pitch) = (
    SELECT pwd.last_x, pwd.last_y, pwd.last_z, pwd.last_yaw, pwd.last_pitch
    FROM rvnk_player_world_data pwd 
    WHERE pwd.player_id = p.id AND pwd.world_name = p.current_world
);
```

## Performance Considerations

### Query Optimization

**Common Query Patterns:**
1. Player world history: `SELECT * FROM rvnk_player_world_data WHERE player_id = ?`
2. World visitor list: `SELECT * FROM rvnk_player_world_data WHERE world_name = ? ORDER BY last_visit DESC`
3. Recent activity: `SELECT * FROM rvnk_player_world_data WHERE last_visit > ?`
4. Top worlds by playtime: `SELECT world_name, SUM(playtime_seconds) FROM rvnk_player_world_data GROUP BY world_name`

**Index Utilization:**
- All common queries leverage existing indexes
- Composite indexes optimize player+world lookups
- Separate indexes handle world-focused analytics queries
- Playtime indexes support performance ranking queries

### Storage Efficiency

**Disk Space Considerations:**
- Composite primary key reduces storage overhead vs. surrogate keys
- Efficient data types minimize row size
- Strategic indexing balances query performance with storage cost
- JSON metadata fields only store necessary extensibility data

**Growth Projections:**
- Estimated 1KB per player per world visited
- 1000 players across 10 worlds = ~10MB base data
- Indexes add approximately 30-40% storage overhead
- Total storage scales linearly with player count and world diversity

## Testing Requirements

### Schema Validation Tests

**Structure Validation:**
- Verify all tables created with correct column types
- Confirm all indexes exist and are properly configured
- Validate foreign key constraints and cascade behavior
- Test composite primary key uniqueness enforcement

**Data Integrity Tests:**
- Attempt to insert invalid data and verify constraint failures
- Test cascade delete behavior when removing players
- Validate default value assignment for new records
- Confirm transaction rollback behavior on failures

### Migration Testing

**Migration Validation:**
- Test migration with various legacy data scenarios
- Verify data integrity before and after migration
- Validate performance impact during migration process
- Test rollback procedures with migrated data

**Performance Testing:**
- Benchmark query performance with realistic data volumes
- Test concurrent access patterns during migration
- Validate index effectiveness with large datasets
- Monitor resource usage during migration operations

## Implementation Checklist

### Database Setup
- [ ] Create enhanced `rvnk_players` table structure
- [ ] Implement new `rvnk_player_world_data` table
- [ ] Create all performance indexes
- [ ] Add foreign key constraints with cascade delete
- [ ] Validate schema integrity and constraints

### Migration Implementation
- [ ] Develop data migration scripts
- [ ] Create rollback procedures
- [ ] Test migration with sample data
- [ ] Validate data integrity post-migration
- [ ] Document migration process and requirements

### Performance Optimization
- [ ] Benchmark query performance with indexes
- [ ] Validate index usage in query execution plans
- [ ] Test concurrent access patterns
- [ ] Monitor storage space and growth patterns
- [ ] Optimize index strategy based on usage patterns

This database schema provides the foundation for comprehensive per-world player tracking while maintaining high performance and data integrity standards required for production Minecraft servers.
