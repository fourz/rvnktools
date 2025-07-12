# RVNKTools Development Roadmap

**Last Updated**: July 12, 2025

This document outlines the planned features and improvements for the RVNKTools plugin.

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

## Q3 2025 Priorities

### Core Infrastructure Improvements

The next phase of development focuses on improving the foundation of RVNKTools to ensure scalability and maintainability.

#### Feature Set

- [ ] **Logging System Refactoring** *(High Priority)*
  - Create and implement an optionally used DebugLogger for performance-critical sections.  
    - DebugLogger should share the same interface as LogManager, allowing for easy switching between normal and debug logging.
  - Implement LogManager pattern for consistent logging across all classes
    - Establish and implement LogManager as centralized logging configuration.
  - Add configurable log levels
  - Create log rotation and archiving
  - Add performance metrics logging

- [ ] **Database Architecture Enhancement** *(High Priority)*
  - Create QueryBuilder interface for database dialect abstraction
  - Add DTO pattern for clean data transfer between layers
  - Support for MySQL in addition to SQLite
  - Add connection pooling for improved performance

- [ ] **Configuration System Upgrade** *(Medium Priority)*
  - Implement automatic config versioning and migration
  - Add configuration validation
  - Improve error handling for malformed configs
  - Create admin commands for config reload

- [ ] **Command Framework Modernization** *(Medium Priority)*
  - Standardize command structure and execution flow
  - Improve tab completion support
  - Add command aliases support
  - Implement better permission checking and messaging

## Q4 2025 Priorities

### Feature Extensions

Building on the infrastructure improvements, these features will extend the functionality of RVNKTools.

#### Feature Set

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
  - Add server performance monitoring tools
  - Create player management utilities
  - Implement backup and restore functionality
  - Add report generation for server statistics

- [ ] **User Interface Improvements** *(Medium Priority)*
  - Create comprehensive in-game GUI for all features
  - Implement chat-based menus for server navigation
  - Add customizable themes for all interfaces
  - Create actionbar and bossbar announcement options

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
  - Refactor codebase into separate modules
  - Enable feature toggles for all components
  - Create module dependency management
  - Implement dynamic module loading/unloading

- [ ] **Announcement System as Standalone Plugin** *(Medium Priority)*
  - Extract announcement system into a separate plugin
  - Create API endpoints for announcement management
  - Implement cross-plugin communication
  - Develop migration path from integrated to standalone

- [ ] **Multi-Server Support** *(Medium Priority)*
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
