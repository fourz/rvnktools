# RVNKCore System Outline Requirements

**Document Version**: 1.0  
**Last Updated**: August 1, 2025  
**Status**: Draft - Subject to Revision

## Purpose

This document outlines the high-level system architecture and requirements for RVNKCore, a centralized data and service layer for the RVNK plugin ecosystem. RVNKCore serves as the foundational infrastructure that enables shared data access, service discovery, and cross-plugin communication.

## System Overview

RVNKCore transforms the RVNK plugin ecosystem from independent, isolated plugins to a cohesive, interconnected system built on shared infrastructure and standardized APIs.

### Current State (Pre-RVNKCore)
```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  RVNKTools  │  │  RVNKLore   │  │ RVNKQuests  │
│             │  │             │  │             │
│ Own DB      │  │ Own DB      │  │ Own DB      │
│ Own Config  │  │ Own Config  │  │ Own Config  │
│ Own Services│  │ Own Services│  │ Own Services│
└─────────────┘  └─────────────┘  └─────────────┘
```

### Target State (With RVNKCore)
```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  RVNKTools  │  │  RVNKLore   │  │ RVNKQuests  │
│             │  │             │  │             │
│ Business    │  │ Business    │  │ Business    │
│ Logic Only  │  │ Logic Only  │  │ Logic Only  │
└─────┬───────┘  └─────┬───────┘  └─────┬───────┘
      │                │                │
      └────────────────┼────────────────┘
                       │
           ┌───────────▼───────────┐
           │      RVNKCore         │
           │                       │
           │ • Shared Database     │
           │ • Service Registry    │
           │ • API Framework       │
           │ • Event System        │
           │ • Configuration Mgmt  │
           └───────────────────────┘
```

## Core Architectural Principles

### 1. Centralized Data Layer
- **Single Source of Truth**: All player data, configurations, and shared state managed centrally
- **Database Abstraction**: Support for both SQLite (development) and MySQL (production)
- **Async Operations**: All database operations use CompletableFuture to prevent main thread blocking
- **Connection Pooling**: Efficient resource management with HikariCP

### 2. Service-Oriented Architecture
- **Service Registry**: Central discovery mechanism for all services
- **Interface-Based Design**: Clean contracts between services and consumers
- **Dependency Injection**: Automatic resolution of service dependencies
- **Lifecycle Management**: Proper initialization and cleanup of all services

### 3. Event-Driven Communication
- **Cross-Plugin Events**: Standardized event system for plugin interactions
- **Real-Time Updates**: Immediate propagation of state changes across plugins
- **Priority-Based Execution**: Ordered event processing for critical operations
- **Event Persistence**: Optional storage of events for audit and replay capabilities

### 4. API-First Development
- **RESTful APIs**: HTTP-based external access for web integrations
- **Versioned Interfaces**: Backward compatibility through API versioning
- **Authentication/Authorization**: Secure access control for external integrations
- **Documentation-Driven**: Comprehensive API documentation with examples

## System Components

### Core Infrastructure

#### 1. Database Layer
- **ConnectionProvider**: Abstracted database connection management
- **QueryBuilder**: Database-agnostic query construction
- **Repository Pattern**: Clean data access layer
- **Schema Management**: Automatic table creation and migration

#### 2. Service Framework
- **ServiceRegistry**: Central service discovery and dependency injection
- **Service Interfaces**: Standardized contracts for all business services
- **Service Implementations**: Concrete implementations with proper lifecycle
- **Service Events**: Cross-service communication and state synchronization

#### 3. Configuration System
- **Centralized Config**: Shared configuration storage and management
- **Type-Safe Access**: Strongly-typed configuration interfaces
- **Hot Reloading**: Runtime configuration updates without restart
- **Validation**: Comprehensive configuration validation and error reporting

### Business Services

#### 1. Player Management
- **Player Registry**: Centralized player tracking and metadata
- **Session Management**: Login/logout tracking and session state
- **Permission Integration**: LuckPerms integration for role-based access
- **Activity Tracking**: Comprehensive player activity and statistics

#### 2. Data Services
- **Announcement Service**: Centralized announcement management and scheduling
- **Link Service**: Shared link creation and management system
- **Preference Service**: Player preferences and customization settings
- **Statistics Service**: Cross-plugin metrics and analytics

#### 3. Integration Services
- **Economy Integration**: Vault integration for cross-plugin economy
- **Permission Bridge**: Unified permission checking across plugins
- **PlaceholderAPI Bridge**: Centralized placeholder management
- **World Integration**: Multiverse and world management support

### External APIs

#### 1. REST API Framework
- **HTTP Server**: Embedded Jetty server for REST endpoints
- **Authentication**: API key and role-based authentication
- **Rate Limiting**: Request throttling and abuse prevention
- **Error Handling**: Standardized error responses and logging

#### 2. Plugin Categories for Web Integration

**Web-Enabled Plugins** (Full REST API):
- **RVNKShops**: Product catalogs, pricing, transaction management
- **RVNKTools**: Announcement management, server administration
- **RVNKLore**: Item galleries, community content management

**Internal-Only Plugins** (Java API Only):
- **RVNKQuests**: Quest progress tracking, game mechanics
- **RVNKWorlds**: World management, server operations

## Development Phases

### Phase 1: Foundation (Q3 2025) - CURRENT
- Core database framework implementation
- Service registry and dependency injection
- Basic player tracking and management
- SQLite connection provider and query builder
- REST API infrastructure migration from RVNKTools

### Phase 2: Service Implementation (Q4 2025)
- Complete player services with tracking and permissions
- Announcement and link service extraction from RVNKTools
- Configuration management system
- Event system for cross-plugin communication
- Full REST API endpoints for web-enabled plugins

### Phase 3: Separation (Q1-Q2 2026)
- RVNKCore becomes standalone plugin
- Migration utilities and backward compatibility
- Complete plugin ecosystem testing
- Production deployment and monitoring

### Phase 4: Ecosystem Growth (Q3-Q4 2026)
- Additional plugin integrations (RVNKLore, RVNKQuests)
- Advanced features (cross-server support, clustering)
- Performance optimization and monitoring
- Community developer tools and documentation

## Success Criteria

### Technical Requirements
- **Zero Data Loss**: Complete data migration without corruption
- **Performance**: Database operations complete in <100ms average
- **Reliability**: 99.9% uptime for core services
- **Compatibility**: Seamless integration with existing plugins

### Functional Requirements
- **Feature Parity**: All existing functionality preserved post-migration
- **Developer Experience**: Simplified plugin development with clear APIs
- **Administration**: Improved server management with centralized tools
- **Extensibility**: Easy addition of new plugins to ecosystem

### Non-Functional Requirements
- **Security**: Secure API access and data protection
- **Scalability**: Support for servers with 500+ concurrent players
- **Maintainability**: Clear code structure and comprehensive documentation
- **Monitoring**: Built-in performance monitoring and health checks

## Risk Assessment

### High-Risk Areas
- **Data Migration**: Complex data migration between plugin databases
- **API Stability**: Maintaining backward compatibility during evolution
- **Performance Impact**: Ensuring centralized services don't create bottlenecks
- **Plugin Dependencies**: Managing complex inter-plugin dependencies

### Mitigation Strategies
- **Gradual Migration**: Phased approach with extensive testing
- **Version Management**: Strict API versioning and compatibility layers
- **Performance Monitoring**: Built-in metrics and alerting
- **Fallback Mechanisms**: Graceful degradation when services unavailable

## Integration Strategy

### Backward Compatibility
- **Legacy Support**: Maintain support for existing plugin configurations
- **Migration Tools**: Automated data and configuration migration utilities
- **Compatibility Layer**: Bridge interfaces for existing plugin APIs
- **Deprecation Policy**: Clear timeline for legacy feature retirement

### Plugin Ecosystem Integration
- **Service Discovery**: Automatic registration and discovery of plugin services
- **Event Integration**: Standardized events for cross-plugin communication
- **Shared Resources**: Common utilities and helper classes
- **Documentation**: Comprehensive developer guides and examples

## Future Considerations

### Scalability
- **Clustering Support**: Multi-server plugin coordination
- **Database Sharding**: Horizontal scaling for large player bases
- **Caching Strategy**: Redis integration for high-performance caching
- **Load Balancing**: API request distribution across multiple servers

### Community Development
- **Plugin Templates**: Starter templates for new RVNK plugins
- **Developer APIs**: Rich APIs for third-party plugin development
- **Documentation Portal**: Comprehensive documentation with examples
- **Community Support**: Developer forums and support channels

This outline serves as the foundation for detailed implementation requirements and will be revised as the project evolves through its development phases.
