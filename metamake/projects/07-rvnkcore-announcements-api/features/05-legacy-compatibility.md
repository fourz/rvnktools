# Feature Specification: Legacy Compatibility Layer

**Feature ID**: 05-legacy-compatibility  
**Priority**: High  
**Dependencies**: Service Architecture, YAML Migration Framework  
**Implementation Phase**: Week 4

## Overview

This feature provides comprehensive backward compatibility with the existing RVNKTools announcement system during the migration to RVNKCore, ensuring zero downtime and seamless transition for server administrators.

## AnnounceManager Hook Points Analysis

### Current AnnounceManager Method Inventory

**Core Data Access Methods (REQUIRES HOOKS)**:
```java
// These methods need compatibility layer hooks for RVNKCore migration
public class AnnounceManager {
    // HOOK REQUIRED: Data retrieval methods
    public List<Announcement> getAnnouncements()  // → AnnouncementService.getAllAnnouncements()
    public List<Announcement> getAnnouncements(String type)  // → AnnouncementService.getAnnouncementsByType()
    public Announcement getAnnouncement(String id)  // → AnnouncementService.getAnnouncement()
    
    // HOOK REQUIRED: Data modification methods  
    public boolean addAnnouncement(Announcement announcement)  // → AnnouncementService.createAnnouncement()
    public boolean deleteAnnouncement(String id)  // → AnnouncementService.deleteAnnouncement()
    public boolean updateAnnouncement(String id, String newMessage)  // → AnnouncementService.updateAnnouncement()
    
    // HOOK REQUIRED: Configuration management
    public void reloadConfig()  // → AnnouncementService.reloadConfiguration()
    public void saveConfig()  // → AnnouncementService.saveConfiguration()
    
    // HOOK REQUIRED: Player preference management
    public void toggleAnnouncementType(Player player, String type)  // → AnnouncementService.togglePlayerDisabledType()
    public String[] getPlayerDisabledAnnouncementTypes(Player player)  // → AnnouncementService.getPlayerDisabledTypes()
    
    // HOOK REQUIRED: Type management
    public Set<String> getAnnounceTypes()  // → AnnouncementService.getAllAnnouncementTypes()
    public AnnounceType getAnnounceType(String type)  // → AnnouncementService.getAnnouncementType()
    public boolean validateAnnounceType(String type)  // → AnnouncementService.validateAnnouncementType()
    
    // HOOK REQUIRED: Operational methods
    public boolean announcementExists(String id)  // → AnnouncementService.announcementExists()
    public boolean sendAnnouncementNow(CommandSender sender, String id)  // → AnnouncementService.broadcastAnnouncement()
}
```

### Deprecated Methods Migration Plan

**DEPRECATED (Phase Out after Migration):**
```java
// These methods will be deprecated and replaced with RVNKCore equivalents
@Deprecated(since = "2.1.0", forRemoval = true)
public void setAnnouncements(List<Announcement> announcementList) {
    // Migration Path: Use AnnouncementService.batchUpdateAnnouncements()
    logger.warning("setAnnouncements() is deprecated. Use AnnouncementService.batchUpdateAnnouncements()");
}

@Deprecated(since = "2.1.0", forRemoval = true) 
public void setAnnouncementImported(String id) {
    // Migration Path: Use AnnouncementService metadata system
    logger.warning("setAnnouncementImported() is deprecated. Use AnnouncementService.updateMetadata()");
}

@Deprecated(since = "2.1.0", forRemoval = true)
public boolean isAnnouncementImported(String id) {
    // Migration Path: Use AnnouncementService.getMetadata()
    logger.warning("isAnnouncementImported() is deprecated. Use AnnouncementService.getMetadata()");
    return false;
}
```

## Compatibility Architecture

### Dual System Support

```text
                    ┌─────────────────────────────────────┐
                    │         RVNKTools Plugin            │
                    ├─────────────────────────────────────┤
                    │  Legacy Compatibility Layer         │
                    │  ┌─────────────────────────────────┐ │
                    │  │    Migration Detection          │ │
                    │  │  ┌─────────┐    ┌─────────────┐ │ │
                    │  │  │ RVNKCore│    │ MySQL/SQLite│ │ │
                    │  │  │Available│    │  + YAML     │ │ │
                    │  │  │         │    │  Fallback   │ │ │
                    │  │  └─────────┘    └─────────────┘ │ │
                    │  └─────────────────────────────────┘ │
                    │                                     │
                    │  ┌─────────────────────────────────┐ │
                    │  │   AnnounceManager Hook Layer    │ │
                    │  │  ┌─────────┐    ┌─────────────┐ │ │
                    │  │  │RVNKCore │ OR │   YAML      │ │ │
                    │  │  │Service  │    │   Manager   │ │ │
                    │  │  │Methods  │    │   Methods   │ │ │
                    │  │  └─────────┘    └─────────────┘ │ │
                    │  └─────────────────────────────────┘ │
                    └─────────────────────────────────────┘
```

### Integration Strategy

#### Phase 1: Detection and Initialization

- Detect RVNKCore availability on plugin startup
- Test database connectivity (MySQL/SQLite)
- Fall back to YAML system if database unavailable
- Initialize appropriate announcement provider with configuration

#### Phase 2: Transparent Operations

- Route all announcement operations through compatibility layer hooks
- Maintain identical API for existing commands and features
- Preserve all configuration options and behavior patterns
- Handle configuration migration automatically

#### Phase 3: Migration Support

- Provide migration utilities and status reporting capabilities
- Support gradual migration with comprehensive data validation
- Enable rollback to YAML system with full data preservation
- Implement seamless operation switching without downtime

## Implementation Components

### AnnouncementCompatibilityManager

```java
package org.fourz.rvnktools.announceManager.compatibility;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Compatibility layer that provides seamless transition between YAML-based
 * announcement system and RVNKCore service-based system.
 */
public class AnnouncementCompatibilityManager {
    
    private final LogManager logger;
    private final AnnounceManager legacyManager;
    private AnnouncementService rvnkCoreService;
    private AnnouncementProvider activeProvider;
    private CompatibilityMode currentMode;
    
    public enum CompatibilityMode {
        YAML_ONLY,          // RVNKCore not available, use YAML system
        RVNKCORE_AVAILABLE, // RVNKCore available, migration possible
        RVNKCORE_ACTIVE,    // Fully migrated to RVNKCore
        HYBRID              // Both systems active during transition
    }
    
    public AnnouncementCompatibilityManager(AnnounceManager legacyManager, LogManager logger) {
        this.legacyManager = legacyManager;
        this.logger = logger;
        this.currentMode = detectCompatibilityMode();
        this.activeProvider = initializeProvider();
        
        logger.info("Announcement compatibility layer initialized in mode: " + currentMode);
    }
    
    /**
     * Detects the current compatibility mode based on RVNKCore availability
     * and configuration settings.
     */
    private CompatibilityMode detectCompatibilityMode() {
        try {
            RVNKCore rvnkCore = RVNKCore.getInstance();
            if (rvnkCore != null && rvnkCore.isInitialized()) {
                
                // Check if RVNKCore has AnnouncementService
                if (rvnkCore.hasService(AnnouncementService.class)) {
                    rvnkCoreService = rvnkCore.getService(AnnouncementService.class);
                    
                    // Check migration preferences
                    String migrationMode = getMigrationMode();
                    switch (migrationMode.toLowerCase()) {
                        case "active":
                            return CompatibilityMode.RVNKCORE_ACTIVE;
                        case "hybrid":
                            return CompatibilityMode.HYBRID;
                        default:
                            return CompatibilityMode.RVNKCORE_AVAILABLE;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("RVNKCore not available: " + e.getMessage());
        }
        
        return CompatibilityMode.YAML_ONLY;
    }
    
    /**
     * Initializes the appropriate announcement provider based on compatibility mode.
     */
    private AnnouncementProvider initializeProvider() {
        switch (currentMode) {
            case YAML_ONLY:
                logger.info("Using YAML-only announcement provider");
                return new YamlAnnouncementProvider(legacyManager);
                
            case RVNKCORE_AVAILABLE:
                logger.info("RVNKCore available - using migration-ready provider");
                return new MigrationReadyProvider(legacyManager, rvnkCoreService, logger);
                
            case RVNKCORE_ACTIVE:
                logger.info("Using RVNKCore announcement provider");
                return new RvnkCoreAnnouncementProvider(rvnkCoreService, logger);
                
            case HYBRID:
                logger.info("Using hybrid announcement provider");
                return new HybridAnnouncementProvider(legacyManager, rvnkCoreService, logger);
                
            default:
                logger.warning("Unknown compatibility mode, falling back to YAML");
                return new YamlAnnouncementProvider(legacyManager);
        }
    }
    
    // === Public API Methods ===
    // These methods maintain the exact same interface as the original AnnounceManager
    
    /**
     * Gets all announcements through the active provider.
     */
    public List<Announcement> getAnnouncements() {
        try {
            return activeProvider.getAllAnnouncements().get();
        } catch (Exception e) {
            logger.error("Failed to get announcements", e);
            return fallbackGetAnnouncements();
        }
    }
    
    /**
     * Gets all announcement types through the active provider.
     */
    public Map<String, AnnounceType> getAnnounceTypes() {
        try {
            return activeProvider.getAllAnnouncementTypes().get();
        } catch (Exception e) {
            logger.error("Failed to get announcement types", e);
            return fallbackGetAnnouncementTypes();
        }
    }
    
    /**
     * Creates a new announcement through the active provider.
     */
    public boolean createAnnouncement(String id, String type, String message, String playerName) {
        try {
            return activeProvider.createAnnouncement(id, type, message, playerName).get();
        } catch (Exception e) {
            logger.error("Failed to create announcement", e);
            return fallbackCreateAnnouncement(id, type, message, playerName);
        }
    }
    
    /**
     * Updates an existing announcement through the active provider.
     */
    public boolean updateAnnouncement(Announcement announcement) {
        try {
            return activeProvider.updateAnnouncement(announcement).get();
        } catch (Exception e) {
            logger.error("Failed to update announcement", e);
            return fallbackUpdateAnnouncement(announcement);
        }
    }
    
    /**
     * Deletes an announcement through the active provider.
     */
    public boolean deleteAnnouncement(String id) {
        try {
            return activeProvider.deleteAnnouncement(id).get();
        } catch (Exception e) {
            logger.error("Failed to delete announcement", e);
            return fallbackDeleteAnnouncement(id);
        }
    }
    
    /**
     * Gets player disabled announcement types.
     */
    public Map<UUID, Set<String>> getPlayerDisabledTypes() {
        try {
            return activeProvider.getPlayerDisabledTypes().get();
        } catch (Exception e) {
            logger.error("Failed to get player disabled types", e);
            return fallbackGetPlayerDisabledTypes();
        }
    }
    
    /**
     * Adds a disabled announcement type for a player.
     */
    public void addPlayerDisabledType(UUID playerId, String type) {
        try {
            activeProvider.addPlayerDisabledType(playerId, type).get();
        } catch (Exception e) {
            logger.error("Failed to add disabled type for player", e);
            fallbackAddPlayerDisabledType(playerId, type);
        }
    }
    
    /**
     * Removes a disabled announcement type for a player.
     */
    public void removePlayerDisabledType(UUID playerId, String type) {
        try {
            activeProvider.removePlayerDisabledType(playerId, type).get();
        } catch (Exception e) {
            logger.error("Failed to remove disabled type for player", e);
            fallbackRemovePlayerDisabledType(playerId, type);
        }
    }
    
    /**
     * Reloads announcement configuration.
     */
    public void reloadConfig() {
        try {
            activeProvider.reloadConfiguration().get();
            logger.info("Configuration reloaded successfully");
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            fallbackReloadConfig();
        }
    }
    
    /**
     * Saves current configuration.
     */
    public void saveConfig() {
        try {
            activeProvider.saveConfiguration().get();
            logger.info("Configuration saved successfully");
        } catch (Exception e) {
            logger.error("Failed to save configuration", e);
            fallbackSaveConfig();
        }
    }
    
    // === Migration Support Methods ===
    
    /**
     * Checks if migration to RVNKCore is possible.
     */
    public boolean isMigrationAvailable() {
        return currentMode == CompatibilityMode.RVNKCORE_AVAILABLE || 
               currentMode == CompatibilityMode.HYBRID;
    }
    
    /**
     * Initiates migration from YAML to RVNKCore.
     */
    public CompletableFuture<MigrationResult> initiateRvnkCoreMigration() {
        if (!isMigrationAvailable()) {
            return CompletableFuture.completedFuture(
                MigrationResult.failed("RVNKCore not available for migration"));
        }
        
        logger.info("Initiating migration to RVNKCore...");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Validate current data
                List<Announcement> yamlAnnouncements = legacyManager.getAnnouncements();
                Map<String, AnnounceType> yamlTypes = legacyManager.getAnnounceTypes();
                
                // Step 2: Convert and migrate data
                MigrationService migrationService = new MigrationService(rvnkCoreService, logger);
                MigrationResult result = migrationService.migrateFromYaml(yamlAnnouncements, yamlTypes);
                
                if (result.isSuccess()) {
                    // Step 3: Switch to RVNKCore provider
                    switchToRvnkCoreProvider();
                    logger.info("Migration to RVNKCore completed successfully");
                } else {
                    logger.warning("Migration failed: " + result.getErrorMessage());
                }
                
                return result;
            } catch (Exception e) {
                logger.error("Migration failed with exception", e);
                return MigrationResult.failed("Migration failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Rolls back from RVNKCore to YAML system.
     */
    public CompletableFuture<MigrationResult> rollbackToYaml() {
        if (currentMode == CompatibilityMode.YAML_ONLY) {
            return CompletableFuture.completedFuture(
                MigrationResult.failed("Already using YAML system"));
        }
        
        logger.warning("Rolling back to YAML system...");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Export current data from RVNKCore
                if (rvnkCoreService != null) {
                    List<AnnouncementDTO> coreAnnouncements = rvnkCoreService.getAllAnnouncements().get();
                    List<AnnouncementTypeDTO> coreTypes = rvnkCoreService.getAllAnnouncementTypes().get();
                    
                    // Step 2: Convert to YAML format and save
                    YamlExportService exportService = new YamlExportService(legacyManager, logger);
                    exportService.exportToYaml(coreAnnouncements, coreTypes);
                }
                
                // Step 3: Switch back to YAML provider
                switchToYamlProvider();
                
                logger.info("Rollback to YAML completed successfully");
                return MigrationResult.success("Rollback completed successfully");
                
            } catch (Exception e) {
                logger.error("Rollback failed", e);
                return MigrationResult.failed("Rollback failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Gets detailed migration status information.
     */
    public MigrationStatus getMigrationStatus() {
        return MigrationStatus.builder()
            .currentMode(currentMode)
            .rvnkCoreAvailable(rvnkCoreService != null)
            .migrationPossible(isMigrationAvailable())
            .yamlAnnouncementCount(legacyManager.getAnnouncements().size())
            .yamlTypeCount(legacyManager.getAnnounceTypes().size())
            .rvnkCoreAnnouncementCount(getRvnkCoreAnnouncementCount())
            .rvnkCoreTypeCount(getRvnkCoreTypeCount())
            .lastMigrationAttempt(getLastMigrationAttempt())
            .build();
    }
    
    // === Private Helper Methods ===
    
    private void switchToRvnkCoreProvider() {
        this.activeProvider = new RvnkCoreAnnouncementProvider(rvnkCoreService, logger);
        this.currentMode = CompatibilityMode.RVNKCORE_ACTIVE;
        updateConfigurationMode("active");
    }
    
    private void switchToYamlProvider() {
        this.activeProvider = new YamlAnnouncementProvider(legacyManager);
        this.currentMode = CompatibilityMode.YAML_ONLY;
        updateConfigurationMode("yaml");
    }
    
    private void updateConfigurationMode(String mode) {
        try {
            // Update configuration to persist the current mode
            ConfigManager configManager = new ConfigManager();
            configManager.setMigrationMode(mode);
            configManager.save();
        } catch (Exception e) {
            logger.warning("Failed to update configuration mode", e);
        }
    }
    
    // === Fallback Methods ===
    
    private List<Announcement> fallbackGetAnnouncements() {
        logger.warning("Using fallback method for getAnnouncements");
        return legacyManager.getAnnouncements();
    }
    
    private Map<String, AnnounceType> fallbackGetAnnouncementTypes() {
        logger.warning("Using fallback method for getAnnouncementTypes");
        return legacyManager.getAnnounceTypes();
    }
    
    private boolean fallbackCreateAnnouncement(String id, String type, String message, String playerName) {
        logger.warning("Using fallback method for createAnnouncement");
        return legacyManager.parseAnnouncement(id, type, message, playerName);
    }
    
    private boolean fallbackUpdateAnnouncement(Announcement announcement) {
        logger.warning("Using fallback method for updateAnnouncement");
        // Legacy update logic
        legacyManager.removeAnnouncement(announcement.getId());
        return legacyManager.addAnnouncement(announcement);
    }
    
    private boolean fallbackDeleteAnnouncement(String id) {
        logger.warning("Using fallback method for deleteAnnouncement");
        return legacyManager.removeAnnouncement(id);
    }
    
    private Map<UUID, Set<String>> fallbackGetPlayerDisabledTypes() {
        logger.warning("Using fallback method for getPlayerDisabledTypes");
        return legacyManager.getPlayerDisabledTypes();
    }
    
    private void fallbackAddPlayerDisabledType(UUID playerId, String type) {
        logger.warning("Using fallback method for addPlayerDisabledType");
        legacyManager.addPlayerDisabledType(playerId, type);
    }
    
    private void fallbackRemovePlayerDisabledType(UUID playerId, String type) {
        logger.warning("Using fallback method for removePlayerDisabledType");
        legacyManager.removePlayerDisabledType(playerId, type);
    }
    
    private void fallbackReloadConfig() {
        logger.warning("Using fallback method for reloadConfig");
        legacyManager.reloadConfig();
    }
    
    private void fallbackSaveConfig() {
        logger.warning("Using fallback method for saveConfig");
        legacyManager.saveConfig();
    }
    
    // === Configuration and Utility Methods ===
    
    private String getMigrationMode() {
        try {
            ConfigManager configManager = new ConfigManager();
            return configManager.getMigrationMode();
        } catch (Exception e) {
            return "available"; // Default mode
        }
    }
    
    private int getRvnkCoreAnnouncementCount() {
        try {
            if (rvnkCoreService != null) {
                return rvnkCoreService.getAllAnnouncements().get().size();
            }
        } catch (Exception e) {
            logger.warning("Failed to get RVNKCore announcement count", e);
        }
        return 0;
    }
    
    private int getRvnkCoreTypeCount() {
        try {
            if (rvnkCoreService != null) {
                return rvnkCoreService.getAllAnnouncementTypes().get().size();
            }
        } catch (Exception e) {
            logger.warning("Failed to get RVNKCore type count", e);
        }
        return 0;
    }
    
    private Instant getLastMigrationAttempt() {
        try {
            ConfigManager configManager = new ConfigManager();
            return configManager.getLastMigrationAttempt();
        } catch (Exception e) {
            return null;
        }
    }
}
```

## Provider Implementations

### YamlAnnouncementProvider

```java
package org.fourz.rvnktools.announceManager.compatibility.providers;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provider that uses the existing YAML-based announcement system.
 * Provides async wrappers around legacy synchronous methods.
 */
public class YamlAnnouncementProvider implements AnnouncementProvider {
    
    private final AnnounceManager legacyManager;
    
    public YamlAnnouncementProvider(AnnounceManager legacyManager) {
        this.legacyManager = legacyManager;
    }
    
    @Override
    public CompletableFuture<List<Announcement>> getAllAnnouncements() {
        return CompletableFuture.completedFuture(legacyManager.getAnnouncements());
    }
    
    @Override
    public CompletableFuture<Map<String, AnnounceType>> getAllAnnouncementTypes() {
        return CompletableFuture.completedFuture(legacyManager.getAnnounceTypes());
    }
    
    @Override
    public CompletableFuture<Boolean> createAnnouncement(String id, String type, 
                                                        String message, String playerName) {
        return CompletableFuture.supplyAsync(() -> 
            legacyManager.parseAnnouncement(id, type, message, playerName));
    }
    
    @Override
    public CompletableFuture<Boolean> updateAnnouncement(Announcement announcement) {
        return CompletableFuture.supplyAsync(() -> {
            legacyManager.removeAnnouncement(announcement.getId());
            return legacyManager.addAnnouncement(announcement);
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteAnnouncement(String id) {
        return CompletableFuture.supplyAsync(() -> legacyManager.removeAnnouncement(id));
    }
    
    @Override
    public CompletableFuture<Map<UUID, Set<String>>> getPlayerDisabledTypes() {
        return CompletableFuture.completedFuture(legacyManager.getPlayerDisabledTypes());
    }
    
    @Override
    public CompletableFuture<Void> addPlayerDisabledType(UUID playerId, String type) {
        return CompletableFuture.runAsync(() -> 
            legacyManager.addPlayerDisabledType(playerId, type));
    }
    
    @Override
    public CompletableFuture<Void> removePlayerDisabledType(UUID playerId, String type) {
        return CompletableFuture.runAsync(() -> 
            legacyManager.removePlayerDisabledType(playerId, type));
    }
    
    @Override
    public CompletableFuture<Void> reloadConfiguration() {
        return CompletableFuture.runAsync(() -> legacyManager.reloadConfig());
    }
    
    @Override
    public CompletableFuture<Void> saveConfiguration() {
        return CompletableFuture.runAsync(() -> legacyManager.saveConfig());
    }
    
    @Override
    public String getProviderName() {
        return "YAML";
    }
    
    @Override
    public boolean isHealthy() {
        return true; // YAML system is always considered healthy if accessible
    }
}
```

### RvnkCoreAnnouncementProvider

```java
package org.fourz.rvnktools.announceManager.compatibility.providers;

import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.api.dto.AnnouncementTypeDTO;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Provider that uses RVNKCore's AnnouncementService.
 * Handles conversion between legacy objects and DTOs.
 */
public class RvnkCoreAnnouncementProvider implements AnnouncementProvider {
    
    private final AnnouncementService announcementService;
    private final LogManager logger;
    private final DtoConverter converter;
    
    public RvnkCoreAnnouncementProvider(AnnouncementService announcementService, LogManager logger) {
        this.announcementService = announcementService;
        this.logger = logger;
        this.converter = new DtoConverter();
    }
    
    @Override
    public CompletableFuture<List<Announcement>> getAllAnnouncements() {
        return announcementService.getAllAnnouncements()
            .thenApply(dtos -> dtos.stream()
                .map(converter::fromDto)
                .collect(Collectors.toList()));
    }
    
    @Override
    public CompletableFuture<Map<String, AnnounceType>> getAllAnnouncementTypes() {
        return announcementService.getAllAnnouncementTypes()
            .thenApply(dtos -> dtos.stream()
                .collect(Collectors.toMap(
                    AnnouncementTypeDTO::getId,
                    converter::fromDto)));
    }
    
    @Override
    public CompletableFuture<Boolean> createAnnouncement(String id, String type, 
                                                        String message, String playerName) {
        AnnouncementDTO dto = AnnouncementDTO.builder()
            .id(id)
            .message(message)
            .type(type)
            .active(true)
            .createdBy(playerName)
            .build();
        
        return announcementService.createAnnouncement(dto)
            .thenApply(createdId -> createdId != null);
    }
    
    @Override
    public CompletableFuture<Boolean> updateAnnouncement(Announcement announcement) {
        AnnouncementDTO dto = converter.toDto(announcement);
        return announcementService.updateAnnouncement(dto)
            .thenApply(v -> true)
            .exceptionally(throwable -> {
                logger.error("Failed to update announcement", throwable);
                return false;
            });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteAnnouncement(String id) {
        return announcementService.deleteAnnouncement(id);
    }
    
    @Override
    public CompletableFuture<Map<UUID, Set<String>>> getPlayerDisabledTypes() {
        return announcementService.getAllPlayerDisabledTypes();
    }
    
    @Override
    public CompletableFuture<Void> addPlayerDisabledType(UUID playerId, String type) {
        return announcementService.addPlayerDisabledType(playerId, type);
    }
    
    @Override
    public CompletableFuture<Void> removePlayerDisabledType(UUID playerId, String type) {
        return announcementService.removePlayerDisabledType(playerId, type);
    }
    
    @Override
    public CompletableFuture<Void> reloadConfiguration() {
        return announcementService.reloadConfiguration();
    }
    
    @Override
    public CompletableFuture<Void> saveConfiguration() {
        return announcementService.saveConfiguration();
    }
    
    @Override
    public String getProviderName() {
        return "RVNKCore";
    }
    
    @Override
    public boolean isHealthy() {
        try {
            return announcementService.isHealthy();
        } catch (Exception e) {
            logger.warning("RVNKCore service health check failed", e);
            return false;
        }
    }
}
```

## Data Transfer Object Conversion

### DtoConverter

```java
package org.fourz.rvnktools.announceManager.compatibility;

import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.api.dto.AnnouncementTypeDTO;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Converts between legacy announcement objects and RVNKCore DTOs.
 */
public class DtoConverter {
    
    /**
     * Converts legacy Announcement to AnnouncementDTO.
     */
    public AnnouncementDTO toDto(Announcement announcement) {
        return AnnouncementDTO.builder()
            .id(announcement.getId())
            .message(announcement.getMessage())
            .type(announcement.getType())
            .active(announcement.isActive())
            .world(announcement.getWorld())
            .permission(announcement.getPermission())
            .displayDurationSeconds(announcement.getDisplayDurationSeconds())
            .priority(announcement.getPriority())
            .createdBy(announcement.getOwner())
            .recurrence(announcement.getRecurrence())
            .recurrenceString(announcement.getRecurrenceString())
            .createdAt(convertToTimestamp(announcement.getCreatedAt()))
            .updatedAt(Timestamp.from(Instant.now()))
            .build();
    }
    
    /**
     * Converts AnnouncementDTO to legacy Announcement.
     */
    public Announcement fromDto(AnnouncementDTO dto) {
        Announcement announcement = new Announcement();
        announcement.setId(dto.getId());
        announcement.setMessage(dto.getMessage());
        announcement.setType(dto.getType());
        announcement.setActive(dto.isActive());
        announcement.setWorld(dto.getWorld());
        announcement.setPermission(dto.getPermission());
        announcement.setDisplayDurationSeconds(dto.getDisplayDurationSeconds());
        announcement.setPriority(dto.getPriority());
        announcement.setOwner(dto.getCreatedBy());
        announcement.setRecurrence(dto.getRecurrence());
        announcement.setRecurrenceString(dto.getRecurrenceString());
        announcement.setCreatedAt(convertFromTimestamp(dto.getCreatedAt()));
        announcement.setImported(false); // Legacy field
        
        return announcement;
    }
    
    /**
     * Converts legacy AnnounceType to AnnouncementTypeDTO.
     */
    public AnnouncementTypeDTO toDto(AnnounceType type) {
        return AnnouncementTypeDTO.builder()
            .id(type.getId())
            .name(type.getDisplayName())
            .description(type.getDescription())
            .defaultPriority(type.getPriority())
            .color(convertColorToHex(type.getColor()))
            .enabled(type.isEnabled())
            .build();
    }
    
    /**
     * Converts AnnouncementTypeDTO to legacy AnnounceType.
     */
    public AnnounceType fromDto(AnnouncementTypeDTO dto) {
        AnnounceType type = new AnnounceType();
        type.setId(dto.getId());
        type.setDisplayName(dto.getName());
        type.setDescription(dto.getDescription());
        type.setPriority(dto.getDefaultPriority());
        type.setColor(convertColorFromHex(dto.getColor()));
        type.setEnabled(dto.isEnabled());
        
        return type;
    }
    
    // Helper methods for data conversion
    private Timestamp convertToTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : Timestamp.from(Instant.now());
    }
    
    private Instant convertFromTimestamp(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : Instant.now();
    }
    
    private String convertColorToHex(String minecraftColor) {
        if (minecraftColor == null) return "#FFFFFF";
        
        // Convert Minecraft color codes to hex
        switch (minecraftColor.toLowerCase()) {
            case "&0": return "#000000"; // Black
            case "&1": return "#0000AA"; // Dark Blue
            case "&2": return "#00AA00"; // Dark Green
            case "&3": return "#00AAAA"; // Dark Aqua
            case "&4": return "#AA0000"; // Dark Red
            case "&5": return "#AA00AA"; // Dark Purple
            case "&6": return "#FFAA00"; // Gold
            case "&7": return "#AAAAAA"; // Gray
            case "&8": return "#555555"; // Dark Gray
            case "&9": return "#5555FF"; // Blue
            case "&a": return "#55FF55"; // Green
            case "&b": return "#55FFFF"; // Aqua
            case "&c": return "#FF5555"; // Red
            case "&d": return "#FF55FF"; // Light Purple
            case "&e": return "#FFFF55"; // Yellow
            case "&f": return "#FFFFFF"; // White
            default: return minecraftColor.startsWith("#") ? minecraftColor : "#FFFFFF";
        }
    }
    
    private String convertColorFromHex(String hexColor) {
        if (hexColor == null) return "&f";
        
        // Convert hex colors back to Minecraft color codes
        switch (hexColor.toUpperCase()) {
            case "#000000": return "&0";
            case "#0000AA": return "&1";
            case "#00AA00": return "&2";
            case "#00AAAA": return "&3";
            case "#AA0000": return "&4";
            case "#AA00AA": return "&5";
            case "#FFAA00": return "&6";
            case "#AAAAAA": return "&7";
            case "#555555": return "&8";
            case "#5555FF": return "&9";
            case "#55FF55": return "&a";
            case "#55FFFF": return "&b";
            case "#FF5555": return "&c";
            case "#FF55FF": return "&d";
            case "#FFFF55": return "&e";
            case "#FFFFFF": return "&f";
            default: return hexColor; // Return as-is if no match
        }
    }
}
```

## Migration Result Tracking

### MigrationResult and Status Classes

```java
// Migration result tracking
public class MigrationResult {
    private final boolean success;
    private final String message;
    private final int migratedAnnouncements;
    private final int migratedTypes;
    private final List<String> warnings;
    private final Exception error;
    
    public static MigrationResult success(String message, int announcements, int types) {
        return new MigrationResult(true, message, announcements, types, List.of(), null);
    }
    
    public static MigrationResult failed(String message) {
        return new MigrationResult(false, message, 0, 0, List.of(), null);
    }
    
    public static MigrationResult failed(String message, Exception error) {
        return new MigrationResult(false, message, 0, 0, List.of(), error);
    }
    
    // Constructors, getters, etc.
}

// Migration status information
@Data
@Builder
public class MigrationStatus {
    private CompatibilityMode currentMode;
    private boolean rvnkCoreAvailable;
    private boolean migrationPossible;
    private int yamlAnnouncementCount;
    private int yamlTypeCount;
    private int rvnkCoreAnnouncementCount;
    private int rvnkCoreTypeCount;
    private Instant lastMigrationAttempt;
    private String lastMigrationResult;
    private List<String> migrationWarnings;
    private boolean dataSyncRequired;
}
```

## Command Integration

### Legacy Command Compatibility

```java
// Update existing RVNKTools commands to use compatibility layer
public class AnnouncerCommand extends BaseCommand {
    
    private final AnnouncementCompatibilityManager compatibilityManager;
    
    public AnnouncerCommand(RVNKTools plugin, AnnouncementCompatibilityManager compatibilityManager) {
        super(plugin);
        this.compatibilityManager = compatibilityManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            return showMainMenu(sender);
        }
        
        switch (args[0].toLowerCase()) {
            case "status":
                return showStatus(sender);
            case "migrate":
                return handleMigration(sender, args);
            case "rollback":
                return handleRollback(sender);
            default:
                return handleLegacyCommands(sender, args);
        }
    }
    
    private boolean showStatus(CommandSender sender) {
        MigrationStatus status = compatibilityManager.getMigrationStatus();
        
        sender.sendMessage(ChatFormat.format("&6⚙ Announcement System Status"));
        sender.sendMessage(ChatFormat.format("&7Current Mode: &e" + status.getCurrentMode()));
        sender.sendMessage(ChatFormat.format("&7RVNKCore Available: " + 
            (status.isRvnkCoreAvailable() ? "&aYes" : "&cNo")));
        sender.sendMessage(ChatFormat.format("&7YAML Announcements: &e" + status.getYamlAnnouncementCount()));
        sender.sendMessage(ChatFormat.format("&7RVNKCore Announcements: &e" + status.getRvnkCoreAnnouncementCount()));
        
        if (status.isMigrationPossible()) {
            sender.sendMessage(ChatFormat.format("&a✓ Migration to RVNKCore is available"));
            sender.sendMessage(ChatFormat.format("&7Use &e/announcer migrate start &7to begin"));
        }
        
        return true;
    }
    
    private boolean handleMigration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.format("&c▶ Usage: /announcer migrate <start|status>"));
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "start":
                if (!compatibilityManager.isMigrationAvailable()) {
                    sender.sendMessage(ChatFormat.format("&c✖ RVNKCore is not available for migration"));
                    return true;
                }
                
                sender.sendMessage(ChatFormat.format("&6⚙ Starting migration to RVNKCore..."));
                
                compatibilityManager.initiateRvnkCoreMigration()
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            sender.sendMessage(ChatFormat.format("&a✓ Migration completed successfully"));
                            sender.sendMessage(ChatFormat.format("&7Migrated: &e" + result.getMigratedAnnouncements() + 
                                                               " &7announcements, &e" + result.getMigratedTypes() + " &7types"));
                        } else {
                            sender.sendMessage(ChatFormat.format("&c✖ Migration failed: " + result.getMessage()));
                        }
                    })
                    .exceptionally(throwable -> {
                        sender.sendMessage(ChatFormat.format("&c✖ Migration failed with error: " + throwable.getMessage()));
                        return null;
                    });
                
                return true;
                
            case "status":
                return showStatus(sender);
                
            default:
                sender.sendMessage(ChatFormat.format("&c▶ Usage: /announcer migrate <start|status>"));
                return true;
        }
    }
    
    private boolean handleRollback(CommandSender sender) {
        if (compatibilityManager.getCurrentMode() == CompatibilityMode.YAML_ONLY) {
            sender.sendMessage(ChatFormat.format("&c✖ Already using YAML system"));
            return true;
        }
        
        sender.sendMessage(ChatFormat.format("&e⚠ Rolling back to YAML system..."));
        
        compatibilityManager.rollbackToYaml()
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    sender.sendMessage(ChatFormat.format("&a✓ Rollback completed successfully"));
                } else {
                    sender.sendMessage(ChatFormat.format("&c✖ Rollback failed: " + result.getMessage()));
                }
            });
        
        return true;
    }
    
    private boolean handleLegacyCommands(CommandSender sender, String[] args) {
        // Handle all existing announcement commands through compatibility layer
        // This ensures existing command functionality is preserved
        return legacyCommandHandler.handle(sender, args);
    }
}
```

## Configuration Management

### Migration Configuration

```yaml
# announcements.yml - Migration configuration section
migration:
  # Migration mode: yaml, available, active, hybrid
  mode: "available"
  
  # Auto-migration settings
  auto-migrate-on-startup: false
  backup-before-migration: true
  
  # Validation settings
  validate-after-migration: true
  allow-partial-migration: false
  
  # Rollback settings
  enable-rollback: true
  keep-yaml-backup: true
  
  # Last migration tracking
  last-attempt: null
  last-result: null
  warnings: []
```

This comprehensive legacy compatibility layer ensures a smooth transition from the existing YAML-based system to RVNKCore while maintaining full backward compatibility and providing robust migration tools.
