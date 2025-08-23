# Feature: YAML to Database Migration Framework

**Feature ID**: 04-yaml-migration  
**Priority**: High  
**Status**: Implementation Ready  
**Estimated Effort**: 3-4 days

## Overview

Implement a comprehensive migration framework to transform existing YAML-based announcements from RVNKTools into database records in RVNKCore, ensuring zero data loss and providing rollback capabilities.

## Requirements

### Current YAML Structure Analysis

**Existing RVNKTools announcements.yml format**:
```yaml
announcements:
  announcement_1:
    type: "general"
    message: "&6[Server] &fWelcome to our server!"
    worlds: ["world", "world_nether"]
    groups: ["default", "premium"]
    active: true
    priority: 1
    created: "2025-08-15T10:30:00"
    listingFee: 100.0
    author: "admin"
  
  shop_announcement_1:
    type: "shop"
    message: "&e[Shop] &bNew items available in the spawn shop!"
    worlds: ["world"]
    groups: ["all"]
    active: true
    priority: 5
    created: "2025-08-20T14:45:00"
    listingFee: 50.0
    author: "shopkeeper"
```

### Migration Framework Components

**YAMLAnnouncementParser** - Robust YAML parsing with validation
```java
public class YAMLAnnouncementParser {
    private final LogManager logger;
    private final List<String> validationErrors;
    
    public ParseResult parseAnnouncementsFile(File yamlFile) {
        // Parse YAML with comprehensive error handling
        // Validate data structure and required fields
        // Return structured result with success/failure status
    }
    
    public List<YAMLAnnouncement> parseAnnouncementsSection(Map<String, Object> yamlData) {
        // Extract individual announcements from YAML structure
        // Handle missing or malformed fields gracefully
        // Provide detailed validation error messages
    }
    
    public static class ParseResult {
        private final boolean success;
        private final List<YAMLAnnouncement> announcements;
        private final List<String> errors;
        private final List<String> warnings;
    }
}
```

**YAMLAnnouncement** - Intermediate data model for YAML data
```java
public class YAMLAnnouncement {
    private String id;              // Original YAML key
    private String type;            // announcement type
    private String message;         // announcement message
    private List<String> worlds;    // target worlds
    private List<String> groups;    // target groups
    private boolean active;         // active status
    private int priority;           // display priority
    private String created;         // creation timestamp (string)
    private Double listingFee;      // listing fee amount
    private String author;          // announcement author
    
    // Validation methods
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (message == null || message.trim().isEmpty()) {
            errors.add("Message cannot be empty");
        }
        if (type == null || type.trim().isEmpty()) {
            errors.add("Type cannot be empty");
        }
        // Additional validation logic...
        return errors;
    }
}
```

### Data Transformation Service

**AnnouncementTransformationService** - Convert YAML to AnnouncementDTO
```java
public class AnnouncementTransformationService {
    private final LogManager logger;
    
    public TransformationResult transformToDatabase(List<YAMLAnnouncement> yamlAnnouncements) {
        List<AnnouncementDTO> databaseAnnouncements = new ArrayList<>();
        List<String> transformationErrors = new ArrayList<>();
        
        for (YAMLAnnouncement yaml : yamlAnnouncements) {
            try {
                AnnouncementDTO dto = transformSingle(yaml);
                databaseAnnouncements.add(dto);
            } catch (TransformationException e) {
                transformationErrors.add("Failed to transform " + yaml.getId() + ": " + e.getMessage());
            }
        }
        
        return new TransformationResult(databaseAnnouncements, transformationErrors);
    }
    
    private AnnouncementDTO transformSingle(YAMLAnnouncement yaml) throws TransformationException {
        return new AnnouncementDTO.Builder()
            .id(generateUniqueId(yaml.getId()))
            .type(yaml.getType())
            .message(yaml.getMessage())
            .targetWorld(String.join(",", yaml.getWorlds()))
            .targetGroup(String.join(",", yaml.getGroups()))
            .active(yaml.isActive())
            .priority(yaml.getPriority())
            .createdAt(parseTimestamp(yaml.getCreated()))
            .metadata(createMetadata(yaml))
            .build();
    }
    
    private Map<String, Object> createMetadata(YAMLAnnouncement yaml) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("originalId", yaml.getId());
        metadata.put("listingFee", yaml.getListingFee());
        metadata.put("author", yaml.getAuthor());
        metadata.put("migrationTimestamp", LocalDateTime.now().toString());
        return metadata;
    }
}
```

### Migration Orchestrator

**AnnouncementMigrationOrchestrator** - Complete migration workflow
```java
public class AnnouncementMigrationOrchestrator {
    private final YAMLAnnouncementParser parser;
    private final AnnouncementTransformationService transformationService;
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    public MigrationResult executeMigration(File yamlFile, MigrationOptions options) {
        try {
            // Phase 1: Parse YAML file
            ParseResult parseResult = parser.parseAnnouncementsFile(yamlFile);
            if (!parseResult.isSuccess()) {
                return MigrationResult.failure("YAML parsing failed", parseResult.getErrors());
            }
            
            // Phase 2: Transform data
            TransformationResult transformResult = transformationService.transformToDatabase(parseResult.getAnnouncements());
            if (transformResult.hasErrors()) {
                return MigrationResult.failure("Data transformation failed", transformResult.getErrors());
            }
            
            // Phase 3: Backup existing data (if any)
            if (options.isCreateBackup()) {
                createDatabaseBackup();
            }
            
            // Phase 4: Validate migration data
            ValidationResult validationResult = validateMigrationData(transformResult.getAnnouncements());
            if (!validationResult.isValid()) {
                return MigrationResult.failure("Data validation failed", validationResult.getErrors());
            }
            
            // Phase 5: Execute database migration
            DatabaseMigrationResult dbResult = executeDatabaseMigration(transformResult.getAnnouncements());
            
            // Phase 6: Post-migration validation
            if (options.isValidateAfterMigration()) {
                ValidationResult postResult = validateMigrationSuccess(transformResult.getAnnouncements());
                if (!postResult.isValid()) {
                    // Rollback if validation fails
                    rollbackMigration();
                    return MigrationResult.failure("Post-migration validation failed", postResult.getErrors());
                }
            }
            
            return MigrationResult.success(dbResult.getMigratedCount(), transformResult.getAnnouncements());
            
        } catch (Exception e) {
            logger.error("Migration failed with unexpected error", e);
            return MigrationResult.failure("Unexpected migration error: " + e.getMessage());
        }
    }
}
```

## Technical Specifications

### Migration Options Configuration
```java
public class MigrationOptions {
    private boolean createBackup = true;              // Create database backup before migration
    private boolean validateAfterMigration = true;   // Validate data after migration
    private boolean preserveOriginalIds = false;     // Try to preserve original YAML keys as IDs
    private boolean allowDuplicates = false;         // Allow duplicate messages
    private boolean skipInvalid = true;              // Skip invalid records instead of failing
    private int batchSize = 100;                     // Number of records to process in batches
    private long timeoutSeconds = 300;               // Migration timeout in seconds
}
```

### Rollback Capability
```java
public class MigrationRollbackService {
    private final AnnouncementService announcementService;
    private final DatabaseBackupService backupService;
    
    public RollbackResult rollbackMigration(String migrationId) {
        try {
            // Remove all migrated announcements
            List<AnnouncementDTO> migratedAnnouncements = findMigratedAnnouncements(migrationId);
            for (AnnouncementDTO announcement : migratedAnnouncements) {
                announcementService.deleteAnnouncement(announcement.getId()).join();
            }
            
            // Restore from backup if available
            if (backupService.hasBackup(migrationId)) {
                backupService.restoreBackup(migrationId);
            }
            
            return RollbackResult.success(migratedAnnouncements.size());
            
        } catch (Exception e) {
            return RollbackResult.failure("Rollback failed: " + e.getMessage());
        }
    }
}
```

### Data Validation Framework
```java
public class MigrationDataValidator {
    public ValidationResult validateYAMLAnnouncement(YAMLAnnouncement yaml) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Required field validation
        if (yaml.getMessage() == null || yaml.getMessage().trim().isEmpty()) {
            errors.add("Message is required");
        }
        
        if (yaml.getType() == null || yaml.getType().trim().isEmpty()) {
            errors.add("Type is required");
        }
        
        // Data format validation
        if (yaml.getPriority() < 0 || yaml.getPriority() > 10) {
            warnings.add("Priority should be between 0 and 10");
        }
        
        // Business rule validation
        if (yaml.getListingFee() != null && yaml.getListingFee() < 0) {
            errors.add("Listing fee cannot be negative");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    public ValidationResult validateAnnouncementDTO(AnnouncementDTO dto) {
        // Database-specific validation
        // UUID format validation
        // Database constraint validation
        // Foreign key validation (if applicable)
    }
}
```

## Implementation Tasks

### Task 1: YAML Parser Implementation
- [ ] Create YAMLAnnouncementParser with SnakeYAML integration
- [ ] Implement robust error handling for malformed YAML files
- [ ] Add comprehensive validation for required and optional fields
- [ ] Create detailed error reporting with line numbers and context

### Task 2: Data Transformation Service
- [ ] Implement AnnouncementTransformationService with field mapping
- [ ] Handle data type conversions (String dates to Timestamp, etc.)
- [ ] Create metadata preservation for migration tracking
- [ ] Add support for custom transformation rules

### Task 3: Migration Orchestrator
- [ ] Build complete migration workflow with phase management
- [ ] Implement backup creation and restoration capabilities
- [ ] Add progress reporting and cancellation support
- [ ] Create comprehensive logging and audit trail

### Task 4: Testing and Validation
- [ ] Unit tests for all transformation logic with edge cases
- [ ] Integration tests with real YAML files and database operations
- [ ] Performance tests with large YAML files (1000+ announcements)
- [ ] Rollback testing to ensure data integrity

## Acceptance Criteria

### Functional Requirements
- [ ] Successfully parse all valid YAML announcement files
- [ ] Transform YAML data to AnnouncementDTO with 100% field mapping
- [ ] Execute complete migration workflow with progress reporting
- [ ] Provide rollback capability with full data restoration

### Data Integrity Requirements
- [ ] Zero data loss during migration process
- [ ] All YAML fields preserved in database or metadata
- [ ] Validation catches all data inconsistencies before database insertion
- [ ] Backup and rollback procedures tested and verified

### Performance Requirements
- [ ] Process 1000+ announcements in < 30 seconds
- [ ] Memory usage remains stable during large migrations
- [ ] Progress reporting updates every 5% of completion
- [ ] Database operations use batching for efficiency

### Error Handling Requirements
- [ ] Graceful handling of malformed YAML files
- [ ] Detailed error messages with actionable information
- [ ] Partial migration support (skip invalid, continue with valid)
- [ ] Comprehensive audit trail of all migration activities

## Dependencies

### Internal Dependencies
- **AnnouncementService**: Target service for migrated announcements
- **AnnouncementDTO**: Target data model for database storage
- **LogManager**: Comprehensive logging and error reporting

### External Dependencies
- **SnakeYAML**: YAML parsing library for robust file processing
- **CompletableFuture**: Async operation support for database operations
- **Jackson**: JSON processing for metadata serialization (optional)

## Risk Assessment

### Data Migration Risks
- **Data Loss**: YAML parsing errors or transformation failures
  - Mitigation: Comprehensive backup system and rollback procedures
- **Data Corruption**: Invalid data inserted into database
  - Mitigation: Multi-phase validation before and after migration
- **Performance Impact**: Migration blocking server operations
  - Mitigation: Batched processing and async operations

### Technical Risks
- **Memory Usage**: Large YAML files causing OutOfMemoryError
  - Mitigation: Streaming parsing and batched processing
- **Database Constraints**: Unique key violations or constraint failures
  - Mitigation: Pre-migration validation and conflict resolution
- **Rollback Complexity**: Unable to restore previous state after failure
  - Mitigation: Comprehensive backup system and tested rollback procedures

## Related Features

- **01-service-architecture**: Provides AnnouncementService target for migration
- **02-database-schema**: Provides database schema for migrated data
- **05-legacy-compatibility**: Uses migration data for backward compatibility testing

This migration framework ensures a smooth transition from YAML-based announcements to the modern database-backed system while maintaining data integrity and providing comprehensive safety mechanisms.
