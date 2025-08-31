package org.fourz.rvnktools.migration;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Data Transformation Service for Phase 1 Migration Framework
 * 
 * Transforms parsed YAML data into RVNKCore-compatible format with:
 * - Data validation and normalization
 * - Format conversion (timestamps, intervals, etc.)
 * - Business logic application
 * - Error handling and reporting
 * 
 * This service bridges the gap between YAML structure and database schema,
 * ensuring data integrity throughout the migration process.
 * 
 * @since Phase 1 Migration Framework
 */
@SuppressWarnings("unused") // Fields used for future enhancements and LogManager access
public class DataTransformationService {
    
    private final RVNKTools plugin;
    private final LogManager logger;
    
    // Transformation statistics
    private int typesTransformed = 0;
    private int announcementsTransformed = 0;
    private int transformationErrors = 0;
    private final List<String> validationIssues;
    
    // Date/time formatting patterns
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]");
    
    public DataTransformationService(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.validationIssues = new ArrayList<>();
    }
    
    /**
     * Transform parsed YAML data to RVNKCore format asynchronously
     * 
     * @param parseResult The parsed YAML data from YAMLAnnouncementParser
     * @return CompletableFuture containing TransformationResult
     */
    public CompletableFuture<TransformationResult> transformData(YAMLAnnouncementParser.ParseResult parseResult) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting data transformation for migration...");
            
            try {
                resetStatistics();
                
                if (!parseResult.isSuccess()) {
                    throw new TransformationException("Cannot transform data from failed parse result: " + 
                                                   parseResult.getErrorMessage());
                }
                
                // Transform announcement types first
                List<AnnouncementTypeDTO> transformedTypes = transformAnnouncementTypes(parseResult.getTypes());
                logger.info("Transformed " + transformedTypes.size() + " announcement types");
                
                // Transform announcements with type validation
                List<AnnouncementDTO> transformedAnnouncements = transformAnnouncements(
                    parseResult.getAnnouncements(), transformedTypes);
                logger.info("Transformed " + transformedAnnouncements.size() + " announcements");
                
                // Transform configuration settings
                Map<String, Object> transformedConfig = transformConfiguration(parseResult.getConfiguration());
                logger.info("Transformed configuration settings");
                
                // Generate transformation summary
                TransformationSummary summary = generateSummary();
                
                return new TransformationResult(transformedTypes, transformedAnnouncements, 
                                              transformedConfig, summary, true);
                
            } catch (Exception e) {
                logger.error("Data transformation failed", e);
                return new TransformationResult(Collections.emptyList(), Collections.emptyList(), 
                                              Collections.emptyMap(), generateSummary(), 
                                              false, e.getMessage());
            }
        });
    }
    
    /**
     * Transform announcement types with validation and normalization
     */
    private List<AnnouncementTypeDTO> transformAnnouncementTypes(List<AnnouncementTypeDTO> originalTypes) {
        List<AnnouncementTypeDTO> transformed = new ArrayList<>();
        
        for (AnnouncementTypeDTO original : originalTypes) {
            try {
                AnnouncementTypeDTO transformed_type = transformAnnouncementType(original);
                transformed.add(transformed_type);
                typesTransformed++;
            } catch (Exception e) {
                transformationErrors++;
                validationIssues.add("Failed to transform announcement type '" + original.getId() + "': " + e.getMessage());
                logger.warning("Skipping invalid announcement type: " + original.getId() + " - " + e.getMessage());
            }
        }
        
        return transformed;
    }
    
    /**
     * Transform individual announcement type with business logic
     */
    private AnnouncementTypeDTO transformAnnouncementType(AnnouncementTypeDTO original) {
        // Validate required fields
        if (original.getId() == null || original.getId().trim().isEmpty()) {
            throw new TransformationException("Announcement type missing required ID");
        }
        
        if (original.getPrefix() == null) {
            throw new TransformationException("Announcement type missing required prefix");
        }
        
        AnnouncementTypeDTO transformed = new AnnouncementTypeDTO();
        transformed.setId(original.getId().trim().toLowerCase()); // Normalize ID
        transformed.setName(generateDisplayName(original.getId()));
        transformed.setPrefix(normalizeColorCodes(original.getPrefix()));
        transformed.setSuffix(normalizeColorCodes(original.getSuffix()));
        transformed.setPermission(original.getPermission());
        transformed.setListFee(original.getListFee());
        transformed.setWeeklyFee(original.getWeeklyFee());
        transformed.setActive(true); // All YAML types are active by default
        transformed.setCreatedAt(LocalDateTime.now());
        
        // Add migration metadata
        transformed.setMetadata("migratedFrom", "YAML");
        transformed.setMetadata("originalId", original.getId());
        
        return transformed;
    }
    
    /**
     * Transform announcements with comprehensive validation
     */
    private List<AnnouncementDTO> transformAnnouncements(List<AnnouncementDTO> originalAnnouncements, 
                                                        List<AnnouncementTypeDTO> availableTypes) {
        List<AnnouncementDTO> transformed = new ArrayList<>();
        Set<String> validTypeIds = new HashSet<>();
        
        // Build type validation set
        for (AnnouncementTypeDTO type : availableTypes) {
            validTypeIds.add(type.getId());
        }
        
        for (AnnouncementDTO original : originalAnnouncements) {
            try {
                AnnouncementDTO transformedAnnouncement = transformAnnouncement(original, validTypeIds);
                transformed.add(transformedAnnouncement);
                announcementsTransformed++;
            } catch (Exception e) {
                transformationErrors++;
                validationIssues.add("Failed to transform announcement '" + original.getId() + "': " + e.getMessage());
                logger.warning("Skipping invalid announcement: " + original.getId() + " - " + e.getMessage());
            }
        }
        
        return transformed;
    }
    
    /**
     * Transform individual announcement with business logic
     */
    private AnnouncementDTO transformAnnouncement(AnnouncementDTO original, Set<String> validTypeIds) {
        // Validate required fields
        if (original.getId() == null || original.getId().trim().isEmpty()) {
            throw new TransformationException("Announcement missing required ID");
        }
        
        if (original.getMessage() == null || original.getMessage().trim().isEmpty()) {
            throw new TransformationException("Announcement missing required message");
        }
        
        if (original.getType() == null || !validTypeIds.contains(original.getType().toLowerCase())) {
            throw new TransformationException("Announcement references invalid type: " + original.getType());
        }
        
        AnnouncementDTO transformed = new AnnouncementDTO();
        transformed.setId(original.getId().trim().toLowerCase()); // Normalize ID
        transformed.setTitle(generateAnnouncementTitle(original));
        transformed.setMessage(normalizeColorCodes(original.getMessage()));
        transformed.setType(original.getType().toLowerCase());
        transformed.setActive(true); // All YAML announcements are active by default
        
        // Handle scheduling and timing
        processSchedulingData(original, transformed);
        
        // Handle expiration
        processExpirationData(original, transformed);
        
        // Set timestamps
        transformed.setCreatedAt(original.getCreatedAt() != null ? original.getCreatedAt() : 
                                Timestamp.valueOf(LocalDateTime.now()));
        
        // Copy all metadata and add migration tracking
        transformed.setMetadata(new HashMap<>(original.getMetadata()));
        transformed.setMetadata("migratedFrom", "YAML");
        transformed.setMetadata("originalId", original.getId());
        transformed.setMetadata("transformedAt", LocalDateTime.now().toString());
        
        return transformed;
    }
    
    /**
     * Process scheduling data from YAML format
     */
    private void processSchedulingData(AnnouncementDTO original, AnnouncementDTO transformed) {
        // Handle interval seconds (already converted from recurrence)
        if (original.getIntervalSeconds() > 0) {
            transformed.setIntervalSeconds(original.getIntervalSeconds());
        }
        
        // Handle scheduled date/time from metadata
        String scheduledDate = (String) original.getMetadata("scheduledDate");
        String scheduledTime = (String) original.getMetadata("scheduledTime");
        
        if (scheduledDate != null && scheduledTime != null) {
            try {
                Timestamp scheduledTimestamp = parseScheduledDateTime(scheduledDate, scheduledTime);
                transformed.setScheduledFor(scheduledTimestamp);
            } catch (Exception e) {
                logger.warning("Invalid scheduling data for announcement " + original.getId() + ": " + e.getMessage());
                transformed.setMetadata("schedulingError", e.getMessage());
            }
        }
    }
    
    /**
     * Process expiration data from YAML format
     */
    private void processExpirationData(AnnouncementDTO original, AnnouncementDTO transformed) {
        String expiration = (String) original.getMetadata("expiration");
        if (expiration != null && !expiration.trim().isEmpty()) {
            try {
                Timestamp expirationTimestamp = parseExpirationDateTime(expiration);
                transformed.setExpiresAt(expirationTimestamp);
            } catch (Exception e) {
                logger.warning("Invalid expiration data for announcement " + original.getId() + ": " + e.getMessage());
                transformed.setMetadata("expirationError", e.getMessage());
            }
        }
    }
    
    /**
     * Transform configuration data
     */
    private Map<String, Object> transformConfiguration(Map<String, Object> originalConfig) {
        Map<String, Object> transformed = new HashMap<>();
        
        // Process storage configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> storage = (Map<String, Object>) originalConfig.get("storage");
        if (storage != null) {
            transformed.put("storage", normalizeStorageConfig(storage));
        }
        
        // Process MOTD configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> motd = (Map<String, Object>) originalConfig.get("motd");
        if (motd != null) {
            transformed.put("motd", motd);
        }
        
        // Process debug configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> debug = (Map<String, Object>) originalConfig.get("debug");
        if (debug != null) {
            transformed.put("debug", debug);
        }
        
        // Add transformation metadata
        transformed.put("migratedFrom", "YAML");
        transformed.put("transformedAt", LocalDateTime.now().toString());
        
        return transformed;
    }
    
    /**
     * Generate display name from type ID
     */
    private String generateDisplayName(String id) {
        if (id == null) return null;
        
        // Convert snake_case or kebab-case to Title Case
        return Arrays.stream(id.split("[_-]"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .reduce((a, b) -> a + " " + b)
                    .orElse(id);
    }
    
    /**
     * Generate announcement title from content
     */
    private String generateAnnouncementTitle(AnnouncementDTO original) {
        String message = original.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Announcement " + original.getId();
        }
        
        // Strip color codes and take first few words
        String cleaned = COLOR_CODE_PATTERN.matcher(message).replaceAll("");
        String[] words = cleaned.trim().split("\\s+");
        
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, 5); i++) {
            if (title.length() > 0) title.append(" ");
            title.append(words[i]);
        }
        
        if (words.length > 5) title.append("...");
        
        return title.toString();
    }
    
    /**
     * Normalize Minecraft color codes
     */
    private String normalizeColorCodes(String text) {
        if (text == null) return null;
        return text.trim();
    }
    
    /**
     * Parse scheduled date/time combination
     */
    private Timestamp parseScheduledDateTime(String date, String time) {
        // Handle yearly recurring dates (yearly-MM-DD format)
        if (date.startsWith("yearly-")) {
            String monthDay = date.substring(7);
            String[] parts = monthDay.split("-");
            if (parts.length == 2) {
                int currentYear = LocalDateTime.now().getYear();
                LocalDateTime scheduledDateTime = LocalDateTime.of(
                    currentYear, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 
                    parseTimeHour(time), parseTimeMinute(time)
                );
                return Timestamp.valueOf(scheduledDateTime);
            }
        }
        
        // Handle specific dates (YYYY-MM-DD format)
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDateTime scheduledDateTime = LocalDateTime.of(
                Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(5, 7)),
                Integer.parseInt(date.substring(8, 10)),
                parseTimeHour(time),
                parseTimeMinute(time)
            );
            return Timestamp.valueOf(scheduledDateTime);
        }
        
        throw new DateTimeParseException("Invalid date format: " + date, date, 0);
    }
    
    /**
     * Parse expiration date string
     */
    private Timestamp parseExpirationDateTime(String expiration) {
        if (expiration.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDateTime expirationDateTime = LocalDateTime.of(
                Integer.parseInt(expiration.substring(0, 4)),
                Integer.parseInt(expiration.substring(5, 7)),
                Integer.parseInt(expiration.substring(8, 10)),
                23, 59, 59 // End of day
            );
            return Timestamp.valueOf(expirationDateTime);
        }
        
        throw new DateTimeParseException("Invalid expiration format: " + expiration, expiration, 0);
    }
    
    /**
     * Parse hour from time string (HHmm format)
     */
    private int parseTimeHour(String time) {
        if (time != null && time.length() >= 2) {
            return Integer.parseInt(time.substring(0, 2));
        }
        return 0;
    }
    
    /**
     * Parse minute from time string (HHmm format)
     */
    private int parseTimeMinute(String time) {
        if (time != null && time.length() >= 4) {
            return Integer.parseInt(time.substring(2, 4));
        }
        return 0;
    }
    
    /**
     * Normalize storage configuration for database compatibility
     */
    private Map<String, Object> normalizeStorageConfig(Map<String, Object> storage) {
        Map<String, Object> normalized = new HashMap<>(storage);
        
        // Ensure type is lowercase
        String type = (String) normalized.get("type");
        if (type != null) {
            normalized.put("type", type.toLowerCase());
        }
        
        return normalized;
    }
    
    /**
     * Reset transformation statistics
     */
    private void resetStatistics() {
        typesTransformed = 0;
        announcementsTransformed = 0;
        transformationErrors = 0;
        validationIssues.clear();
    }
    
    /**
     * Generate transformation summary
     */
    private TransformationSummary generateSummary() {
        return new TransformationSummary(typesTransformed, announcementsTransformed, 
                                       transformationErrors, new ArrayList<>(validationIssues));
    }
    
    /**
     * Result container for transformation operations
     */
    public static class TransformationResult {
        private final List<AnnouncementTypeDTO> transformedTypes;
        private final List<AnnouncementDTO> transformedAnnouncements;
        private final Map<String, Object> transformedConfiguration;
        private final TransformationSummary summary;
        private final boolean success;
        private final String errorMessage;
        
        public TransformationResult(List<AnnouncementTypeDTO> transformedTypes,
                                  List<AnnouncementDTO> transformedAnnouncements,
                                  Map<String, Object> transformedConfiguration,
                                  TransformationSummary summary, boolean success) {
            this(transformedTypes, transformedAnnouncements, transformedConfiguration, 
                 summary, success, null);
        }
        
        public TransformationResult(List<AnnouncementTypeDTO> transformedTypes,
                                  List<AnnouncementDTO> transformedAnnouncements,
                                  Map<String, Object> transformedConfiguration,
                                  TransformationSummary summary, boolean success, String errorMessage) {
            this.transformedTypes = transformedTypes;
            this.transformedAnnouncements = transformedAnnouncements;
            this.transformedConfiguration = transformedConfiguration;
            this.summary = summary;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public List<AnnouncementTypeDTO> getTransformedTypes() { return transformedTypes; }
        public List<AnnouncementDTO> getTransformedAnnouncements() { return transformedAnnouncements; }
        public Map<String, Object> getTransformedConfiguration() { return transformedConfiguration; }
        public TransformationSummary getSummary() { return summary; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Summary statistics for transformation operations
     */
    public static class TransformationSummary {
        private final int typesTransformed;
        private final int announcementsTransformed;
        private final int errors;
        private final List<String> validationIssues;
        
        public TransformationSummary(int typesTransformed, int announcementsTransformed, 
                                   int errors, List<String> validationIssues) {
            this.typesTransformed = typesTransformed;
            this.announcementsTransformed = announcementsTransformed;
            this.errors = errors;
            this.validationIssues = validationIssues;
        }
        
        public int getTypesTransformed() { return typesTransformed; }
        public int getAnnouncementsTransformed() { return announcementsTransformed; }
        public int getErrors() { return errors; }
        public List<String> getValidationIssues() { return validationIssues; }
        
        public boolean hasIssues() { return errors > 0 || !validationIssues.isEmpty(); }
        
        @Override
        public String toString() {
            return "TransformationSummary{" +
                    "typesTransformed=" + typesTransformed +
                    ", announcementsTransformed=" + announcementsTransformed +
                    ", errors=" + errors +
                    ", validationIssues=" + validationIssues.size() +
                    '}';
        }
    }
    
    /**
     * Custom exception for transformation errors
     */
    public static class TransformationException extends RuntimeException {
        public TransformationException(String message) {
            super(message);
        }
        
        public TransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
