# REST API Implementation Guide

**Guide ID**: 03-rest-api-implementation-guide  
**Phase**: REST API Implementation  
**Prerequisites**: Service layer implemented, Jetty server configured

## REST API Controller Implementation

### PlayerWorld REST Controller Structure

Create the REST controller classes to expose PlayerWorld functionality via HTTP endpoints:

```java
package org.fourz.rvnkcore.api.rest.controller;

import org.fourz.rvnkcore.service.PlayerWorldService;
import org.fourz.rvnkcore.dto.PlayerWorldDataDTO;
import org.fourz.rvnkcore.dto.PlayerAnalyticsDTO;
import org.fourz.rvnkcore.dto.LocationDTO;
import org.fourz.rvnkcore.logging.LogManager;
import org.fourz.rvnkcore.api.rest.response.ApiResponse;
import org.fourz.rvnkcore.api.rest.validation.ApiValidator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for PlayerWorld API endpoints.
 * Provides HTTP access to per-world player data and analytics.
 */
@RestController
@RequestMapping("/api/v1")
public class PlayerWorldController extends BaseApiController {
    
    private final PlayerWorldService playerWorldService;
    private final LogManager logger;
    
    public PlayerWorldController(PlayerWorldService playerWorldService, LogManager logger) {
        this.playerWorldService = playerWorldService;
        this.logger = logger;
    }
}
```

### Player World Data Endpoints

#### GET /players/{uuid}/worlds

```java
/**
 * Get all world data for a specific player
 */
@GetMapping("/players/{uuid}/worlds")
public CompletableFuture<ApiResponse<List<PlayerWorldDataDTO>>> getPlayerWorlds(
        @PathVariable String uuid,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "lastSeen") String sort,
        @RequestParam(defaultValue = "desc") String order,
        @RequestParam(required = false) Long minPlaytime,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        // Validate UUID format
        UUID playerId = ApiValidator.validateUUID(uuid);
        
        // Validate pagination parameters
        ApiValidator.validatePagination(page, size);
        
        // Get player world data
        return playerWorldService.getPlayerWorldHistory(playerId)
            .thenApply(worldData -> {
                // Apply filtering if minPlaytime specified
                List<PlayerWorldDataDTO> filteredData = worldData;
                if (minPlaytime != null) {
                    filteredData = worldData.stream()
                        .filter(data -> data.getTotalPlaytimeMs() >= minPlaytime)
                        .collect(Collectors.toList());
                }
                
                // Apply sorting
                filteredData.sort(getSortComparator(sort, order));
                
                // Apply pagination
                List<PlayerWorldDataDTO> pagedData = applyPagination(filteredData, page, size);
                
                return ApiResponse.success(pagedData)
                    .withPagination(page, size, filteredData.size());
            });
    });
}

private Comparator<PlayerWorldDataDTO> getSortComparator(String sort, String order) {
    boolean ascending = "asc".equalsIgnoreCase(order);
    
    switch (sort.toLowerCase()) {
        case "lastseen":
            return ascending 
                ? Comparator.comparing(PlayerWorldDataDTO::getLastSeen)
                : Comparator.comparing(PlayerWorldDataDTO::getLastSeen).reversed();
        case "playtime":
            return ascending
                ? Comparator.comparing(PlayerWorldDataDTO::getTotalPlaytimeMs)
                : Comparator.comparing(PlayerWorldDataDTO::getTotalPlaytimeMs).reversed();
        case "visitcount":
            return ascending
                ? Comparator.comparing(PlayerWorldDataDTO::getVisitCount)
                : Comparator.comparing(PlayerWorldDataDTO::getVisitCount).reversed();
        case "world":
            return ascending
                ? Comparator.comparing(PlayerWorldDataDTO::getWorldName)
                : Comparator.comparing(PlayerWorldDataDTO::getWorldName).reversed();
        default:
            return Comparator.comparing(PlayerWorldDataDTO::getLastSeen).reversed();
    }
}
```

#### GET /players/{uuid}/worlds/{world}

```java
/**
 * Get player data for a specific world
 */
@GetMapping("/players/{uuid}/worlds/{world}")
public CompletableFuture<ApiResponse<PlayerWorldDataDTO>> getPlayerWorldData(
        @PathVariable String uuid,
        @PathVariable String world,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        String worldName = ApiValidator.validateWorldName(world);
        
        return playerWorldService.getPlayerWorldData(playerId, worldName)
            .thenApply(worldData -> {
                if (worldData == null) {
                    throw new ApiNotFoundException("Player has not visited world: " + worldName);
                }
                return ApiResponse.success(worldData);
            });
    });
}
```

#### GET /players/{uuid}/worlds/{world}/location

```java
/**
 * Get last known location for player in specific world
 */
@GetMapping("/players/{uuid}/worlds/{world}/location")
public CompletableFuture<ApiResponse<LocationDTO>> getPlayerWorldLocation(
        @PathVariable String uuid,
        @PathVariable String world,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        String worldName = ApiValidator.validateWorldName(world);
        
        return playerWorldService.getLastKnownLocation(playerId, worldName)
            .thenApply(location -> {
                if (location == null) {
                    throw new ApiNotFoundException("No location data found for player in world: " + worldName);
                }
                
                LocationDTO locationDTO = LocationDTO.builder()
                    .world(worldName)
                    .x(location.getX())
                    .y(location.getY())
                    .z(location.getZ())
                    .yaw(location.getYaw())
                    .pitch(location.getPitch())
                    .biome(location.getBlock().getBiome().name())
                    .lastUpdated(System.currentTimeMillis())
                    .build();
                
                return ApiResponse.success(locationDTO);
            });
    });
}
```

### Player Analytics Endpoints

#### GET /players/{uuid}/analytics

```java
/**
 * Get comprehensive analytics for a player
 */
@GetMapping("/players/{uuid}/analytics")
public CompletableFuture<ApiResponse<PlayerAnalyticsDTO>> getPlayerAnalytics(
        @PathVariable String uuid,
        @RequestParam(required = false) String period,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        
        // Get analytics data
        CompletableFuture<Long> totalPlaytime = playerWorldService.getTotalPlaytime(playerId);
        CompletableFuture<List<String>> visitedWorlds = playerWorldService.getVisitedWorlds(playerId);
        CompletableFuture<List<String>> activeWorlds = playerWorldService.getMostActiveWorlds(playerId, 10);
        
        return CompletableFuture.allOf(totalPlaytime, visitedWorlds, activeWorlds)
            .thenApply(v -> {
                // Build analytics DTO
                PlayerAnalyticsDTO analytics = PlayerAnalyticsDTO.builder()
                    .playerId(playerId)
                    .totalPlaytimeMs(totalPlaytime.join())
                    .totalWorlds(visitedWorlds.join().size())
                    .mostActiveWorld(activeWorlds.join().isEmpty() ? null : activeWorlds.join().get(0))
                    .build();
                
                return ApiResponse.success(analytics)
                    .withMetadata("period", period != null ? period : "all-time");
            });
    });
}
```

#### GET /players/{uuid}/analytics/playtime

```java
/**
 * Get total playtime across all worlds with breakdown
 */
@GetMapping("/players/{uuid}/analytics/playtime")
public CompletableFuture<ApiResponse<Map<String, Object>>> getPlayerPlaytimeAnalytics(
        @PathVariable String uuid,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        
        return playerWorldService.getPlayerWorldHistory(playerId)
            .thenApply(worldData -> {
                long totalPlaytime = worldData.stream()
                    .mapToLong(PlayerWorldDataDTO::getTotalPlaytimeMs)
                    .sum();
                
                Map<String, Long> playtimeByWorld = worldData.stream()
                    .collect(Collectors.toMap(
                        PlayerWorldDataDTO::getWorldName,
                        PlayerWorldDataDTO::getTotalPlaytimeMs
                    ));
                
                Map<String, Object> response = Map.of(
                    "totalPlaytimeMs", totalPlaytime,
                    "totalPlaytimeFormatted", formatDuration(totalPlaytime),
                    "worldBreakdown", playtimeByWorld,
                    "averageSessionLength", calculateAverageSession(worldData),
                    "worldCount", worldData.size()
                );
                
                return ApiResponse.success(response);
            });
    });
}

private String formatDuration(long milliseconds) {
    long hours = milliseconds / (1000 * 60 * 60);
    long minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
    return String.format("%dh %dm", hours, minutes);
}
```

### World Analytics Endpoints

#### GET /worlds/{world}/analytics

```java
/**
 * Get analytics for a specific world
 */
@GetMapping("/worlds/{world}/analytics")
public CompletableFuture<ApiResponse<WorldAnalyticsDTO>> getWorldAnalytics(
        @PathVariable String world,
        @RequestParam(required = false) String period,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        String worldName = ApiValidator.validateWorldName(world);
        
        return playerWorldService.getWorldVisitCounts(worldName)
            .thenApply(analytics -> {
                return ApiResponse.success(analytics)
                    .withMetadata("period", period != null ? period : "all-time");
            });
    });
}
```

#### GET /worlds/{world}/players

```java
/**
 * Get all players who have visited a world
 */
@GetMapping("/worlds/{world}/players")
public CompletableFuture<ApiResponse<List<PlayerSummaryDTO>>> getWorldPlayers(
        @PathVariable String world,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "lastSeen") String sort,
        @RequestParam(defaultValue = "desc") String order,
        @RequestParam(defaultValue = "false") boolean activeOnly,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        String worldName = ApiValidator.validateWorldName(world);
        ApiValidator.validatePagination(page, size);
        
        return worldDataRepository.getPlayersInWorld(worldName, activeOnly)
            .thenApply(players -> {
                // Apply sorting and pagination
                players.sort(getPlayerSortComparator(sort, order));
                List<PlayerSummaryDTO> pagedPlayers = applyPagination(players, page, size);
                
                return ApiResponse.success(pagedPlayers)
                    .withPagination(page, size, players.size());
            });
    });
}
```

### WorldSwap Integration Endpoints

#### GET /players/{uuid}/worldswap/eligible

```java
/**
 * Get worlds player is eligible to swap to
 */
@GetMapping("/players/{uuid}/worldswap/eligible")
public CompletableFuture<ApiResponse<List<String>>> getEligibleWorlds(
        @PathVariable String uuid,
        HttpServletRequest request) {
    
    return handleApiRequest(request, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        
        // Get all available worlds
        List<String> allWorlds = plugin.getServer().getWorlds().stream()
            .map(world -> world.getName())
            .collect(Collectors.toList());
        
        // Filter based on permissions and access rules
        return CompletableFuture.supplyAsync(() -> {
            return allWorlds.stream()
                .filter(worldName -> hasWorldAccess(playerId, worldName))
                .collect(Collectors.toList());
        }).thenApply(eligibleWorlds -> {
            return ApiResponse.success(eligibleWorlds);
        });
    });
}
```

#### POST /players/{uuid}/worldswap/validate

```java
/**
 * Validate a proposed world swap
 */
@PostMapping("/players/{uuid}/worldswap/validate")
public CompletableFuture<ApiResponse<WorldSwapValidationDTO>> validateWorldSwap(
        @PathVariable String uuid,
        @RequestBody WorldSwapRequestDTO request,
        HttpServletRequest httpRequest) {
    
    return handleApiRequest(httpRequest, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        ApiValidator.validateNotNull(request.getToWorld(), "toWorld");
        
        return CompletableFuture.supplyAsync(() -> {
            boolean canSwap = hasWorldAccess(playerId, request.getToWorld());
            String reason = canSwap ? null : "Insufficient permissions for world: " + request.getToWorld();
            
            WorldSwapValidationDTO validation = WorldSwapValidationDTO.builder()
                .playerId(playerId)
                .fromWorld(request.getFromWorld())
                .toWorld(request.getToWorld())
                .canSwap(canSwap)
                .reason(reason)
                .estimatedCooldown(calculateSwapCooldown(playerId))
                .build();
            
            return ApiResponse.success(validation);
        });
    });
}
```

#### POST /players/{uuid}/worldswap/record

```java
/**
 * Record a completed world swap
 */
@PostMapping("/players/{uuid}/worldswap/record")
public CompletableFuture<ApiResponse<Void>> recordWorldSwap(
        @PathVariable String uuid,
        @RequestBody WorldSwapRecordDTO request,
        HttpServletRequest httpRequest) {
    
    return handleApiRequest(httpRequest, () -> {
        UUID playerId = ApiValidator.validateUUID(uuid);
        ApiValidator.validateNotNull(request.getFromWorld(), "fromWorld");
        ApiValidator.validateNotNull(request.getToWorld(), "toWorld");
        ApiValidator.validateNotNull(request.getMethod(), "method");
        
        return playerWorldService.recordWorldSwap(
                playerId, 
                request.getFromWorld(), 
                request.getToWorld(), 
                request.getMethod()
            )
            .thenApply(v -> ApiResponse.success());
    });
}
```

### Error Handling and Validation

#### Custom Exception Handling

```java
@ExceptionHandler(ApiValidationException.class)
public CompletableFuture<ApiResponse<Object>> handleValidationException(
        ApiValidationException ex,
        HttpServletResponse response) {
    
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    return CompletableFuture.completedFuture(
        ApiResponse.error("VALIDATION_ERROR", ex.getMessage())
            .withDetails(ex.getValidationErrors())
    );
}

@ExceptionHandler(ApiNotFoundException.class)
public CompletableFuture<ApiResponse<Object>> handleNotFoundException(
        ApiNotFoundException ex,
        HttpServletResponse response) {
    
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    return CompletableFuture.completedFuture(
        ApiResponse.error("NOT_FOUND", ex.getMessage())
    );
}

@ExceptionHandler(Exception.class)
public CompletableFuture<ApiResponse<Object>> handleGeneralException(
        Exception ex,
        HttpServletResponse response) {
    
    logger.error("Unexpected API error", ex);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    return CompletableFuture.completedFuture(
        ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred")
    );
}
```

#### Input Validation Utilities

```java
public class ApiValidator {
    
    public static UUID validateUUID(String uuidString) throws ApiValidationException {
        if (uuidString == null || uuidString.trim().isEmpty()) {
            throw new ApiValidationException("UUID cannot be null or empty");
        }
        
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("Invalid UUID format: " + uuidString);
        }
    }
    
    public static String validateWorldName(String worldName) throws ApiValidationException {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new ApiValidationException("World name cannot be null or empty");
        }
        
        // Validate world exists
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiValidationException("World not found: " + worldName);
        }
        
        return worldName;
    }
    
    public static void validatePagination(int page, int size) throws ApiValidationException {
        if (page < 1) {
            throw new ApiValidationException("Page number must be >= 1");
        }
        if (size < 1 || size > 200) {
            throw new ApiValidationException("Page size must be between 1 and 200");
        }
    }
    
    public static void validateNotNull(Object value, String fieldName) throws ApiValidationException {
        if (value == null) {
            throw new ApiValidationException(fieldName + " cannot be null");
        }
    }
}
```

### Response DTOs

#### ApiResponse Wrapper

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> metadata;
    private PaginationInfo pagination;
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        return response;
    }
    
    public static <T> ApiResponse<T> success() {
        return success(null);
    }
    
    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.errorCode = code;
        response.errorMessage = message;
        return response;
    }
    
    public ApiResponse<T> withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    public ApiResponse<T> withPagination(int page, int size, int total) {
        this.pagination = new PaginationInfo(page, size, total);
        return this;
    }
}
```

### Rate Limiting and Caching

#### Rate Limiting Implementation

```java
@Component
public class ApiRateLimiter {
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    
    public boolean tryAcquire(String apiKey, String endpoint) {
        String key = apiKey + ":" + endpoint;
        RateLimitBucket bucket = buckets.computeIfAbsent(key, k -> new RateLimitBucket());
        
        return bucket.tryAcquire();
    }
    
    private static class RateLimitBucket {
        private final int capacity = 100; // requests per hour
        private final long windowMs = 3600000; // 1 hour
        private int tokens = capacity;
        private long lastRefill = System.currentTimeMillis();
        
        synchronized boolean tryAcquire() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
        
        private void refill() {
            long now = System.currentTimeMillis();
            if (now - lastRefill >= windowMs) {
                tokens = capacity;
                lastRefill = now;
            }
        }
    }
}
```

#### Caching Headers

```java
private void addCacheHeaders(HttpServletResponse response, int maxAgeSeconds) {
    response.setHeader("Cache-Control", "public, max-age=" + maxAgeSeconds);
    response.setHeader("ETag", generateETag());
    response.setDateHeader("Last-Modified", System.currentTimeMillis());
}

private String generateETag() {
    return "\"" + System.currentTimeMillis() + "\"";
}
```

---

**Implementation Notes:**
- All endpoints return CompletableFuture for async processing
- Comprehensive input validation prevents malformed requests
- Rate limiting protects against API abuse
- Caching headers optimize response performance
- Error handling provides consistent API responses
- WorldSwap integration enables external plugin compatibility
