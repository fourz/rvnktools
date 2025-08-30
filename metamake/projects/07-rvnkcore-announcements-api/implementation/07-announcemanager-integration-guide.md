# Implementation Guide: AnnounceManager Integration and Hook Points

**Guide ID**: 07-announcemanager-integration-guide  
**Related Feature**: [Legacy Compatibility Layer](../features/05-legacy-compatibility.md)  
**Prerequisites**: Service layer implemented, compatibility layer ready  
**Estimated Time**: 2-3 days

## Overview

This guide provides comprehensive instructions for integrating RVNKCore announcements with the existing AnnounceManager, establishing hook points for all data access methods, and implementing the deprecation strategy for legacy YAML-based operations.

## Current AnnounceManager Analysis

### Method Mapping and Hook Requirements

**Critical Data Access Methods Requiring Hooks:**

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnktools/announceManager/AnnounceManager.java

public class AnnounceManager {
    
    // === DATA RETRIEVAL HOOKS ===
    
    /**
     * HOOK REQUIRED: Primary announcement retrieval method
     * Current: Returns List<Announcement> from YAML/memory
     * Target: Route to AnnouncementService.getAllAnnouncements()
     */
    public List<Announcement> getAnnouncements() {
        // This method is called by:
        // - AnnounceCommand for listing
        // - REST API endpoints
        // - Scheduler for broadcast operations
        // - Configuration validation
        return announcements.values().stream().collect(Collectors.toList());
    }
    
    /**
     * HOOK REQUIRED: Type-filtered announcement retrieval
     * Current: Filters in-memory announcements by type
     * Target: Route to AnnouncementService.getAnnouncementsByType()
     */
    public List<Announcement> getAnnouncements(String type) {
        // Called by:
        // - Type-specific broadcasting
        // - Administrative commands
        // - Performance analytics
        return announcements.values().stream()
            .filter(a -> a.getType().equals(type))
            .collect(Collectors.toList());
    }
    
    /**
     * HOOK REQUIRED: Single announcement retrieval
     * Current: Direct HashMap lookup
     * Target: Route to AnnouncementService.getAnnouncement()
     */
    public Announcement getAnnouncement(String id) {
        // Called by:
        // - Individual announcement operations
        // - Update and delete operations
        // - Broadcasting specific announcements
        return announcements.get(id);
    }
    
    // === DATA MODIFICATION HOOKS ===
    
    /**
     * HOOK REQUIRED: Announcement creation
     * Current: Adds to HashMap and saves to YAML
     * Target: Route to AnnouncementService.createAnnouncement()
     */
    public boolean addAnnouncement(Announcement announcement) {
        // Called by:
        // - AnnounceCommand add operations
        // - Migration processes
        // - Bulk import operations
        // - REST API endpoints
    }
    
    /**
     * HOOK REQUIRED: Announcement deletion
     * Current: Removes from HashMap and saves to YAML
     * Target: Route to AnnouncementService.deleteAnnouncement()
     */
    public boolean deleteAnnouncement(String id) {
        // Called by:
        // - AnnounceCommand delete operations
        // - Administrative cleanup
        // - Migration rollback
        // - REST API endpoints
    }
    
    /**
     * HOOK REQUIRED: Announcement updates
     * Current: Direct HashMap modification and YAML save
     * Target: Route to AnnouncementService.updateAnnouncement()
     */
    public boolean updateAnnouncement(String id, String newMessage) {
        // Called by:
        // - Message editing operations
        // - Bulk update operations
        // - Migration data transformation
    }
    
    // === CONFIGURATION MANAGEMENT HOOKS ===
    
    /**
     * HOOK REQUIRED: Configuration reload
     * Current: Reloads YAML file and rebuilds in-memory cache
     * Target: Route to AnnouncementService.reloadConfiguration()
     */
    public void reloadConfig() {
        // Called by:
        // - Plugin reload operations
        // - Administrative commands
        // - Configuration change detection
    }
    
    /**
     * HOOK REQUIRED: Configuration save
     * Current: Writes HashMap to YAML file
     * Target: Route to AnnouncementService.saveConfiguration()
     */
    public void saveConfig() {
        // Called by:
        // - Shutdown operations
        // - Periodic saves
        // - Administrative force saves
    }
    
    // === PLAYER PREFERENCE HOOKS ===
    
    /**
     * HOOK REQUIRED: Player preference toggle
     * Current: Modifies player preferences and saves to YAML
     * Target: Route to AnnouncementService.togglePlayerDisabledType()
     */
    public void toggleAnnouncementType(Player player, String type) {
        // Called by:
        // - Player preference commands
        // - Administrative player management
        // - Bulk preference updates
    }
    
    /**
     * HOOK REQUIRED: Player disabled types retrieval
     * Current: Returns array from YAML configuration
     * Target: Route to AnnouncementService.getPlayerDisabledTypes()
     */
    public String[] getPlayerDisabledAnnouncementTypes(Player player) {
        // Called by:
        // - Broadcasting logic (filtering)
        // - Player preference display
        // - Administrative queries
    }
}
```

## Step 1: Implement Compatibility Hooks

### 1.1 Create AnnounceManagerCompatibilityHooks

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnktools/announceManager/compatibility/AnnounceManagerCompatibilityHooks.java

package org.fourz.rvnktools.announceManager.compatibility;

import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Compatibility hooks that intercept AnnounceManager calls and route them
 * to RVNKCore services when available, falling back to YAML operations
 * when RVNKCore is not available or configured to use YAML mode.
 */
public class AnnounceManagerCompatibilityHooks {
    
    private final AnnounceManager legacyManager;
    private final Optional<AnnouncementService> rvnkCoreService;
    private final AnnouncementDTOConverter converter;
    private final LogManager logger;
    private final CompatibilityMode mode;
    
    public enum CompatibilityMode {
        YAML_ONLY,           // Use YAML exclusively
        RVNKCORE_PREFERRED,  // Use RVNKCore when available, fallback to YAML
        RVNKCORE_ONLY,       // Use RVNKCore exclusively, fail if unavailable
        HYBRID               // Use both systems with synchronization
    }
    
    public AnnounceManagerCompatibilityHooks(AnnounceManager legacyManager, 
                                           Optional<AnnouncementService> rvnkCoreService,
                                           LogManager logger) {
        this.legacyManager = legacyManager;
        this.rvnkCoreService = rvnkCoreService;
        this.converter = new AnnouncementDTOConverter();
        this.logger = logger;
        this.mode = detectCompatibilityMode();
        
        logger.info("AnnounceManager compatibility hooks initialized in mode: " + mode);
    }
    
    // === DATA RETRIEVAL HOOKS ===
    
    /**
     * Hook for getAnnouncements() - routes to appropriate data source
     */
    public List<Announcement> hookGetAnnouncements() {
        if (shouldUseRVNKCore()) {
            try {
                List<AnnouncementDTO> dtos = rvnkCoreService.get().getAllAnnouncements().get();
                return dtos.stream()
                    .map(converter::fromDTO)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                logger.error("Failed to get announcements from RVNKCore, falling back to YAML", e);
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // Fallback to legacy YAML method
        return legacyManager.getAnnouncementsFromYaml();
    }
    
    /**
     * Hook for getAnnouncements(type) - routes to appropriate data source
     */
    public List<Announcement> hookGetAnnouncementsByType(String type) {
        if (shouldUseRVNKCore()) {
            try {
                List<AnnouncementDTO> dtos = rvnkCoreService.get().getAnnouncementsByType(type).get();
                return dtos.stream()
                    .map(converter::fromDTO)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                logger.error("Failed to get announcements by type from RVNKCore, falling back to YAML", e);
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // Fallback to legacy filtered method
        return legacyManager.getAnnouncementsFromYaml().stream()
            .filter(a -> a.getType().equals(type))
            .collect(Collectors.toList());
    }
    
    /**
     * Hook for getAnnouncement(id) - routes to appropriate data source
     */
    public Announcement hookGetAnnouncement(String id) {
        if (shouldUseRVNKCore()) {
            try {
                Optional<AnnouncementDTO> dto = rvnkCoreService.get().getAnnouncement(id).get();
                return dto.map(converter::fromDTO).orElse(null);
            } catch (Exception e) {
                logger.error("Failed to get announcement from RVNKCore, falling back to YAML", e);
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // Fallback to legacy HashMap lookup
        return legacyManager.getAnnouncementFromYaml(id);
    }
    
    // === DATA MODIFICATION HOOKS ===
    
    /**
     * Hook for addAnnouncement() - routes to appropriate data sink
     */
    public boolean hookAddAnnouncement(Announcement announcement) {
        boolean success = true;
        
        if (shouldUseRVNKCore()) {
            try {
                AnnouncementDTO dto = converter.toDTO(announcement);
                String id = rvnkCoreService.get().createAnnouncement(dto).get();
                success = id != null;
                
                if (success) {
                    logger.debug("Added announcement to RVNKCore: " + id);
                }
            } catch (Exception e) {
                logger.error("Failed to add announcement to RVNKCore", e);
                success = false;
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also save to YAML
        if (mode == CompatibilityMode.HYBRID || (mode == CompatibilityMode.RVNKCORE_PREFERRED && !success)) {
            boolean yamlSuccess = legacyManager.addAnnouncementToYaml(announcement);
            success = success || yamlSuccess;
        }
        
        return success;
    }
    
    /**
     * Hook for deleteAnnouncement() - routes to appropriate data sink
     */
    public boolean hookDeleteAnnouncement(String id) {
        boolean success = true;
        
        if (shouldUseRVNKCore()) {
            try {
                success = rvnkCoreService.get().deleteAnnouncement(id).get();
                
                if (success) {
                    logger.debug("Deleted announcement from RVNKCore: " + id);
                }
            } catch (Exception e) {
                logger.error("Failed to delete announcement from RVNKCore", e);
                success = false;
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also remove from YAML
        if (mode == CompatibilityMode.HYBRID || (mode == CompatibilityMode.RVNKCORE_PREFERRED && !success)) {
            boolean yamlSuccess = legacyManager.deleteAnnouncementFromYaml(id);
            success = success || yamlSuccess;
        }
        
        return success;
    }
    
    /**
     * Hook for updateAnnouncement() - routes to appropriate data sink
     */
    public boolean hookUpdateAnnouncement(String id, String newMessage) {
        boolean success = true;
        
        if (shouldUseRVNKCore()) {
            try {
                // Get existing announcement
                Optional<AnnouncementDTO> existing = rvnkCoreService.get().getAnnouncement(id).get();
                if (existing.isPresent()) {
                    AnnouncementDTO updated = existing.get().toBuilder()
                        .message(newMessage)
                        .build();
                    
                    rvnkCoreService.get().updateAnnouncement(updated).get();
                    logger.debug("Updated announcement in RVNKCore: " + id);
                } else {
                    success = false;
                }
            } catch (Exception e) {
                logger.error("Failed to update announcement in RVNKCore", e);
                success = false;
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also update YAML
        if (mode == CompatibilityMode.HYBRID || (mode == CompatibilityMode.RVNKCORE_PREFERRED && !success)) {
            boolean yamlSuccess = legacyManager.updateAnnouncementInYaml(id, newMessage);
            success = success || yamlSuccess;
        }
        
        return success;
    }
    
    // === CONFIGURATION MANAGEMENT HOOKS ===
    
    /**
     * Hook for reloadConfig() - routes to appropriate configuration source
     */
    public void hookReloadConfig() {
        if (shouldUseRVNKCore()) {
            try {
                rvnkCoreService.get().reloadConfiguration().get();
                logger.info("Reloaded configuration via RVNKCore");
            } catch (Exception e) {
                logger.error("Failed to reload configuration via RVNKCore", e);
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also reload YAML
        if (mode == CompatibilityMode.HYBRID || mode == CompatibilityMode.RVNKCORE_PREFERRED || mode == CompatibilityMode.YAML_ONLY) {
            legacyManager.reloadYamlConfig();
            logger.info("Reloaded YAML configuration");
        }
    }
    
    /**
     * Hook for saveConfig() - routes to appropriate configuration sink
     */
    public void hookSaveConfig() {
        if (shouldUseRVNKCore()) {
            try {
                rvnkCoreService.get().saveConfiguration().get();
                logger.debug("Saved configuration via RVNKCore");
            } catch (Exception e) {
                logger.error("Failed to save configuration via RVNKCore", e);
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also save YAML
        if (mode == CompatibilityMode.HYBRID || mode == CompatibilityMode.RVNKCORE_PREFERRED || mode == CompatibilityMode.YAML_ONLY) {
            legacyManager.saveYamlConfig();
            logger.debug("Saved YAML configuration");
        }
    }
    
    // === PLAYER PREFERENCE HOOKS ===
    
    /**
     * Hook for toggleAnnouncementType() - routes to appropriate preference store
     */
    public void hookToggleAnnouncementType(Player player, String type) {
        if (shouldUseRVNKCore()) {
            try {
                UUID playerId = player.getUniqueId();
                rvnkCoreService.get().togglePlayerDisabledType(playerId, type).get();
                logger.debug("Toggled announcement type for player via RVNKCore: " + player.getName());
            } catch (Exception e) {
                logger.error("Failed to toggle announcement type via RVNKCore", e);
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // For HYBRID mode or fallback scenarios, also update YAML
        if (mode == CompatibilityMode.HYBRID || mode == CompatibilityMode.RVNKCORE_PREFERRED || mode == CompatibilityMode.YAML_ONLY) {
            legacyManager.toggleAnnouncementTypeInYaml(player, type);
            logger.debug("Toggled announcement type for player in YAML: " + player.getName());
        }
    }
    
    /**
     * Hook for getPlayerDisabledAnnouncementTypes() - routes to appropriate preference source
     */
    public String[] hookGetPlayerDisabledAnnouncementTypes(Player player) {
        if (shouldUseRVNKCore()) {
            try {
                UUID playerId = player.getUniqueId();
                List<String> disabledTypes = rvnkCoreService.get().getPlayerDisabledTypes(playerId).get();
                return disabledTypes.toArray(new String[0]);
            } catch (Exception e) {
                logger.error("Failed to get player disabled types from RVNKCore, falling back to YAML", e);
                
                if (mode == CompatibilityMode.RVNKCORE_ONLY) {
                    throw new RuntimeException("RVNKCore required but failed", e);
                }
            }
        }
        
        // Fallback to legacy YAML method
        return legacyManager.getPlayerDisabledAnnouncementTypesFromYaml(player);
    }
    
    // === UTILITY METHODS ===
    
    private boolean shouldUseRVNKCore() {
        return rvnkCoreService.isPresent() && 
               (mode == CompatibilityMode.RVNKCORE_PREFERRED || 
                mode == CompatibilityMode.RVNKCORE_ONLY || 
                mode == CompatibilityMode.HYBRID);
    }
    
    private CompatibilityMode detectCompatibilityMode() {
        // Check configuration for mode preference
        String configMode = getConfigurationMode();
        
        switch (configMode.toLowerCase()) {
            case "yaml":
                return CompatibilityMode.YAML_ONLY;
            case "rvnkcore":
                return rvnkCoreService.isPresent() ? CompatibilityMode.RVNKCORE_ONLY : CompatibilityMode.YAML_ONLY;
            case "hybrid":
                return rvnkCoreService.isPresent() ? CompatibilityMode.HYBRID : CompatibilityMode.YAML_ONLY;
            case "preferred":
            default:
                return rvnkCoreService.isPresent() ? CompatibilityMode.RVNKCORE_PREFERRED : CompatibilityMode.YAML_ONLY;
        }
    }
    
    private String getConfigurationMode() {
        // Read from plugin configuration
        try {
            return legacyManager.getConfig().getConfigurationSection("announcement_storage").getString("mode", "preferred");
        } catch (Exception e) {
            logger.warning("Failed to read storage mode from configuration, using default: preferred");
            return "preferred";
        }
    }
    
    public CompatibilityMode getMode() {
        return mode;
    }
    
    public boolean isRVNKCoreAvailable() {
        return rvnkCoreService.isPresent();
    }
}
```

## Step 2: Integrate Hooks into AnnounceManager

### 2.1 Modify AnnounceManager Constructor

```java
// In AnnounceManager.java - Add compatibility hooks initialization

public class AnnounceManager {
    private static final String CLASS_NAME = "AnnounceManager";
    private final RVNKLogger logger;
    private final RVNKTools plugin;
    private final AnnounceConfig announceConfig;
    private final AnnounceScheduler announceScheduler;
    private final ChatServiceInterface chatService;
    private final Map<String, Announcement> announcements = new ConcurrentHashMap<>();
    
    // ADD: Compatibility hooks
    private final AnnounceManagerCompatibilityHooks compatibilityHooks;
    private boolean usingPlaceholderAPI;

    public AnnounceManager(RVNKTools plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
        logger.info("Enabling AnnounceManager.");
        this.plugin = plugin;
        this.chatService = new ChatService();
        
        // Initialize compatibility hooks
        Optional<AnnouncementService> rvnkCoreService = detectRVNKCoreService();
        this.compatibilityHooks = new AnnounceManagerCompatibilityHooks(
            this, rvnkCoreService, logger);
        
        logger.info("AnnounceManager initialized with compatibility mode: " + 
                   compatibilityHooks.getMode());
        
        // Rest of initialization...
        this.announceConfig = new AnnounceConfig(this, plugin);
        this.announceScheduler = new AnnounceScheduler(this, plugin);
        
        // Detect PlaceholderAPI
        this.usingPlaceholderAPI = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }
    
    private Optional<AnnouncementService> detectRVNKCoreService() {
        try {
            // Check if RVNKCore plugin is loaded
            Plugin rvnkCorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
            if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
                logger.info("RVNKCore plugin not available");
                return Optional.empty();
            }
            
            // Get service from RVNKCore
            RVNKCore rvnkCore = RVNKCore.getInstance();
            if (rvnkCore == null) {
                logger.warning("RVNKCore instance not available");
                return Optional.empty();
            }
            
            Optional<AnnouncementService> service = rvnkCore.getServiceRegistry()
                .getService(AnnouncementService.class);
            
            if (service.isPresent()) {
                logger.info("RVNKCore AnnouncementService detected and available");
            } else {
                logger.warning("RVNKCore available but AnnouncementService not registered");
            }
            
            return service;
            
        } catch (Exception e) {
            logger.warning("Failed to detect RVNKCore service: " + e.getMessage());
            return Optional.empty();
        }
    }
}
```

### 2.2 Update Core Data Access Methods

```java
// In AnnounceManager.java - Replace direct data access with hook calls

    /**
     * Gets all announcements - HOOKED to compatibility layer
     */
    public List<Announcement> getAnnouncements() {
        return compatibilityHooks.hookGetAnnouncements();
    }
    
    /**
     * Gets announcements by type - HOOKED to compatibility layer
     */
    public List<Announcement> getAnnouncements(String type) {
        return compatibilityHooks.hookGetAnnouncementsByType(type);
    }
    
    /**
     * Gets single announcement - HOOKED to compatibility layer
     */
    public Announcement getAnnouncement(String id) {
        return compatibilityHooks.hookGetAnnouncement(id);
    }
    
    /**
     * Adds announcement - HOOKED to compatibility layer
     */
    public boolean addAnnouncement(Announcement announcement) {
        return compatibilityHooks.hookAddAnnouncement(announcement);
    }
    
    /**
     * Deletes announcement - HOOKED to compatibility layer
     */
    public boolean deleteAnnouncement(String id) {
        return compatibilityHooks.hookDeleteAnnouncement(id);
    }
    
    /**
     * Updates announcement - HOOKED to compatibility layer
     */
    public boolean updateAnnouncement(String id, String newMessage) {
        return compatibilityHooks.hookUpdateAnnouncement(id, newMessage);
    }
    
    /**
     * Reloads configuration - HOOKED to compatibility layer
     */
    public void reloadConfig() {
        compatibilityHooks.hookReloadConfig();
    }
    
    /**
     * Saves configuration - HOOKED to compatibility layer
     */
    public void saveConfig() {
        compatibilityHooks.hookSaveConfig();
    }
    
    /**
     * Toggles player announcement type - HOOKED to compatibility layer
     */
    public void toggleAnnouncementType(Player player, String type) {
        compatibilityHooks.hookToggleAnnouncementType(player, type);
    }
    
    /**
     * Gets player disabled types - HOOKED to compatibility layer
     */
    public String[] getPlayerDisabledAnnouncementTypes(Player player) {
        return compatibilityHooks.hookGetPlayerDisabledAnnouncementTypes(player);
    }
```

## Step 3: Create Legacy Method Wrappers

### 3.1 Add Legacy YAML Methods

```java
// In AnnounceManager.java - Add methods that directly access YAML (for fallback)

    // === LEGACY YAML ACCESS METHODS ===
    // These methods provide direct YAML access for compatibility hooks
    
    /**
     * Direct YAML announcement retrieval (for compatibility fallback)
     */
    protected List<Announcement> getAnnouncementsFromYaml() {
        return new ArrayList<>(announcements.values());
    }
    
    /**
     * Direct YAML announcement lookup (for compatibility fallback)
     */
    protected Announcement getAnnouncementFromYaml(String id) {
        return announcements.get(id);
    }
    
    /**
     * Direct YAML announcement addition (for compatibility fallback)
     */
    protected boolean addAnnouncementToYaml(Announcement announcement) {
        announcements.put(announcement.getId(), announcement);
        return announceConfig.saveConfig();
    }
    
    /**
     * Direct YAML announcement deletion (for compatibility fallback)
     */
    protected boolean deleteAnnouncementFromYaml(String id) {
        boolean existed = announcements.remove(id) != null;
        if (existed) {
            announceConfig.saveConfig();
        }
        return existed;
    }
    
    /**
     * Direct YAML announcement update (for compatibility fallback)
     */
    protected boolean updateAnnouncementInYaml(String id, String newMessage) {
        Announcement announcement = announcements.get(id);
        if (announcement != null) {
            announcement.setMessage(newMessage);
            return announceConfig.saveConfig();
        }
        return false;
    }
    
    /**
     * Direct YAML configuration reload (for compatibility fallback)
     */
    protected void reloadYamlConfig() {
        announceConfig.reloadConfig();
    }
    
    /**
     * Direct YAML configuration save (for compatibility fallback)
     */
    protected void saveYamlConfig() {
        announceConfig.saveConfig();
    }
    
    /**
     * Direct YAML player preference toggle (for compatibility fallback)
     */
    protected void toggleAnnouncementTypeInYaml(Player player, String type) {
        // Legacy implementation for player preferences
        announceConfig.togglePlayerDisabledType(player.getUniqueId(), type);
    }
    
    /**
     * Direct YAML player disabled types retrieval (for compatibility fallback)
     */
    protected String[] getPlayerDisabledAnnouncementTypesFromYaml(Player player) {
        // Legacy implementation for player preferences
        return announceConfig.getPlayerDisabledTypes(player.getUniqueId());
    }
```

## Step 4: Deprecation Strategy Implementation

### 4.1 Add Deprecation Warnings

```java
// In AnnounceManager.java - Add deprecation markers and warnings

    /**
     * @deprecated This method directly manipulates the announcement list and will be removed in v2.2.0.
     * Use {@link AnnouncementService#batchUpdateAnnouncements(List)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public void setAnnouncements(List<Announcement> announcementList) {
        logger.warning("setAnnouncements() is deprecated and will be removed in v2.2.0. " +
                      "Use AnnouncementService.batchUpdateAnnouncements() instead.");
        
        // Provide backward compatibility for now
        announcements.clear();
        for (Announcement announcement : announcementList) {
            announcements.put(announcement.getId(), announcement);
        }
        
        // If using RVNKCore, also sync to database
        if (compatibilityHooks.isRVNKCoreAvailable()) {
            logger.info("Syncing deprecated setAnnouncements() call to RVNKCore database");
            for (Announcement announcement : announcementList) {
                compatibilityHooks.hookAddAnnouncement(announcement);
            }
        }
    }
    
    /**
     * @deprecated This method is used for YAML migration tracking and will be removed in v2.2.0.
     * Use {@link AnnouncementService#updateMetadata(String, String, Object)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public void setAnnouncementImported(String id) {
        logger.warning("setAnnouncementImported() is deprecated and will be removed in v2.2.0. " +
                      "Use AnnouncementService.updateMetadata() instead.");
        
        Announcement announcement = getAnnouncement(id);
        if (announcement != null) {
            announcement.setImported(true);
            updateAnnouncement(id, announcement.getMessage()); // Trigger hooks
        }
    }
    
    /**
     * @deprecated This method is used for YAML migration tracking and will be removed in v2.2.0.
     * Use {@link AnnouncementService#getMetadata(String, String)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public boolean isAnnouncementImported(String id) {
        logger.warning("isAnnouncementImported() is deprecated and will be removed in v2.2.0. " +
                      "Use AnnouncementService.getMetadata() instead.");
        
        Announcement announcement = getAnnouncement(id);
        return announcement != null && announcement.isImported();
    }
```

## Step 5: Configuration Integration

### 5.1 Update Configuration File

```yaml
# In announcements.yml - Add storage configuration section

# Announcement storage configuration
announcement_storage:
  # Storage mode: yaml, rvnkcore, preferred, hybrid
  # - yaml: Use YAML files exclusively
  # - rvnkcore: Use RVNKCore database exclusively (fails if unavailable)
  # - preferred: Use RVNKCore when available, fallback to YAML
  # - hybrid: Use both systems with synchronization
  mode: "preferred"
  
  # Database fallback settings
  database_fallback:
    enabled: true
    timeout_seconds: 10
    retry_attempts: 3
    
  # Synchronization settings (for hybrid mode)
  synchronization:
    enabled: true
    sync_interval_minutes: 5
    conflict_resolution: "database_wins" # database_wins, yaml_wins, manual
    
  # Migration settings
  migration:
    auto_migrate_on_detection: false
    backup_before_migration: true
    validate_after_migration: true

# Rest of existing announcements configuration...
announcements:
  # Existing announcement definitions...
```

## Step 6: Testing Integration Points

### 6.1 Integration Test Suite

```java
// Location: toolkitplugin/src/test/java/org/fourz/rvnktools/announceManager/compatibility/

@TestMethodOrder(OrderAnnotation.class)
class AnnounceManagerIntegrationTest {
    
    private static RVNKCoreTestBootstrap testBootstrap;
    private static AnnounceManager announceManager;
    private static AnnouncementService announcementService;
    
    @BeforeAll
    static void setUpClass() throws Exception {
        // Initialize test environment with both systems
        testBootstrap = new RVNKCoreTestBootstrap();
        testBootstrap.initializeTestEnvironment();
        
        // Get RVNKCore service
        ServiceRegistry registry = testBootstrap.getServiceRegistry();
        announcementService = registry.getService(AnnouncementService.class)
            .orElseThrow(() -> new IllegalStateException("AnnouncementService not available"));
        
        // Initialize AnnounceManager with RVNKCore available
        RVNKTools mockPlugin = testBootstrap.getMockPlugin();
        announceManager = new AnnounceManager(mockPlugin);
    }
    
    @Test
    @Order(1)
    void testHookDetection() {
        assertThat(announceManager.compatibilityHooks.isRVNKCoreAvailable()).isTrue();
        assertThat(announceManager.compatibilityHooks.getMode())
            .isIn(CompatibilityMode.RVNKCORE_PREFERRED, CompatibilityMode.HYBRID);
    }
    
    @Test
    @Order(2)
    void testDataRetrievalHooks() throws Exception {
        // Create test data via RVNKCore service
        AnnouncementDTO testAnnouncement = AnnouncementDTO.builder()
            .id("hook-test-1")
            .message("Hook test announcement")
            .type("test")
            .active(true)
            .build();
        
        String id = announcementService.createAnnouncement(testAnnouncement).get(5, TimeUnit.SECONDS);
        
        // Retrieve via AnnounceManager hooks
        Announcement retrieved = announceManager.getAnnouncement(id);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getMessage()).isEqualTo("Hook test announcement");
        
        // Test list retrieval
        List<Announcement> allAnnouncements = announceManager.getAnnouncements();
        assertThat(allAnnouncements).isNotEmpty();
        assertThat(allAnnouncements.stream().anyMatch(a -> a.getId().equals(id))).isTrue();
        
        // Test type-filtered retrieval
        List<Announcement> testAnnouncements = announceManager.getAnnouncements("test");
        assertThat(testAnnouncements).hasSize(1);
        assertThat(testAnnouncements.get(0).getId()).isEqualTo(id);
    }
    
    @Test
    @Order(3)
    void testDataModificationHooks() throws Exception {
        // Test creation via AnnounceManager hook
        Announcement newAnnouncement = new Announcement();
        newAnnouncement.setId("hook-create-test");
        newAnnouncement.setMessage("Created via hook");
        newAnnouncement.setType("test");
        newAnnouncement.setActive(true);
        
        boolean created = announceManager.addAnnouncement(newAnnouncement);
        assertThat(created).isTrue();
        
        // Verify it exists in RVNKCore
        Optional<AnnouncementDTO> inCore = announcementService.getAnnouncement("hook-create-test")
            .get(5, TimeUnit.SECONDS);
        assertThat(inCore).isPresent();
        assertThat(inCore.get().getMessage()).isEqualTo("Created via hook");
        
        // Test update via AnnounceManager hook
        boolean updated = announceManager.updateAnnouncement("hook-create-test", "Updated via hook");
        assertThat(updated).isTrue();
        
        // Verify update in RVNKCore
        Optional<AnnouncementDTO> afterUpdate = announcementService.getAnnouncement("hook-create-test")
            .get(5, TimeUnit.SECONDS);
        assertThat(afterUpdate.get().getMessage()).isEqualTo("Updated via hook");
        
        // Test deletion via AnnounceManager hook
        boolean deleted = announceManager.deleteAnnouncement("hook-create-test");
        assertThat(deleted).isTrue();
        
        // Verify deletion in RVNKCore
        Optional<AnnouncementDTO> afterDelete = announcementService.getAnnouncement("hook-create-test")
            .get(5, TimeUnit.SECONDS);
        assertThat(afterDelete).isEmpty();
    }
    
    @Test
    @Order(4)
    void testFallbackBehavior() throws Exception {
        // Simulate RVNKCore unavailability
        // This would require mocking the service to throw exceptions
        
        // For now, test YAML_ONLY mode behavior
        AnnounceManagerCompatibilityHooks yamlOnlyHooks = new AnnounceManagerCompatibilityHooks(
            announceManager, Optional.empty(), announceManager.logger);
        
        assertThat(yamlOnlyHooks.getMode()).isEqualTo(CompatibilityMode.YAML_ONLY);
        assertThat(yamlOnlyHooks.isRVNKCoreAvailable()).isFalse();
        
        // Test that YAML fallback works
        List<Announcement> yamlAnnouncements = yamlOnlyHooks.hookGetAnnouncements();
        assertThat(yamlAnnouncements).isNotNull(); // Should not fail
    }
    
    @Test
    @Order(5)
    void testDeprecatedMethodWarnings() {
        // Capture log output to verify deprecation warnings
        TestLogCapture logCapture = new TestLogCapture();
        
        // Call deprecated method
        announceManager.setAnnouncementImported("test-id");
        
        // Verify warning was logged
        assertThat(logCapture.getWarnings())
            .anyMatch(msg -> msg.contains("setAnnouncementImported() is deprecated"));
    }
}
```

## Completion Checklist

### Hook Implementation Verification

- [ ] **Data Retrieval Hooks**: All `get*()` methods route through compatibility layer
- [ ] **Data Modification Hooks**: All `add*()`, `delete*()`, `update*()` methods route through compatibility layer
- [ ] **Configuration Hooks**: `reloadConfig()` and `saveConfig()` methods route through compatibility layer
- [ ] **Player Preference Hooks**: Player disable/enable methods route through compatibility layer
- [ ] **Fallback Behavior**: YAML fallback works when RVNKCore unavailable
- [ ] **Mode Detection**: Compatibility mode properly detected based on configuration and availability

### Deprecation Strategy Verification

- [ ] **Deprecation Annotations**: All deprecated methods properly annotated with version info
- [ ] **Deprecation Warnings**: Runtime warnings logged when deprecated methods called
- [ ] **Migration Guidance**: Clear documentation on replacement methods and migration paths
- [ ] **Backward Compatibility**: Deprecated methods still functional during transition period

### Testing Verification

- [ ] **Integration Tests**: Full hook integration tested with both systems
- [ ] **Fallback Tests**: YAML fallback behavior verified when RVNKCore unavailable
- [ ] **Mode Tests**: All compatibility modes tested and functional
- [ ] **Performance Tests**: Hook overhead minimal and within acceptable limits

### Documentation Verification

- [ ] **Hook Documentation**: All hook points documented with before/after behavior
- [ ] **Migration Guide**: Clear migration path for each deprecated method
- [ ] **Configuration Guide**: Storage mode options and settings documented
- [ ] **Troubleshooting Guide**: Common issues and resolutions documented

This integration establishes comprehensive hook points for all AnnounceManager data access methods while providing smooth migration paths and maintaining backward compatibility during the transition to RVNKCore.
