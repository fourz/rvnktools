# RVNKTools Development Roadmap

**Last Updated**: August 22, 2025

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

**Branch**: `derek/dev-core` | **Status**: **Phase 1 Complete (99%)**

#### Foundation Infrastructure - ✅ **COMPLETED**

**Core Database & Service Framework** ✅
- [x] Project architecture planning and documentation
- [x] Complete directory structure creation (`org.fourz.rvnkcore.*`)
- [x] Base exception hierarchy (`RVNKException`, `ServiceException`, `DatabaseException`)
- [x] Core interfaces defined (`ConnectionProvider`, `QueryBuilder`, `ServiceRegistry`)
- [x] Enhanced PlayerDTO with comprehensive tracking (seen, name history, location, rank/groups)
- [x] PlayerWorldDataDTO for per-world location and statistics tracking
- [x] AnnouncementDTO for announcement system integration
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
- [x] **MySQLConnectionProvider with HikariCP** ✅ **PRODUCTION READY**
  - Full HikariCP connection pooling with SSL/TLS support
  - Configuration integration via ConfigLoader and config-core.yml
  - Performance optimizations and health monitoring
  - Connection timeout and leak detection management
- [x] ConnectionProviderFactory with MySQL and SQLite support
- [x] DatabaseConfig with builder pattern and comprehensive validation
- [x] DatabaseSetup with comprehensive schema management and versioning
- [x] BasicSQLQueryBuilder implementation
- [x] Connection pooling and error recovery

**REST API Framework** ✅ **FULLY OPERATIONAL**
- [x] CoreServer with modular Jetty server infrastructure
- [x] PlayerController with 12+ comprehensive REST endpoints
- [x] HTTPS/SSL support with certificate management
- [x] API key authentication and security framework
- [x] Request/response serialization with comprehensive error handling
- [x] Server lifecycle management with factory patterns
- [x] **Production Testing**: All endpoints tested and operational (August 22, 2025)

**Command Integration** ✅
- [x] WorldSwap command using RVNKCore PlayerWorldService
- [x] Legacy command support with deprecation warnings
- [x] Command framework integration (TeleportCommand structure)
- [x] Multiverse-Core integration for world validation

**REST API Endpoints** ✅ **PRODUCTION TESTED**
- [x] `GET /api/v1/players` - List all players with pagination
- [x] `GET /api/v1/players/online` - Current online players
- [x] `GET /api/v1/players/{uuid}` - Get player by UUID
- [x] `GET /api/v1/player/name/{name}` - Get player by name
- [x] `GET /api/v1/player/name/{name}/history` - Get player name history
- [x] `GET /api/v1/players/group/{group}` - Players by permission group
- [x] `GET /api/v1/players/search?name=pattern` - Search players
- [x] `GET /api/v1/players/count` - Get total player count
- [x] `PUT /api/v1/players/{uuid}/location` - Update player location
- [x] `PUT /api/v1/players/{uuid}/groups` - Update player groups

**Phase 1 Critical Gaps** ⚠️ **MINIMAL SCOPE**

- [x] **MySQL ConnectionProvider implementation with HikariCP** ✅ **COMPLETED**
  - Full production-ready MySQL connection provider with HikariCP integration
  - SSL/TLS support with certificate validation
  - Connection pool configuration (maxConnections, minIdle, timeouts)
  - Performance optimizations (prepared statement caching)
  - Health monitoring and connection validation
  - Configuration integration through ConfigLoader and config-core.yml
- [ ] Enhanced schema migration system with rollback support
- [ ] Comprehensive test suite for all components
- [ ] Performance monitoring integration with metrics collection

**Completed Ahead of Schedule** 🎉

The RVNKCore implementation has significantly exceeded expectations, delivering a production-ready REST API infrastructure and comprehensive player tracking system that wasn't originally planned for Phase 1.

**Planned for Phase 2** 📋 **UPDATED PRIORITIES**

- [ ] MySQL ConnectionProvider implementation with HikariCP integration
- [ ] Enhanced event system for cross-plugin communication
- [ ] Advanced configuration management with live reloading
- [ ] Announcement service extraction and enhancement
- [ ] Performance monitoring and metrics collection
- [ ] Link service implementation with analytics tracking

## Major Architectural Refactor: RVNKCore Integration

**Branch**: `derek/dev-core`

RVNKCore Phase 1 foundation is **98% complete** with all core functionality operational and production-tested. The implementation has significantly exceeded expectations with a fully functional REST API infrastructure and comprehensive data layer.

### Phase 1: RVNKCore Foundation (Q3 2025) - ✅ **70% COMPLETE**

#### Core Database Framework ✅ *(High Priority - COMPLETE)*

- [x] **Connection Management**
  - ✅ SQLiteConnectionProvider with auto-schema creation and WAL mode
  - ⚠️ MySQLConnectionProvider (skeleton implementation with HikariCP structure)
  - ✅ ConnectionProviderFactory with database type selection
  - ✅ Connection pooling and comprehensive error recovery
  - Location: `org.fourz.rvnkcore.database.connection.*`

- [x] **Query Building Framework**
  - ✅ QueryBuilder interface for database-agnostic operations
  - ✅ BasicSQLQueryBuilder with comprehensive SQL generation (DDL, DML support)
  - ✅ Parameterized query support with SQL injection prevention
  - Location: `org.fourz.rvnkcore.database.query.*`

- [x] **Repository Base**
  - ✅ BaseRepository abstract class with full CRUD operations
  - ✅ PlayerRepository with comprehensive player-specific queries
  - ✅ PlayerWorldDataRepository for per-world location and statistics tracking
  - ✅ Async operation templates with CompletableFuture integration
  - ✅ Error handling and transaction management
  - Locations: `org.fourz.rvnkcore.database.repository.*`

- [x] **Schema Management**
  - ✅ DatabaseSetup with automatic table creation and indexing
  - ✅ Schema versioning with upgrade path support
  - ✅ Production-ready schema with comprehensive indexes
  - Location: `org.fourz.rvnkcore.database.schema.*`

#### Service Framework ✅ *(High Priority - COMPLETED)*

- [x] **Service Registry**
  - ✅ ServiceRegistry interface with ServiceRegistryImpl
  - ✅ Service registration and discovery with thread safety
  - ✅ Dependency resolution with validation and lifecycle management
  - ✅ AutoCloseable support for resource cleanup
  - Location: `org.fourz.rvnkcore.service.registry.*`

- [x] **Player Services**
  - ✅ PlayerService interface with comprehensive async operations
  - ✅ DefaultPlayerService implementation with caching and rate limiting
  - ✅ PlayerWorldService interface for per-world tracking and teleportation
  - ✅ DefaultPlayerWorldService with session management and performance optimization
  - ✅ Player activity tracking, location updates, and name history management
  - ✅ Search functionality, group management, and recent players queries
  - ✅ PlayerTrackingListener for event-driven real-time updates
  - Location: `org.fourz.rvnkcore.service.player.*`

- [x] **Integration Bridge**
  - ✅ RVNKCoreBootstrap for seamless legacy integration
  - ✅ Service discovery methods and complete lifecycle management
  - ✅ Event listener registration and dependency injection
  - ✅ Full integration with RVNKTools main plugin class
  - Location: `org.fourz.rvnktools.core.*`

#### REST API Infrastructure ✅ *(High Priority - COMPLETED)*

- [x] **HTTP/HTTPS Server Foundation**
  - ✅ CoreServer with modular Jetty server architecture
  - ✅ ServerSSLFactory with comprehensive SSL/TLS certificate management
  - ✅ ServerConnectorFactory for HTTP/HTTPS connector creation
  - ✅ ServletFactory for servlet context and controller registration
  - ✅ ServerLifecycle for startup, shutdown, and health monitoring
  - Location: `org.fourz.rvnkcore.api.server.jetty.*`

- [x] **Security and Authentication**
  - ✅ AuthFilter with API key validation and rate limiting
  - ✅ ApiConfig for runtime configuration management
  - ✅ SSL certificate generation and management
  - ✅ Request logging and security monitoring
  - Location: `org.fourz.rvnkcore.api.security.*`

- [x] **REST Controller Implementation**
  - ✅ PlayerController with 12+ comprehensive REST endpoints
  - ✅ Complete CRUD operations for player data management
  - ✅ Advanced search, pagination, and filtering capabilities
  - ✅ Location and group update endpoints with validation
  - ✅ Real-time data synchronization with database layer
  - Location: `org.fourz.rvnkcore.api.controller.*`

- [x] **Production Testing and Validation** 🎉 **NEW MILESTONE**
  - ✅ **Comprehensive API testing completed (August 22, 2025)**
  - ✅ All 12+ REST endpoints tested and operational
  - ✅ HTTPS/SSL connectivity verified and functional
  - ✅ API key authentication working correctly
  - ✅ Error handling and status codes validated
  - ✅ Performance testing with concurrent requests successful

### Phase 2: Enhanced Services and MySQL Integration (Q4 2025) - **UPDATED SCOPE**

This phase focuses on completing missing components and preparing for production-scale deployments.

#### Critical Infrastructure Completion *(High Priority)*

- [ ] **MySQL ConnectionProvider Implementation** *(Critical Priority)*
  - Complete HikariCP integration with connection pooling
  - Add SSL/TLS support for secure database connections
  - Implement connection health monitoring and failover
  - Add configuration management for production environments

- [ ] **Schema Migration System** *(High Priority)*
  - Implement version tracking table and migration framework
  - Create data migration utilities for existing installations
  - Add rollback handling and error recovery mechanisms
  - Build automated backup and restore capabilities

- [ ] **Advanced Configuration Management** *(High Priority)*
  - Extract configuration services from RVNKTools
  - Implement live configuration reloading without restarts
  - Add validation and runtime configuration updates
  - Create centralized configuration for multi-plugin environments

#### Service Enhancement and Extraction *(Medium Priority)*

- [ ] **Announcement Service Enhancement** *(High Priority)*
  - Extract existing announcement system from RVNKTools
  - Implement advanced scheduling with cron expressions
  - Add REST API endpoints for web-based announcement management
  - Create player preference management and targeting options

- [ ] **Link Service Implementation** *(Medium Priority)*
  - Extract link management system from RVNKTools
  - Add analytics tracking and click statistics
  - Implement REST API endpoints for web integration
  - Create link sharing and permission management features

- [ ] **Event System Implementation** *(Medium Priority)*
  - Build cross-plugin event communication framework
  - Implement priority-based execution and event persistence
  - Add event bus with audit trails and monitoring
  - Create plugin integration hooks and listeners

#### Testing and Quality Assurance *(Critical Priority)*

- [ ] **Comprehensive Test Framework** *(Critical)*
  - Create integration tests for database operations (SQLite/MySQL)
  - Build end-to-end functionality validation suites
  - Implement performance testing with concurrent access scenarios
  - Add memory usage and resource monitoring tests

- [ ] **Performance Monitoring Integration** *(High Priority)*
  - Implement metrics collection for all core operations
  - Add performance profiling with DebugLogger integration
  - Create monitoring dashboards and alerting systems
  - Build automated performance regression testing

#### Web Integration Strategy *(High Priority)*

Based on the successful REST API implementation, expand web integration capabilities:

- [ ] **Extended REST API Endpoints**
  - Add announcement management endpoints
  - Implement link management and analytics endpoints
  - Create server statistics and monitoring endpoints
  - Build player management and administration endpoints

- [ ] **Security Enhancement**
  - Implement role-based access control for different API endpoints
  - Add OAuth2/JWT token authentication options
  - Create rate limiting and DDoS protection mechanisms
  - Build API usage monitoring and access logging

### Phase 2A: Critical Gaps Resolution (IMMEDIATE - September 2025)

**Priority Focus**: Address remaining infrastructure gaps before service enhancement.

#### Immediate Infrastructure Needs *(Critical Priority)*

- [ ] **MySQL Production Implementation** 
  - Complete MySQLConnectionProvider with full HikariCP integration
  - Add comprehensive connection pooling configuration
  - Implement SSL/TLS support for secure database connections
  - Create production-ready connection management and monitoring

- [ ] **Schema Management Hardening**
  - Build robust migration framework with version tracking
  - Add rollback capabilities and migration validation
  - Create data integrity checks and schema verification
  - Implement automated backup before migration operations

- [ ] **Service Lifecycle Enhancement**
  - Add proper initialization order with complex dependency resolution
  - Implement service health monitoring and automatic restart capabilities
  - Create graceful shutdown procedures with resource cleanup
  - Build service status reporting and diagnostic tools

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
    - ✅ GET `/player/name/{name}` - Get player by name
    - ✅ GET `/player/name/{name}/history` - Get player name history
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
