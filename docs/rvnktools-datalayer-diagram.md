# RVNKTools Data Layer Architecture Diagram

*Last Updated: July 22, 2025*

This document provides a comprehensive overview of the RVNKTools data layer architecture, including project structure diagrams, relationship models, and database schema specifications for the transition to the RVNKCore architecture.

## 1. Project Structure

### 1.1 Current Monolithic Structure

The current RVNKTools architecture follows a monolithic approach where all components are part of a single plugin:

```
rvnktools/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/
│       │       └── fourz/
│       │           └── rvnktools/
│       │               ├── RVNKTools.java (Main plugin class)
│       │               ├── announceManager/
│       │               │   ├── AnnounceManager.java
│       │               │   ├── AnnounceConfig.java
│       │               │   └── ...
│       │               ├── hatManager/
│       │               │   ├── HatManager.java
│       │               │   └── ...
│       │               ├── linkMaker/
│       │               │   ├── LinkManager.java
│       │               │   └── ...
│       │               ├── command/
│       │               │   ├── manager/
│       │               │   │   ├── CommandManager.java
│       │               │   │   ├── BaseCommand.java
│       │               │   │   └── ...
│       │               │   └── ...
│       │               ├── database/
│       │               │   ├── DatabaseManager.java
│       │               │   ├── SQLiteManager.java
│       │               │   └── ...
│       │               ├── util/
│       │               │   ├── log/
│       │               │   │   ├── LogManager.java
│       │               │   │   └── ...
│       │               │   ├── ChatService.java
│       │               │   └── ...
│       │               └── config/
│       │                   ├── ConfigManager.java
│       │                   └── ...
│       └── resources/
│           ├── plugin.yml
│           ├── config.yml
│           └── ...
└── pom.xml
```

### 1.2 Target Transitive Structure

The target architecture separates concerns into two distinct plugins with a clear dependency relationship:

```
rvnk-mono-repo/
├── rvnkcore/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── org/
│   │       │       └── fourz/
│   │       │           └── rvnkcore/
│   │       │               ├── RVNKCore.java (Core plugin class)
│   │       │               ├── api/
│   │       │               │   ├── RVNKCoreAPI.java
│   │       │               │   ├── service/
│   │       │               │   │   ├── IPlayerService.java
│   │       │               │   │   ├── IAnnouncementService.java
│   │       │               │   │   └── ...
│   │       │               │   └── model/
│   │       │               │       ├── PlayerDTO.java
│   │       │               │       ├── AnnouncementDTO.java
│   │       │               │       └── ...
│   │       │               ├── database/
│   │       │               │   ├── ConnectionProvider.java
│   │       │               │   ├── provider/
│   │       │               │   │   ├── MySQLProvider.java
│   │       │               │   │   └── SQLiteProvider.java
│   │       │               │   ├── query/
│   │       │               │   │   ├── QueryBuilder.java
│   │       │               │   │   ├── QueryExecutor.java
│   │       │               │   │   └── ...
│   │       │               │   ├── schema/
│   │       │               │   │   ├── SchemaManager.java
│   │       │               │   │   └── ...
│   │       │               │   └── repository/
│   │       │               │       ├── BaseRepository.java
│   │       │               │       ├── PlayerRepository.java
│   │       │               │       ├── AnnouncementRepository.java
│   │       │               │       └── ...
│   │       │               ├── service/
│   │       │               │   ├── ServiceRegistry.java
│   │       │               │   ├── player/
│   │       │               │   │   ├── PlayerService.java
│   │       │               │   │   └── ...
│   │       │               │   ├── announcement/
│   │       │               │   │   ├── AnnouncementService.java
│   │       │               │   │   └── ...
│   │       │               │   └── ...
│   │       │               ├── util/
│   │       │               │   ├── log/
│   │       │               │   │   ├── LogManager.java
│   │       │               │   │   └── ...
│   │       │               │   └── config/
│   │       │               │       ├── ConfigurationManager.java
│   │       │               │       └── ...
│   │       │               └── rest/
│   │       │                   ├── RestServer.java
│   │       │                   ├── controller/
│   │       │                   │   ├── PlayerController.java
│   │       │                   │   └── ...
│   │       │                   └── ...
│   │       └── resources/
│   │           ├── plugin.yml
│   │           ├── config.yml
│   │           └── ...
│   └── pom.xml
│
└── rvnktools/
    ├── src/
    │   └── main/
    │       ├── java/
    │       │   └── org/
    │       │       └── fourz/
    │       │           └── rvnktools/
    │       │               ├── RVNKTools.java (Feature plugin class)
    │       │               ├── announceManager/
    │       │               │   ├── AnnounceManager.java (using RVNKCore API)
    │       │               │   └── ...
    │       │               ├── hatManager/
    │       │               │   ├── HatManager.java
    │       │               │   └── ...
    │       │               ├── linkMaker/
    │       │               │   ├── LinkManager.java
    │       │               │   └── ...
    │       │               ├── command/
    │       │               │   ├── manager/
    │       │               │   │   ├── CommandManager.java
    │       │               │   │   └── ...
    │       │               │   └── ...
    │       │               └── config/
    │       │                   ├── ToolsConfigManager.java
    │       │                   └── ...
    │       └── resources/
    │           ├── plugin.yml (with dependency on RVNKCore)
    │           ├── config.yml
    │           └── ...
    └── pom.xml (with dependency on RVNKCore)
```

### 1.3 Dependency Relationships

```
┌─────────────────────────┐
│        RVNKCore         │
│                         │
│  ┌───────────────────┐  │
│  │ Core API          │  │
│  │ - Interfaces      │  │
│  │ - DTOs            │◀───────────┐
│  │ - Events          │  │         │
│  └───────────────────┘  │         │
│                         │         │
│  ┌───────────────────┐  │         │
│  │ Data Layer        │  │         │ API Dependency
│  │ - Database        │  │         │
│  │ - Repositories    │  │         │
│  └───────────────────┘  │         │
│                         │         │
│  ┌───────────────────┐  │         │
│  │ Service Layer     │  │         │
│  │ - Implementation  │  │         │
│  └───────────────────┘  │         │
└─────────────────────────┘         │
                                    │
┌─────────────────────────┐         │
│       RVNKTools         │         │
│                         │         │
│  ┌───────────────────┐  │         │
│  │ Features          │  │         │
│  │ - Announcements   │◀─┼─────────┘
│  │ - Hat Manager     │  │
│  │ - Link Maker      │  │
│  └───────────────────┘  │
│                         │
│  ┌───────────────────┐  │
│  │ Commands          │  │
│  │ - Command Manager │  │
│  └───────────────────┘  │
└─────────────────────────┘
```

## 2. Core Database Schema

### 2.1 API Version Management Schema

The RVNKCore database includes schema version tracking to ensure proper migrations and compatibility:

```sql
CREATE TABLE api_version (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    component VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    checksum VARCHAR(64),
    execution_time INTEGER,
    UNIQUE (component, version)
);

CREATE TABLE schema_migrations (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(50) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    script_name VARCHAR(255),
    checksum VARCHAR(64),
    execution_time INTEGER,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (version)
);
```

### 2.2 Player Data Schema

The central player registry maintains consistent player data across all plugins:

```sql
CREATE TABLE players (
    id VARCHAR(36) PRIMARY KEY,           -- UUID of the player
    username VARCHAR(16) NOT NULL,        -- Current username
    first_join TIMESTAMP NOT NULL,        -- When player first joined
    last_seen TIMESTAMP NOT NULL,         -- When player was last online
    playtime_seconds BIGINT DEFAULT 0,    -- Total playtime in seconds
    is_banned BOOLEAN DEFAULT FALSE,      -- Ban status
    UNIQUE (username)
);

CREATE TABLE player_username_history (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    player_id VARCHAR(36) NOT NULL,
    previous_name VARCHAR(16) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE player_metadata (
    player_id VARCHAR(36) NOT NULL,
    namespace VARCHAR(50) NOT NULL,       -- Plugin namespace (e.g., 'rvnktools', 'rvnklore')
    key VARCHAR(50) NOT NULL,             -- Metadata key
    value TEXT,                           -- Serialized value
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, namespace, key),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE player_preferences (
    player_id VARCHAR(36) NOT NULL,
    namespace VARCHAR(50) NOT NULL,       -- Plugin namespace
    key VARCHAR(50) NOT NULL,             -- Preference key
    value TEXT,                           -- Preference value
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, namespace, key),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);
```

### 2.3 Announcement Schema

The announcement system is migrated to the core database schema:

```sql
CREATE TABLE announcements (
    id VARCHAR(36) PRIMARY KEY,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    permission VARCHAR(100),
    owner VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_time VARCHAR(50),         -- Cron expression or specific time
    expiration TIMESTAMP,               -- When announcement expires
    recurrence VARCHAR(50),             -- Recurrence pattern
    active BOOLEAN DEFAULT TRUE,
    metadata TEXT,                      -- JSON serialized additional data
    FOREIGN KEY (owner) REFERENCES players(id) ON DELETE SET NULL
);

CREATE TABLE announcement_settings (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    description TEXT,
    UNIQUE (key)
);

CREATE TABLE announcement_deliveries (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    announcement_id VARCHAR(36) NOT NULL,
    delivered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    player_count INTEGER DEFAULT 0,
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);

CREATE TABLE player_announcement_preferences (
    player_id VARCHAR(36) NOT NULL,
    disabled_types TEXT,                -- JSON array of disabled announcement types
    location VARCHAR(20) DEFAULT 'chat', -- Where to display announcements (chat, title, action-bar)
    sound VARCHAR(50) DEFAULT 'none',    -- Sound to play with announcements
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);
```

### 2.4 Link Management Schema

The link system is migrated to the core database:

```sql
CREATE TABLE links (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    description TEXT,
    owner VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    click_count INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (owner) REFERENCES players(id) ON DELETE SET NULL
);

CREATE TABLE link_clicks (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    link_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (link_id) REFERENCES links(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);
```

### 2.5 Plugin Registration Schema

This schema allows the core to track which plugins are using its services:

```sql
CREATE TABLE registered_plugins (
    id VARCHAR(100) PRIMARY KEY,        -- Plugin ID (e.g., 'rvnktools', 'rvnklore')
    version VARCHAR(20) NOT NULL,       -- Plugin version
    first_registered TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN DEFAULT TRUE,
    config TEXT                         -- JSON serialized plugin-specific configuration
);

CREATE TABLE plugin_data_access (
    plugin_id VARCHAR(100) NOT NULL,
    namespace VARCHAR(50) NOT NULL,     -- Data namespace being accessed
    access_level VARCHAR(20) NOT NULL,  -- 'READ', 'WRITE', 'ADMIN'
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(100),           -- Who granted the access
    PRIMARY KEY (plugin_id, namespace),
    FOREIGN KEY (plugin_id) REFERENCES registered_plugins(id) ON DELETE CASCADE
);
```

## 3. Entity Relationship Diagram

### 3.1 Core Data Relationships

```
                                    ┌──────────────────┐
                                    │    players       │
                                    ├──────────────────┤
                                    │ id (PK)          │
                                    │ username         │
                                    │ first_join       │
                                    │ last_seen        │
                                    │ playtime_seconds │
                                    │ is_banned        │
                                    └─────────┬────────┘
                                              │
                 ┌───────────────────────────┼───────────────────────────┐
                 │                           │                           │
                 │                           │                           │
    ┌────────────▼───────────┐   ┌───────────▼───────────┐   ┌───────────▼───────────┐
    │player_username_history │   │   player_metadata     │   │  player_preferences   │
    ├────────────────────────┤   ├───────────────────────┤   ├───────────────────────┤
    │ id (PK)                │   │ player_id (PK)        │   │ player_id (PK)        │
    │ player_id (FK)         │   │ namespace (PK)        │   │ namespace (PK)        │
    │ previous_name          │   │ key (PK)              │   │ key (PK)              │
    │ changed_at             │   │ value                 │   │ value                 │
    └────────────────────────┘   │ updated_at            │   │ updated_at            │
                                 └───────────────────────┘   └───────────────────────┘

                 ┌───────────────────────────┐
                 │      announcements        │
                 ├───────────────────────────┤
                 │ id (PK)                   │
                 │ message                   │
                 │ type                      │
                 │ permission                │
                 │ owner (FK)────────────────┼───────────────┐
                 │ created_at                │               │
                 │ updated_at                │               │
                 │ scheduled_time            │               │
                 │ expiration                │               │
                 │ recurrence                │               │
                 │ active                    │               │
                 │ metadata                  │               │
                 └───────────┬───────────────┘               │
                             │                               │
                             │                               │
            ┌────────────────▼─────────────┐                 │
            │   announcement_deliveries    │                 │
            ├────────────────────────────┬─┤                 │
            │ id (PK)                    │ │                 │
            │ announcement_id (FK)       │ │                 │
            │ delivered_at               │ │                 │
            │ player_count               │ │                 │
            └────────────────────────────┘ │                 │
                                           │                 │
                                           │                 ▼
            ┌────────────────────────────┐ │    ┌───────────────────────┐
            │player_announcement_preferences│    │       players        │
            ├────────────────────────────┤ │    ├───────────────────────┤
            │ player_id (PK) (FK)────────┼─┼────┤ id (PK)              │
            │ disabled_types             │ │    │ ...                  │
            │ location                   │ │    └───────────────────────┘
            │ sound                      │ │
            │ updated_at                 │ │
            └────────────────────────────┘ │
                                           │
                                           │
            ┌────────────────────────────┐ │
            │           links            │ │
            ├────────────────────────────┤ │
            │ id (PK)                    │ │
            │ name                       │ │
            │ url                        │ │
            │ description                │ │
            │ owner (FK)                 │ │
            │ created_at                 │ │
            │ updated_at                 │ │
            │ click_count                │ │
            │ active                     │ │
            └──────────────┬─────────────┘ │
                           │               │
                           │               │
                ┌──────────▼─────────────┐ │
                │       link_clicks      │ │
                ├────────────────────────┤ │
                │ id (PK)                │ │
                │ link_id (FK)           │ │
                │ player_id (FK)─────────┼─┘
                │ clicked_at             │
                └────────────────────────┘
```

### 3.2 Plugin Integration Relationships

```
                 ┌───────────────────────┐
                 │  registered_plugins   │
                 ├───────────────────────┤
                 │ id (PK)               │
                 │ version               │
                 │ first_registered      │
                 │ last_active           │
                 │ enabled               │
                 │ config                │
                 └─────────┬─────────────┘
                           │
                           │
                ┌──────────▼─────────────┐
                │   plugin_data_access   │
                ├────────────────────────┤
                │ plugin_id (PK) (FK)    │
                │ namespace (PK)         │
                │ access_level           │
                │ granted_at             │
                │ granted_by             │
                └────────────────────────┘

                 ┌───────────────────────┐
                 │     api_version       │
                 ├───────────────────────┤
                 │ id (PK)               │
                 │ component             │
                 │ version               │
                 │ installed_at          │
                 │ description           │
                 │ success               │
                 │ checksum              │
                 │ execution_time        │
                 └───────────────────────┘

                 ┌───────────────────────┐
                 │   schema_migrations   │
                 ├───────────────────────┤
                 │ id (PK)               │
                 │ version               │
                 │ applied_at            │
                 │ description           │
                 │ script_name           │
                 │ checksum              │
                 │ execution_time        │
                 │ success               │
                 └───────────────────────┘
```

## 4. Data Flow Diagrams

### 4.1 Plugin Initialization Flow

```
┌─────────────┐         ┌─────────────┐          ┌─────────────┐
│             │         │             │          │             │
│  RVNKTools  │         │  RVNKCore   │          │  Database   │
│             │         │             │          │             │
└──────┬──────┘         └──────┬──────┘          └──────┬──────┘
       │                       │                        │
       │                       │  1. Initialize         │
       │                       │─────────────────────────▶
       │                       │                        │
       │                       │  2. Check Schema       │
       │                       │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
       │                       │                        │
       │                       │  3. Migrate if needed  │
       │                       │─────────────────────────▶
       │                       │                        │
       │                       │  4. Load Core Services │
       │                       │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
       │                       │                        │
       │  5. Initialize        │                        │
       │─────────────────────────▶                      │
       │                       │                        │
       │  6. Register with Core│                        │
       │─────────────────────────▶                      │
       │                       │  7. Register Plugin    │
       │                       │─────────────────────────▶
       │                       │                        │
       │  8. Request API Access│                        │
       │─────────────────────────▶                      │
       │                       │  9. Record Access      │
       │                       │─────────────────────────▶
       │                       │                        │
       │  10. Get API Instance │                        │
       │─────────────────────────▶                      │
       │                       │                        │
       │  11. API Response     │                        │
       │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                        │
       │                       │                        │
       │  12. Load Plugin Data │                        │
       │─────────────────────────▶                      │
       │                       │  13. Data Access       │
       │                       │─────────────────────────▶
       │                       │                        │
       │  14. Data Response    │                        │
       │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│◀ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
       │                       │                        │
```

### 4.2 Data Access Flow

```
┌────────────┐      ┌────────────┐      ┌────────────┐      ┌────────────┐
│            │      │            │      │            │      │            │
│   Plugin   │      │  Core API  │      │  Service   │      │ Repository │
│            │      │            │      │            │      │            │
└──────┬─────┘      └──────┬─────┘      └──────┬─────┘      └──────┬─────┘
       │                   │                   │                   │
       │ 1. API Request    │                   │                   │
       │──────────────────▶│                   │                   │
       │                   │                   │                   │
       │                   │ 2. Service Call   │                   │
       │                   │──────────────────▶│                   │
       │                   │                   │                   │
       │                   │                   │ 3. Repository Call│
       │                   │                   │──────────────────▶│
       │                   │                   │                   │
       │                   │                   │                   │ 4. Database
       │                   │                   │                   │    Operation
       │                   │                   │                   │
       │                   │                   │                   │
       │                   │                   │ 5. Data Response  │
       │                   │                   │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─│
       │                   │                   │                   │
       │                   │ 6. Service Response                   │
       │                   │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─│                   │
       │                   │                   │                   │
       │ 7. API Response   │                   │                   │
       │◀ ─ ─ ─ ─ ─ ─ ─ ─ ─│                   │                   │
       │                   │                   │                   │
```

## 5. Implementation Guidelines

### 5.1 Connection Configuration

Database connection properties should be stored in `application.properties`:

```properties
# Database Configuration
database.type=sqlite
database.sqlite.file=rvnkcore.db
database.sqlite.wal=true
database.sqlite.timeout=5000

# MySQL Configuration (when selected)
database.mysql.host=localhost
database.mysql.port=3306
database.mysql.database=rvnkcore
database.mysql.username=minecraft
database.mysql.password=secure_password
database.mysql.pool.size=10
database.mysql.pool.idle=2
database.mysql.pool.timeout=30000

# Database Behavior
database.table.prefix=rvnk_
database.auto.migrate=true
database.backup.enabled=true
database.backup.interval=86400
```

### 5.2 Migration Patterns

Schema migrations should follow these patterns:

1. **Version Based**: Each migration has a version number (e.g., `V1.0.0__initial_schema.sql`)
2. **Incremental**: Migrations build on previous versions
3. **Forward Only**: No rollback support in production
4. **Validation**: Schema validated on startup
5. **Locking**: Migration process uses database locks

### 5.3 Performance Considerations

1. **Connection Pooling**: HikariCP for connection management
2. **Async Operations**: All database operations should be async
3. **Batch Processing**: Group operations when possible
4. **Caching**: Cache frequently accessed data
5. **Index Strategy**: Create indexes for commonly queried fields
6. **Query Optimization**: Use query plans to optimize complex queries

## 6. Transition Strategy

### 6.1 Phase 1: Shared Development

During this phase, both plugins exist in the same project but with clear separation:

1. Create core packages with clear interfaces
2. Refactor common functionality into core
3. Update existing code to use core APIs
4. Maintain backward compatibility

### 6.2 Phase 2: Split Repositories

Once core components are stable:

1. Create separate Maven projects for core and tools
2. Set up dependency relationship
3. Move code to appropriate projects
4. Create integration tests

### 6.3 Phase 3: Deployment Separation

Final phase with separate deployments:

1. Create separate release artifacts
2. Update documentation for deployment
3. Create migration tools for existing servers
4. Implement version compatibility checks

## 7. Development Timeline

| Phase | Timeline | Key Deliverables |
|-------|----------|------------------|
| Planning | Q3 2025 | Architecture documents, Interface definitions |
| Core Database | Q3 2025 | Schema design, Connection management, Base repositories |
| Core Services | Q4 2025 | Service implementations, API interfaces, Event system |
| RVNKTools Refactor | Q4 2025 | Update tools to use core services |
| Split Repositories | Q1 2026 | Separate Maven projects, Dependency management |
| Testing | Q1 2026 | Integration tests, Performance benchmarks |
| Release | Q2 2026 | First stable release of separated plugins |

## 8. Conclusion

The transition to a RVNKCore-based architecture with a centralized data layer represents a significant architectural improvement. By separating core data services from feature implementations, we create a more maintainable, extensible, and scalable ecosystem that can support multiple plugins while ensuring data consistency and integrity.

The schema design presented here provides the foundation for cross-plugin data sharing while maintaining proper isolation and security. The migration strategy ensures a smooth transition for both developers and users, minimizing disruption while delivering significant benefits.
