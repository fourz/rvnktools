package org.fourz.rvnktools.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnkcore.RVNKCore;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * YAML Data Parser for Phase 1 Migration Framework
 * 
 * Parses existing announcements.yml structure and converts data into
 * intermediate format compatible with RVNKCore database services.
 * 
 * This component handles:
 * - YAML file parsing and validation
 * - Announcement type extraction and conversion
 * - Individual announcement parsing with all attributes
 * - Data structure validation and error reporting
 * 
 * @since Phase 1 Migration Framework
 */
@SuppressWarnings("unused") // Plugin field used for LogManager and data folder access
public class YAMLAnnouncementParser {
    
    private final RVNKCore plugin;
    private final LogManager logger;
    private final File yamlFile;
    
    // Parsed data containers
    private List<AnnouncementTypeDTO> parsedTypes;
    private List<AnnouncementDTO> parsedAnnouncements;
    private Map<String, Object> parsedConfig;
    
    public YAMLAnnouncementParser(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.yamlFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.parsedTypes = new ArrayList<>();
        this.parsedAnnouncements = new ArrayList<>();
        this.parsedConfig = new HashMap<>();
    }
    
    /**
     * Parse the complete YAML file structure asynchronously
     * 
     * @return CompletableFuture containing ParseResult with all extracted data
     */
    public CompletableFuture<ParseResult> parseYAMLFile() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting YAML announcement file parsing...");
            
            try {
                if (!yamlFile.exists()) {
                    throw new MigrationException("YAML file not found: " + yamlFile.getAbsolutePath());
                }
                
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
                
                // Parse announcement types first (required for announcements)
                parseAnnouncementTypes(yaml);
                logger.info("Parsed " + parsedTypes.size() + " announcement types");
                
                // Parse individual announcements
                parseAnnouncements(yaml);
                logger.info("Parsed " + parsedAnnouncements.size() + " announcements");
                
                // Parse configuration settings
                parseConfiguration(yaml);
                logger.info("Parsed configuration settings");
                
                return new ParseResult(parsedTypes, parsedAnnouncements, parsedConfig, true);
                
            } catch (Exception e) {
                logger.error("Failed to parse YAML announcement file", e);
                return new ParseResult(Collections.emptyList(), Collections.emptyList(), 
                                     Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Parse announcement types section from YAML
     */
    private void parseAnnouncementTypes(YamlConfiguration yaml) {
        List<?> typesSection = yaml.getList("announce_types");
        if (typesSection == null) {
            throw new MigrationException("Missing 'announce_types' section in YAML");
        }
        
        for (Object typeObj : typesSection) {
            if (typeObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typeMap = (Map<String, Object>) typeObj;
                
                AnnouncementTypeDTO typeDTO = parseAnnouncementType(typeMap);
                parsedTypes.add(typeDTO);
            }
        }
    }
    
    /**
     * Parse individual announcement type from map data
     */
    private AnnouncementTypeDTO parseAnnouncementType(Map<String, Object> typeMap) {
        String id = (String) typeMap.get("id");
        String prefix = (String) typeMap.get("prefix");
        String suffix = (String) typeMap.get("suffix");
        String permission = (String) typeMap.get("permission");
        
        // Optional fee fields
        Integer listFee = typeMap.containsKey("list_fee") ? 
            ((Number) typeMap.get("list_fee")).intValue() : null;
        Integer weeklyFee = typeMap.containsKey("weekly_fee") ? 
            ((Number) typeMap.get("weekly_fee")).intValue() : null;
        
        if (id == null || prefix == null) {
            throw new MigrationException("Invalid announcement type: missing required fields (id, prefix)");
        }
        
        AnnouncementTypeDTO typeDTO = new AnnouncementTypeDTO();
        typeDTO.setId(id);
        typeDTO.setPrefix(prefix);
        typeDTO.setSuffix(suffix != null ? suffix : "");
        typeDTO.setPermission(permission);
        
        // Set fee information if present
        if (listFee != null) typeDTO.setListFee(listFee);
        if (weeklyFee != null) typeDTO.setWeeklyFee(weeklyFee);
        
        // Set creation timestamp for database compatibility
        typeDTO.setCreatedAt(LocalDateTime.now());
        
        return typeDTO;
    }
    
    /**
     * Parse announcements section from YAML
     */
    private void parseAnnouncements(YamlConfiguration yaml) {
        List<?> announcementsSection = yaml.getList("announcements");
        if (announcementsSection == null) {
            logger.warning("No 'announcements' section found in YAML");
            return;
        }
        
        for (Object announcementObj : announcementsSection) {
            if (announcementObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> announcementMap = (Map<String, Object>) announcementObj;
                
                AnnouncementDTO announcementDTO = parseAnnouncement(announcementMap);
                parsedAnnouncements.add(announcementDTO);
            }
        }
    }
    
    /**
     * Parse individual announcement from map data
     */
    private AnnouncementDTO parseAnnouncement(Map<String, Object> announcementMap) {
        String id = (String) announcementMap.get("id");
        String text = (String) announcementMap.get("text");
        String type = (String) announcementMap.get("type");
        
        if (id == null || text == null || type == null) {
            throw new MigrationException("Invalid announcement: missing required fields (id, text, type)");
        }
        
        AnnouncementDTO dto = new AnnouncementDTO();
        dto.setId(id);
        dto.setMessage(text); // text maps to message field
        dto.setType(type);
        
        // Map optional fields to metadata for migration compatibility
        String permission = (String) announcementMap.get("permission");
        if (permission != null) {
            dto.setMetadata("permission", permission);
        }
        
        Integer recurrence = parseRecurrence((String) announcementMap.get("recurrence"));
        if (recurrence != null) {
            dto.setIntervalSeconds(recurrence * 60); // Convert minutes to seconds
        }
        
        String scheduledDate = parseScheduledDate((String) announcementMap.get("date"));
        if (scheduledDate != null) {
            dto.setMetadata("scheduledDate", scheduledDate);
        }
        
        String scheduledTime = (String) announcementMap.get("time");
        if (scheduledTime != null) {
            dto.setMetadata("scheduledTime", scheduledTime);
        }
        
        String expiration = parseExpiration((String) announcementMap.get("expiration"));
        if (expiration != null) {
            dto.setMetadata("expiration", expiration);
        }
        
        // Handle imported flag (default false for YAML sources)
        Boolean imported = (Boolean) announcementMap.get("imported");
        dto.setMetadata("imported", imported != null ? imported : false);
        
        // Set metadata for migration tracking
        dto.setCreatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now()));
        dto.setMetadata("migratedFrom", "YAML");
        dto.setMetadata("originalId", id); // Preserve original YAML ID
        
        return dto;
    }
    
    /**
     * Parse recurrence string (e.g., "90m", "2h") to minutes
     */
    private Integer parseRecurrence(String recurrence) {
        if (recurrence == null || recurrence.isEmpty()) {
            return null;
        }
        
        try {
            if (recurrence.endsWith("m")) {
                return Integer.parseInt(recurrence.substring(0, recurrence.length() - 1));
            } else if (recurrence.endsWith("h")) {
                return Integer.parseInt(recurrence.substring(0, recurrence.length() - 1)) * 60;
            } else {
                // Assume minutes if no suffix
                return Integer.parseInt(recurrence);
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid recurrence format: " + recurrence);
            return null;
        }
    }
    
    /**
     * Parse scheduled date string (e.g., "09-24", "2024-12-18")
     */
    private String parseScheduledDate(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        
        // Normalize date format for database storage
        if (date.matches("\\d{2}-\\d{2}")) {
            // Monthly recurring format (MM-DD)
            return "yearly-" + date;
        } else if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            // Specific date format (YYYY-MM-DD)
            return date;
        }
        
        return date;
    }
    
    /**
     * Parse expiration string to LocalDateTime
     */
    private String parseExpiration(String expiration) {
        if (expiration == null || expiration.isEmpty()) {
            return null;
        }
        
        try {
            // Handle YYYY-MM-DD format
            if (expiration.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return expiration + " 00:00:00";
            }
        } catch (Exception e) {
            logger.warning("Invalid expiration format: " + expiration);
        }
        
        return expiration;
    }
    
    /**
     * Parse configuration sections (storage, motd, debug)
     */
    private void parseConfiguration(YamlConfiguration yaml) {
        // Parse storage configuration
        ConfigurationSection storageSection = yaml.getConfigurationSection("storage");
        if (storageSection != null) {
            parsedConfig.put("storage", storageSection.getValues(true));
        }
        
        // Parse MOTD configuration
        ConfigurationSection motdSection = yaml.getConfigurationSection("motd");
        if (motdSection != null) {
            parsedConfig.put("motd", motdSection.getValues(true));
        }
        
        // Parse debug configuration
        ConfigurationSection debugSection = yaml.getConfigurationSection("debug");
        if (debugSection != null) {
            parsedConfig.put("debug", debugSection.getValues(true));
        }
    }
    
    /**
     * Validate parsed data integrity
     * 
     * @return ValidationResult with success status and any issues found
     */
    public ValidationResult validateParsedData() {
        List<String> issues = new ArrayList<>();
        
        // Validate announcement types
        Set<String> typeIds = new HashSet<>();
        for (AnnouncementTypeDTO type : parsedTypes) {
            if (typeIds.contains(type.getId())) {
                issues.add("Duplicate announcement type ID: " + type.getId());
            }
            typeIds.add(type.getId());
        }
        
        // Validate announcements reference valid types
        for (AnnouncementDTO announcement : parsedAnnouncements) {
            if (!typeIds.contains(announcement.getType())) {
                issues.add("Announcement '" + announcement.getId() + 
                          "' references unknown type: " + announcement.getType());
            }
        }
        
        // Validate unique announcement IDs
        Set<String> announcementIds = new HashSet<>();
        for (AnnouncementDTO announcement : parsedAnnouncements) {
            if (announcementIds.contains(announcement.getId())) {
                issues.add("Duplicate announcement ID: " + announcement.getId());
            }
            announcementIds.add(announcement.getId());
        }
        
        boolean isValid = issues.isEmpty();
        return new ValidationResult(isValid, issues);
    }
    
    /**
     * Result container for parse operations
     */
    public static class ParseResult {
        private final List<AnnouncementTypeDTO> types;
        private final List<AnnouncementDTO> announcements;
        private final Map<String, Object> configuration;
        private final boolean success;
        private final String errorMessage;
        
        public ParseResult(List<AnnouncementTypeDTO> types, List<AnnouncementDTO> announcements, 
                          Map<String, Object> configuration, boolean success) {
            this(types, announcements, configuration, success, null);
        }
        
        public ParseResult(List<AnnouncementTypeDTO> types, List<AnnouncementDTO> announcements, 
                          Map<String, Object> configuration, boolean success, String errorMessage) {
            this.types = types;
            this.announcements = announcements;
            this.configuration = configuration;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public List<AnnouncementTypeDTO> getTypes() { return types; }
        public List<AnnouncementDTO> getAnnouncements() { return announcements; }
        public Map<String, Object> getConfiguration() { return configuration; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Result container for validation operations
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> issues;
        
        public ValidationResult(boolean valid, List<String> issues) {
            this.valid = valid;
            this.issues = issues;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getIssues() { return issues; }
    }
    
    /**
     * Custom exception for migration parsing errors
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
