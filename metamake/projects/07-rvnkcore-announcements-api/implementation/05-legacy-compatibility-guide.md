# Implementation Guide: Legacy Compatibility Layer

**Guide ID**: 05-legacy-compatibility-guide  
**Feature Reference**: 05-legacy-compatibility.md  
**Implementation Phase**: Week 4  
**Prerequisites**: Service layer, Database integration, Migration framework

## Implementation Overview

This guide provides step-by-step instructions for implementing the legacy compatibility layer that enables seamless transition between the existing YAML-based announcement system and the new RVNKCore service-based system.

## Project Structure Setup

### 1. Create Compatibility Package Structure

```text
src/main/java/org/fourz/rvnktools/
└── announceManager/
    └── compatibility/
        ├── AnnouncementCompatibilityManager.java
        ├── providers/
        │   ├── AnnouncementProvider.java
        │   ├── YamlAnnouncementProvider.java
        │   ├── RvnkCoreAnnouncementProvider.java
        │   ├── MigrationReadyProvider.java
        │   └── HybridAnnouncementProvider.java
        ├── migration/
        │   ├── MigrationService.java
        │   ├── YamlExportService.java
        │   └── ConfigManager.java
        ├── dto/
        │   ├── MigrationResult.java
        │   ├── MigrationStatus.java
        │   └── CompatibilityMode.java
        └── util/
            └── DtoConverter.java
```

## Step 1: Provider Interface Implementation

### AnnouncementProvider.java

```java
package org.fourz.rvnktools.announceManager.compatibility.providers;

import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provider interface for announcement operations that abstracts
 * the underlying storage mechanism (YAML or RVNKCore).
 */
public interface AnnouncementProvider {
    
    // === Core Operations ===
    
    /**
     * Gets all announcements from the provider.
     */
    CompletableFuture<List<Announcement>> getAllAnnouncements();
    
    /**
     * Gets all announcement types from the provider.
     */
    CompletableFuture<Map<String, AnnounceType>> getAllAnnouncementTypes();
    
    /**
     * Creates a new announcement.
     */
    CompletableFuture<Boolean> createAnnouncement(String id, String type, String message, String playerName);
    
    /**
     * Updates an existing announcement.
     */
    CompletableFuture<Boolean> updateAnnouncement(Announcement announcement);
    
    /**
     * Deletes an announcement.
     */
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    
    // === Player Preferences ===
    
    /**
     * Gets all player disabled types.
     */
    CompletableFuture<Map<UUID, Set<String>>> getPlayerDisabledTypes();
    
    /**
     * Adds a disabled announcement type for a player.
     */
    CompletableFuture<Void> addPlayerDisabledType(UUID playerId, String type);
    
    /**
     * Removes a disabled announcement type for a player.
     */
    CompletableFuture<Void> removePlayerDisabledType(UUID playerId, String type);
    
    // === Configuration Management ===
    
    /**
     * Reloads configuration from storage.
     */
    CompletableFuture<Void> reloadConfiguration();
    
    /**
     * Saves current configuration to storage.
     */
    CompletableFuture<Void> saveConfiguration();
    
    // === Provider Information ===
    
    /**
     * Gets the name/type of this provider.
     */
    String getProviderName();
    
    /**
     * Checks if the provider is healthy and operational.
     */
    boolean isHealthy();
}
```

## Step 2: YAML Provider Implementation

### YamlAnnouncementProvider.java

```java
package org.fourz.rvnktools.announceManager.compatibility.providers;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provider implementation that uses the existing YAML-based announcement system.
 * Provides async wrappers around legacy synchronous methods.
 */
public class YamlAnnouncementProvider implements AnnouncementProvider {
    
    private final AnnounceManager legacyManager;
    private final LogManager logger;
    
    public YamlAnnouncementProvider(AnnounceManager legacyManager) {
        this.legacyManager = legacyManager;
        this.logger = LogManager.getInstance(); // Or inject if available
    }
    
    @Override
    public CompletableFuture<List<Announcement>> getAllAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Announcement> announcements = legacyManager.getAnnouncements();
                logger.debug("Retrieved " + announcements.size() + " announcements from YAML");
                return announcements;
            } catch (Exception e) {
                logger.error("Failed to get announcements from YAML", e);
                throw new RuntimeException("Failed to get announcements", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Map<String, AnnounceType>> getAllAnnouncementTypes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, AnnounceType> types = legacyManager.getAnnounceTypes();
                logger.debug("Retrieved " + types.size() + " announcement types from YAML");
                return types;
            } catch (Exception e) {
                logger.error("Failed to get announcement types from YAML", e);
                throw new RuntimeException("Failed to get announcement types", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> createAnnouncement(String id, String type, String message, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean result = legacyManager.parseAnnouncement(id, type, message, playerName);
                if (result) {
                    logger.info("Created announcement '" + id + "' in YAML system");
                } else {
                    logger.warning("Failed to create announcement '" + id + "' in YAML system");
                }
                return result;
            } catch (Exception e) {
                logger.error("Error creating announcement '" + id + "'", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> updateAnnouncement(Announcement announcement) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Legacy update: remove and add
                boolean removed = legacyManager.removeAnnouncement(announcement.getId());
                if (!removed) {
                    logger.warning("Could not find existing announcement to update: " + announcement.getId());
                }
                
                boolean added = legacyManager.addAnnouncement(announcement);
                if (added) {
                    logger.info("Updated announcement '" + announcement.getId() + "' in YAML system");
                } else {
                    logger.warning("Failed to update announcement '" + announcement.getId() + "' in YAML system");
                }
                return added;
            } catch (Exception e) {
                logger.error("Error updating announcement '" + announcement.getId() + "'", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteAnnouncement(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean result = legacyManager.removeAnnouncement(id);
                if (result) {
                    logger.info("Deleted announcement '" + id + "' from YAML system");
                } else {
                    logger.warning("Failed to delete announcement '" + id + "' from YAML system");
                }
                return result;
            } catch (Exception e) {
                logger.error("Error deleting announcement '" + id + "'", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Map<UUID, Set<String>>> getPlayerDisabledTypes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<UUID, Set<String>> disabledTypes = legacyManager.getPlayerDisabledTypes();
                logger.debug("Retrieved disabled types for " + disabledTypes.size() + " players from YAML");
                return disabledTypes;
            } catch (Exception e) {
                logger.error("Failed to get player disabled types from YAML", e);
                throw new RuntimeException("Failed to get player disabled types", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> addPlayerDisabledType(UUID playerId, String type) {
        return CompletableFuture.runAsync(() -> {
            try {
                legacyManager.addPlayerDisabledType(playerId, type);
                logger.debug("Added disabled type '" + type + "' for player " + playerId + " in YAML system");
            } catch (Exception e) {
                logger.error("Error adding disabled type for player " + playerId, e);
                throw new RuntimeException("Failed to add disabled type", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> removePlayerDisabledType(UUID playerId, String type) {
        return CompletableFuture.runAsync(() -> {
            try {
                legacyManager.removePlayerDisabledType(playerId, type);
                logger.debug("Removed disabled type '" + type + "' for player " + playerId + " in YAML system");
            } catch (Exception e) {
                logger.error("Error removing disabled type for player " + playerId, e);
                throw new RuntimeException("Failed to remove disabled type", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> reloadConfiguration() {
        return CompletableFuture.runAsync(() -> {
            try {
                legacyManager.reloadConfig();
                logger.info("Reloaded YAML configuration");
            } catch (Exception e) {
                logger.error("Error reloading YAML configuration", e);
                throw new RuntimeException("Failed to reload configuration", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> saveConfiguration() {
        return CompletableFuture.runAsync(() -> {
            try {
                legacyManager.saveConfig();
                logger.info("Saved YAML configuration");
            } catch (Exception e) {
                logger.error("Error saving YAML configuration", e);
                throw new RuntimeException("Failed to save configuration", e);
            }
        });
    }
    
    @Override
    public String getProviderName() {
        return "YAML";
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Basic health check - try to get announcements
            legacyManager.getAnnouncements();
            return true;
        } catch (Exception e) {
            logger.warning("YAML provider health check failed", e);
            return false;
        }
    }
}
```

## Step 3: RVNKCore Provider Implementation

### RvnkCoreAnnouncementProvider.java

```java
package org.fourz.rvnktools.announceManager.compatibility.providers;

import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.api.dto.AnnouncementTypeDTO;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.compatibility.util.DtoConverter;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Provider implementation that uses RVNKCore's AnnouncementService.
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
            .thenApply(dtos -> {
                List<Announcement> announcements = dtos.stream()
                    .map(converter::fromDto)
                    .collect(Collectors.toList());
                logger.debug("Retrieved " + announcements.size() + " announcements from RVNKCore");
                return announcements;
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get announcements from RVNKCore", throwable);
                throw new RuntimeException("Failed to get announcements", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Map<String, AnnounceType>> getAllAnnouncementTypes() {
        return announcementService.getAllAnnouncementTypes()
            .thenApply(dtos -> {
                Map<String, AnnounceType> types = dtos.stream()
                    .collect(Collectors.toMap(
                        AnnouncementTypeDTO::getId,
                        converter::fromDto));
                logger.debug("Retrieved " + types.size() + " announcement types from RVNKCore");
                return types;
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get announcement types from RVNKCore", throwable);
                throw new RuntimeException("Failed to get announcement types", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Boolean> createAnnouncement(String id, String type, String message, String playerName) {
        AnnouncementDTO dto = AnnouncementDTO.builder()
            .id(id)
            .message(message)
            .type(type)
            .active(true)
            .createdBy(playerName)
            .build();
        
        return announcementService.createAnnouncement(dto)
            .thenApply(createdId -> {
                boolean success = createdId != null;
                if (success) {
                    logger.info("Created announcement '" + id + "' in RVNKCore");
                } else {
                    logger.warning("Failed to create announcement '" + id + "' in RVNKCore");
                }
                return success;
            })
            .exceptionally(throwable -> {
                logger.error("Error creating announcement '" + id + "' in RVNKCore", throwable);
                return false;
            });
    }
    
    @Override
    public CompletableFuture<Boolean> updateAnnouncement(Announcement announcement) {
        AnnouncementDTO dto = converter.toDto(announcement);
        
        return announcementService.updateAnnouncement(dto)
            .thenApply(v -> {
                logger.info("Updated announcement '" + announcement.getId() + "' in RVNKCore");
                return true;
            })
            .exceptionally(throwable -> {
                logger.error("Error updating announcement '" + announcement.getId() + "' in RVNKCore", throwable);
                return false;
            });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteAnnouncement(String id) {
        return announcementService.deleteAnnouncement(id)
            .thenApply(deleted -> {
                if (deleted) {
                    logger.info("Deleted announcement '" + id + "' from RVNKCore");
                } else {
                    logger.warning("Failed to delete announcement '" + id + "' from RVNKCore");
                }
                return deleted;
            })
            .exceptionally(throwable -> {
                logger.error("Error deleting announcement '" + id + "' from RVNKCore", throwable);
                return false;
            });
    }
    
    @Override
    public CompletableFuture<Map<UUID, Set<String>>> getPlayerDisabledTypes() {
        return announcementService.getAllPlayerDisabledTypes()
            .thenApply(disabledTypes -> {
                logger.debug("Retrieved disabled types for " + disabledTypes.size() + " players from RVNKCore");
                return disabledTypes;
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get player disabled types from RVNKCore", throwable);
                throw new RuntimeException("Failed to get player disabled types", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Void> addPlayerDisabledType(UUID playerId, String type) {
        return announcementService.addPlayerDisabledType(playerId, type)
            .thenRun(() -> {
                logger.debug("Added disabled type '" + type + "' for player " + playerId + " in RVNKCore");
            })
            .exceptionally(throwable -> {
                logger.error("Error adding disabled type for player " + playerId + " in RVNKCore", throwable);
                throw new RuntimeException("Failed to add disabled type", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Void> removePlayerDisabledType(UUID playerId, String type) {
        return announcementService.removePlayerDisabledType(playerId, type)
            .thenRun(() -> {
                logger.debug("Removed disabled type '" + type + "' for player " + playerId + " in RVNKCore");
            })
            .exceptionally(throwable -> {
                logger.error("Error removing disabled type for player " + playerId + " in RVNKCore", throwable);
                throw new RuntimeException("Failed to remove disabled type", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Void> reloadConfiguration() {
        return announcementService.reloadConfiguration()
            .thenRun(() -> {
                logger.info("Reloaded RVNKCore configuration");
            })
            .exceptionally(throwable -> {
                logger.error("Error reloading RVNKCore configuration", throwable);
                throw new RuntimeException("Failed to reload configuration", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Void> saveConfiguration() {
        return announcementService.saveConfiguration()
            .thenRun(() -> {
                logger.info("Saved RVNKCore configuration");
            })
            .exceptionally(throwable -> {
                logger.error("Error saving RVNKCore configuration", throwable);
                throw new RuntimeException("Failed to save configuration", throwable);
            });
    }
    
    @Override
    public String getProviderName() {
        return "RVNKCore";
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Check if the service is available and responsive
            return announcementService.isHealthy();
        } catch (Exception e) {
            logger.warning("RVNKCore provider health check failed", e);
            return false;
        }
    }
}
```

## Step 4: Main Compatibility Manager

### AnnouncementCompatibilityManager.java

```java
package org.fourz.rvnktools.announceManager.compatibility;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.compatibility.providers.*;
import org.fourz.rvnktools.announceManager.compatibility.dto.MigrationResult;
import org.fourz.rvnktools.announceManager.compatibility.dto.MigrationStatus;
import org.fourz.rvnktools.announceManager.compatibility.dto.CompatibilityMode;
import org.fourz.rvnktools.announceManager.compatibility.migration.ConfigManager;
import org.fourz.rvnktools.util.log.LogManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main compatibility manager that provides seamless transition between 
 * YAML-based and RVNKCore service-based announcement systems.
 */
public class AnnouncementCompatibilityManager {
    
    private final LogManager logger;
    private final AnnounceManager legacyManager;
    private final ConfigManager configManager;
    private AnnouncementService rvnkCoreService;
    private AnnouncementProvider activeProvider;
    private CompatibilityMode currentMode;
    
    public AnnouncementCompatibilityManager(AnnounceManager legacyManager, LogManager logger) {
        this.legacyManager = legacyManager;
        this.logger = logger;
        this.configManager = new ConfigManager(logger);
        this.currentMode = detectCompatibilityMode();
        this.activeProvider = initializeProvider();
        
        logger.info("Announcement compatibility layer initialized in mode: " + currentMode);
    }
    
    /**
     * Gets all announcements through the active provider.
     */
    public List<Announcement> getAnnouncements() {
        try {
            return activeProvider.getAllAnnouncements().get();
        } catch (Exception e) {
            logger.error("Failed to get announcements through compatibility layer", e);
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
            logger.error("Failed to get announcement types through compatibility layer", e);
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
            logger.error("Failed to create announcement through compatibility layer", e);
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
            logger.error("Failed to update announcement through compatibility layer", e);
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
            logger.error("Failed to delete announcement through compatibility layer", e);
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
            logger.error("Failed to get player disabled types through compatibility layer", e);
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
            logger.error("Failed to add disabled type through compatibility layer", e);
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
            logger.error("Failed to remove disabled type through compatibility layer", e);
            fallbackRemovePlayerDisabledType(playerId, type);
        }
    }
    
    /**
     * Reloads announcement configuration.
     */
    public void reloadConfig() {
        try {
            activeProvider.reloadConfiguration().get();
            logger.info("Configuration reloaded successfully through compatibility layer");
        } catch (Exception e) {
            logger.error("Failed to reload configuration through compatibility layer", e);
            fallbackReloadConfig();
        }
    }
    
    /**
     * Saves current configuration.
     */
    public void saveConfig() {
        try {
            activeProvider.saveConfiguration().get();
            logger.info("Configuration saved successfully through compatibility layer");
        } catch (Exception e) {
            logger.error("Failed to save configuration through compatibility layer", e);
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
            .lastMigrationResult(getLastMigrationResult())
            .dataSyncRequired(isDataSyncRequired())
            .build();
    }
    
    /**
     * Gets the current compatibility mode.
     */
    public CompatibilityMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Gets the name of the active provider.
     */
    public String getActiveProviderName() {
        return activeProvider != null ? activeProvider.getProviderName() : "None";
    }
    
    /**
     * Checks if the active provider is healthy.
     */
    public boolean isHealthy() {
        return activeProvider != null && activeProvider.isHealthy();
    }
    
    // === Private Implementation Methods ===
    
    private CompatibilityMode detectCompatibilityMode() {
        try {
            RVNKCore rvnkCore = RVNKCore.getInstance();
            if (rvnkCore != null && rvnkCore.isInitialized()) {
                
                // Check if RVNKCore has AnnouncementService
                if (rvnkCore.hasService(AnnouncementService.class)) {
                    rvnkCoreService = rvnkCore.getService(AnnouncementService.class);
                    
                    // Check migration preferences from configuration
                    String migrationMode = configManager.getMigrationMode();
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
            logger.warning("RVNKCore not available for compatibility layer: " + e.getMessage());
        }
        
        return CompatibilityMode.YAML_ONLY;
    }
    
    private AnnouncementProvider initializeProvider() {
        switch (currentMode) {
            case YAML_ONLY:
                logger.info("Initializing YAML-only announcement provider");
                return new YamlAnnouncementProvider(legacyManager);
                
            case RVNKCORE_AVAILABLE:
                logger.info("Initializing migration-ready announcement provider");
                return new MigrationReadyProvider(legacyManager, rvnkCoreService, logger);
                
            case RVNKCORE_ACTIVE:
                logger.info("Initializing RVNKCore announcement provider");
                return new RvnkCoreAnnouncementProvider(rvnkCoreService, logger);
                
            case HYBRID:
                logger.info("Initializing hybrid announcement provider");
                return new HybridAnnouncementProvider(legacyManager, rvnkCoreService, logger);
                
            default:
                logger.warning("Unknown compatibility mode, falling back to YAML provider");
                return new YamlAnnouncementProvider(legacyManager);
        }
    }
    
    // === Fallback Methods ===
    
    private List<Announcement> fallbackGetAnnouncements() {
        logger.warning("Using fallback method for getAnnouncements");
        try {
            return legacyManager.getAnnouncements();
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return List.of();
        }
    }
    
    private Map<String, AnnounceType> fallbackGetAnnouncementTypes() {
        logger.warning("Using fallback method for getAnnouncementTypes");
        try {
            return legacyManager.getAnnounceTypes();
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return Map.of();
        }
    }
    
    private boolean fallbackCreateAnnouncement(String id, String type, String message, String playerName) {
        logger.warning("Using fallback method for createAnnouncement");
        try {
            return legacyManager.parseAnnouncement(id, type, message, playerName);
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return false;
        }
    }
    
    private boolean fallbackUpdateAnnouncement(Announcement announcement) {
        logger.warning("Using fallback method for updateAnnouncement");
        try {
            legacyManager.removeAnnouncement(announcement.getId());
            return legacyManager.addAnnouncement(announcement);
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return false;
        }
    }
    
    private boolean fallbackDeleteAnnouncement(String id) {
        logger.warning("Using fallback method for deleteAnnouncement");
        try {
            return legacyManager.removeAnnouncement(id);
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return false;
        }
    }
    
    private Map<UUID, Set<String>> fallbackGetPlayerDisabledTypes() {
        logger.warning("Using fallback method for getPlayerDisabledTypes");
        try {
            return legacyManager.getPlayerDisabledTypes();
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
            return Map.of();
        }
    }
    
    private void fallbackAddPlayerDisabledType(UUID playerId, String type) {
        logger.warning("Using fallback method for addPlayerDisabledType");
        try {
            legacyManager.addPlayerDisabledType(playerId, type);
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
        }
    }
    
    private void fallbackRemovePlayerDisabledType(UUID playerId, String type) {
        logger.warning("Using fallback method for removePlayerDisabledType");
        try {
            legacyManager.removePlayerDisabledType(playerId, type);
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
        }
    }
    
    private void fallbackReloadConfig() {
        logger.warning("Using fallback method for reloadConfig");
        try {
            legacyManager.reloadConfig();
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
        }
    }
    
    private void fallbackSaveConfig() {
        logger.warning("Using fallback method for saveConfig");
        try {
            legacyManager.saveConfig();
        } catch (Exception e) {
            logger.error("Fallback method failed", e);
        }
    }
    
    // === Helper Methods ===
    
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
            return configManager.getLastMigrationAttempt();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getLastMigrationResult() {
        try {
            return configManager.getLastMigrationResult();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private boolean isDataSyncRequired() {
        try {
            if (currentMode == CompatibilityMode.HYBRID) {
                // Check if YAML and RVNKCore data are in sync
                int yamlCount = legacyManager.getAnnouncements().size();
                int coreCount = getRvnkCoreAnnouncementCount();
                return yamlCount != coreCount;
            }
        } catch (Exception e) {
            logger.warning("Failed to check data sync status", e);
        }
        return false;
    }
}
```

## Step 5: Integration with Existing RVNKTools

### Update AnnounceManager Integration

```java
// In RVNKTools main plugin class
public class RVNKTools extends JavaPlugin {
    
    private AnnouncementCompatibilityManager compatibilityManager;
    private AnnounceManager legacyAnnounceManager;
    
    @Override
    public void onEnable() {
        // Initialize legacy announce manager first
        legacyAnnounceManager = new AnnounceManager(this);
        
        // Initialize compatibility layer
        compatibilityManager = new AnnouncementCompatibilityManager(
            legacyAnnounceManager, 
            LogManager.getInstance(this)
        );
        
        // Register compatibility manager for dependency injection
        ServiceRegistry.getInstance().registerService(
            AnnouncementCompatibilityManager.class, 
            compatibilityManager
        );
        
        logger.info("RVNKTools initialized with compatibility mode: " + 
                   compatibilityManager.getCurrentMode());
    }
    
    /**
     * Gets the announcement compatibility manager.
     */
    public AnnouncementCompatibilityManager getCompatibilityManager() {
        return compatibilityManager;
    }
    
    /**
     * Gets announcements through the compatibility layer.
     * Maintains backward compatibility for existing code.
     */
    public List<Announcement> getAnnouncements() {
        return compatibilityManager.getAnnouncements();
    }
    
    // Other existing methods can delegate to compatibility manager...
}
```

### Update Existing Commands

```java
// Update AnnouncerCommand to use compatibility manager
public class AnnouncerCommand extends BaseCommand {
    
    private final AnnouncementCompatibilityManager compatibilityManager;
    
    public AnnouncerCommand(RVNKTools plugin) {
        super(plugin);
        this.compatibilityManager = plugin.getCompatibilityManager();
    }
    
    // Add new subcommands for compatibility management
    
    private boolean showCompatibilityStatus(CommandSender sender) {
        MigrationStatus status = compatibilityManager.getMigrationStatus();
        
        sender.sendMessage(ChatFormat.format("&6⚙ Announcement System Compatibility Status"));
        sender.sendMessage(ChatFormat.format("&7Current Mode: &e" + status.getCurrentMode()));
        sender.sendMessage(ChatFormat.format("&7Active Provider: &e" + compatibilityManager.getActiveProviderName()));
        sender.sendMessage(ChatFormat.format("&7System Healthy: " + 
            (compatibilityManager.isHealthy() ? "&aYes" : "&cNo")));
        sender.sendMessage(ChatFormat.format("&7RVNKCore Available: " + 
            (status.isRvnkCoreAvailable() ? "&aYes" : "&cNo")));
        
        if (status.isMigrationPossible()) {
            sender.sendMessage(ChatFormat.format("&a✓ Migration to RVNKCore is available"));
            sender.sendMessage(ChatFormat.format("&7Use &e/announcer migrate &7for migration options"));
        }
        
        if (status.isDataSyncRequired()) {
            sender.sendMessage(ChatFormat.format("&e⚠ Data synchronization may be required"));
        }
        
        return true;
    }
}
```

## Step 6: Configuration Integration

### Update announcements.yml

```yaml
# Add compatibility section to existing configuration
compatibility:
  # Mode: yaml, available, active, hybrid
  mode: "available"
  
  # Migration settings
  migration:
    auto-migrate-on-startup: false
    create-backup-before-migration: true
    validate-after-migration: true
    continue-on-errors: false
    
  # Fallback behavior
  fallback:
    use-yaml-on-rvnkcore-failure: true
    log-fallback-usage: true
    
  # Health check settings
  health-check:
    enabled: true
    interval-seconds: 300
    
# Existing announcement configuration continues...
announcements:
  # ... existing announcements
```

## Validation Checklist

- [ ] Provider interface implemented correctly
- [ ] YAML provider wraps legacy manager properly
- [ ] RVNKCore provider handles DTO conversion
- [ ] Compatibility manager detects modes correctly
- [ ] Fallback mechanisms working
- [ ] Migration status reporting accurate
- [ ] Command integration functional
- [ ] Configuration management working
- [ ] Error handling comprehensive
- [ ] Health checks operational
- [ ] Backward compatibility maintained
- [ ] Documentation updated

## Troubleshooting

### Common Issues

1. **RVNKCore detection fails**: Check plugin loading order and dependencies
2. **DTO conversion errors**: Verify DtoConverter implementation
3. **Fallback not working**: Check exception handling in provider methods
4. **Configuration not loading**: Verify YAML parsing and file permissions
5. **Provider switching fails**: Check mode detection and initialization logic

### Debug Commands

```bash
# Check compatibility status
/announcer status

# Force provider switch (admin only)
/announcer compatibility mode <yaml|rvnkcore|hybrid>

# Health check
/announcer compatibility health

# Provider information
/announcer compatibility provider
```

This implementation guide provides a comprehensive framework for creating a backward-compatible transition layer that maintains existing functionality while enabling migration to the new RVNKCore service architecture.
