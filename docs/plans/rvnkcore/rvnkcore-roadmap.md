# RVNKCore Development Roadmap

**Last Updated**: July 23, 2025

This document outlines the planned development path for RVNKCore, the centralized data and service layer for the RVNK plugin ecosystem. It includes specific milestones, separation strategy, and integration goals.

## Current Status

**UPDATE - August 2, 2025**: RVNKCore Phase 1 implementation has significantly exceeded expectations. Core infrastructure is **90% complete** and operational, with major components ready for testing. The project has progressed ahead of schedule with working database layer, service framework, and bonus REST API infrastructure.

## Development Phases

### Phase 1: Foundation (Q3 2025) - ✅ **90% COMPLETE**

The initial phase focuses on establishing the core architecture while RVNKCore remains within the RVNKTools codebase.

#### Core Database Framework - ✅ **MOSTLY COMPLETE**

- [x] **Architecture Design** - Complete architectural planning
- [x] **Connection Management** *(High Priority)* - **✅ SQLite Complete, ❌ MySQL Pending**
  - [x] Implement ConnectionProvider interface ✅
  - [x] SQLite implementation with auto-schema creation ✅
  - [ ] MySQL implementation with HikariCP ❌ (Critical Gap)
  - [ ] Build connection health monitoring ❌
  - [ ] Implement transaction management ❌
  - [x] Integrate with LogManager for database operations ✅

- [x] **Query Building Framework** *(High Priority)* - **✅ COMPLETE**
  - [x] Create QueryBuilder interface ✅
  - [x] Implement BasicSQLQueryBuilder with dialect support ✅
  - [x] Build query parameter management ✅
  - [x] Add query execution utilities ✅
  - [ ] Add performance tracking with DebugLogger ❌

- [x] **Repository Base** *(High Priority)* - **✅ COMPLETE**
  - [x] Implement BaseRepository abstract class ✅
  - [x] Create DTO-based data transfer ✅
  - [x] Add CRUD operation templates ✅
  - [x] Build PlayerRepository with player-specific queries ✅
  - [ ] Build batch operation support ❌

#### Service Framework - ✅ **MOSTLY COMPLETE**

- [x] **Service Registry** *(High Priority)* - **✅ COMPLETE**
  - [x] Implement ServiceRegistry interface ✅
  - [x] DefaultServiceRegistry with dependency injection ✅
  - [x] Add service registration and discovery ✅
  - [x] Create service lifecycle management ✅ (Basic)
  - [x] Build dependency resolution ✅
  - [x] RVNKCoreBootstrap integration bridge ✅

- [x] **Player Services** *(High Priority)* - **✅ COMPLETE**
  - [x] PlayerService interface with comprehensive async operations ✅
  - [x] DefaultPlayerService with full business logic ✅
  - [x] PlayerDTO with comprehensive tracking ✅
  - [x] PlayerTrackingListener for event-driven updates ✅

- [x] **Logging Framework** *(High Priority)* - **✅ COMPLETE**
  - [x] Integrated with existing RVNKTools LogManager ✅
  - [ ] Create DebugLogger for performance monitoring ❌
  - [x] Add log level configuration ✅
  - [ ] Build performance logging utilities ❌

- [ ] **Basic Command Support** *(Low Priority)* - **❌ NOT STARTED**
  - Implement essential administrative commands
  - Add basic permission checks
  - Support simple tab completion

- [ ] **Event System** *(Medium Priority)* - **🔄 PARTIAL**
  - [x] Design event interfaces ✅ (Basic structure)
  - [ ] Implement event dispatcher ❌
  - [ ] Add listener registration ❌
  - [ ] Create priority-based execution ❌

- [ ] **Configuration Management** *(Medium Priority)* - **🔄 PARTIAL**
  - [x] Basic ConfigurationService structure ✅
  - [ ] Add configuration versioning ❌
  - [ ] Build validation framework ❌
  - [ ] Create migration utilities ❌

#### ✅ **BONUS: REST API Infrastructure** *(Ahead of Schedule)*

- [x] **REST API Server Framework** - **✅ COMPLETE**
  - [x] CoreServer with Jetty integration ✅
  - [x] PlayerController with comprehensive endpoints ✅
  - [x] Authentication/Security framework ✅
  - [x] ApiConfig configuration management ✅

### Phase 1A: Critical Gaps Resolution (IMMEDIATE - August 2025)

**Status**: Phase 1 foundation is 90% complete but has critical gaps that must be resolved before Phase 2.

#### Critical Database Gaps *(High Priority)*
- [ ] **MySQL ConnectionProvider Implementation** 
  - Add HikariCP dependency and configuration
  - Implement connection pooling and health monitoring
  - Add SSL/TLS support for secure connections
  
- [ ] **Schema Migration System**
  - Version tracking table and migration framework  
  - Rollback capabilities and validation
  - Data migration utilities for existing installations

- [ ] **Transaction Management**
  - Cross-repository transaction support
  - Rollback handling and error recovery

#### Critical Service Gaps *(High Priority)*
- [ ] **Service Lifecycle Hardening**
  - Proper initialization order with dependency resolution
  - Graceful shutdown and restart procedures
  - Service health monitoring and status reporting

- [ ] **Event System Completion**
  - Event bus implementation with priority handling
  - Cross-plugin event propagation
  - Event persistence for audit trails

#### Testing Infrastructure *(Critical)*
- [ ] **Integration Test Framework**
  - Database operation testing with both SQLite/MySQL
  - Service discovery and lifecycle testing
  - End-to-end functionality validation

- [ ] **Performance Testing**
  - Concurrent access and load testing
  - Database performance benchmarking
  - Memory usage and resource monitoring

### Phase 2: Service Implementation (Q4 2025) - **UPDATED TIMELINE**

This phase focuses on completing business services and advanced features.

#### Player Services - ✅ **COMPLETE**

- [x] **Player Registry** *(High Priority)* - **✅ COMPLETE**
  - [x] Implement centralized player tracking ✅
  - [x] Add player metadata storage ✅
  - [x] Build comprehensive PlayerDTO with activity tracking ✅
  - [x] Create player events and tracking listeners ✅

- [ ] **Permission Management** *(Medium Priority)* - **❌ NOT STARTED**
  - Implement permission caching
  - Add LuckPerms integration
  - Build permission inheritance
  - Create permission evaluation

#### Data Services - 🔄 **PARTIAL**

- [ ] **Announcement Service** *(High Priority)* - **🔄 PARTIAL**
  - [x] Basic AnnouncementService interface exists ✅
  - [ ] Extract from RVNKTools announcement system ❌
  - [ ] Implement complete service interface ❌
  - [ ] Create database repositories ❌
  - [ ] Build scheduling framework ❌

- [ ] **Link Service** *(Medium Priority)* - **❌ NOT STARTED**
  - Extract from RVNKTools link system
  - Implement service interface
  - Create database repositories
  - Add tracking and analytics

- [x] **API Framework** *(High Priority)* - **✅ COMPLETE (Bonus)**
  - [x] Design API interfaces ✅
  - [x] Implement REST API server with Jetty ✅
  - [x] Create PlayerController with full endpoints ✅
  - [x] Build authentication and security ✅

### Phase 3: Separation (Q1-Q2 2026)

This phase focuses on separating RVNKCore into its own plugin while maintaining full compatibility with existing plugins.

#### Plugin Separation

- [ ] **Project Structure** *(High Priority)*
  - Create separate Maven project
  - Configure build process
  - Set up CI/CD pipeline
  - Establish dependency management

- [ ] **Plugin Implementation** *(High Priority)*
  - Create RVNKCore main class
  - Implement plugin lifecycle
  - Add service initialization
  - Build plugin hooks

- [ ] **Backward Compatibility** *(Critical Priority)*
  - Create compatibility layer
  - Implement API shims
  - Build data migration tools
  - Test with existing plugins

#### Integration Points

- [ ] **REST API** *(Medium Priority)*
  - Implement embedded HTTP server
  - Create authentication
  - Build endpoint framework
  - Add documentation

- [ ] **Cross-Server Support** *(Low Priority)*
  - Add BungeeCord/Velocity support
  - Implement data synchronization
  - Create messaging framework
  - Build cluster management

### Phase 4: Ecosystem Growth (Q3-Q4 2026)

This phase focuses on expanding the RVNKCore ecosystem with additional plugins and integrations.

- [ ] **RVNKLore Integration** *(Medium Priority)*
  - Create data sharing framework
  - Implement lore repositories
  - Build integration points
  - Add cross-plugin functionality

- [ ] **RVNKQuests Development** *(Medium Priority)*
  - Implement quest framework
  - Create objective system
  - Build reward management
  - Add progress tracking

- [ ] **RVNKWorlds Integration** *(Low Priority)*
  - Implement world data management
  - Create world repository
  - Build permission integration
  - Add cross-world functionality

## Separation Timeline and Strategy

### When to Separate

The recommended timeline for separating RVNKCore into its own plugin is at the beginning of **Q1 2026**, after completing Phase 2 (Service Implementation). This timing is critical for several reasons:

1. **Core Services Maturity**: By this point, all critical services will be implemented and tested within the RVNKTools codebase
2. **API Stability**: The API will have stabilized based on real-world usage in RVNKTools
3. **Database Robustness**: The database layer will have been thoroughly tested with production data
4. **Feature Completion**: All essential features for supporting RVNKTools will be implemented
5. **Migration Path**: A clear migration path will have been established and documented

Earlier separation risks creating an unstable foundation, while later separation increases the complexity of migration.

### Separation Steps

1. **Preparation (Late Q4 2025)**
   - Finalize all interfaces and contracts
   - Complete comprehensive test suite
   - Document all APIs and services
   - Create migration utilities
   - Establish version compatibility strategy

2. **Initial Separation (Early Q1 2026)**
   - Create new Maven project for RVNKCore
   - Move core code from RVNKTools to RVNKCore
   - Implement plugin dependency in RVNKTools
   - Create shim layer for backward compatibility

3. **RVNKTools Adaptation (Mid Q1 2026)**
   - Update RVNKTools to use RVNKCore API
   - Migrate data to new schema if needed
   - Implement feature parity with previous version
   - Test all functionality extensively

4. **Release Strategy (Late Q1 2026)**
   - Beta release for testing
   - Documentation for server administrators
   - Migration guide for existing servers
   - Coordinated release of both plugins

5. **Post-Separation (Q2 2026)**
   - Monitor for issues
   - Address compatibility edge cases
   - Optimize performance
   - Add additional features

## Implementation Guidelines

### Database Migration

A critical aspect of the separation is the database migration strategy:

1. **Schema Versioning**: Implement version tracking for all schemas
2. **Data Migration**: Create utilities for migrating data from RVNKTools to RVNKCore
3. **Fallback Support**: Maintain ability to read legacy data formats
4. **Backward Compatibility**: Ensure older versions of RVNKTools can still function (read-only) with newer RVNKCore versions

### API Versioning

To ensure long-term stability:

1. **Semantic Versioning**: Follow semantic versioning for all APIs
2. **Deprecation Policy**: Mark methods as deprecated for at least one minor version before removal
3. **Compatibility Layers**: Implement compatibility layers for major changes
4. **Version Detection**: Detect and adapt to different client versions

## Success Metrics

The success of the separation will be measured by:

1. **Zero Data Loss**: All existing data is successfully migrated
2. **Feature Parity**: All features continue to work post-separation
3. **Performance Improvement**: Performance metrics should show improvement
4. **Code Quality**: Improved maintainability scores and reduced complexity
5. **Developer Adoption**: Other plugins begin to adopt the RVNKCore API

## Risk Assessment

### Identified Risks

1. **Data Migration Complexity**: Migration of existing data may be complex
   - **Mitigation**: Extensive testing with production-like data before release

2. **API Breaking Changes**: Changes may break existing code
   - **Mitigation**: Comprehensive compatibility layer and clear deprecation policy

3. **Performance Regression**: Additional abstraction may impact performance
   - **Mitigation**: Performance benchmarking and optimization

4. **Deployment Complexity**: Multiple plugins increase deployment complexity
   - **Mitigation**: Clear documentation and possibly automated deployment tools

5. **Version Compatibility**: Ensuring compatibility between different versions
   - **Mitigation**: Robust versioning strategy and compatibility testing

## Conclusion

The separation of RVNKCore from RVNKTools represents a significant architectural improvement that will provide long-term benefits for the entire RVNK plugin ecosystem. By following this roadmap and adhering to the outlined strategies, we can ensure a smooth transition while setting the foundation for future growth and innovation.

## Revision History

| Date | Version | Notes |
|------|---------|-------|
| July 23, 2025 | 1.0 | Initial roadmap draft |
