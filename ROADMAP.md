# RVNKTools Development Roadmap

**Last Updated**: July 19, 2025

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
- ✅ **New**: Centralized `CommandManager` framework for consistent command handling.

## Major Architectural Refactor: RVNKCore Integration

**Branch**: `derek/dev-core`

The primary focus for Q3-Q4 2025 is the extraction of core functionality into RVNKCore, a centralized data and service layer for the RVNK plugin ecosystem.

### Phase 1: RVNKCore Foundation (Q3 2025) - **CURRENT PRIORITY**

#### Core Database Framework *(High Priority)*

- [ ] **Connection Management**
  - Implement ConnectionProvider interface with MySQL and SQLite implementations
  - Add connection pooling with HikariCP
  - Integrate with LogManager for database operations

- [ ] **Query Building Framework**
  - Create QueryBuilder interface for database dialect independence
  - Implement dialect-specific implementations
  - Add performance tracking with DebugLogger

- [ ] **Repository Base**
  - Implement BaseRepository abstract class
  - Create DTO-based data transfer
  - Add CRUD operation templates

#### Service Framework *(High Priority)*

- [ ] **Service Registry**
  - Implement ServiceRegistry singleton
  - Add service registration and discovery
  - Build dependency resolution

- [ ] **Logging Framework**
  - Implement LogManager with RVNKLogger interface
  - Create DebugLogger for performance monitoring
  - Add log level configuration

- [ ] **Basic Command Support** *(Low Priority)*
  - Implement essential administrative commands
  - Add basic permission checks
  - Support simple tab completion

- [ ] **Event System** *(Medium Priority)*
  - Design event interfaces
  - Implement event dispatcher
  - Create priority-based execution

- [ ] **Configuration Management** *(Medium Priority)*
  - Implement ConfigurationManager
  - Add configuration versioning
  - Build validation framework

### Phase 2: Service Implementation (Q4 2025)

This phase focuses on implementing the core services that will be provided by RVNKCore.

#### Player Services *(High Priority)*

- [ ] **Player Registry**
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
