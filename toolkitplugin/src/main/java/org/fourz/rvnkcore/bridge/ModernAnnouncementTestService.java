package org.fourz.rvnkcore.bridge;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.log.RVNKLogger;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Modern test service for creating announcements using the new RVNKCore AnnouncementService.
 * This replaces the legacy AnnounceManager bridge and uses the modern async API.
 */
public class ModernAnnouncementTestService {
    
    private final RVNKCore rvnkCore;
    private final RVNKLogger logger;
    
    /**
     * Creates a new modern announcement test service.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param rvnkCore The RVNKCore instance
     */
    public ModernAnnouncementTestService(RVNKCore plugin, RVNKCore rvnkCore) {
        this.rvnkCore = rvnkCore;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Creates default announcement types for testing.
     * Since the service uses string types, this just returns the available types.
     * 
     * @return CompletableFuture with the number of types available (always 5)
     */
    public CompletableFuture<Integer> createDefaultAnnouncementTypes() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Available announcement types: info, warning, alert, system, event");
            return 5; // We have 5 predefined types
        });
    }
    
    /**
     * Creates test announcements using the modern API.
     * 
     * @return CompletableFuture with the number of announcements created
     */
    public CompletableFuture<Integer> createTestAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AnnouncementService service = rvnkCore.getAnnouncementService();
                
                // Define test announcements
                List<TestAnnouncement> testAnnouncements = Arrays.asList(
                    new TestAnnouncement("welcome", "Welcome to the server!", "Welcome Message", "info", true),
                    new TestAnnouncement("maintenance", "Server maintenance scheduled", "Maintenance Notice", "warning", true),
                    new TestAnnouncement("emergency", "Emergency maintenance in progress", "Emergency Alert", "alert", true),
                    new TestAnnouncement("backup", "Daily backup starting", "System Notification", "system", false),
                    new TestAnnouncement("holiday-event", "Special holiday event this weekend!", "Holiday Event", "event", true)
                );
                
                logger.info("Creating " + testAnnouncements.size() + " test announcements...");
                
                int createdCount = 0;
                
                for (TestAnnouncement testAnnouncement : testAnnouncements) {
                    // Check if announcement already exists
                    CompletableFuture<Optional<AnnouncementDTO>> existingFuture = service.getAnnouncement(testAnnouncement.getId());
                    
                    try {
                        Optional<AnnouncementDTO> existing = existingFuture.get();
                        if (existing.isPresent()) {
                            logger.info("Announcement '" + testAnnouncement.getId() + "' already exists, skipping");
                            continue;
                        }
                    } catch (Exception e) {
                        // Announcement doesn't exist, continue with creation
                    }
                    
                    // Create new announcement
                    AnnouncementDTO announcement = new AnnouncementDTO.Builder()
                        .id(testAnnouncement.getId())
                        .title(testAnnouncement.getTitle())
                        .message(testAnnouncement.getMessage())
                        .type(testAnnouncement.getType())
                        .active(testAnnouncement.isEnabled())
                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                        .build();
                    
                    CompletableFuture<AnnouncementDTO> createFuture = service.createAnnouncement(announcement);
                    AnnouncementDTO created = createFuture.get();
                    
                    if (created != null) {
                        createdCount++;
                        logger.info("Created announcement: " + testAnnouncement.getId());
                    } else {
                        logger.warning("Failed to create announcement: " + testAnnouncement.getId());
                    }
                }
                
                logger.info("Created " + createdCount + " test announcements");
                return createdCount;
                
            } catch (Exception e) {
                logger.error("Failed to create test announcements", e);
                throw new RuntimeException("Failed to create test announcements", e);
            }
        });
    }
    
    /**
     * Creates a single announcement with the specified parameters.
     * 
     * @param id The announcement ID
     * @param message The announcement message
     * @param type The announcement type
     * @param enabled Whether the announcement is enabled
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> createAnnouncement(String id, String message, String type, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AnnouncementService service = rvnkCore.getAnnouncementService();
                
                // Check if announcement already exists
                try {
                    CompletableFuture<Optional<AnnouncementDTO>> existingFuture = service.getAnnouncement(id);
                    Optional<AnnouncementDTO> existing = existingFuture.get();
                    if (existing.isPresent()) {
                        logger.warning("Announcement with ID '" + id + "' already exists");
                        return false;
                    }
                } catch (Exception e) {
                    // Announcement doesn't exist, continue with creation
                }
                
                // Validate announcement type (basic validation for known types)
                List<String> validTypes = Arrays.asList("info", "warning", "alert", "system", "event");
                String finalType = type;
                if (!validTypes.contains(type)) {
                    logger.warning("Unknown announcement type '" + type + "'. Using 'info' instead.");
                    finalType = "info";
                }
                
                // Create the announcement
                AnnouncementDTO announcement = new AnnouncementDTO.Builder()
                    .id(id)
                    .title(message) // Use message as title for simple announcements
                    .message(message)
                    .type(finalType)
                    .active(enabled)
                    .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                    .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                    .build();
                
                CompletableFuture<AnnouncementDTO> createFuture = service.createAnnouncement(announcement);
                AnnouncementDTO created = createFuture.get();
                
                boolean success = created != null;
                if (success) {
                    logger.info("Successfully created announcement: " + id);
                } else {
                    logger.warning("Failed to create announcement: " + id);
                }
                
                return success;
                
            } catch (Exception e) {
                logger.error("Failed to create announcement '" + id + "'", e);
                return false;
            }
        });
    }
    
    /**
     * Gets the list of available announcement types.
     * 
     * @return List of announcement type IDs
     */
    public List<String> getAvailableTypes() {
        return Arrays.asList("info", "warning", "alert", "system", "event");
    }
    
    /**
     * Gets the list of current announcement IDs.
     * 
     * @return List of announcement IDs
     */
    public List<String> getCurrentAnnouncements() {
        try {
            AnnouncementService service = rvnkCore.getAnnouncementService();
            CompletableFuture<List<AnnouncementDTO>> future = service.getAllAnnouncements();
            List<AnnouncementDTO> announcements = future.get();
            return announcements.stream().map(AnnouncementDTO::getId).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get current announcements", e);
            return Arrays.asList(); // Empty list on error
        }
    }
    
    /**
     * Gets current announcement metrics.
     * 
     * @return Map of metrics or null if unavailable
     */
    public Map<String, Object> getMetrics() {
        try {
            AnnouncementService service = rvnkCore.getAnnouncementService();
            CompletableFuture<Map<String, Object>> future = service.getAnnouncementMetrics();
            return future.get();
        } catch (Exception e) {
            logger.error("Failed to get announcement metrics", e);
            return null;
        }
    }
    
    // Helper classes for test data
    
    private static class TestAnnouncement {
        private final String id;
        private final String message;
        private final String title;
        private final String type;
        private final boolean enabled;
        
        public TestAnnouncement(String id, String message, String title, String type, boolean enabled) {
            this.id = id;
            this.message = message;
            this.title = title;
            this.type = type;
            this.enabled = enabled;
        }
        
        public String getId() { return id; }
        public String getMessage() { return message; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public boolean isEnabled() { return enabled; }
    }
}
