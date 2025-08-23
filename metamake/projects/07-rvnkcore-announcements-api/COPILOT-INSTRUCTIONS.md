# RVNKCore Announcements API Implementation - Copilot Instructions

**Project Context**: Minecraft Plugin Architecture - Service Separation Pattern Implementation  
**Domain**: Java Plugin Development with Database Integration and REST API  
**Last Updated**: August 22, 2025

## 🎯 Project Objectives

This project implements the **RVNKCore Announcements API** as the foundational example of the service separation pattern for the RVNK plugin ecosystem. The implementation migrates from YAML-based announcement storage to a modern, database-backed service architecture with REST API integration.

### Primary Goals
1. **Service Architecture**: Implement comprehensive announcement service layer in RVNKCore
2. **Database Migration**: Transition from YAML files to MySQL/SQLite database storage  
3. **REST API**: Create web-enabled endpoints for announcement management
4. **Legacy Compatibility**: Maintain backward compatibility with existing RVNKTools features
5. **Pattern Template**: Establish service separation pattern for other RVNK plugins

## 📂 Project Structure

### Implementation Modules
- **features/** - Feature specifications and requirements
- **implementation/** - Step-by-step implementation guides
- **validation/** - Testing checklists and validation procedures
- **docs/** - Architecture decisions and API documentation

### Key Implementation Areas
- **Service Layer**: AnnouncementService interface and DefaultAnnouncementService implementation
- **Repository Layer**: Database operations with specialized queries and caching
- **API Layer**: REST endpoints with authentication and CORS support
- **Migration Layer**: YAML to database transformation with validation and rollback
- **Compatibility Layer**: Adapter pattern for existing AnnounceManager integration

## 🔧 Technical Context

### Current Infrastructure Status (✅ Complete)
**RVNKCore Foundation** - Operational as of August 22, 2025:
- AnnouncementService interface with 17 comprehensive async methods
- DefaultAnnouncementService with caching and validation
- AnnouncementRepository extending BaseRepository with specialized queries
- AnnouncementController with 15+ REST API endpoints  
- Database schema (rvnk_announcements) with MySQL/SQLite compatibility
- Complete indexing strategy for performance optimization

### Migration Source (📋 Ready for Integration)
**RVNKTools AnnounceManager** - Current YAML-based system:
- Command interface: `/announce add`, `/announce list`, `/announce remove`
- YAML configuration storage in `announcements.yml`
- PlaceholderAPI integration for dynamic content
- Permission-based announcement visibility
- Scheduled task system for delivery timing
- Economy integration with listing fees

## 🎨 Service Separation Pattern

### Architecture Design
```text
┌─────────────────────────────────────────┐
│              RVNKTools                  │
│         (Consumer Plugin)               │  
│  - AnnounceManager (Adapter)            │
│  - Commands & Permissions               │
│  - Economy Integration                  │
└─────────────┬───────────────────────────┘
              │ Service Consumption via
              │ Dependency Injection
              ▼
┌─────────────────────────────────────────┐
│             RVNKCore                    │
│        (Service Provider)               │
│  - AnnouncementService                  │
│  - Database Operations                  │
│  - REST API Endpoints                   │
│  - Caching & Performance                │
└─────────────────────────────────────────┘
```

### Implementation Strategy
- **RVNKCore Role**: Provides business services, data persistence, REST API
- **RVNKTools Role**: Consumes services via ServiceRegistry dependency injection
- **Benefits**: Performance, scalability, web integration, code reuse, multi-server support

## 🔄 Development Workflow

### Phase 1: Service Layer (Week 1)
1. **Service Interface Design**
   - Review existing AnnouncementService with 17 async methods
   - Implement ServiceRegistry integration patterns
   - Design dependency injection for RVNKTools consumption
   - Create service lifecycle management

### Phase 2: Database Integration (Week 2)  
1. **Repository Implementation**
   - Optimize AnnouncementRepository with performance tuning
   - Implement connection pooling with HikariCP
   - Create database migration framework with version management

2. **YAML Migration Framework**
   - Build robust YAML parser with error handling
   - Implement data transformation pipeline (YAML → AnnouncementDTO)
   - Create migration orchestrator with validation and rollback

### Phase 3: API and Compatibility (Week 3)
1. **REST API Controllers**
   - Complete AnnouncementController implementation
   - Add authentication, CORS, and rate limiting
   - Create comprehensive API documentation

2. **Legacy Compatibility**
   - Design adapter pattern for existing AnnounceManager interface
   - Maintain command structure and permissions
   - Implement graceful fallback to YAML if needed

### Phase 4: Testing and Deployment (Week 4)
1. **Comprehensive Testing**
   - Unit tests for service layer operations
   - Integration tests for database and API
   - Performance testing with large datasets
   - Migration testing with real YAML samples

## 💻 Technical Implementation Guidelines

### Code Quality Standards
- **Architecture**: Follow SOLID principles and dependency injection patterns
- **Async Operations**: Use CompletableFuture for all database operations
- **Error Handling**: Implement comprehensive ServiceException hierarchy
- **Documentation**: JavaDoc for all public APIs with examples
- **Testing**: Unit and integration tests with >90% coverage

### Performance Requirements
- **Database Operations**: Support 10,000+ announcements with proper indexing
- **Response Times**: <100ms for cached operations, <500ms for database operations
- **Concurrency**: Thread-safe operations with minimal lock contention
- **Memory Usage**: Efficient caching with configurable TTL and eviction

### Security Considerations
- **Authentication**: API key-based with role permissions
- **Data Validation**: Input validation and sanitization
- **SQL Injection**: Parameterized queries and prepared statements
- **Error Exposure**: Secure error messages without sensitive information

## 📚 Reference Materials

### Working Code Examples
- **AnnouncementService**: `toolkitplugin/src/main/java/org/fourz/rvnkcore/api/service/AnnouncementService.java`
- **DefaultAnnouncementService**: Complete implementation with caching
- **AnnouncementRepository**: Specialized queries extending BaseRepository
- **AnnouncementController**: REST endpoints with authentication
- **Current AnnounceManager**: `toolkitplugin/src/main/java/org/fourz/rvnktools/announceManager/`

### Documentation References
- **API Requirements**: `docs/requirements/rvnkcore-api.md`
- **Migration Requirements**: `docs/requirements/announcemanager-migration-requirements.md`
- **REST API Guide**: `docs/implementation/rest-api-implementation-guide.md`
- **Database Implementation**: `docs/rvnkcore-mysql-implementation.md`

### Technology Stack
- **Java 17+**: Core implementation language
- **Spigot/Paper API**: Minecraft server integration
- **MySQL/SQLite**: Database backends with HikariCP connection pooling
- **Jetty**: HTTP server for REST API
- **Gson**: JSON serialization for API responses
- **JUnit 5**: Testing framework with comprehensive coverage

## ⚠️ Critical Implementation Notes

### Migration Complexity
- **Data Integrity**: Ensure zero data loss during YAML to database migration
- **Rollback Strategy**: Complete rollback capability with backup creation
- **Validation Framework**: Comprehensive validation of migrated data
- **Performance Impact**: Minimize downtime during migration process

### Backward Compatibility
- **Command Interface**: Preserve existing `/announce` command structure
- **Permission System**: Maintain current permission nodes and behavior
- **Configuration**: Support existing configuration patterns during transition
- **Fallback Behavior**: Graceful degradation if RVNKCore services unavailable

### Production Considerations
- **Connection Pooling**: HikariCP configuration for high-concurrency environments
- **Monitoring**: Health checks and performance metrics collection
- **Logging**: Comprehensive logging with appropriate levels and context
- **Security**: Production-ready authentication and authorization

## 🚀 Success Criteria

### Functional Requirements
- [ ] Complete service layer with all 17 AnnouncementService methods operational
- [ ] Database operations with MySQL/SQLite support and performance optimization
- [ ] REST API with authentication, CORS, and comprehensive error handling
- [ ] YAML to database migration with validation and rollback capabilities
- [ ] Full backward compatibility with existing RVNKTools AnnounceManager

### Non-Functional Requirements  
- [ ] Performance: 10,000+ announcements with <500ms response times
- [ ] Reliability: 99.9% uptime with proper error handling and recovery
- [ ] Security: Authentication, authorization, and comprehensive data validation
- [ ] Maintainability: Clean architecture with extensive documentation
- [ ] Scalability: Multi-server support with database backend

### Template Establishment
- [ ] Service separation pattern documented and validated
- [ ] Reusable templates created for other RVNK plugins
- [ ] Developer integration guide for service consumption
- [ ] Code generation tools for new service implementations

This project establishes the foundation for the entire RVNK plugin ecosystem's evolution toward modern, scalable, and web-integrated architecture patterns.
