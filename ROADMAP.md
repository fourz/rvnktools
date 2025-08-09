# RVNKTools Development Roadmap

**Last Updated**: August 9, 2025

This document outlines the planned features and improvements for the RVNKTools plugin, with a focus on the RVNKCore architectural refactor.

## Current Status

RVNKTools has established a solid foundation with core functionality in place:

- ✅ Announcement system with scheduling and YAML configuration
- ✅ Hat management system
- ✅ Link creation and management
- ✅ Player join message customization
- ✅ Permission-based access control
- ✅ PlaceholderAPI integration
- ✅ Multiverse integration
- ✅ SQLite database support
- ✅ Centralized `CommandManager` framework for consistent command handling
- ✅ **WorldSwap command integration with per-world location tracking**
- ✅ **Comprehensive player and world-specific data services**

### RVNKCore Implementation Status

**Branch**: `derek/dev-core` | **Status**: **Phase 1 Complete (95%)**

#### Foundation Infrastructure - ✅ **COMPLETED**

**Core Database & Service Framework** ✅
- [x] Project architecture planning and documentation
- [x] Complete directory structure creation (`org.fourz.rvnkcore.*`)
- [x] Base exception hierarchy (`RVNKException`, `ServiceException`, `DatabaseException`)
- [x] Core interfaces defined (`ConnectionProvider`, `QueryBuilder`, `ServiceRegistry`)
- [x] Enhanced PlayerDTO with comprehensive tracking (seen, name history, location, rank/groups)
- [x] PlayerWorldDataDTO for per-world location and statistics tracking
- [x] Main RVNKCore class with lifecycle management
- [x] Package structure with api/, database/, service/, util/ directories
- [x] Foundation documentation and implementation guidelines

**Service Layer Implementation** ✅
- [x] PlayerService interface with async operations
- [x] DefaultPlayerService implementation with full business logic
- [x] PlayerWorldService interface for per-world tracking
- [x] DefaultPlayerWorldService with rate limiting and session management
- [x] ServiceRegistry implementation for dependency injection
- [x] RVNKCoreBootstrap for legacy integration
- [x] PlayerTrackingListener for event-driven updates

**Database Layer Implementation** ✅
- [x] BaseRepository abstract class with CRUD operations
- [x] PlayerRepository implementation with player-specific queries
- [x] PlayerWorldDataRepository for world-specific data operations
- [x] SQLiteConnectionProvider with auto-schema creation
- [x] BasicSQLQueryBuilder implementation
- [x] Connection pooling and error recovery

**REST API Framework** ✅
- [x] PlayerController with full CRUD endpoints
- [x] Jetty server infrastructure with SSL/TLS support
- [x] Authentication and authorization framework
- [x] Request/response serialization with comprehensive error handling
- [x] Modular server architecture for future extensibility

**Command Integration** ✅
- [x] WorldSwap command using RVNKCore PlayerWorldService
- [x] Legacy command support with deprecation warnings
- [x] Command framework integration (TeleportCommand structure)
- [x] Multiverse-Core integration for world validation

**Phase 1 Critical Gaps** ⚠️

- [ ] MySQL ConnectionProvider implementation with HikariCP
- [ ] Schema migration system with version tracking
- [ ] Comprehensive test suite for all components
- [ ] PlayerWorld REST API endpoints for web integration

**Planned for Phase 2** 📋

- [ ] Event system for cross-plugin communication
- [ ] Configuration management with versioning
- [ ] Performance monitoring and logging integration
- [ ] Cross-plugin data sharing capabilities

## Major Architectural Refactor: RVNKCore Integration

**Branch**: `derek/dev-core`

RVNKCore Phase 1 foundation is **95% complete** with all core functionality operational. The focus now shifts to gap resolution and Phase 2 implementation.

### Phase 1: RVNKCore Foundation (Q3 2025) - ✅ **95% COMPLETE**

#### Core Database Framework ✅ *(High Priority - COMPLETE)*

- [x] **Connection Management**
  - ✅ SQLite ConnectionProvider with auto-schema creation
  - ⚠️ MySQL ConnectionProvider (skeleton implementation only)
  - ✅ Connection pooling and error recovery
  - Location: `org.fourz.rvnkcore.database.connection.*`

- [x] **Query Building Framework**
  - ✅ QueryBuilder interface for database-agnostic operations
  - ✅ BasicSQLQueryBuilder with comprehensive SQL generation
  - ✅ DDL support for schema creation
  - Location: `org.fourz.rvnkcore.database.query.*`
  - ✅ Added DDL support for schema creation
  - Location: `org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder`

- [x] **Repository Base**
  - ✅ Implemented BaseRepository abstract class with full CRUD
  - ✅ Created DTO-based data transfer with PlayerDTO
  - ✅ Added async operation templates with CompletableFuture
  - ✅ PlayerRepository implementation with player-specific queries
  - Locations: `org.fourz.rvnkcore.database.repository.*`

#### Service Framework ✅ *(High Priority - COMPLETED)*

- [x] **Service Registry**
  - ✅ Implemented ServiceRegistry interface and ServiceRegistryImpl
  - ✅ Added service registration and discovery with thread safety
  - ✅ Built dependency resolution with validation
  - ✅ Integrated lifecycle management and AutoCloseable support
  - Location: `org.fourz.rvnkcore.service.registry.*`

- [x] **Player Services**
  - ✅ PlayerService interface with comprehensive async operations
  - ✅ DefaultPlayerService implementation with caching and rate limiting
  - ✅ PlayerWorldService interface for per-world tracking
  - ✅ DefaultPlayerWorldService with session management and performance optimization
  - ✅ Player activity tracking, location updates, name history
  - ✅ Search functionality and recent players queries
  - Location: `org.fourz.rvnkcore.service.player.*`

- [x] **Integration Bridge**
  - ✅ RVNKCoreBootstrap for legacy integration
  - ✅ Service discovery methods and lifecycle management
  - ✅ PlayerTrackingListener for event-driven updates
  - ✅ Complete integration with RVNKTools main class
  - Location: `org.fourz.rvnktools.core.*`

- [x] **Supporting Components**
  - ✅ Exception framework with ServiceException
  - ✅ PlayerDTO and PlayerWorldDataDTO models with builder pattern
  - ✅ Working player and world tracking example
  - Status: All foundation components complete and operational

### Phase 2: Service Implementation (Q4 2025)

This phase focuses on implementing the core services that will be provided by RVNKCore.

#### Data Services *(High Priority)*

- [ ] **Announcement Service**
  - Extract from RVNKTools announcement system
  - Implement service interface
  - Build scheduling framework

- [ ] **Link Service** *(Medium Priority)*
  - Extract from RVNKTools link system
  - Add tracking and analytics
  - Implement centralized player tracking
  - Add player metadata storage
  - Create player events

- [ ] **Permission Management** *(Medium Priority)*
  - Implement permission caching
  - Add LuckPerms integration
  - Create permission evaluation

#### Data Services *(High Priority)*

- [ ] **Announcement Service**
  - Extract from RVNKTools announcement system
  - Implement service interface
  - Build scheduling framework

- [ ] **Link Service** *(Medium Priority)*
  - Extract from RVNKTools link system
  - Add tracking and analytics

#### Web Integration & REST API Framework *(High Priority)*

- [ ] **Plugin Categorization for Web Integration**
  - **Web-Enabled Plugins** (require full REST API):
    - RVNKShops: Product catalog, pricing, transactions
    - RVNKTools: Announcement management, server statistics
    - RVNKLore: Item galleries, player collections
  - **Internal-Only Plugins** (Java-internal only):
    - RVNKQuests: Quest progress, internal game mechanics
    - RVNKWorlds: World management, internal server operations

- [x] **RVNKCore REST API Infrastructure Migration** ✅ **COMPLETED**
  - **Core API Package Structure** (`org.fourz.rvnkcore.api.*`)
    - ✅ Migrated Jetty server infrastructure from RVNKTools
    - ✅ Created `org.fourz.rvnkcore.api.server.jetty` package
    - ✅ Extracted `ApiConfig`, `AuthFilter`, security components
    - ✅ Implemented modular REST endpoint registration system
  - **HTTP/HTTPS Server Foundation**
    - ✅ Ported Jetty server configuration and SSL/TLS support
    - ✅ Implemented centralized authentication and authorization
    - ✅ Created request/response serialization framework
    - ✅ Added comprehensive error handling and status codes
  - **Player REST API Endpoints** (`/api/v1/players`)
    - ✅ GET `/players` - List all players with pagination
    - ✅ GET `/players/online` - Current online players
    - ✅ GET `/players/recent?hours=X` - Recent players
    - ✅ GET `/players/{uuid}` - Get player by UUID
    - ✅ GET `/players/name/{name}` - Get player by name
    - ✅ GET `/players/group/{group}` - Players by permission group
    - ✅ GET `/players/search?name=pattern` - Search players
    - ✅ PUT `/players/{uuid}/location` - Update player location
    - ✅ PUT `/players/{uuid}/groups` - Update player groups
  - **Service Integration Layer**
    - ✅ Connected REST endpoints to PlayerService operations
    - ✅ Implemented async request handling with CompletableFuture
    - ✅ Added comprehensive logging and performance monitoring
    - ✅ Created consistent response format across all endpoints

#### Modular Server Architecture ✅ *(High Priority - COMPLETED)*

- [x] **RVNKCoreServer Refactoring** ✅ **COMPLETED**
  - **Specialized Factory Components**
    - ✅ `RVNKCoreSSLFactory` - SSL/TLS configuration and keystore management
    - ✅ `RVNKCoreConnectorFactory` - HTTP/HTTPS connector creation and management
    - ✅ `RVNKCoreServletFactory` - Servlet context and controller registration
    - ✅ `RVNKCoreServerLifecycle` - Server startup, shutdown, and monitoring
  - **Future Framework Abstractions**
    - ✅ `RVNKCoreMonitoringFactory` - Performance monitoring and health checks (placeholder)
    - ✅ `RVNKCoreSecurityFactory` - Advanced security features (placeholder)
    - ✅ `RVNKCoreCacheFactory` - Response and data caching (placeholder)
  - **Composition-Based Architecture**
    - ✅ Separated concerns using factory pattern
    - ✅ Improved maintainability and testability
    - ✅ Enhanced error handling and logging
    - ✅ Created foundation for future extensions

- [ ] **REST API Framework**
  - Design RESTful endpoints for data access
  - Implement authentication and authorization
  - Create API versioning strategy
  - Build comprehensive CRUD operations for web-enabled plugins

- [ ] **Web Integration Infrastructure**
  - Develop consistent data serialization
  - Implement caching strategies for web requests
  - Create rate limiting and security measures
  - Build API documentation and examples

- [ ] **API Framework** *(High Priority)*
  - Design API interfaces
  - Build example implementations

### Phase 3: Separation & Legacy Maintenance (Q1-Q2 2026)

- [ ] **Plugin Separation** *(Critical Priority)*
  - Create separate Maven project for RVNKCore
  - Build compatibility layer
  - Test with existing plugins

- [ ] **Legacy Feature Maintenance** *(Medium Priority)*
  - Continue support for existing RVNKTools features
  - Implement migration tools
  - Maintain backward compatibility

## Q4 2025 Priorities (Post-RVNKCore Foundation)

### Enhanced Features

- [ ] **Enhanced Announcement System** *(High Priority)*
  - Add support for scheduled announcements with cron expressions
  - Implement announcement categories and groups
  - Create GUI-based announcement management
  - Add support for random announcement selection
  - Implement announcement metrics (views, clicks)

- [ ] **Expanded Integration Support** *(Medium Priority)*
  - Add integration with VotingPlugin
  - Enhance PlaceholderAPI support with more dynamic placeholders
  - WorldGuard integration for region-specific features
  - Economy integration for premium features

- [ ] **Admin Tools Expansion** *(Medium Priority)*
  - Add server performance reporting tools
  - Utilize the DebugLogger and SparkLogger for performance-critical debugging
  - Create player management utilities
  - Add report generation for server statistics

- [ ] **User Interface Improvements** *(Medium Priority)*
  - Add GUI for hat management

## Q1-Q2 2026 Priorities

### API Development and Plugin Architecture

This phase focuses on transforming RVNKTools into a platform that other plugins can build upon.

#### Feature Set

- [ ] **API Framework Development** *(High Priority)*
  - Create comprehensive API for third-party plugin integration
  - Implement event system for plugin interactions
  - Add developer documentation and examples
  - Create plugin hook system for extensibility

- [ ] **Modular Architecture** *(High Priority)*
  - Refactor RVNK plugin ecosystem/codebase to extract database and api components into the core module, centralizing shared functionality for plugin data with yaml fallback for each plugin.
  - Enable feature toggles for all components
  - Create module dependency management
  - Implement dynamic module loading/unloading

- [ ] **Announcement System as Standalone Plugin** *(Medium Priority)*
  - Extract announcement system into a separate plugin
  - Create API endpoints for announcement management
  - Implement cross-plugin communication
  - Develop migration path from integrated to standalone

- [ ] **Multi-Server Support** *(Low Priority)*
  - Add BungeeCord/Velocity integration
  - Implement cross-server announcement synchronization
  - Create shared database for multi-server setups
  - Add server-specific configuration options

## Long-Term Vision (2026+)

- **Web Interface Development**
  - Create browser-based admin dashboard
  - Add real-time server monitoring
  - Implement user management portal
  - Develop API endpoints for web interaction

- **Advanced Data Analytics**
  - Implement metrics collection for all features
  - Create visualization tools for server data
  - Add predictive analytics for server management
  - Develop automated reporting system

- **Community Features**
  - Add player feedback and suggestion system
  - Create community engagement tools
  - Implement player recognition and rewards
  - Develop server event management system

## Implementation Notes

### Development Approach

1. Focus on core infrastructure improvements first
2. Ensure backward compatibility with existing configurations
3. Build comprehensive tests for core functionality
4. Create migration paths for all major changes
5. Prioritize user experience and simplicity

### API Development Strategy

- Design API with backward compatibility in mind
- Document all API endpoints and events
- Create example plugins for reference
- Establish versioning strategy for API changes

## Success Metrics

- **Infrastructure**: Improved stability and performance metrics
- **Features**: User adoption and feedback for new capabilities
- **API**: Third-party plugin integration and developer feedback
- **Modularization**: Successful separation of components with minimal user impact

## Revision History

| Date | Version | Notes |
|------|---------|-------|
| July 12, 2025 | 1.0 | Initial roadmap draft |
