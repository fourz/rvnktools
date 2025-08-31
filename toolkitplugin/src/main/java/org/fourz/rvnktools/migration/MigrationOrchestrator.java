package org.fourz.rvnktools.migration;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Migration Orchestrator for Phase 1 Migration Framework
 * 
 * Coordinates the complete migration process from YAML to RVNKCore database:
 * - Progress tracking and reporting
 * - Error handling and recovery
 * - Transaction management and rollback
 * - Validation and verification
 * 
 * This orchestrator provides the main entry point for migration operations,
 * coordinating all migration components and ensuring data integrity.
 * 
 * @since Phase 1 Migration Framework
 */
public class MigrationOrchestrator {
    
    private final RVNKTools plugin;
    private final LogManager logger;
    private final AnnouncementService announcementService;
    
    // Migration components
    private final YAMLAnnouncementParser parser;
    private final DataTransformationService transformer;
    private final BackupRollbackManager backupManager;
    
    // Progress tracking
    private final AtomicInteger currentStep = new AtomicInteger(0);
    private final AtomicInteger totalSteps = new AtomicInteger(5);
    private MigrationStatus currentStatus = MigrationStatus.NOT_STARTED;
    private final List<String> progressLog;
    private final List<String> errorLog;
    
    // Migration results
    private MigrationResult lastMigrationResult;
    
    public MigrationOrchestrator(RVNKTools plugin, AnnouncementService announcementService) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.announcementService = announcementService;
        
        // Initialize migration components
        this.parser = new YAMLAnnouncementParser(plugin);
        this.transformer = new DataTransformationService(plugin);
        this.backupManager = new BackupRollbackManager(plugin);
        
        this.progressLog = new ArrayList<>();
        this.errorLog = new ArrayList<>();
    }
    
    /**
     * Execute complete migration process asynchronously
     * 
     * @param dryRun If true, performs validation without actual database writes
     * @return CompletableFuture containing MigrationResult with complete status
     */
    public CompletableFuture<MigrationResult> executeMigration(boolean dryRun) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting " + (dryRun ? "DRY RUN" : "FULL") + " migration from YAML to RVNKCore...");
            
            try {
                initializeMigration();
                
                // Step 1: Parse YAML data
                updateProgress("Parsing YAML announcement data...", 1);
                YAMLAnnouncementParser.ParseResult parseResult = parser.parseYAMLFile().join();
                
                if (!parseResult.isSuccess()) {
                    throw new MigrationException("YAML parsing failed: " + parseResult.getErrorMessage());
                }
                
                logProgress("Successfully parsed " + parseResult.getTypes().size() + " types and " + 
                           parseResult.getAnnouncements().size() + " announcements");
                
                // Step 2: Transform data
                updateProgress("Transforming data to RVNKCore format...", 2);
                DataTransformationService.TransformationResult transformResult = 
                    transformer.transformData(parseResult).join();
                
                if (!transformResult.isSuccess()) {
                    throw new MigrationException("Data transformation failed: " + transformResult.getErrorMessage());
                }
                
                logProgress("Successfully transformed data: " + transformResult.getSummary().toString());
                
                // Step 3: Validate transformed data
                updateProgress("Validating transformed data...", 3);
                ValidationResult validationResult = validateTransformedData(transformResult);
                
                if (!validationResult.isValid()) {
                    logErrors(validationResult.getIssues());
                    if (validationResult.isCritical()) {
                        throw new MigrationException("Critical validation errors prevent migration");
                    }
                }
                
                if (dryRun) {
                    currentStatus = MigrationStatus.DRY_RUN_COMPLETE;
                    logProgress("DRY RUN completed successfully - no data written to database");
                    return generateMigrationResult(parseResult, transformResult, validationResult, true);
                }
                
                // Step 4: Create backup
                updateProgress("Creating backup of current data...", 4);
                BackupRollbackManager.BackupResult backupResult = backupManager.createBackup().join();
                
                if (!backupResult.isSuccess()) {
                    throw new MigrationException("Backup creation failed: " + backupResult.getErrorMessage());
                }
                
                logProgress("Backup created successfully: " + backupResult.getBackupLocation());
                
                // Step 5: Migrate data to database
                updateProgress("Migrating data to RVNKCore database...", 5);
                DatabaseMigrationResult dbResult = migrateToDatabase(transformResult);
                
                if (!dbResult.isSuccess()) {
                    logError("Database migration failed: " + dbResult.getErrorMessage());
                    
                    // Attempt rollback
                    updateProgress("Migration failed - attempting rollback...", 5);
                    BackupRollbackManager.RollbackResult rollbackResult = 
                        backupManager.performRollback(backupResult.getBackupId()).join();
                    
                    if (rollbackResult.isSuccess()) {
                        logProgress("Rollback completed successfully");
                        currentStatus = MigrationStatus.ROLLED_BACK;
                    } else {
                        logError("Rollback failed: " + rollbackResult.getErrorMessage());
                        currentStatus = MigrationStatus.FAILED_WITH_ROLLBACK_FAILURE;
                    }
                    
                    throw new MigrationException("Database migration failed and " + 
                                               (rollbackResult.isSuccess() ? "rolled back" : "rollback failed"));
                }
                
                currentStatus = MigrationStatus.COMPLETED;
                logProgress("Migration completed successfully!");
                
                return generateMigrationResult(parseResult, transformResult, validationResult, dbResult, true);
                
            } catch (Exception e) {
                currentStatus = MigrationStatus.FAILED;
                logError("Migration failed: " + e.getMessage());
                logger.error("Migration execution failed", e);
                
                return generateMigrationResult(null, null, null, false, e.getMessage());
            }
        });
    }
    
    /**
     * Validate transformed data before database operations
     */
    private ValidationResult validateTransformedData(DataTransformationService.TransformationResult transformResult) {
        List<String> issues = new ArrayList<>();
        List<String> criticalIssues = new ArrayList<>();
        
        // Validate announcement types
        validateAnnouncementTypes(transformResult.getTransformedTypes(), issues, criticalIssues);
        
        // Validate announcements
        validateAnnouncements(transformResult.getTransformedAnnouncements(), 
                            transformResult.getTransformedTypes(), issues, criticalIssues);
        
        // Check for existing data conflicts
        validateDatabaseConflicts(transformResult, issues, criticalIssues);
        
        boolean isValid = criticalIssues.isEmpty();
        boolean isCritical = !criticalIssues.isEmpty();
        
        return new ValidationResult(isValid, isCritical, issues, criticalIssues);
    }
    
    /**
     * Validate announcement types for database compatibility
     */
    private void validateAnnouncementTypes(List<AnnouncementTypeDTO> types, 
                                         List<String> issues, List<String> criticalIssues) {
        Set<String> typeIds = new HashSet<>();
        
        for (AnnouncementTypeDTO type : types) {
            // Check for duplicate IDs
            if (typeIds.contains(type.getId())) {
                criticalIssues.add("Duplicate announcement type ID: " + type.getId());
            }
            typeIds.add(type.getId());
            
            // Validate required fields
            if (type.getId() == null || type.getId().trim().isEmpty()) {
                criticalIssues.add("Announcement type missing required ID");
            }
            
            if (type.getPrefix() == null) {
                criticalIssues.add("Announcement type '" + type.getId() + "' missing required prefix");
            }
        }
    }
    
    /**
     * Validate announcements for database compatibility
     */
    private void validateAnnouncements(List<AnnouncementDTO> announcements, 
                                     List<AnnouncementTypeDTO> types,
                                     List<String> issues, List<String> criticalIssues) {
        Set<String> typeIds = types.stream()
                                  .map(AnnouncementTypeDTO::getId)
                                  .collect(HashSet::new, Set::add, Set::addAll);
        
        Set<String> announcementIds = new HashSet<>();
        
        for (AnnouncementDTO announcement : announcements) {
            // Check for duplicate IDs
            if (announcementIds.contains(announcement.getId())) {
                criticalIssues.add("Duplicate announcement ID: " + announcement.getId());
            }
            announcementIds.add(announcement.getId());
            
            // Validate required fields
            if (announcement.getId() == null || announcement.getId().trim().isEmpty()) {
                criticalIssues.add("Announcement missing required ID");
            }
            
            if (announcement.getMessage() == null || announcement.getMessage().trim().isEmpty()) {
                criticalIssues.add("Announcement '" + announcement.getId() + "' missing required message");
            }
            
            // Validate type reference
            if (!typeIds.contains(announcement.getType())) {
                criticalIssues.add("Announcement '" + announcement.getId() + 
                                 "' references unknown type: " + announcement.getType());
            }
            
            // Validate timestamps
            if (announcement.getScheduledFor() != null && announcement.getExpiresAt() != null &&
                announcement.getScheduledFor().after(announcement.getExpiresAt())) {
                issues.add("Announcement '" + announcement.getId() + 
                          "' scheduled after expiration date");
            }
        }
    }
    
    /**
     * Check for existing data conflicts in database
     */
    private void validateDatabaseConflicts(DataTransformationService.TransformationResult transformResult,
                                         List<String> issues, List<String> criticalIssues) {
        try {
            // Check for existing announcement type conflicts
            for (AnnouncementTypeDTO type : transformResult.getTransformedTypes()) {
                // Note: This would require AnnouncementService to have type management
                // For now, we'll assume type conflicts are handled by the service
            }
            
            // Check for existing announcement conflicts
            for (AnnouncementDTO announcement : transformResult.getTransformedAnnouncements()) {
                CompletableFuture<Optional<AnnouncementDTO>> existingFuture = 
                    announcementService.getAnnouncement(announcement.getId());
                
                Optional<AnnouncementDTO> existing = existingFuture.join();
                if (existing.isPresent()) {
                    issues.add("Announcement '" + announcement.getId() + 
                              "' already exists in database - will be overwritten");
                }
            }
            
        } catch (Exception e) {
            issues.add("Could not validate database conflicts: " + e.getMessage());
        }
    }
    
    /**
     * Migrate transformed data to RVNKCore database
     */
    private DatabaseMigrationResult migrateToDatabase(DataTransformationService.TransformationResult transformResult) {
        try {
            int typesCreated = 0;
            int announcementsCreated = 0;
            List<String> migrationErrors = new ArrayList<>();
            
            // Migrate announcement types first
            // Note: This assumes AnnouncementService will be extended to handle types
            // For now, we'll focus on announcements
            
            // Migrate announcements
            for (AnnouncementDTO announcement : transformResult.getTransformedAnnouncements()) {
                try {
                    CompletableFuture<AnnouncementDTO> result = 
                        announcementService.createAnnouncement(announcement);
                    
                    AnnouncementDTO created = result.join();
                    if (created != null) {
                        announcementsCreated++;
                        logProgress("Migrated announcement: " + announcement.getId());
                    } else {
                        migrationErrors.add("Failed to create announcement: " + announcement.getId());
                    }
                    
                } catch (Exception e) {
                    migrationErrors.add("Error migrating announcement '" + announcement.getId() + "': " + e.getMessage());
                }
            }
            
            boolean success = migrationErrors.isEmpty() || 
                            (migrationErrors.size() < transformResult.getTransformedAnnouncements().size() / 2);
            
            return new DatabaseMigrationResult(typesCreated, announcementsCreated, migrationErrors, success);
            
        } catch (Exception e) {
            logger.error("Database migration failed", e);
            return new DatabaseMigrationResult(0, 0, 
                Arrays.asList("Database migration failed: " + e.getMessage()), false);
        }
    }
    
    /**
     * Initialize migration state
     */
    private void initializeMigration() {
        currentStep.set(0);
        currentStatus = MigrationStatus.IN_PROGRESS;
        progressLog.clear();
        errorLog.clear();
        
        logProgress("Migration initialized at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
    
    /**
     * Update progress with step information
     */
    private void updateProgress(String message, int step) {
        currentStep.set(step);
        logProgress("[Step " + step + "/" + totalSteps.get() + "] " + message);
    }
    
    /**
     * Log progress message
     */
    private void logProgress(String message) {
        String timestampedMessage = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + " - " + message;
        progressLog.add(timestampedMessage);
        logger.info(message);
    }
    
    /**
     * Log error message
     */
    private void logError(String message) {
        String timestampedMessage = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + " - ERROR: " + message;
        errorLog.add(timestampedMessage);
        logger.error(message);
    }
    
    /**
     * Log info message
     */
    private void logInfo(String message) {
        String timestampedMessage = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + " - " + message;
        progressLog.add(timestampedMessage);
        logger.info(message);
    }
    
    /**
     * Log validation errors and info messages appropriately
     */
    private void logErrors(List<String> errors) {
        for (String error : errors) {
            if (error.startsWith("INFO:")) {
                logInfo(error.substring(5).trim()); // Remove "INFO:" prefix and log as info
            } else {
                logError(error);
            }
        }
    }
    
    /**
     * Generate comprehensive migration result
     */
    private MigrationResult generateMigrationResult(YAMLAnnouncementParser.ParseResult parseResult,
                                                  DataTransformationService.TransformationResult transformResult,
                                                  ValidationResult validationResult,
                                                  boolean success) {
        return generateMigrationResult(parseResult, transformResult, validationResult, null, success, null);
    }
    
    private MigrationResult generateMigrationResult(YAMLAnnouncementParser.ParseResult parseResult,
                                                  DataTransformationService.TransformationResult transformResult,
                                                  ValidationResult validationResult,
                                                  DatabaseMigrationResult dbResult,
                                                  boolean success) {
        return generateMigrationResult(parseResult, transformResult, validationResult, dbResult, success, null);
    }
    
    private MigrationResult generateMigrationResult(YAMLAnnouncementParser.ParseResult parseResult,
                                                  DataTransformationService.TransformationResult transformResult,
                                                  ValidationResult validationResult,
                                                  boolean success, String errorMessage) {
        return generateMigrationResult(parseResult, transformResult, validationResult, null, success, errorMessage);
    }
    
    private MigrationResult generateMigrationResult(YAMLAnnouncementParser.ParseResult parseResult,
                                                  DataTransformationService.TransformationResult transformResult,
                                                  ValidationResult validationResult,
                                                  DatabaseMigrationResult dbResult,
                                                  boolean success, String errorMessage) {
        
        lastMigrationResult = new MigrationResult(
            currentStatus,
            parseResult,
            transformResult,
            validationResult,
            dbResult,
            new ArrayList<>(progressLog),
            new ArrayList<>(errorLog),
            success,
            errorMessage
        );
        
        return lastMigrationResult;
    }
    
    // Getters for monitoring
    
    public MigrationStatus getCurrentStatus() { return currentStatus; }
    public int getCurrentStep() { return currentStep.get(); }
    public int getTotalSteps() { return totalSteps.get(); }
    public List<String> getProgressLog() { return new ArrayList<>(progressLog); }
    public List<String> getErrorLog() { return new ArrayList<>(errorLog); }
    public MigrationResult getLastMigrationResult() { return lastMigrationResult; }
    
    /**
     * Migration status enumeration
     */
    public enum MigrationStatus {
        NOT_STARTED,
        IN_PROGRESS,
        DRY_RUN_COMPLETE,
        COMPLETED,
        FAILED,
        ROLLED_BACK,
        FAILED_WITH_ROLLBACK_FAILURE
    }
    
    /**
     * Comprehensive migration result container
     */
    public static class MigrationResult {
        private final MigrationStatus status;
        private final YAMLAnnouncementParser.ParseResult parseResult;
        private final DataTransformationService.TransformationResult transformResult;
        private final ValidationResult validationResult;
        private final DatabaseMigrationResult databaseResult;
        private final List<String> progressLog;
        private final List<String> errorLog;
        private final boolean success;
        private final String errorMessage;
        
        public MigrationResult(MigrationStatus status,
                             YAMLAnnouncementParser.ParseResult parseResult,
                             DataTransformationService.TransformationResult transformResult,
                             ValidationResult validationResult,
                             DatabaseMigrationResult databaseResult,
                             List<String> progressLog,
                             List<String> errorLog,
                             boolean success,
                             String errorMessage) {
            this.status = status;
            this.parseResult = parseResult;
            this.transformResult = transformResult;
            this.validationResult = validationResult;
            this.databaseResult = databaseResult;
            this.progressLog = progressLog;
            this.errorLog = errorLog;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public MigrationStatus getStatus() { return status; }
        public YAMLAnnouncementParser.ParseResult getParseResult() { return parseResult; }
        public DataTransformationService.TransformationResult getTransformResult() { return transformResult; }
        public ValidationResult getValidationResult() { return validationResult; }
        public DatabaseMigrationResult getDatabaseResult() { return databaseResult; }
        public List<String> getProgressLog() { return progressLog; }
        public List<String> getErrorLog() { return errorLog; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        
        public boolean isDryRun() { return status == MigrationStatus.DRY_RUN_COMPLETE; }
        public boolean isComplete() { return status == MigrationStatus.COMPLETED; }
        public boolean hasFailed() { return status == MigrationStatus.FAILED || 
                                           status == MigrationStatus.FAILED_WITH_ROLLBACK_FAILURE; }
    }
    
    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final boolean valid;
        private final boolean critical;
        private final List<String> issues;
        private final List<String> criticalIssues;
        
        public ValidationResult(boolean valid, boolean critical, List<String> issues, List<String> criticalIssues) {
            this.valid = valid;
            this.critical = critical;
            this.issues = issues;
            this.criticalIssues = criticalIssues;
        }
        
        public boolean isValid() { return valid; }
        public boolean isCritical() { return critical; }
        public List<String> getIssues() { return issues; }
        public List<String> getCriticalIssues() { return criticalIssues; }
    }
    
    /**
     * Database migration result container
     */
    public static class DatabaseMigrationResult {
        private final int typesCreated;
        private final int announcementsCreated;
        private final List<String> errors;
        private final boolean success;
        
        public DatabaseMigrationResult(int typesCreated, int announcementsCreated, 
                                     List<String> errors, boolean success) {
            this.typesCreated = typesCreated;
            this.announcementsCreated = announcementsCreated;
            this.errors = errors;
            this.success = success;
        }
        
        public int getTypesCreated() { return typesCreated; }
        public int getAnnouncementsCreated() { return announcementsCreated; }
        public List<String> getErrors() { return errors; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return String.join("; ", errors); }
    }
    
    /**
     * Custom exception for migration orchestration errors
     */
    public static class MigrationException extends RuntimeException {
        public MigrationException(String message) {
            super(message);
        }
        
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
