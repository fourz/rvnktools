# Implementation Guide: YAML Migration Framework

**Guide ID**: 04-yaml-migration-guide  
**Feature Reference**: 04-yaml-migration.md  
**Implementation Phase**: Week 1-2  
**Prerequisites**: Service layer implementation, Database schema setup

## Implementation Overview

This guide provides step-by-step instructions for implementing the YAML migration framework that converts existing RVNKTools announcement data from YAML files to the RVNKCore database system.

## Project Structure Setup

### 1. Create Migration Package Structure

```text
src/main/java/org/fourz/rvnkcore/
└── migration/
    ├── YamlMigrationService.java
    ├── YamlDataReader.java
    ├── MigrationValidator.java
    ├── BackupManager.java
    ├── dto/
    │   ├── YamlAnnouncementData.java
    │   ├── YamlTypeData.java
    │   └── MigrationReport.java
    └── converter/
        ├── AnnouncementConverter.java
        ├── TypeConverter.java
        └── PlayerPreferenceConverter.java
```

### 2. Add Migration Dependencies

Add to `pom.xml`:

```xml
<dependencies>
    <!-- YAML Processing -->
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
        <version>2.0</version>
    </dependency>
    
    <!-- File Operations -->
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.11.0</version>
    </dependency>
    
    <!-- Date/Time Utilities -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
        <version>3.12.0</version>
    </dependency>
</dependencies>
```

## Step 1: YAML Data Reader Implementation

### YamlDataReader.java

```java
package org.fourz.rvnkcore.migration;

import org.fourz.rvnkcore.migration.dto.YamlAnnouncementData;
import org.fourz.rvnkcore.migration.dto.YamlTypeData;
import org.fourz.rvnkcore.util.log.LogManager;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads and parses YAML configuration files from RVNKTools.
 */
@Singleton
public class YamlDataReader {
    
    private final LogManager logger;
    private final Yaml yaml;
    
    @Inject
    public YamlDataReader(LogManager logger) {
        this.logger = logger;
        this.yaml = new Yaml(new Constructor(Map.class));
    }
    
    /**
     * Reads announcements from YAML configuration file.
     */
    public List<YamlAnnouncementData> readAnnouncements(Path configPath) throws IOException {
        logger.info("Reading announcements from: " + configPath);
        
        File configFile = configPath.toFile();
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configPath);
        }
        
        List<YamlAnnouncementData> announcements = new ArrayList<>();
        
        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config == null) {
                logger.warning("Empty configuration file: " + configPath);
                return announcements;
            }
            
            // Parse announcements section
            Object announcementsSection = config.get("announcements");
            if (announcementsSection instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> announcementMap = (Map<String, Object>) announcementsSection;
                
                for (Map.Entry<String, Object> entry : announcementMap.entrySet()) {
                    try {
                        YamlAnnouncementData announcement = parseAnnouncement(entry.getKey(), entry.getValue());
                        if (announcement != null) {
                            announcements.add(announcement);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to parse announcement: " + entry.getKey(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to read YAML configuration", e);
            throw new IOException("Failed to read YAML configuration: " + e.getMessage(), e);
        }
        
        logger.info("Successfully read " + announcements.size() + " announcements");
        return announcements;
    }
    
    /**
     * Reads announcement types from YAML configuration file.
     */
    public List<YamlTypeData> readAnnouncementTypes(Path configPath) throws IOException {
        logger.info("Reading announcement types from: " + configPath);
        
        List<YamlTypeData> types = new ArrayList<>();
        
        try (FileInputStream inputStream = new FileInputStream(configPath.toFile())) {
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config == null) {
                return types;
            }
            
            // Parse announcement types section
            Object typesSection = config.get("announcement-types");
            if (typesSection instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typeMap = (Map<String, Object>) typesSection;
                
                for (Map.Entry<String, Object> entry : typeMap.entrySet()) {
                    try {
                        YamlTypeData type = parseAnnouncementType(entry.getKey(), entry.getValue());
                        if (type != null) {
                            types.add(type);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to parse announcement type: " + entry.getKey(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to read YAML types configuration", e);
            throw new IOException("Failed to read YAML types: " + e.getMessage(), e);
        }
        
        logger.info("Successfully read " + types.size() + " announcement types");
        return types;
    }
    
    /**
     * Reads player disabled types from YAML file.
     */
    public Map<UUID, Set<String>> readPlayerDisabledTypes(Path configPath) throws IOException {
        logger.info("Reading player disabled types from: " + configPath);
        
        Map<UUID, Set<String>> disabledTypes = new HashMap<>();
        
        try (FileInputStream inputStream = new FileInputStream(configPath.toFile())) {
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config == null) {
                return disabledTypes;
            }
            
            // Parse player preferences section
            Object playersSection = config.get("player-disabled-types");
            if (playersSection instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> playerMap = (Map<String, Object>) playersSection;
                
                for (Map.Entry<String, Object> entry : playerMap.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        Set<String> disabled = parseDisabledTypes(entry.getValue());
                        if (!disabled.isEmpty()) {
                            disabledTypes.put(playerId, disabled);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to parse player disabled types: " + entry.getKey(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to read player disabled types", e);
            throw new IOException("Failed to read player disabled types: " + e.getMessage(), e);
        }
        
        logger.info("Successfully read disabled types for " + disabledTypes.size() + " players");
        return disabledTypes;
    }
    
    /**
     * Discovers YAML configuration files in the plugin directory.
     */
    public List<Path> discoverConfigFiles(Path pluginDirectory) {
        List<Path> configFiles = new ArrayList<>();
        
        try {
            // Common RVNKTools configuration file patterns
            String[] configPatterns = {
                "announcements.yml",
                "announce.yml",
                "config.yml",
                "announcement-config.yml"
            };
            
            for (String pattern : configPatterns) {
                Path configPath = pluginDirectory.resolve(pattern);
                if (Files.exists(configPath)) {
                    configFiles.add(configPath);
                    logger.info("Found configuration file: " + configPath);
                }
            }
            
            // Also check subdirectories
            Path configDir = pluginDirectory.resolve("config");
            if (Files.exists(configDir) && Files.isDirectory(configDir)) {
                Files.walk(configDir)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .forEach(path -> {
                        configFiles.add(path);
                        logger.info("Found configuration file: " + path);
                    });
            }
            
        } catch (Exception e) {
            logger.warning("Error discovering configuration files", e);
        }
        
        return configFiles;
    }
    
    // === Private Helper Methods ===
    
    private YamlAnnouncementData parseAnnouncement(String id, Object data) {
        if (!(data instanceof Map)) {
            logger.warning("Invalid announcement data format for ID: " + id);
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> announcementMap = (Map<String, Object>) data;
        
        YamlAnnouncementData announcement = new YamlAnnouncementData();
        announcement.setId(id);
        announcement.setMessage(getString(announcementMap, "message", ""));
        announcement.setType(getString(announcementMap, "type", "info"));
        announcement.setActive(getBoolean(announcementMap, "active", true));
        announcement.setWorld(getString(announcementMap, "world", null));
        announcement.setPermission(getString(announcementMap, "permission", null));
        announcement.setDisplayDurationSeconds(getInteger(announcementMap, "displayDurationSeconds", 5));
        announcement.setPriority(getInteger(announcementMap, "priority", 0));
        announcement.setOwner(getString(announcementMap, "owner", "system"));
        announcement.setRecurrence(getString(announcementMap, "recurrence", null));
        announcement.setRecurrenceString(getString(announcementMap, "recurrenceString", null));
        announcement.setImported(getBoolean(announcementMap, "imported", false));
        
        // Parse creation time
        String createdAtString = getString(announcementMap, "createdAt", null);
        if (createdAtString != null) {
            try {
                announcement.setCreatedAt(parseTimestamp(createdAtString));
            } catch (Exception e) {
                logger.warning("Failed to parse createdAt for announcement: " + id, e);
                announcement.setCreatedAt(new Date());
            }
        } else {
            announcement.setCreatedAt(new Date());
        }
        
        return announcement;
    }
    
    private YamlTypeData parseAnnouncementType(String id, Object data) {
        if (!(data instanceof Map)) {
            logger.warning("Invalid announcement type data format for ID: " + id);
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> typeMap = (Map<String, Object>) data;
        
        YamlTypeData type = new YamlTypeData();
        type.setId(id);
        type.setDisplayName(getString(typeMap, "displayName", id));
        type.setDescription(getString(typeMap, "description", ""));
        type.setPriority(getInteger(typeMap, "priority", 0));
        type.setColor(getString(typeMap, "color", "&f"));
        type.setEnabled(getBoolean(typeMap, "enabled", true));
        
        return type;
    }
    
    private Set<String> parseDisabledTypes(Object data) {
        Set<String> disabledTypes = new HashSet<>();
        
        if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) data;
            for (Object item : list) {
                if (item != null) {
                    disabledTypes.add(item.toString());
                }
            }
        } else if (data instanceof String) {
            // Single type as string
            disabledTypes.add((String) data);
        }
        
        return disabledTypes;
    }
    
    private Date parseTimestamp(String timestamp) {
        // Try different timestamp formats
        try {
            // ISO format
            return Date.from(java.time.Instant.parse(timestamp));
        } catch (Exception e1) {
            try {
                // Unix timestamp (milliseconds)
                long millis = Long.parseLong(timestamp);
                return new Date(millis);
            } catch (Exception e2) {
                try {
                    // Unix timestamp (seconds)
                    long seconds = Long.parseLong(timestamp);
                    return new Date(seconds * 1000);
                } catch (Exception e3) {
                    throw new IllegalArgumentException("Unable to parse timestamp: " + timestamp);
                }
            }
        }
    }
    
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    private int getInteger(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid integer value for key '" + key + "': " + value);
            }
        }
        return defaultValue;
    }
}
```

## Step 2: Migration Service Implementation

### YamlMigrationService.java

```java
package org.fourz.rvnkcore.migration;

import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.ScheduleService;
import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.api.dto.AnnouncementTypeDTO;
import org.fourz.rvnkcore.migration.converter.AnnouncementConverter;
import org.fourz.rvnkcore.migration.converter.TypeConverter;
import org.fourz.rvnkcore.migration.dto.*;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main service for migrating YAML-based announcement data to RVNKCore database.
 */
@Singleton
public class YamlMigrationService {
    
    private final YamlDataReader yamlReader;
    private final MigrationValidator validator;
    private final BackupManager backupManager;
    private final AnnouncementConverter announcementConverter;
    private final TypeConverter typeConverter;
    private final AnnouncementService announcementService;
    private final ScheduleService scheduleService;
    private final LogManager logger;
    
    @Inject
    public YamlMigrationService(YamlDataReader yamlReader,
                               MigrationValidator validator,
                               BackupManager backupManager,
                               AnnouncementConverter announcementConverter,
                               TypeConverter typeConverter,
                               AnnouncementService announcementService,
                               ScheduleService scheduleService,
                               LogManager logger) {
        this.yamlReader = yamlReader;
        this.validator = validator;
        this.backupManager = backupManager;
        this.announcementConverter = announcementConverter;
        this.typeConverter = typeConverter;
        this.announcementService = announcementService;
        this.scheduleService = scheduleService;
        this.logger = logger;
    }
    
    /**
     * Performs complete migration from YAML to RVNKCore database.
     */
    public CompletableFuture<MigrationReport> performMigration(Path pluginDirectory, 
                                                              MigrationOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting YAML migration from: " + pluginDirectory);
                
                MigrationReport report = new MigrationReport();
                report.setStartTime(new Date());
                report.setSourceDirectory(pluginDirectory.toString());
                
                // Step 1: Discover configuration files
                List<Path> configFiles = yamlReader.discoverConfigFiles(pluginDirectory);
                if (configFiles.isEmpty()) {
                    throw new RuntimeException("No YAML configuration files found in: " + pluginDirectory);
                }
                
                report.setConfigurationFilesFound(configFiles.size());
                
                // Step 2: Create backup if requested
                if (options.isCreateBackup()) {
                    logger.info("Creating backup before migration...");
                    Path backupPath = backupManager.createBackup(pluginDirectory);
                    report.setBackupPath(backupPath.toString());
                    logger.info("Backup created at: " + backupPath);
                }
                
                // Step 3: Read YAML data
                logger.info("Reading YAML data from configuration files...");
                YamlData yamlData = readYamlData(configFiles);
                
                report.setYamlAnnouncementsFound(yamlData.getAnnouncements().size());
                report.setYamlTypesFound(yamlData.getTypes().size());
                report.setPlayerPreferencesFound(yamlData.getPlayerDisabledTypes().size());
                
                // Step 4: Validate YAML data
                logger.info("Validating YAML data...");
                List<String> validationErrors = validator.validateYamlData(yamlData);
                
                if (!validationErrors.isEmpty() && !options.isIgnoreValidationErrors()) {
                    report.setValidationErrors(validationErrors);
                    report.setSuccess(false);
                    report.setErrorMessage("Validation failed: " + String.join("; ", validationErrors));
                    return report;
                }
                
                report.setValidationErrors(validationErrors);
                
                // Step 5: Convert and migrate data
                logger.info("Converting and migrating data...");
                MigrationResult migrationResult = migrateData(yamlData, options);
                
                // Step 6: Update report with results
                report.setAnnouncementsMigrated(migrationResult.getAnnouncementsMigrated());
                report.setTypesMigrated(migrationResult.getTypesMigrated());
                report.setPlayerPreferencesMigrated(migrationResult.getPlayerPreferencesMigrated());
                report.setMigrationWarnings(migrationResult.getWarnings());
                
                // Step 7: Post-migration validation
                if (options.isValidateAfterMigration()) {
                    logger.info("Performing post-migration validation...");
                    List<String> postValidationErrors = performPostMigrationValidation(yamlData);
                    report.setPostMigrationValidationErrors(postValidationErrors);
                }
                
                report.setEndTime(new Date());
                report.setSuccess(migrationResult.isSuccess());
                
                if (report.isSuccess()) {
                    logger.info("Migration completed successfully - migrated " + 
                               report.getAnnouncementsMigrated() + " announcements and " +
                               report.getTypesMigrated() + " types");
                } else {
                    logger.error("Migration failed: " + report.getErrorMessage());
                }
                
                return report;
                
            } catch (Exception e) {
                logger.error("Migration failed with exception", e);
                
                MigrationReport errorReport = new MigrationReport();
                errorReport.setStartTime(new Date());
                errorReport.setEndTime(new Date());
                errorReport.setSuccess(false);
                errorReport.setErrorMessage("Migration failed: " + e.getMessage());
                
                return errorReport;
            }
        });
    }
    
    /**
     * Performs dry run migration to preview changes without applying them.
     */
    public CompletableFuture<MigrationReport> performDryRun(Path pluginDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Performing dry run migration from: " + pluginDirectory);
                
                MigrationReport report = new MigrationReport();
                report.setStartTime(new Date());
                report.setSourceDirectory(pluginDirectory.toString());
                report.setDryRun(true);
                
                // Read and validate data only
                List<Path> configFiles = yamlReader.discoverConfigFiles(pluginDirectory);
                YamlData yamlData = readYamlData(configFiles);
                List<String> validationErrors = validator.validateYamlData(yamlData);
                
                // Populate report
                report.setConfigurationFilesFound(configFiles.size());
                report.setYamlAnnouncementsFound(yamlData.getAnnouncements().size());
                report.setYamlTypesFound(yamlData.getTypes().size());
                report.setPlayerPreferencesFound(yamlData.getPlayerDisabledTypes().size());
                report.setValidationErrors(validationErrors);
                
                // Preview conversions
                List<String> conversionWarnings = new ArrayList<>();
                for (YamlAnnouncementData yamlAnnouncement : yamlData.getAnnouncements()) {
                    try {
                        announcementConverter.convert(yamlAnnouncement);
                    } catch (Exception e) {
                        conversionWarnings.add("Announcement '" + yamlAnnouncement.getId() + "': " + e.getMessage());
                    }
                }
                
                report.setMigrationWarnings(conversionWarnings);
                report.setSuccess(validationErrors.isEmpty());
                report.setEndTime(new Date());
                
                logger.info("Dry run completed - found " + yamlData.getAnnouncements().size() + 
                           " announcements and " + yamlData.getTypes().size() + " types");
                
                return report;
                
            } catch (Exception e) {
                logger.error("Dry run failed", e);
                
                MigrationReport errorReport = new MigrationReport();
                errorReport.setSuccess(false);
                errorReport.setErrorMessage("Dry run failed: " + e.getMessage());
                errorReport.setDryRun(true);
                
                return errorReport;
            }
        });
    }
    
    /**
     * Rolls back a migration by restoring from backup.
     */
    public CompletableFuture<Boolean> rollbackMigration(String backupId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Rolling back migration with backup ID: " + backupId);
                
                // Clear current database data
                clearDatabaseData();
                
                // Restore from backup
                boolean restored = backupManager.restoreFromBackup(backupId);
                
                if (restored) {
                    logger.info("Migration rollback completed successfully");
                } else {
                    logger.error("Failed to restore from backup: " + backupId);
                }
                
                return restored;
                
            } catch (Exception e) {
                logger.error("Rollback failed", e);
                return false;
            }
        });
    }
    
    // === Private Helper Methods ===
    
    private YamlData readYamlData(List<Path> configFiles) throws IOException {
        List<YamlAnnouncementData> allAnnouncements = new ArrayList<>();
        List<YamlTypeData> allTypes = new ArrayList<>();
        Map<UUID, Set<String>> allPlayerDisabledTypes = new HashMap<>();
        
        for (Path configFile : configFiles) {
            try {
                // Read announcements
                List<YamlAnnouncementData> announcements = yamlReader.readAnnouncements(configFile);
                allAnnouncements.addAll(announcements);
                
                // Read types
                List<YamlTypeData> types = yamlReader.readAnnouncementTypes(configFile);
                allTypes.addAll(types);
                
                // Read player preferences
                Map<UUID, Set<String>> playerPrefs = yamlReader.readPlayerDisabledTypes(configFile);
                for (Map.Entry<UUID, Set<String>> entry : playerPrefs.entrySet()) {
                    allPlayerDisabledTypes.merge(entry.getKey(), entry.getValue(), 
                        (existing, newSet) -> {
                            existing.addAll(newSet);
                            return existing;
                        });
                }
                
            } catch (Exception e) {
                logger.warning("Failed to read configuration file: " + configFile, e);
            }
        }
        
        // Remove duplicates
        Map<String, YamlAnnouncementData> uniqueAnnouncements = new HashMap<>();
        for (YamlAnnouncementData announcement : allAnnouncements) {
            uniqueAnnouncements.put(announcement.getId(), announcement);
        }
        
        Map<String, YamlTypeData> uniqueTypes = new HashMap<>();
        for (YamlTypeData type : allTypes) {
            uniqueTypes.put(type.getId(), type);
        }
        
        YamlData yamlData = new YamlData();
        yamlData.setAnnouncements(new ArrayList<>(uniqueAnnouncements.values()));
        yamlData.setTypes(new ArrayList<>(uniqueTypes.values()));
        yamlData.setPlayerDisabledTypes(allPlayerDisabledTypes);
        
        return yamlData;
    }
    
    private MigrationResult migrateData(YamlData yamlData, MigrationOptions options) {
        MigrationResult result = new MigrationResult();
        AtomicInteger announcementCount = new AtomicInteger(0);
        AtomicInteger typeCount = new AtomicInteger(0);
        AtomicInteger playerPrefCount = new AtomicInteger(0);
        List<String> warnings = new ArrayList<>();
        
        try {
            // Migrate announcement types first (referenced by announcements)
            logger.info("Migrating " + yamlData.getTypes().size() + " announcement types...");
            
            for (YamlTypeData yamlType : yamlData.getTypes()) {
                try {
                    AnnouncementTypeDTO typeDto = typeConverter.convert(yamlType);
                    announcementService.createAnnouncementType(typeDto).join();
                    typeCount.incrementAndGet();
                    
                } catch (Exception e) {
                    String warning = "Failed to migrate type '" + yamlType.getId() + "': " + e.getMessage();
                    warnings.add(warning);
                    logger.warning(warning, e);
                    
                    if (!options.isContinueOnError()) {
                        throw e;
                    }
                }
            }
            
            // Migrate announcements
            logger.info("Migrating " + yamlData.getAnnouncements().size() + " announcements...");
            
            for (YamlAnnouncementData yamlAnnouncement : yamlData.getAnnouncements()) {
                try {
                    AnnouncementDTO announcementDto = announcementConverter.convert(yamlAnnouncement);
                    announcementService.createAnnouncement(announcementDto).join();
                    announcementCount.incrementAndGet();
                    
                } catch (Exception e) {
                    String warning = "Failed to migrate announcement '" + yamlAnnouncement.getId() + "': " + e.getMessage();
                    warnings.add(warning);
                    logger.warning(warning, e);
                    
                    if (!options.isContinueOnError()) {
                        throw e;
                    }
                }
            }
            
            // Migrate player preferences
            logger.info("Migrating player preferences for " + yamlData.getPlayerDisabledTypes().size() + " players...");
            
            for (Map.Entry<UUID, Set<String>> entry : yamlData.getPlayerDisabledTypes().entrySet()) {
                try {
                    UUID playerId = entry.getKey();
                    for (String disabledType : entry.getValue()) {
                        announcementService.addPlayerDisabledType(playerId, disabledType).join();
                    }
                    playerPrefCount.incrementAndGet();
                    
                } catch (Exception e) {
                    String warning = "Failed to migrate preferences for player '" + entry.getKey() + "': " + e.getMessage();
                    warnings.add(warning);
                    logger.warning(warning, e);
                    
                    if (!options.isContinueOnError()) {
                        throw e;
                    }
                }
            }
            
            result.setSuccess(true);
            result.setAnnouncementsMigrated(announcementCount.get());
            result.setTypesMigrated(typeCount.get());
            result.setPlayerPreferencesMigrated(playerPrefCount.get());
            result.setWarnings(warnings);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Migration failed: " + e.getMessage());
            result.setWarnings(warnings);
        }
        
        return result;
    }
    
    private List<String> performPostMigrationValidation(YamlData yamlData) {
        List<String> errors = new ArrayList<>();
        
        try {
            // Verify announcement count
            List<AnnouncementDTO> migratedAnnouncements = announcementService.getAllAnnouncements().join();
            if (migratedAnnouncements.size() < yamlData.getAnnouncements().size()) {
                errors.add("Migrated announcement count (" + migratedAnnouncements.size() + 
                          ") is less than source count (" + yamlData.getAnnouncements().size() + ")");
            }
            
            // Verify type count
            List<AnnouncementTypeDTO> migratedTypes = announcementService.getAllAnnouncementTypes().join();
            if (migratedTypes.size() < yamlData.getTypes().size()) {
                errors.add("Migrated type count (" + migratedTypes.size() + 
                          ") is less than source count (" + yamlData.getTypes().size() + ")");
            }
            
            // Verify specific announcements exist
            for (YamlAnnouncementData yamlAnnouncement : yamlData.getAnnouncements()) {
                AnnouncementDTO migrated = announcementService.getAnnouncement(yamlAnnouncement.getId()).join();
                if (migrated == null) {
                    errors.add("Announcement '" + yamlAnnouncement.getId() + "' was not migrated");
                } else {
                    // Verify content matches
                    if (!Objects.equals(migrated.getMessage(), yamlAnnouncement.getMessage())) {
                        errors.add("Message mismatch for announcement '" + yamlAnnouncement.getId() + "'");
                    }
                }
            }
            
        } catch (Exception e) {
            errors.add("Post-migration validation failed: " + e.getMessage());
        }
        
        return errors;
    }
    
    private void clearDatabaseData() {
        try {
            // This would require implementing a clear/reset method in the services
            logger.warning("Database clearing not implemented - manual cleanup required");
        } catch (Exception e) {
            logger.error("Failed to clear database data", e);
        }
    }
}
```

## Step 3: Data Converter Implementation

### AnnouncementConverter.java

```java
package org.fourz.rvnkcore.migration.converter;

import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.migration.dto.YamlAnnouncementData;

import javax.inject.Singleton;
import java.sql.Timestamp;

/**
 * Converts YAML announcement data to RVNKCore DTOs.
 */
@Singleton
public class AnnouncementConverter {
    
    /**
     * Converts YamlAnnouncementData to AnnouncementDTO.
     */
    public AnnouncementDTO convert(YamlAnnouncementData yamlData) {
        if (yamlData == null) {
            throw new IllegalArgumentException("YAML announcement data cannot be null");
        }
        
        return AnnouncementDTO.builder()
            .id(yamlData.getId())
            .message(convertMessage(yamlData.getMessage()))
            .type(yamlData.getType())
            .active(yamlData.isActive())
            .world(yamlData.getWorld())
            .permission(yamlData.getPermission())
            .displayDurationSeconds(yamlData.getDisplayDurationSeconds())
            .priority(yamlData.getPriority())
            .createdBy(yamlData.getOwner())
            .recurrence(convertRecurrence(yamlData.getRecurrence()))
            .recurrenceString(yamlData.getRecurrenceString())
            .createdAt(yamlData.getCreatedAt() != null ? 
                      Timestamp.from(yamlData.getCreatedAt().toInstant()) : 
                      Timestamp.from(java.time.Instant.now()))
            .updatedAt(Timestamp.from(java.time.Instant.now()))
            .build();
    }
    
    private String convertMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Announcement message cannot be empty");
        }
        
        // Convert legacy color codes and formatting
        return message.replace("&", "§")  // Basic color code conversion
            .replace("\\n", "\n")         // Newline conversion
            .trim();
    }
    
    private Integer convertRecurrence(String recurrence) {
        if (recurrence == null || recurrence.trim().isEmpty()) {
            return null;
        }
        
        try {
            return Integer.parseInt(recurrence);
        } catch (NumberFormatException e) {
            // Try to parse time-based recurrence (e.g., "5m", "1h", "30s")
            return parseTimeBasedRecurrence(recurrence);
        }
    }
    
    private Integer parseTimeBasedRecurrence(String recurrence) {
        if (recurrence == null) {
            return null;
        }
        
        String trimmed = recurrence.toLowerCase().trim();
        
        if (trimmed.endsWith("s")) {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
        } else if (trimmed.endsWith("m")) {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 60;
        } else if (trimmed.endsWith("h")) {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 3600;
        } else {
            return Integer.parseInt(trimmed); // Assume seconds
        }
    }
}
```

## Step 4: Migration Validator Implementation

### MigrationValidator.java

```java
package org.fourz.rvnkcore.migration;

import org.fourz.rvnkcore.migration.dto.YamlData;
import org.fourz.rvnkcore.migration.dto.YamlAnnouncementData;
import org.fourz.rvnkcore.migration.dto.YamlTypeData;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates YAML data before migration to ensure data integrity.
 */
@Singleton
public class MigrationValidator {
    
    /**
     * Validates complete YAML data structure.
     */
    public List<String> validateYamlData(YamlData yamlData) {
        List<String> errors = new ArrayList<>();
        
        if (yamlData == null) {
            errors.add("YAML data is null");
            return errors;
        }
        
        // Validate announcements
        errors.addAll(validateAnnouncements(yamlData.getAnnouncements()));
        
        // Validate announcement types
        errors.addAll(validateAnnouncementTypes(yamlData.getTypes()));
        
        // Validate consistency between announcements and types
        errors.addAll(validateAnnouncementTypeConsistency(
            yamlData.getAnnouncements(), yamlData.getTypes()));
        
        return errors;
    }
    
    /**
     * Validates announcement data.
     */
    public List<String> validateAnnouncements(List<YamlAnnouncementData> announcements) {
        List<String> errors = new ArrayList<>();
        
        if (announcements == null) {
            errors.add("Announcements list is null");
            return errors;
        }
        
        Set<String> seenIds = new HashSet<>();
        
        for (YamlAnnouncementData announcement : announcements) {
            if (announcement == null) {
                errors.add("Found null announcement");
                continue;
            }
            
            // Validate ID
            if (announcement.getId() == null || announcement.getId().trim().isEmpty()) {
                errors.add("Announcement has null or empty ID");
            } else if (seenIds.contains(announcement.getId())) {
                errors.add("Duplicate announcement ID: " + announcement.getId());
            } else {
                seenIds.add(announcement.getId());
            }
            
            // Validate message
            if (announcement.getMessage() == null || announcement.getMessage().trim().isEmpty()) {
                errors.add("Announcement '" + announcement.getId() + "' has null or empty message");
            }
            
            // Validate type
            if (announcement.getType() == null || announcement.getType().trim().isEmpty()) {
                errors.add("Announcement '" + announcement.getId() + "' has null or empty type");
            }
            
            // Validate display duration
            if (announcement.getDisplayDurationSeconds() < 0) {
                errors.add("Announcement '" + announcement.getId() + 
                          "' has negative display duration: " + announcement.getDisplayDurationSeconds());
            }
            
            // Validate priority
            if (announcement.getPriority() < 0) {
                errors.add("Announcement '" + announcement.getId() + 
                          "' has negative priority: " + announcement.getPriority());
            }
        }
        
        return errors;
    }
    
    /**
     * Validates announcement type data.
     */
    public List<String> validateAnnouncementTypes(List<YamlTypeData> types) {
        List<String> errors = new ArrayList<>();
        
        if (types == null) {
            errors.add("Announcement types list is null");
            return errors;
        }
        
        Set<String> seenIds = new HashSet<>();
        
        for (YamlTypeData type : types) {
            if (type == null) {
                errors.add("Found null announcement type");
                continue;
            }
            
            // Validate ID
            if (type.getId() == null || type.getId().trim().isEmpty()) {
                errors.add("Announcement type has null or empty ID");
            } else if (seenIds.contains(type.getId())) {
                errors.add("Duplicate announcement type ID: " + type.getId());
            } else {
                seenIds.add(type.getId());
            }
            
            // Validate display name
            if (type.getDisplayName() == null || type.getDisplayName().trim().isEmpty()) {
                errors.add("Announcement type '" + type.getId() + "' has null or empty display name");
            }
            
            // Validate color format
            if (type.getColor() != null && !isValidColorCode(type.getColor())) {
                errors.add("Announcement type '" + type.getId() + 
                          "' has invalid color code: " + type.getColor());
            }
        }
        
        return errors;
    }
    
    /**
     * Validates consistency between announcements and their referenced types.
     */
    public List<String> validateAnnouncementTypeConsistency(List<YamlAnnouncementData> announcements,
                                                            List<YamlTypeData> types) {
        List<String> errors = new ArrayList<>();
        
        if (announcements == null || types == null) {
            return errors; // Skip consistency check if data is null
        }
        
        Set<String> availableTypes = new HashSet<>();
        for (YamlTypeData type : types) {
            if (type != null && type.getId() != null) {
                availableTypes.add(type.getId());
            }
        }
        
        for (YamlAnnouncementData announcement : announcements) {
            if (announcement != null && announcement.getType() != null) {
                if (!availableTypes.contains(announcement.getType())) {
                    errors.add("Announcement '" + announcement.getId() + 
                              "' references unknown type: " + announcement.getType());
                }
            }
        }
        
        return errors;
    }
    
    private boolean isValidColorCode(String color) {
        if (color == null) {
            return true; // null is valid (will use default)
        }
        
        // Validate Minecraft color codes
        if (color.startsWith("&") && color.length() == 2) {
            char code = color.charAt(1);
            return (code >= '0' && code <= '9') || 
                   (code >= 'a' && code <= 'f') ||
                   (code >= 'k' && code <= 'r');
        }
        
        // Validate hex color codes
        if (color.startsWith("#") && color.length() == 7) {
            try {
                Integer.parseInt(color.substring(1), 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return false;
    }
}
```

## Step 5: Integration and Command Implementation

### Migration Command

```java
package org.fourz.rvnktools.commands;

import org.fourz.rvnkcore.migration.YamlMigrationService;
import org.fourz.rvnkcore.migration.dto.MigrationReport;
import org.fourz.rvnkcore.migration.dto.MigrationOptions;
import org.fourz.rvnktools.commands.base.BaseCommand;
import org.fourz.rvnktools.util.ChatFormat;

import java.nio.file.Paths;

/**
 * Command for managing YAML to RVNKCore migration.
 */
public class MigrationCommand extends BaseCommand {
    
    private final YamlMigrationService migrationService;
    
    public MigrationCommand(RVNKTools plugin, YamlMigrationService migrationService) {
        super(plugin);
        this.migrationService = migrationService;
    }
    
    @Override
    public boolean execute(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        switch (args[0].toLowerCase()) {
            case "migrate":
                return handleMigrate(sender, args);
            case "dryrun":
                return handleDryRun(sender, args);
            case "rollback":
                return handleRollback(sender, args);
            case "status":
                return handleStatus(sender);
            default:
                return showHelp(sender);
        }
    }
    
    private boolean handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.format("&c▶ Usage: /migration migrate <plugin-directory> [options]"));
            return true;
        }
        
        String pluginDirectory = args[1];
        MigrationOptions options = parseMigrationOptions(args, 2);
        
        sender.sendMessage(ChatFormat.format("&6⚙ Starting migration from: " + pluginDirectory));
        
        migrationService.performMigration(Paths.get(pluginDirectory), options)
            .thenAccept(report -> {
                if (report.isSuccess()) {
                    sender.sendMessage(ChatFormat.format("&a✓ Migration completed successfully"));
                    sender.sendMessage(ChatFormat.format("&7   Announcements: &e" + report.getAnnouncementsMigrated()));
                    sender.sendMessage(ChatFormat.format("&7   Types: &e" + report.getTypesMigrated()));
                    sender.sendMessage(ChatFormat.format("&7   Player preferences: &e" + report.getPlayerPreferencesMigrated()));
                    
                    if (!report.getMigrationWarnings().isEmpty()) {
                        sender.sendMessage(ChatFormat.format("&e⚠ Warnings: " + report.getMigrationWarnings().size()));
                    }
                } else {
                    sender.sendMessage(ChatFormat.format("&c✖ Migration failed: " + report.getErrorMessage()));
                }
            })
            .exceptionally(throwable -> {
                sender.sendMessage(ChatFormat.format("&c✖ Migration failed with error: " + throwable.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean handleDryRun(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.format("&c▶ Usage: /migration dryrun <plugin-directory>"));
            return true;
        }
        
        String pluginDirectory = args[1];
        sender.sendMessage(ChatFormat.format("&6⚙ Performing dry run for: " + pluginDirectory));
        
        migrationService.performDryRun(Paths.get(pluginDirectory))
            .thenAccept(report -> {
                sender.sendMessage(ChatFormat.format("&6⚙ Dry run results:"));
                sender.sendMessage(ChatFormat.format("&7   Configuration files found: &e" + report.getConfigurationFilesFound()));
                sender.sendMessage(ChatFormat.format("&7   Announcements found: &e" + report.getYamlAnnouncementsFound()));
                sender.sendMessage(ChatFormat.format("&7   Types found: &e" + report.getYamlTypesFound()));
                sender.sendMessage(ChatFormat.format("&7   Player preferences found: &e" + report.getPlayerPreferencesFound()));
                
                if (!report.getValidationErrors().isEmpty()) {
                    sender.sendMessage(ChatFormat.format("&c✖ Validation errors:"));
                    for (String error : report.getValidationErrors()) {
                        sender.sendMessage(ChatFormat.format("&7   • " + error));
                    }
                } else {
                    sender.sendMessage(ChatFormat.format("&a✓ No validation errors found"));
                }
            });
        
        return true;
    }
    
    private MigrationOptions parseMigrationOptions(String[] args, int startIndex) {
        MigrationOptions options = new MigrationOptions();
        
        for (int i = startIndex; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--no-backup":
                    options.setCreateBackup(false);
                    break;
                case "--continue-on-error":
                    options.setContinueOnError(true);
                    break;
                case "--skip-validation":
                    options.setIgnoreValidationErrors(true);
                    break;
                case "--no-post-validation":
                    options.setValidateAfterMigration(false);
                    break;
            }
        }
        
        return options;
    }
}
```

## Validation Checklist

- [ ] YAML reading functionality implemented and tested
- [ ] Data conversion working correctly
- [ ] Validation logic comprehensive
- [ ] Backup and restore functionality working
- [ ] Error handling robust
- [ ] Dry run functionality complete
- [ ] Migration reporting detailed
- [ ] Command integration working
- [ ] Database integration tested
- [ ] Rollback functionality implemented

This implementation guide provides a complete framework for migrating YAML-based announcement data to the RVNKCore database system with comprehensive validation, backup, and error handling capabilities.
