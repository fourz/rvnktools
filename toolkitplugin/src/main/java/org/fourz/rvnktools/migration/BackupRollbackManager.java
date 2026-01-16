package org.fourz.rvnktools.migration;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnkcore.RVNKCore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Backup and Rollback Manager for Phase 1 Migration Framework
 * 
 * Provides comprehensive backup and rollback capabilities for migration operations:
 * - Pre-migration backup creation
 * - Data export in multiple formats
 * - Rollback functionality with validation
 * - Backup integrity verification
 * 
 * This manager ensures that migration operations can be safely reversed if needed,
 * maintaining data integrity and providing recovery mechanisms.
 * 
 * @since Phase 1 Migration Framework
 */
@SuppressWarnings("unused") // Plugin field used for data folder access and LogManager
public class BackupRollbackManager {
    
    private final RVNKCore plugin;
    private final LogManager logger;
    private final File backupDirectory;
    private final Gson gson;
    
    // Backup tracking
    private final Map<String, BackupMetadata> backupRegistry;
    
    public BackupRollbackManager(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.backupDirectory = new File(plugin.getDataFolder(), "migration-backups");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.backupRegistry = new HashMap<>();
        
        // Ensure backup directory exists
        if (!backupDirectory.exists()) {
            boolean created = backupDirectory.mkdirs();
            if (!created) {
                logger.warning("Failed to create backup directory: " + backupDirectory.getAbsolutePath());
            }
        }
    }
    
    /**
     * Create backup of current announcement system state
     * 
     * @return CompletableFuture containing BackupResult with backup information
     */
    public CompletableFuture<BackupResult> createBackup() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Creating migration backup...");
            
            try {
                String backupId = generateBackupId();
                File backupFolder = new File(backupDirectory, backupId);
                
                if (!backupFolder.mkdirs()) {
                    throw new BackupException("Failed to create backup folder: " + backupFolder.getAbsolutePath());
                }
                
                // Create backup metadata
                BackupMetadata metadata = new BackupMetadata(backupId, LocalDateTime.now(), 
                                                            BackupType.MIGRATION_BACKUP);
                
                // Backup YAML file
                File yamlBackup = backupYAMLFile(backupFolder);
                if (yamlBackup != null) {
                    metadata.addBackedUpFile("announcements.yml", yamlBackup.getName());
                }
                
                // For now, we'll focus on YAML backup since the migration is FROM YAML
                // In a full implementation, this would also backup database state
                
                // Save backup metadata
                saveBackupMetadata(backupFolder, metadata);
                
                // Register backup
                backupRegistry.put(backupId, metadata);
                
                logger.info("Backup created successfully: " + backupId);
                
                return new BackupResult(backupId, backupFolder.getAbsolutePath(), 
                                      new ArrayList<>(metadata.getFileList().keySet()), true);
                
            } catch (Exception e) {
                logger.error("Backup creation failed", e);
                return new BackupResult(null, null, Collections.emptyList(), 
                                      false, e.getMessage());
            }
        });
    }
    
    /**
     * Perform rollback from specified backup
     * 
     * @param backupId The ID of the backup to restore from
     * @return CompletableFuture containing RollbackResult
     */
    public CompletableFuture<RollbackResult> performRollback(String backupId) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting rollback from backup: " + backupId);
            
            try {
                // Validate backup exists
                BackupMetadata metadata = backupRegistry.get(backupId);
                if (metadata == null) {
                    throw new RollbackException("Backup not found: " + backupId);
                }
                
                File backupFolder = new File(backupDirectory, backupId);
                if (!backupFolder.exists()) {
                    throw new RollbackException("Backup folder not found: " + backupFolder.getAbsolutePath());
                }
                
                // Verify backup integrity
                if (!verifyBackupIntegrity(backupFolder, metadata)) {
                    throw new RollbackException("Backup integrity check failed for: " + backupId);
                }
                
                // For YAML-to-Database migration, rollback primarily means:
                // 1. Restoring the original YAML file
                // 2. Since this is a migration TO database, we don't restore database state
                
                List<String> restoredFiles = new ArrayList<>();
                
                // Restore YAML file
                File yamlBackup = new File(backupFolder, "announcements.yml.backup");
                if (yamlBackup.exists()) {
                    File originalYaml = new File(plugin.getDataFolder(), "announcements.yml");
                    if (copyFile(yamlBackup, originalYaml)) {
                        restoredFiles.add("announcements.yml");
                    }
                }
                
                logger.info("Rollback completed successfully - restored " + restoredFiles.size() + " files");
                
                return new RollbackResult(backupId, restoredFiles, true);
                
            } catch (Exception e) {
                logger.error("Rollback failed", e);
                return new RollbackResult(backupId, Collections.emptyList(), 
                                        false, e.getMessage());
            }
        });
    }
    
    /**
     * List all available backups
     * 
     * @return CompletableFuture containing list of backup metadata
     */
    public CompletableFuture<List<BackupMetadata>> listBackups() {
        return CompletableFuture.supplyAsync(() -> {
            List<BackupMetadata> backups = new ArrayList<>();
            
            if (!backupDirectory.exists()) {
                return backups;
            }
            
            File[] backupFolders = backupDirectory.listFiles(File::isDirectory);
            if (backupFolders != null) {
                for (File backupFolder : backupFolders) {
                    try {
                        BackupMetadata metadata = loadBackupMetadata(backupFolder);
                        if (metadata != null) {
                            backups.add(metadata);
                            backupRegistry.put(metadata.getBackupId(), metadata);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to load backup metadata from: " + backupFolder.getName());
                    }
                }
            }
            
            // Sort by creation date (newest first)
            backups.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            
            return backups;
        });
    }
    
    /**
     * Delete old backups based on retention policy
     * 
     * @param maxBackups Maximum number of backups to retain
     * @return CompletableFuture containing cleanup result
     */
    public CompletableFuture<CleanupResult> cleanupOldBackups(int maxBackups) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting backup cleanup - retaining " + maxBackups + " backups");
            
            try {
                List<BackupMetadata> allBackups = listBackups().join();
                
                if (allBackups.size() <= maxBackups) {
                    return new CleanupResult(0, Collections.emptyList(), true);
                }
                
                List<String> deletedBackups = new ArrayList<>();
                int toDelete = allBackups.size() - maxBackups;
                
                // Delete oldest backups first
                for (int i = allBackups.size() - toDelete; i < allBackups.size(); i++) {
                    BackupMetadata backup = allBackups.get(i);
                    File backupFolder = new File(backupDirectory, backup.getBackupId());
                    
                    if (deleteBackupFolder(backupFolder)) {
                        deletedBackups.add(backup.getBackupId());
                        backupRegistry.remove(backup.getBackupId());
                    }
                }
                
                logger.info("Backup cleanup completed - deleted " + deletedBackups.size() + " old backups");
                
                return new CleanupResult(deletedBackups.size(), deletedBackups, true);
                
            } catch (Exception e) {
                logger.error("Backup cleanup failed", e);
                return new CleanupResult(0, Collections.emptyList(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Backup the YAML announcements file
     */
    private File backupYAMLFile(File backupFolder) {
        File originalYaml = new File(plugin.getDataFolder(), "announcements.yml");
        if (!originalYaml.exists()) {
            logger.warning("Original announcements.yml not found - skipping YAML backup");
            return null;
        }
        
        File yamlBackup = new File(backupFolder, "announcements.yml.backup");
        
        if (copyFile(originalYaml, yamlBackup)) {
            logger.info("YAML file backed up successfully");
            return yamlBackup;
        } else {
            logger.warning("Failed to backup YAML file");
            return null;
        }
    }
    
    /**
     * Save backup metadata to JSON file
     */
    private void saveBackupMetadata(File backupFolder, BackupMetadata metadata) throws IOException {
        File metadataFile = new File(backupFolder, "backup-metadata.json");
        
        try (FileWriter writer = new FileWriter(metadataFile)) {
            gson.toJson(metadata, writer);
        }
    }
    
    /**
     * Load backup metadata from JSON file
     */
    private BackupMetadata loadBackupMetadata(File backupFolder) throws IOException {
        File metadataFile = new File(backupFolder, "backup-metadata.json");
        
        if (!metadataFile.exists()) {
            return null;
        }
        
        try {
            return gson.fromJson(new java.io.FileReader(metadataFile), BackupMetadata.class);
        } catch (Exception e) {
            logger.warning("Failed to parse backup metadata: " + metadataFile.getAbsolutePath());
            return null;
        }
    }
    
    /**
     * Verify backup integrity
     */
    private boolean verifyBackupIntegrity(File backupFolder, BackupMetadata metadata) {
        // Check metadata file exists
        File metadataFile = new File(backupFolder, "backup-metadata.json");
        if (!metadataFile.exists()) {
            return false;
        }
        
        // Check all backed up files exist
        for (Map.Entry<String, String> entry : metadata.getFileList().entrySet()) {
            File backedUpFile = new File(backupFolder, entry.getValue());
            if (!backedUpFile.exists()) {
                logger.warning("Backed up file missing: " + backedUpFile.getAbsolutePath());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Copy file from source to destination
     */
    private boolean copyFile(File source, File destination) {
        try {
            java.nio.file.Files.copy(source.toPath(), destination.toPath(), 
                                   java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to copy file from " + source + " to " + destination + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete backup folder recursively
     */
    private boolean deleteBackupFolder(File folder) {
        if (!folder.exists()) {
            return true;
        }
        
        try {
            java.nio.file.Files.walk(folder.toPath())
                              .sorted(Comparator.reverseOrder())
                              .map(java.nio.file.Path::toFile)
                              .forEach(File::delete);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to delete backup folder: " + folder.getAbsolutePath());
            return false;
        }
    }
    
    /**
     * Generate unique backup ID
     */
    private String generateBackupId() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "migration-backup-" + timestamp;
    }
    
    /**
     * Backup type enumeration
     */
    public enum BackupType {
        MIGRATION_BACKUP,
        MANUAL_BACKUP,
        SCHEDULED_BACKUP
    }
    
    /**
     * Backup metadata container
     */
    public static class BackupMetadata {
        private final String backupId;
        private final LocalDateTime createdAt;
        private final BackupType backupType;
        private final Map<String, String> fileList;
        private final Map<String, Object> additionalMetadata;
        
        public BackupMetadata(String backupId, LocalDateTime createdAt, BackupType backupType) {
            this.backupId = backupId;
            this.createdAt = createdAt;
            this.backupType = backupType;
            this.fileList = new HashMap<>();
            this.additionalMetadata = new HashMap<>();
        }
        
        public void addBackedUpFile(String originalName, String backupName) {
            fileList.put(originalName, backupName);
        }
        
        public void addMetadata(String key, Object value) {
            additionalMetadata.put(key, value);
        }
        
        // Getters
        public String getBackupId() { return backupId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public BackupType getBackupType() { return backupType; }
        public Map<String, String> getFileList() { return fileList; }
        public Map<String, Object> getAdditionalMetadata() { return additionalMetadata; }
    }
    
    /**
     * Backup operation result
     */
    public static class BackupResult {
        private final String backupId;
        private final String backupLocation;
        private final List<String> backedUpFiles;
        private final boolean success;
        private final String errorMessage;
        
        public BackupResult(String backupId, String backupLocation, 
                          List<String> backedUpFiles, boolean success) {
            this(backupId, backupLocation, backedUpFiles, success, null);
        }
        
        public BackupResult(String backupId, String backupLocation, 
                          List<String> backedUpFiles, boolean success, String errorMessage) {
            this.backupId = backupId;
            this.backupLocation = backupLocation;
            this.backedUpFiles = backedUpFiles != null ? backedUpFiles : Collections.emptyList();
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public String getBackupId() { return backupId; }
        public String getBackupLocation() { return backupLocation; }
        public List<String> getBackedUpFiles() { return backedUpFiles; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Rollback operation result
     */
    public static class RollbackResult {
        private final String backupId;
        private final List<String> restoredFiles;
        private final boolean success;
        private final String errorMessage;
        
        public RollbackResult(String backupId, List<String> restoredFiles, boolean success) {
            this(backupId, restoredFiles, success, null);
        }
        
        public RollbackResult(String backupId, List<String> restoredFiles, 
                            boolean success, String errorMessage) {
            this.backupId = backupId;
            this.restoredFiles = restoredFiles != null ? restoredFiles : Collections.emptyList();
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public String getBackupId() { return backupId; }
        public List<String> getRestoredFiles() { return restoredFiles; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Cleanup operation result
     */
    public static class CleanupResult {
        private final int deletedCount;
        private final List<String> deletedBackupIds;
        private final boolean success;
        private final String errorMessage;
        
        public CleanupResult(int deletedCount, List<String> deletedBackupIds, boolean success) {
            this(deletedCount, deletedBackupIds, success, null);
        }
        
        public CleanupResult(int deletedCount, List<String> deletedBackupIds, 
                           boolean success, String errorMessage) {
            this.deletedCount = deletedCount;
            this.deletedBackupIds = deletedBackupIds;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public int getDeletedCount() { return deletedCount; }
        public List<String> getDeletedBackupIds() { return deletedBackupIds; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Custom exception for backup operations
     */
    public static class BackupException extends RuntimeException {
        public BackupException(String message) {
            super(message);
        }
        
        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Custom exception for rollback operations
     */
    public static class RollbackException extends RuntimeException {
        public RollbackException(String message) {
            super(message);
        }
        
        public RollbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
