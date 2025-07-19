# RVNKCore Development Roadmap

**Last Updated**: July 23, 2025

This document outlines the planned development path for RVNKCore, the centralized data and service layer for the RVNK plugin ecosystem. It includes specific milestones, separation strategy, and integration goals.

## Current Status

RVNKCore is currently in the early development phase as part of the planned separation from RVNKTools. The architectural foundation has been designed and documented, with implementation beginning in Q3 2025.

## Development Phases

### Phase 1: Foundation (Q3 2025)

The initial phase focuses on establishing the core architecture while RVNKCore remains within the RVNKTools codebase.

#### Core Database Framework

- [x] **Architecture Design** - Complete architectural planning
- [ ] **Connection Management** *(High Priority)*
  - Implement ConnectionProvider interface with MySQL and SQLite implementations
  - Add connection pooling with HikariCP
  - Build connection health monitoring
  - Implement transaction management
  - Integrate with LogManager for database operations

- [ ] **Query Building Framework** *(High Priority)*
  - Create QueryBuilder interface
  - Implement dialect-specific implementations
  - Build query parameter management
  - Add query execution utilities
  - Add performance tracking with DebugLogger

- [ ] **Repository Base** *(High Priority)*
  - Implement BaseRepository abstract class
  - Create DTO-based data transfer
  - Add CRUD operation templates
  - Build batch operation support

#### Service Framework

- [ ] **Service Registry** *(High Priority)*
  - Implement ServiceRegistry singleton
  - Add service registration and discovery
  - Create service lifecycle management
  - Build dependency resolution

- [ ] **Logging Framework** *(High Priority)*
  - Implement LogManager with RVNKLogger interface
  - Create DebugLogger for performance monitoring
  - Add log level configuration
  - Build performance logging utilities

- [ ] **Basic Command Support** *(Low Priority)*
  - Implement essential administrative commands
  - Add basic permission checks
  - Support simple tab completion

- [ ] **Event System** *(Medium Priority)*
  - Design event interfaces
  - Implement event dispatcher
  - Add listener registration
  - Create priority-based execution

- [ ] **Configuration Management** *(Medium Priority)*
  - Implement ConfigurationManager
  - Add configuration versioning
  - Build validation framework
  - Create migration utilities

### Phase 2: Service Implementation (Q4 2025)

This phase focuses on implementing the core services that will be provided by RVNKCore.

#### Player Services

- [ ] **Player Registry** *(High Priority)*
  - Implement centralized player tracking
  - Add player metadata storage
  - Build preferences framework
  - Create player events

- [ ] **Permission Management** *(Medium Priority)*
  - Implement permission caching
  - Add LuckPerms integration
  - Build permission inheritance
  - Create permission evaluation

#### Data Services

- [ ] **Announcement Service** *(High Priority)*
  - Extract from RVNKTools announcement system
  - Implement service interface
  - Create database repositories
  - Build scheduling framework

- [ ] **Link Service** *(Medium Priority)*
  - Extract from RVNKTools link system
  - Implement service interface
  - Create database repositories
  - Add tracking and analytics

- [ ] **API Framework** *(High Priority)*
  - Design API interfaces
  - Implement API versioning
  - Create documentation
  - Build example implementations

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
