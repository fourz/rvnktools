# Implementation Guide: Missing Metrics Endpoint

**Guide ID**: 02-metrics-endpoint-implementation  
**Priority**: High  
**Estimated Time**: 6-8 hours  
**Dependencies**: Service layer, repository layer

## Issue Analysis

### Problem Description

The `/api/v1/announcements/metrics` endpoint returns an empty response instead of meaningful metrics data.

**Current Status**:
- Endpoint accessible via HTTPS
- Returns empty string instead of JSON metrics
- No handler method in AnnouncementController
- No corresponding service method

### Expected Functionality

The metrics endpoint should provide comprehensive announcement statistics:

```json
{
  "totalAnnouncements": 15,
  "activeAnnouncements": 12,
  "inactiveAnnouncements": 3,
  "typeBreakdown": {
    "BROADCAST": 5,
    "WELCOME": 4,
    "WARNING": 2,
    "NOTIFICATION": 1
  },
  "worldBreakdown": {
    "survival": 8,
    "creative": 4,
    "global": 3
  },
  "groupBreakdown": {
    "vip": 2,
    "staff": 1,
    "default": 12
  },
  "recentActivity": {
    "createdLast24Hours": 2,
    "createdLast7Days": 5,
    "lastModified": "2025-08-23T10:30:00Z"
  },
  "deliveryStats": {
    "totalDeliveries": 1247,
    "averageDeliveriesPerDay": 45,
    "mostActiveAnnouncement": "announce-001"
  }
}
```

## Implementation Steps

### Step 1: Add Controller Handler Method

Add the missing metrics handler to `AnnouncementController.java`:

```java
// Add this method to AnnouncementController
/**
 * Handles GET /api/v1/announcements/metrics - Get announcement metrics
 */
private void handleGetAnnouncementMetrics(HttpServletRequest request, HttpServletResponse response) throws IOException {
    CompletableFuture<AnnouncementMetricsDTO> future = announcementService.getAnnouncementMetrics();
    
    future.thenAccept(metrics -> {
        try {
            String json = buildMetricsResponse(metrics);
            sendSuccessResponse(response, json);
        } catch (IOException e) {
            logger.error("Error sending metrics response", e);
        }
    }).exceptionally(ex -> {
        try {
            sendErrorResponse(response, 500, "Failed to retrieve announcement metrics: " + ex.getMessage());
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
        return null;
    });
}
```

### Step 2: Add Metrics Route to doGet Method

Update the `doGet` method in `AnnouncementController` to handle the metrics endpoint:

```java
// Add this case to the doGet method routing
else if (pathInfo.equals("/metrics")) {
    // GET /api/v1/announcements/metrics - Get announcement metrics
    handleGetAnnouncementMetrics(request, response);
}
```

### Step 3: Create AnnouncementMetricsDTO

Create a new DTO class for metrics data:

```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/model/AnnouncementMetricsDTO.java

package org.fourz.rvnkcore.api.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data transfer object for announcement metrics and statistics.
 * 
 * @since 1.0.0
 */
public class AnnouncementMetricsDTO {
    
    private final long totalAnnouncements;
    private final long activeAnnouncements;
    private final long inactiveAnnouncements;
    private final Map<String, Long> typeBreakdown;
    private final Map<String, Long> worldBreakdown;
    private final Map<String, Long> groupBreakdown;
    private final RecentActivity recentActivity;
    private final DeliveryStats deliveryStats;
    
    // Constructor, getters, and builder pattern...
    
    public static class RecentActivity {
        private final long createdLast24Hours;
        private final long createdLast7Days;
        private final LocalDateTime lastModified;
        
        // Constructor and getters...
    }
    
    public static class DeliveryStats {
        private final long totalDeliveries;
        private final double averageDeliveriesPerDay;
        private final String mostActiveAnnouncement;
        
        // Constructor and getters...
    }
    
    // Builder pattern implementation...
}
```

### Step 4: Add Service Method

Add metrics method to `AnnouncementService` interface:

```java
// Add to AnnouncementService interface
/**
 * Retrieves comprehensive metrics and statistics for all announcements.
 * 
 * @return CompletableFuture containing detailed announcement metrics
 * @throws org.fourz.rvnkcore.api.exception.ServiceException if metrics retrieval fails
 * @since 1.0.0
 */
CompletableFuture<AnnouncementMetricsDTO> getAnnouncementMetrics();
```

### Step 5: Implement Service Method

Add implementation to `DefaultAnnouncementService`:

```java
// Add to DefaultAnnouncementService
@Override
public CompletableFuture<AnnouncementMetricsDTO> getAnnouncementMetrics() {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Get all announcements for analysis
            List<AnnouncementDTO> allAnnouncements = repository.findAll().join();
            
            // Calculate basic counts
            long totalCount = allAnnouncements.size();
            long activeCount = allAnnouncements.stream()
                .filter(AnnouncementDTO::isActive)
                .count();
            long inactiveCount = totalCount - activeCount;
            
            // Calculate type breakdown
            Map<String, Long> typeBreakdown = allAnnouncements.stream()
                .collect(Collectors.groupingBy(
                    AnnouncementDTO::getType,
                    Collectors.counting()
                ));
            
            // Calculate world breakdown
            Map<String, Long> worldBreakdown = calculateWorldBreakdown(allAnnouncements);
            
            // Calculate group breakdown
            Map<String, Long> groupBreakdown = calculateGroupBreakdown(allAnnouncements);
            
            // Calculate recent activity
            RecentActivity recentActivity = calculateRecentActivity(allAnnouncements);
            
            // Calculate delivery stats (if available)
            DeliveryStats deliveryStats = calculateDeliveryStats(allAnnouncements);
            
            return new AnnouncementMetricsDTO.Builder()
                .totalAnnouncements(totalCount)
                .activeAnnouncements(activeCount)
                .inactiveAnnouncements(inactiveCount)
                .typeBreakdown(typeBreakdown)
                .worldBreakdown(worldBreakdown)
                .groupBreakdown(groupBreakdown)
                .recentActivity(recentActivity)
                .deliveryStats(deliveryStats)
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to calculate announcement metrics", e);
            throw new RuntimeException("Metrics calculation failed", e);
        }
    }, executorService);
}
```

### Step 6: Add Repository Support Methods

Add supporting methods to `AnnouncementRepository` if needed:

```java
// Add to AnnouncementRepository
/**
 * Gets announcements created within the specified time period.
 */
public CompletableFuture<List<AnnouncementDTO>> findCreatedAfter(LocalDateTime since) {
    return CompletableFuture.supplyAsync(() -> {
        String sql = queryBuilder.select("*")
            .from(tableName)
            .where("created_at >= ?")
            .build();
            
        // Implementation...
    });
}

/**
 * Gets the most recently modified announcement timestamp.
 */
public CompletableFuture<Optional<LocalDateTime>> getLastModifiedTimestamp() {
    return CompletableFuture.supplyAsync(() -> {
        String sql = queryBuilder.select("MAX(updated_at)")
            .from(tableName)
            .build();
            
        // Implementation...
    });
}
```

### Step 7: Add JSON Response Builder

Add metrics response builder to the controller:

```java
// Add to AnnouncementController
/**
 * Builds a JSON response for announcement metrics.
 */
private String buildMetricsResponse(AnnouncementMetricsDTO metrics) {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"totalAnnouncements\":").append(metrics.getTotalAnnouncements()).append(",");
    json.append("\"activeAnnouncements\":").append(metrics.getActiveAnnouncements()).append(",");
    json.append("\"inactiveAnnouncements\":").append(metrics.getInactiveAnnouncements()).append(",");
    
    // Type breakdown
    json.append("\"typeBreakdown\":{");
    metrics.getTypeBreakdown().entrySet().stream()
        .forEach(entry -> json.append("\"").append(escapeJson(entry.getKey()))
                             .append("\":").append(entry.getValue()).append(","));
    if (!metrics.getTypeBreakdown().isEmpty()) {
        json.setLength(json.length() - 1); // Remove trailing comma
    }
    json.append("},");
    
    // World breakdown
    json.append("\"worldBreakdown\":{");
    metrics.getWorldBreakdown().entrySet().stream()
        .forEach(entry -> json.append("\"").append(escapeJson(entry.getKey()))
                             .append("\":").append(entry.getValue()).append(","));
    if (!metrics.getWorldBreakdown().isEmpty()) {
        json.setLength(json.length() - 1); // Remove trailing comma
    }
    json.append("},");
    
    // Additional metrics...
    
    json.append("}");
    return json.toString();
}
```

## Testing and Validation

### Test Metrics Endpoint

After implementation:

```powershell
# Test metrics endpoint via HTTPS
Invoke-WebRequest -Uri "https://localhost:8081/api/v1/announcements/metrics" -Headers @{"X-API-Key"="test-api-key"} -SkipCertificateCheck

# Use API testing script
.\Test-RestRVNKCoreAPI.ps1 -Tests all -HttpsOnly -Detail
```

### Expected Results

Should return comprehensive metrics:

```json
{
  "totalAnnouncements": 0,
  "activeAnnouncements": 0,
  "inactiveAnnouncements": 0,
  "typeBreakdown": {},
  "worldBreakdown": {},
  "groupBreakdown": {},
  "recentActivity": {
    "createdLast24Hours": 0,
    "createdLast7Days": 0,
    "lastModified": null
  },
  "deliveryStats": {
    "totalDeliveries": 0,
    "averageDeliveriesPerDay": 0.0,
    "mostActiveAnnouncement": null
  }
}
```

## Completion Checklist

- [ ] **Added metrics handler method to controller**
- [ ] **Updated doGet routing to include metrics endpoint**
- [ ] **Created AnnouncementMetricsDTO class**
- [ ] **Added service interface method**
- [ ] **Implemented service method logic**
- [ ] **Added repository support methods if needed**
- [ ] **Created JSON response builder**
- [ ] **Tested endpoint returns proper JSON**
- [ ] **Validated metrics calculations**
- [ ] **Updated API documentation**

## Success Metrics

- Metrics endpoint returns valid JSON response
- All metric categories populated with correct data
- Response time under 2 seconds for typical datasets
- Proper error handling for calculation failures
- Metrics accurately reflect database state

This implementation will provide comprehensive announcement system insights for dashboard and monitoring purposes.
