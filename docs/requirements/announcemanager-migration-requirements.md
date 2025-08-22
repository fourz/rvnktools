# AnnounceManager Migration Requirements

**Document Version**: 1.0  
**Date**: August 22, 2025  
**Status**: Implementation Ready

## Executive Summary

This document defines the requirements for migrating RVNKTools' `AnnounceManager` from its current YAML-based announcement system to the new RVNKCore announcement service infrastructure. The migration will transition from file-based storage to database-backed operations while maintaining backward compatibility and preserving existing functionality.

## Current State Analysis

### AnnounceManager Architecture

The existing `AnnounceManager` in RVNKTools operates with:

- **YAML Configuration**: Announcements stored in `announcements.yml`
- **Scheduled Tasks**: Bukkit scheduler-based announcement timing
- **PlaceholderAPI Integration**: Dynamic content replacement in messages
- **Permission Checking**: Player-specific announcement visibility
- **Manual Management**: Commands for adding, removing, and listing announcements

### RVNKCore Announcement Infrastructure

The new infrastructure provides:

- **AnnouncementService**: 17 comprehensive async methods for announcement management
- **AnnouncementRepository**: Database operations with specialized queries
- **DefaultAnnouncementService**: Caching, validation, and performance optimization
- **AnnouncementController**: REST API with 15+ endpoints for web integration
- **Database Schema**: MySQL/SQLite compatible with proper indexing
- **Migration Support**: Ready for data transformation and import

## Migration Objectives

### Primary Goals

1. **Zero Data Loss**: All existing announcements must be preserved and migrated accurately
2. **Feature Parity**: All current AnnounceManager functionality must be maintained
3. **Performance Improvement**: Leverage database-backed operations for better performance
4. **Backward Compatibility**: Maintain existing command structure and permissions
5. **Web Integration**: Enable REST API access for web-based announcement management

### Secondary Goals

1. **Enhanced Scheduling**: Add support for advanced scheduling patterns
2. **Improved Analytics**: Track announcement delivery and engagement metrics
3. **Multi-Server Support**: Prepare for cross-server announcement synchronization
4. **Rich Content**: Support for enhanced formatting and multimedia content

## Migration Strategy

### Phase 1: Infrastructure Preparation (Completed ✅)

**Status**: ✅ **COMPLETED** - August 22, 2025

- [x] **AnnouncementService Interface**: Complete with 17 async methods
- [x] **AnnouncementRepository**: Database operations with specialized queries
- [x] **DefaultAnnouncementService**: Business logic with caching and validation
- [x] **Database Schema**: MySQL/SQLite compatible table with proper indexing
- [x] **REST API Controller**: Web endpoints for announcement management
- [x] **Integration Testing**: Service operations validated and operational

### Phase 2: Data Migration Framework

**Priority**: High | **Timeline**: Next Implementation Phase

#### Migration Components

1. **YAML Parser and Validator**
   ```java
   package org.fourz.rvnktools.migration;
   
   /**
    * Reads and validates existing YAML announcement configuration.
    */
   public class AnnouncementYamlParser {
       public CompletableFuture<List<AnnouncementMigrationData>> parseYamlAnnouncements(File yamlFile);
       public CompletableFuture<ValidationResult> validateYamlStructure(File yamlFile);
   }
   ```

2. **Data Transformation Service**
   ```java
   package org.fourz.rvnktools.migration;
   
   /**
    * Transforms YAML announcement data to RVNKCore AnnouncementDTO format.
    */
   public class AnnouncementDataTransformer {
       public CompletableFuture<List<AnnouncementDTO>> transformToDto(List<AnnouncementMigrationData> yamlData);
       public CompletableFuture<ValidationResult> validateTransformation(List<AnnouncementDTO> dtos);
   }
   ```

3. **Migration Orchestrator**
   ```java
   package org.fourz.rvnktools.migration;
   
   /**
    * Coordinates the complete migration process from YAML to database.
    */
   public class AnnouncementMigrationOrchestrator {
       public CompletableFuture<MigrationResult> performMigration(File yamlFile, boolean backupExisting);
       public CompletableFuture<Void> rollbackMigration(String migrationId);
   }
   ```

#### Data Mapping Strategy

**YAML Structure** → **AnnouncementDTO Mapping**:

```yaml
# Current YAML format
announcements:
  announcement_1:
    message: "Welcome to the server!"
    interval: 300
    enabled: true
    type: "welcome"
    permission: "rvnktools.announcements.see.welcome"
```

**Maps to**:
```java
AnnouncementDTO.Builder()
    .id(UUID.randomUUID().toString())
    .title("announcement_1")
    .message("Welcome to the server!")
    .intervalSeconds(300)
    .active(true)
    .type("welcome")
    .targetGroup(extractGroupFromPermission("rvnktools.announcements.see.welcome"))
    .build();
```

### Phase 3: AnnounceManager Refactoring

**Priority**: High | **Timeline**: After Migration Framework

#### Refactoring Components

1. **Service Integration**
   ```java
   package org.fourz.rvnktools.manager;
   
   /**
    * Refactored AnnounceManager using RVNKCore services.
    */
   public class AnnounceManager {
       private final AnnouncementService announcementService;
       private final RVNKCoreBootstrap coreBootstrap;
       
       public AnnounceManager(RVNKTools plugin) {
           this.coreBootstrap = new RVNKCoreBootstrap(plugin);
           this.announcementService = coreBootstrap.getService(AnnouncementService.class);
       }
   }
   ```

2. **Command Adapter Layer**
   ```java
   package org.fourz.rvnktools.command.announce;
   
   /**
    * Adapts existing announce commands to use RVNKCore services.
    */
   public class AnnounceCommand extends BaseCommand {
       private final AnnouncementService announcementService;
       
       // Maintain existing command structure while using new service backend
   }
   ```

3. **Scheduler Integration**
   ```java
   package org.fourz.rvnktools.announcement;
   
   /**
    * Integrates announcement delivery with Bukkit scheduler.
    */
   public class AnnouncementScheduler {
       private final AnnouncementService announcementService;
       private final Map<String, BukkitTask> scheduledTasks;
       
       public void scheduleAnnouncements();
       public void updateSchedule(String announcementId);
   }
   ```

### Phase 4: Configuration Migration

**Priority**: Medium | **Timeline**: After Core Refactoring

#### Configuration Strategy

1. **Hybrid Configuration Support**
   - Database operations as primary source
   - YAML fallback for offline configuration
   - Migration utility for existing setups
   - Configuration export/import capabilities

2. **Backward Compatibility**
   ```java
   package org.fourz.rvnktools.config;
   
   /**
    * Provides backward compatibility for YAML-based configuration.
    */
   public class AnnouncementConfigAdapter {
       public CompletableFuture<Void> syncDatabaseToYaml();
       public CompletableFuture<Void> syncYamlToDatabase();
       public boolean isYamlFallbackEnabled();
   }
   ```

3. **Configuration Validation**
   - Validate existing YAML before migration
   - Verify database connectivity and schema
   - Provide detailed migration reports
   - Support rollback procedures

## Technical Requirements

### Database Schema Requirements

The `rvnk_announcements` table must support all existing YAML functionality:

```sql
CREATE TABLE rvnk_announcements (
    id VARCHAR(36) PRIMARY KEY,              -- UUID
    title VARCHAR(255),                      -- Announcement identifier
    message TEXT NOT NULL,                   -- Message content
    type VARCHAR(50) NOT NULL DEFAULT 'general', -- Announcement category
    active BOOLEAN NOT NULL DEFAULT true,   -- Enabled/disabled status
    interval_seconds INT DEFAULT 300,       -- Scheduling interval
    target_world VARCHAR(100),               -- World-specific targeting
    target_group VARCHAR(100),               -- Permission group targeting
    priority INT DEFAULT 0,                 -- Delivery priority
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_delivered_at TIMESTAMP,            -- Last delivery tracking
    delivery_count BIGINT DEFAULT 0         -- Delivery statistics
);

-- Performance indexes
CREATE INDEX idx_announcements_active ON rvnk_announcements(active);
CREATE INDEX idx_announcements_type ON rvnk_announcements(type);
CREATE INDEX idx_announcements_world ON rvnk_announcements(target_world);
CREATE INDEX idx_announcements_group ON rvnk_announcements(target_group);
CREATE INDEX idx_announcements_priority ON rvnk_announcements(priority);
```

### Service Interface Requirements

The AnnouncementService must provide all current AnnounceManager functionality:

```java
public interface AnnouncementService {
    // Core CRUD Operations (matching current functionality)
    CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement);
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    CompletableFuture<AnnouncementDTO> updateAnnouncement(String id, AnnouncementDTO announcement);
    CompletableFuture<Void> deleteAnnouncement(String id);
    
    // List and Query Operations
    CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements();
    CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements();
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type);
    
    // Targeting Operations (new functionality)
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForWorld(String worldName);
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForGroup(String groupName);
    
    // Scheduling and Delivery
    CompletableFuture<Void> activateAnnouncement(String id);
    CompletableFuture<Void> deactivateAnnouncement(String id);
    CompletableFuture<Void> recordDelivery(String id, LocalDateTime deliveredAt);
    
    // Migration Support
    CompletableFuture<List<AnnouncementDTO>> bulkImportAnnouncements(List<AnnouncementDTO> announcements);
    CompletableFuture<Void> validateMigrationData(List<AnnouncementDTO> announcements);
}
```

### Command Interface Requirements

Existing commands must maintain their structure and behavior:

```java
// Existing command structure must be preserved
/announce add <message> [type] [interval]     // Create announcement
/announce remove <id|index>                  // Delete announcement  
/announce list                               // List all announcements
/announce reload                             // Reload configuration
/announce enable <id>                        // Activate announcement
/announce disable <id>                       // Deactivate announcement

// Enhanced commands (optional)
/announce import <file>                      // Import from YAML/JSON
/announce export <file>                      // Export to YAML/JSON
/announce stats                              // Delivery statistics
```

## Migration Process

### Pre-Migration Requirements

1. **Backup Creation**
   - Complete backup of existing `announcements.yml`
   - Database backup if upgrading existing installation
   - Configuration files backup
   - Plugin data directory backup

2. **Environment Validation**
   - Verify database connectivity (MySQL/SQLite)
   - Validate RVNKCore service availability
   - Check disk space for migration operations
   - Verify plugin permissions and access

3. **Data Validation**
   - Parse and validate existing YAML structure
   - Check for duplicate announcement IDs
   - Validate message content and formatting
   - Verify permission group references

### Migration Execution

1. **Migration Initialization**
   ```java
   // Migration process initiation
   AnnouncementMigrationOrchestrator migrator = new AnnouncementMigrationOrchestrator(
       announcementService,
       yamlParser,
       dataTransformer
   );
   
   CompletableFuture<MigrationResult> result = migrator.performMigration(
       yamlFile,
       true  // Create backup
   );
   ```

2. **Data Processing Pipeline**
   - Parse existing YAML announcements
   - Transform to AnnouncementDTO format
   - Validate transformed data
   - Insert into database via AnnouncementService
   - Verify successful migration

3. **Service Transition**
   - Switch AnnounceManager to use database service
   - Update scheduled tasks to query database
   - Verify command functionality with new backend
   - Test announcement delivery

### Post-Migration Validation

1. **Functional Testing**
   - Verify all announcements migrated correctly
   - Test announcement scheduling and delivery
   - Validate command functionality
   - Check permission-based filtering

2. **Performance Validation**
   - Monitor database query performance
   - Verify caching effectiveness
   - Check memory usage impact
   - Validate concurrent access handling

3. **Rollback Testing**
   - Test rollback procedures
   - Verify YAML fallback functionality
   - Validate backup restoration
   - Document rollback scenarios

## Risk Assessment and Mitigation

### High-Risk Areas

1. **Data Loss During Migration**
   - **Risk**: Announcement data corruption or loss
   - **Mitigation**: Comprehensive backup strategy, transaction-based migration, rollback procedures

2. **Performance Regression**
   - **Risk**: Database operations slower than YAML access
   - **Mitigation**: Caching strategy, performance benchmarking, query optimization

3. **Compatibility Issues**
   - **Risk**: Existing functionality breaks after migration
   - **Mitigation**: Extensive testing, phased rollout, backward compatibility layer

4. **Service Dependencies**
   - **Risk**: RVNKCore service unavailable during migration
   - **Mitigation**: Service health checking, fallback mechanisms, error handling

### Medium-Risk Areas

1. **Configuration Complexity**
   - **Risk**: Users struggle with new configuration options
   - **Mitigation**: Migration utilities, documentation, gradual feature rollout

2. **Command Changes**
   - **Risk**: Command syntax or behavior changes confuse users
   - **Mitigation**: Maintain existing command structure, provide aliases, clear documentation

## Success Criteria

### Technical Success Criteria

- [ ] **Complete Data Migration**: All existing announcements successfully migrated to database
- [ ] **Performance Parity**: Database operations perform as well as or better than YAML operations
- [ ] **Feature Parity**: All existing AnnounceManager functionality preserved
- [ ] **Zero Downtime**: Migration process doesn't require server restart or extended downtime
- [ ] **Rollback Capability**: Ability to revert to YAML-based system if needed

### Functional Success Criteria

- [ ] **Command Compatibility**: All existing announce commands work without changes
- [ ] **Scheduling Accuracy**: Announcement timing remains consistent after migration
- [ ] **Permission Integration**: Player-specific announcement visibility preserved
- [ ] **PlaceholderAPI Support**: Dynamic content replacement continues working
- [ ] **Performance Metrics**: System performance maintains or improves current levels

### User Experience Success Criteria

- [ ] **Transparent Migration**: Users notice no functional changes in announcement behavior
- [ ] **Enhanced Capabilities**: New features (REST API, web management) available
- [ ] **Configuration Simplicity**: Migration process requires minimal user intervention
- [ ] **Documentation Quality**: Clear migration guide and troubleshooting documentation
- [ ] **Support Readiness**: Issues can be quickly diagnosed and resolved

## Implementation Timeline

### Immediate Tasks (Week 1-2)

- [ ] **Migration Framework Development**
  - Create YAML parser and validation
  - Build data transformation pipeline
  - Implement migration orchestrator

- [ ] **Testing Infrastructure**
  - Unit tests for migration components
  - Integration tests with sample data
  - Performance benchmarking

### Integration Phase (Week 3-4)

- [ ] **AnnounceManager Refactoring**
  - Integrate with RVNKCore services
  - Maintain command compatibility
  - Update scheduling system

- [ ] **Validation and Testing**
  - End-to-end migration testing
  - Performance validation
  - Rollback procedure testing

### Deployment Phase (Week 5-6)

- [ ] **Production Preparation**
  - Migration documentation
  - Rollback procedures
  - Performance monitoring

- [ ] **Migration Deployment**
  - Staged rollout process
  - User communication
  - Support readiness

## Documentation Requirements

### Migration Guide

- **Pre-migration checklist** with environment requirements
- **Step-by-step migration process** with screenshots and examples
- **Troubleshooting guide** for common migration issues
- **Rollback procedures** with detailed instructions
- **Performance optimization** recommendations

### API Documentation

- **REST API reference** for AnnouncementController endpoints
- **Java API documentation** for AnnouncementService interface
- **Integration examples** for third-party development
- **Authentication and authorization** setup guide
- **Rate limiting and usage policies**

### Administrative Guide

- **Configuration management** for hybrid YAML/database setup
- **Monitoring and maintenance** procedures for database operations
- **Backup and recovery** strategies for announcement data
- **Performance tuning** guidelines for large-scale deployments
- **Security considerations** for REST API exposure

## Conclusion

The migration from AnnounceManager's YAML-based system to RVNKCore's database-backed announcement service represents a significant architectural improvement that will:

- **Enhance Performance**: Database operations with caching and query optimization
- **Enable Web Integration**: REST API access for web-based management tools  
- **Improve Scalability**: Support for larger announcement datasets and multi-server deployments
- **Maintain Compatibility**: Preserve existing functionality and command structure
- **Add New Capabilities**: Advanced scheduling, analytics, and targeting options

With the RVNKCore announcement infrastructure complete and operational, the migration framework represents the next critical implementation phase. Success will depend on careful data handling, comprehensive testing, and thorough documentation to ensure a smooth transition for all RVNKTools users.

The hybrid configuration approach (database primary, YAML fallback) ensures that users maintain flexibility while benefiting from the enhanced capabilities of the new system. This migration strategy positions RVNKTools for future enhancements while preserving the reliability and simplicity that users expect.
