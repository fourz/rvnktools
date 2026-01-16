package org.fourz.rvnkcore.bridge;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.log.LogManager;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Service for creating test announcements and announcement types using the legacy AnnounceManager API.
 * This bridge service helps populate the database with test data for API validation.
 * 
 * @since 1.3.0-alpha
 */
public class LegacyAnnouncementTestService {
    
    private final RVNKCore plugin;
    private final LogManager logger;
    private final AnnounceManager announceManager;
    
    /**
     * Constructor for LegacyAnnouncementTestService.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param announceManager The legacy announcement manager
     */
    public LegacyAnnouncementTestService(RVNKCore plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Creates default announcement types for testing.
     * 
     * @return CompletableFuture that completes with the number of types created
     */
    public CompletableFuture<Integer> createDefaultAnnouncementTypes() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Creating default announcement types for testing...");
            
            List<TestAnnouncementType> defaultTypes = createDefaultTypes();
            int createdCount = 0;
            
            for (TestAnnouncementType testType : defaultTypes) {
                try {
                    // Create legacy AnnounceType and add it through the system
                    AnnounceType announceType = new AnnounceType();
                    announceType.setId(testType.getId());
                    announceType.setPrefix(testType.getPrefix());
                    announceType.setSuffix(testType.getSuffix());
                    announceType.setPermission(testType.getPermission());
                    announceType.setListingFee(testType.getListingFee());
                    
                    // Add to the announce manager's type system
                    if (announceManager.getConfig().getAnnounceTypes().containsKey(testType.getId())) {
                        logger.debug("Announcement type '" + testType.getId() + "' already exists, skipping");
                        continue;
                    }
                    
                    // Manually add to the configuration (this would normally be loaded from YAML/DB)
                    announceManager.getConfig().getAnnounceTypes().put(testType.getId(), announceType);
                    createdCount++;
                    logger.debug("Created announcement type: " + testType.getId());
                    
                } catch (Exception e) {
                    logger.error("Failed to create announcement type: " + testType.getId(), e);
                }
            }
            
            logger.info("Created " + createdCount + " announcement types");
            return createdCount;
        });
    }
    
    /**
     * Creates test announcements using the legacy API.
     * 
     * @return CompletableFuture that completes with the number of announcements created
     */
    public CompletableFuture<Integer> createTestAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Creating test announcements...");
            
            List<TestAnnouncement> testAnnouncements = createTestAnnouncementData();
            int createdCount = 0;
            
            for (TestAnnouncement testAnnouncement : testAnnouncements) {
                try {
                    // Check if announcement already exists
                    if (announceManager.announcementExists(testAnnouncement.getId())) {
                        logger.debug("Announcement '" + testAnnouncement.getId() + "' already exists, skipping");
                        continue;
                    }
                    
                    // Create legacy Announcement object
                    Announcement announcement = new Announcement();
                    announcement.setId(testAnnouncement.getId());
                    announcement.setMessage(testAnnouncement.getMessage());
                    announcement.setType(testAnnouncement.getType());
                    announcement.setEnabled(testAnnouncement.isEnabled());
                    announcement.setInterval(testAnnouncement.getInterval());
                    announcement.setOwner(testAnnouncement.getOwner());
                    announcement.setPermission(testAnnouncement.getPermission());
                    
                    // Set scheduling if provided
                    if (testAnnouncement.getScheduledTime() != null) {
                        announcement.setTime(testAnnouncement.getScheduledTime());
                    }
                    if (testAnnouncement.getScheduledDate() != null) {
                        announcement.setDate(testAnnouncement.getScheduledDate());
                    }
                    if (testAnnouncement.getExpiration() != null) {
                        announcement.setExpiration(testAnnouncement.getExpiration());
                    }
                    
                    // Add announcement through the manager
                    if (announceManager.addAnnouncement(announcement)) {
                        createdCount++;
                        logger.debug("Created announcement: " + testAnnouncement.getId());
                    } else {
                        logger.warning("Failed to add announcement: " + testAnnouncement.getId());
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to create announcement: " + testAnnouncement.getId(), e);
                }
            }
            
            logger.info("Created " + createdCount + " test announcements");
            return createdCount;
        });
    }
    
    /**
     * Creates a single test announcement with the specified parameters.
     * 
     * @param id The announcement ID
     * @param message The announcement message
     * @param type The announcement type
     * @param enabled Whether the announcement is enabled
     * @return CompletableFuture that completes with true if created successfully
     */
    public CompletableFuture<Boolean> createAnnouncement(String id, String message, String type, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if announcement already exists
                if (announceManager.announcementExists(id)) {
                    logger.warning("Announcement '" + id + "' already exists");
                    return false;
                }
                
                // Validate type exists
                if (!announceManager.validateAnnounceType(type)) {
                    logger.warning("Invalid announcement type: " + type);
                    return false;
                }
                
                // Create announcement
                Announcement announcement = new Announcement();
                announcement.setId(id);
                announcement.setMessage(message);
                announcement.setType(type);
                announcement.setEnabled(enabled);
                announcement.setOwner("API-TEST");
                
                boolean success = announceManager.addAnnouncement(announcement);
                if (success) {
                    logger.info("Created test announcement: " + id);
                } else {
                    logger.warning("Failed to create announcement: " + id);
                }
                
                return success;
                
            } catch (Exception e) {
                logger.error("Failed to create announcement: " + id, e);
                return false;
            }
        });
    }
    
    /**
     * Gets all available announcement types.
     * 
     * @return List of announcement type IDs
     */
    public List<String> getAvailableTypes() {
        return new ArrayList<>(announceManager.getAnnounceTypes());
    }
    
    /**
     * Gets all current announcements.
     * 
     * @return List of announcement IDs
     */
    public List<String> getCurrentAnnouncements() {
        return new ArrayList<>(announceManager.getAnnouncementIds());
    }
    
    /**
     * Creates the default announcement types for testing.
     */
    private List<TestAnnouncementType> createDefaultTypes() {
        List<TestAnnouncementType> types = new ArrayList<>();
        
        types.add(new TestAnnouncementType(
            "info", 
            "&7[&bINFO&7] ", 
            "", 
            "rvnktools.announce.type.info", 
            0.0
        ));
        
        types.add(new TestAnnouncementType(
            "warning", 
            "&7[&eWARNING&7] ", 
            "", 
            "rvnktools.announce.type.warning", 
            0.0
        ));
        
        types.add(new TestAnnouncementType(
            "alert", 
            "&7[&cALERT&7] ", 
            "", 
            "rvnktools.announce.type.alert", 
            0.0
        ));
        
        types.add(new TestAnnouncementType(
            "system", 
            "&7[&9SYSTEM&7] ", 
            "", 
            "rvnktools.announce.type.system", 
            0.0
        ));
        
        types.add(new TestAnnouncementType(
            "event", 
            "&7[&5EVENT&7] ", 
            "", 
            "rvnktools.announce.type.event", 
            0.0
        ));
        
        return types;
    }
    
    /**
     * Creates test announcement data.
     */
    private List<TestAnnouncement> createTestAnnouncementData() {
        List<TestAnnouncement> announcements = new ArrayList<>();
        
        announcements.add(new TestAnnouncement(
            "test-001",
            "Welcome to the server! This is a test announcement for API validation.",
            "info",
            true,
            300000L, // 5 minutes
            "API-TEST",
            null,
            LocalTime.of(12, 0),
            LocalDate.now().plusDays(1),
            LocalDateTime.now().plusHours(24)
        ));
        
        announcements.add(new TestAnnouncement(
            "test-002",
            "Server maintenance scheduled for tonight at midnight.",
            "warning",
            true,
            600000L, // 10 minutes
            "API-TEST",
            "rvnktools.announce.maintenance",
            LocalTime.of(23, 30),
            LocalDate.now(),
            LocalDateTime.now().plusHours(2)
        ));
        
        announcements.add(new TestAnnouncement(
            "test-003",
            "Emergency server restart in 5 minutes!",
            "alert",
            false, // Disabled for testing
            60000L, // 1 minute
            "API-TEST",
            null,
            null,
            null,
            LocalDateTime.now().plusMinutes(10)
        ));
        
        announcements.add(new TestAnnouncement(
            "test-004",
            "New features have been added to the server. Check /help for details.",
            "system",
            true,
            1800000L, // 30 minutes
            "API-TEST",
            null,
            null,
            null,
            LocalDateTime.now().plusDays(7)
        ));
        
        announcements.add(new TestAnnouncement(
            "test-005",
            "Special event starting now! Join the fun in the event world.",
            "event",
            true,
            120000L, // 2 minutes
            "API-TEST",
            "rvnktools.announce.event",
            LocalTime.now(),
            LocalDate.now(),
            LocalDateTime.now().plusHours(4)
        ));
        
        return announcements;
    }
    
    /**
     * Helper class to hold test announcement type data.
     */
    private static class TestAnnouncementType {
        private final String id;
        private final String prefix;
        private final String suffix;
        private final String permission;
        private final Double listingFee;
        
        public TestAnnouncementType(String id, String prefix, String suffix, String permission, Double listingFee) {
            this.id = id;
            this.prefix = prefix;
            this.suffix = suffix;
            this.permission = permission;
            this.listingFee = listingFee;
        }
        
        public String getId() { return id; }
        public String getPrefix() { return prefix; }
        public String getSuffix() { return suffix; }
        public String getPermission() { return permission; }
        public Double getListingFee() { return listingFee; }
    }
    
    /**
     * Helper class to hold test announcement data.
     */
    private static class TestAnnouncement {
        private final String id;
        private final String message;
        private final String type;
        private final boolean enabled;
        private final Long interval;
        private final String owner;
        private final String permission;
        private final LocalTime scheduledTime;
        private final LocalDate scheduledDate;
        private final LocalDateTime expiration;
        
        public TestAnnouncement(String id, String message, String type, boolean enabled, Long interval,
                              String owner, String permission, LocalTime scheduledTime, LocalDate scheduledDate,
                              LocalDateTime expiration) {
            this.id = id;
            this.message = message;
            this.type = type;
            this.enabled = enabled;
            this.interval = interval;
            this.owner = owner;
            this.permission = permission;
            this.scheduledTime = scheduledTime;
            this.scheduledDate = scheduledDate;
            this.expiration = expiration;
        }
        
        public String getId() { return id; }
        public String getMessage() { return message; }
        public String getType() { return type; }
        public boolean isEnabled() { return enabled; }
        public Long getInterval() { return interval; }
        public String getOwner() { return owner; }
        public String getPermission() { return permission; }
        public LocalTime getScheduledTime() { return scheduledTime; }
        public LocalDate getScheduledDate() { return scheduledDate; }
        public LocalDateTime getExpiration() { return expiration; }
    }
}
