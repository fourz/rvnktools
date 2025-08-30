# Service Layer Implementation Guide

**Guide ID**: 03-service-layer-implementation  
**Priority**: High  
**Estimated Time**: 6-8 hours  
**Dependencies**: AnnouncementService interface, AnnouncementRepository

## Service Methods to Implement

### 1. Metrics Service Implementation

#### Create AnnouncementMetricsDTO

**File**: `toolkitplugin/src/main/java/org/fourz/rvnkcore/data/dto/AnnouncementMetricsDTO.java`

```java
package org.fourz.rvnkcore.data.dto;

/**
 * Data Transfer Object for announcement metrics and statistics.
 * Provides comprehensive analytics data for announcement system monitoring.
 */
public class AnnouncementMetricsDTO {
    
    private long totalAnnouncements;
    private long activeAnnouncements;
    private long inactiveAnnouncements;
    private long announcementsThisWeek;
    private long announcementsThisMonth;
    
    // Type breakdown
    private long serverAnnouncements;
    private long groupAnnouncements;
    private long worldAnnouncements;
    private long playerAnnouncements;
    
    // Performance metrics
    private double averageDisplayDuration;
    private long totalDisplays;
    
    // Constructors
    public AnnouncementMetricsDTO() {}
    
    public AnnouncementMetricsDTO(long total, long active, long inactive) {
        this.totalAnnouncements = total;
        this.activeAnnouncements = active;
        this.inactiveAnnouncements = inactive;
    }
    
    // Getters and setters
    public long getTotalAnnouncements() { return totalAnnouncements; }
    public void setTotalAnnouncements(long totalAnnouncements) { this.totalAnnouncements = totalAnnouncements; }
    
    public long getActiveAnnouncements() { return activeAnnouncements; }
    public void setActiveAnnouncements(long activeAnnouncements) { this.activeAnnouncements = activeAnnouncements; }
    
    public long getInactiveAnnouncements() { return inactiveAnnouncements; }
    public void setInactiveAnnouncements(long inactiveAnnouncements) { this.inactiveAnnouncements = inactiveAnnouncements; }
    
    // Additional getters/setters...
    
    /**
     * Calculate percentage of active announcements
     */
    public double getActivePercentage() {
        if (totalAnnouncements == 0) return 0.0;
        return (double) activeAnnouncements / totalAnnouncements * 100.0;
    }
}
```

#### Add Method to Service Interface

**File**: `toolkitplugin/src/main/java/org/fourz/rvnkcore/service/AnnouncementService.java`

```java
/**
 * Retrieve comprehensive announcement metrics and statistics.
 * 
 * @return CompletableFuture containing AnnouncementMetricsDTO with system statistics
 * @since 1.5.0
 */
CompletableFuture<AnnouncementMetricsDTO> getAnnouncementMetrics();
```

#### Implement in DefaultAnnouncementService

**File**: `toolkitplugin/src/main/java/org/fourz/rvnkcore/service/impl/DefaultAnnouncementService.java`

```java
@Override
public CompletableFuture<AnnouncementMetricsDTO> getAnnouncementMetrics() {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Get basic counts
            long totalCount = getAnnouncementCount().join();
            long activeCount = getActiveAnnouncementCount().join();
            long inactiveCount = totalCount - activeCount;
            
            // Create metrics DTO with basic data
            AnnouncementMetricsDTO metrics = new AnnouncementMetricsDTO(totalCount, activeCount, inactiveCount);
            
            // TODO: Add more detailed metrics calculations
            // - Type breakdown queries
            // - Time-based statistics
            // - Performance metrics
            
            return metrics;
            
        } catch (Exception e) {
            logger.error("Error calculating announcement metrics", e);
            // Return basic metrics on error
            return new AnnouncementMetricsDTO(0, 0, 0);
        }
    });
}
```

### 2. Update Announcement Implementation

#### Add Method to Service Interface

```java
/**
 * Update an existing announcement with new data.
 * 
 * @param id the announcement ID to update
 * @param announcementData the updated announcement data
 * @return CompletableFuture that completes when update is finished
 * @throws IllegalArgumentException if ID is null or announcement data is invalid
 * @since 1.5.0
 */
CompletableFuture<Void> updateAnnouncement(String id, AnnouncementDTO announcementData);
```

#### Implement in DefaultAnnouncementService

```java
@Override
public CompletableFuture<Void> updateAnnouncement(String id, AnnouncementDTO announcementData) {
    return CompletableFuture.runAsync(() -> {
        try {
            // Validate input parameters
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Announcement ID cannot be null or empty");
            }
            
            if (announcementData == null) {
                throw new IllegalArgumentException("Announcement data cannot be null");
            }
            
            // Check if announcement exists
            AnnouncementDTO existing = getAnnouncementById(id).join();
            if (existing == null) {
                throw new IllegalArgumentException("Announcement with ID " + id + " not found");
            }
            
            // Update announcement through repository
            announcementRepository.updateAnnouncement(id, announcementData);
            
            logger.info("Successfully updated announcement: " + id);
            
        } catch (Exception e) {
            logger.error("Error updating announcement: " + id, e);
            throw new RuntimeException("Failed to update announcement", e);
        }
    });
}
```

### 3. Search Announcements Implementation

#### Add Method to Service Interface

```java
/**
 * Search announcements by text content in title or message.
 * 
 * @param searchTerm the search term to look for
 * @return CompletableFuture containing list of matching announcements
 * @throws IllegalArgumentException if search term is null or empty
 * @since 1.5.0
 */
CompletableFuture<List<AnnouncementDTO>> searchAnnouncements(String searchTerm);
```

#### Implement in DefaultAnnouncementService

```java
@Override
public CompletableFuture<List<AnnouncementDTO>> searchAnnouncements(String searchTerm) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Validate search term
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                throw new IllegalArgumentException("Search term cannot be null or empty");
            }
            
            // Sanitize search term
            String cleanSearchTerm = searchTerm.trim().toLowerCase();
            
            // Get all announcements and filter by search term
            List<AnnouncementDTO> allAnnouncements = getAllAnnouncements().join();
            
            return allAnnouncements.stream()
                .filter(announcement -> matchesSearchTerm(announcement, cleanSearchTerm))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error searching announcements for term: " + searchTerm, e);
            return new ArrayList<>();
        }
    });
}

/**
 * Check if an announcement matches the search term.
 * Searches in title, message, and group fields.
 */
private boolean matchesSearchTerm(AnnouncementDTO announcement, String searchTerm) {
    if (announcement.getTitle() != null && 
        announcement.getTitle().toLowerCase().contains(searchTerm)) {
        return true;
    }
    
    if (announcement.getMessage() != null && 
        announcement.getMessage().toLowerCase().contains(searchTerm)) {
        return true;
    }
    
    if (announcement.getGroup() != null && 
        announcement.getGroup().toLowerCase().contains(searchTerm)) {
        return true;
    }
    
    return false;
}
```

### 4. Bulk Import Implementation

#### Add Method to Service Interface

```java
/**
 * Import multiple announcements in a single transaction.
 * 
 * @param announcements list of announcements to import
 * @return CompletableFuture that completes when all imports are finished
 * @throws IllegalArgumentException if announcements list is null or empty
 * @since 1.5.0
 */
CompletableFuture<Void> bulkImportAnnouncements(List<AnnouncementDTO> announcements);
```

#### Implement in DefaultAnnouncementService

```java
@Override
public CompletableFuture<Void> bulkImportAnnouncements(List<AnnouncementDTO> announcements) {
    return CompletableFuture.runAsync(() -> {
        try {
            // Validate input
            if (announcements == null || announcements.isEmpty()) {
                throw new IllegalArgumentException("Announcements list cannot be null or empty");
            }
            
            // Validate each announcement
            for (int i = 0; i < announcements.size(); i++) {
                AnnouncementDTO announcement = announcements.get(i);
                if (announcement == null) {
                    throw new IllegalArgumentException("Announcement at index " + i + " is null");
                }
                
                // Basic validation
                if (announcement.getTitle() == null || announcement.getTitle().trim().isEmpty()) {
                    throw new IllegalArgumentException("Announcement at index " + i + " has empty title");
                }
            }
            
            // Import announcements in batches to avoid overwhelming the database
            int batchSize = 10;
            for (int i = 0; i < announcements.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, announcements.size());
                List<AnnouncementDTO> batch = announcements.subList(i, endIndex);
                
                // Process batch
                for (AnnouncementDTO announcement : batch) {
                    try {
                        announcementRepository.createAnnouncement(announcement);
                    } catch (Exception e) {
                        logger.error("Failed to import announcement: " + announcement.getTitle(), e);
                        // Continue with other announcements
                    }
                }
                
                logger.info("Imported batch " + (i / batchSize + 1) + " of " + 
                           ((announcements.size() + batchSize - 1) / batchSize));
            }
            
            logger.info("Bulk import completed. Processed " + announcements.size() + " announcements");
            
        } catch (Exception e) {
            logger.error("Error during bulk import", e);
            throw new RuntimeException("Bulk import failed", e);
        }
    });
}
```

## Implementation Steps

### Step 1: Create DTO Class
1. Create `AnnouncementMetricsDTO.java` in the DTO package
2. Add all necessary fields and methods
3. Include validation and utility methods

### Step 2: Update Service Interface
1. Add all four new method signatures to `AnnouncementService.java`
2. Include proper JavaDoc documentation
3. Add `@since` tags for version tracking

### Step 3: Implement Service Methods
1. Add implementations to `DefaultAnnouncementService.java`
2. Include proper error handling and logging
3. Use async patterns consistently
4. Add input validation for all methods

### Step 4: Test Service Layer
1. Create unit tests for each new method
2. Test error scenarios and edge cases
3. Validate async behavior and performance

## Success Criteria

- [ ] All service methods compile without errors
- [ ] Service methods handle null/invalid inputs gracefully
- [ ] Async patterns used consistently throughout
- [ ] Comprehensive error logging implemented
- [ ] Unit tests pass for all new functionality
- [ ] Performance meets requirements (<2 second response times)

This service layer implementation provides the foundation for the controller methods and ensures robust, scalable announcement management functionality.
