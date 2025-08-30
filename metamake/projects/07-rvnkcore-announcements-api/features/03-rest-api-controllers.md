# Feature Specification: REST API Controllers

**Feature ID**: 03-rest-api-controllers  
**Priority**: High  
**Dependencies**: Service Architecture, Database Schema  
**Implementation Phase**: Week 3

## Overview

This feature defines the comprehensive REST API layer for RVNKCore announcements, providing HTTP endpoints for web dashboard integration, external system communication, and administrative management.

## API Architecture

### Base API Structure

```text
/api/v1/
├── announcements/              # Announcement management
│   ├── GET    /                # List all announcements (paginated)
│   ├── POST   /                # Create new announcement
│   ├── GET    /{id}            # Get specific announcement
│   ├── PUT    /{id}            # Update announcement
│   ├── DELETE /{id}            # Delete announcement
│   ├── POST   /{id}/activate   # Activate announcement
│   ├── POST   /{id}/deactivate # Deactivate announcement
│   └── GET    /stats           # Get announcement statistics
├── types/                      # Announcement types
│   ├── GET    /                # List all types
│   ├── POST   /                # Create new type
│   ├── GET    /{id}            # Get specific type
│   ├── PUT    /{id}            # Update type
│   └── DELETE /{id}            # Delete type
├── schedules/                  # Scheduling management
│   ├── GET    /                # List all schedules
│   ├── POST   /                # Create new schedule
│   ├── GET    /{id}            # Get specific schedule
│   ├── PUT    /{id}            # Update schedule
│   └── DELETE /{id}            # Delete schedule
└── migration/                  # Migration utilities
    ├── POST   /yaml/import     # Import from YAML
    ├── GET    /yaml/validate   # Validate YAML structure
    └── GET    /status          # Migration status
```

## Controller Implementations

### AnnouncementController

```java
package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.dto.*;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.util.log.LogManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for announcement management.
 * Handles HTTP requests for CRUD operations on announcements.
 */
public class AnnouncementController extends BaseController {
    
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
        this.logger = LogManager.getInstance(this.getClass());
    }
    
    /**
     * GET /api/v1/announcements
     * List all announcements with pagination and filtering
     */
    public CompletableFuture<ApiResponse<PagedResult<AnnouncementDTO>>> listAnnouncements(
            HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            // Parse query parameters
            int page = getIntParameter(request, "page", 0);
            int size = getIntParameter(request, "size", 20);
            String type = getStringParameter(request, "type");
            String world = getStringParameter(request, "world");
            Boolean active = getBooleanParameter(request, "active");
            String sortBy = getStringParameter(request, "sort", "created_at");
            String sortOrder = getStringParameter(request, "order", "desc");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size < 1 || size > 100) size = 20;
            
            // Build query based on filters
            CompletableFuture<List<AnnouncementDTO>> announcementsFuture;
            
            if (type != null && world != null && active != null) {
                announcementsFuture = announcementService.findActiveByTypeAndWorld(type, world);
            } else if (type != null) {
                announcementsFuture = announcementService.getAnnouncementsByType(type);
            } else if (active != null) {
                announcementsFuture = announcementService.getActiveAnnouncements();
            } else {
                announcementsFuture = announcementService.getAllAnnouncements();
            }
            
            return announcementsFuture.thenApply(announcements -> {
                // Apply sorting
                announcements.sort(createComparator(sortBy, sortOrder));
                
                // Apply pagination
                int total = announcements.size();
                int start = page * size;
                int end = Math.min(start + size, total);
                
                List<AnnouncementDTO> pageContent = start < total ? 
                    announcements.subList(start, end) : List.of();
                
                PagedResult<AnnouncementDTO> result = new PagedResult<>(
                    pageContent, page, size, total, calculateTotalPages(total, size));
                
                return ApiResponse.success(result);
            });
        }, response);
    }
    
    /**
     * GET /api/v1/announcements/{id}
     * Get specific announcement by ID
     */
    public CompletableFuture<ApiResponse<AnnouncementDTO>> getAnnouncement(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Announcement ID is required"));
            }
            
            return announcementService.getAnnouncement(id)
                .thenApply(announcement -> {
                    if (announcement.isPresent()) {
                        return ApiResponse.success(announcement.get());
                    } else {
                        return ApiResponse.notFound("Announcement not found: " + id);
                    }
                });
        }, response);
    }
    
    /**
     * POST /api/v1/announcements
     * Create new announcement
     */
    public CompletableFuture<ApiResponse<AnnouncementDTO>> createAnnouncement(
            CreateAnnouncementRequest request, HttpServletRequest httpRequest, 
            HttpServletResponse response) {
        
        return handleAsync(() -> {
            // Validate request
            List<String> validationErrors = validateCreateRequest(request);
            if (!validationErrors.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Validation failed", validationErrors));
            }
            
            // Convert request to DTO
            AnnouncementDTO announcementDTO = AnnouncementDTO.builder()
                .message(request.getMessage())
                .type(request.getType())
                .active(request.isActive())
                .world(request.getWorld())
                .permission(request.getPermission())
                .displayDurationSeconds(request.getDisplayDurationSeconds())
                .priority(request.getPriority())
                .createdBy(getAuthenticatedUser(httpRequest))
                .build();
            
            return announcementService.createAnnouncement(announcementDTO)
                .thenCompose(id -> announcementService.getAnnouncement(id))
                .thenApply(created -> {
                    if (created.isPresent()) {
                        return ApiResponse.created(created.get());
                    } else {
                        return ApiResponse.error("Failed to create announcement");
                    }
                });
        }, response);
    }
    
    /**
     * PUT /api/v1/announcements/{id}
     * Update existing announcement
     */
    public CompletableFuture<ApiResponse<AnnouncementDTO>> updateAnnouncement(
            String id, UpdateAnnouncementRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Announcement ID is required"));
            }
            
            // Validate request
            List<String> validationErrors = validateUpdateRequest(request);
            if (!validationErrors.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Validation failed", validationErrors));
            }
            
            return announcementService.getAnnouncement(id)
                .thenCompose(existing -> {
                    if (!existing.isPresent()) {
                        return CompletableFuture.completedFuture(
                            ApiResponse.notFound("Announcement not found: " + id));
                    }
                    
                    // Apply updates
                    AnnouncementDTO updated = existing.get().toBuilder()
                        .message(request.getMessage())
                        .type(request.getType())
                        .active(request.isActive())
                        .world(request.getWorld())
                        .permission(request.getPermission())
                        .displayDurationSeconds(request.getDisplayDurationSeconds())
                        .priority(request.getPriority())
                        .build();
                    
                    return announcementService.updateAnnouncement(updated)
                        .thenApply(result -> ApiResponse.success(updated));
                });
        }, response);
    }
    
    /**
     * DELETE /api/v1/announcements/{id}
     * Delete announcement
     */
    public CompletableFuture<ApiResponse<Void>> deleteAnnouncement(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Announcement ID is required"));
            }
            
            return announcementService.deleteAnnouncement(id)
                .thenApply(deleted -> {
                    if (deleted) {
                        return ApiResponse.success(null, "Announcement deleted successfully");
                    } else {
                        return ApiResponse.notFound("Announcement not found: " + id);
                    }
                });
        }, response);
    }
    
    /**
     * POST /api/v1/announcements/{id}/activate
     * Activate announcement
     */
    public CompletableFuture<ApiResponse<AnnouncementDTO>> activateAnnouncement(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return toggleAnnouncementStatus(id, true, request, response);
    }
    
    /**
     * POST /api/v1/announcements/{id}/deactivate
     * Deactivate announcement
     */
    public CompletableFuture<ApiResponse<AnnouncementDTO>> deactivateAnnouncement(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return toggleAnnouncementStatus(id, false, request, response);
    }
    
    /**
     * GET /api/v1/announcements/stats
     * Get announcement system statistics
     */
    public CompletableFuture<ApiResponse<AnnouncementSystemStats>> getSystemStats(
            HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            return CompletableFuture.allOf(
                announcementService.getAllAnnouncements(),
                announcementService.getActiveAnnouncements(),
                announcementService.getAllAnnouncementTypes()
            ).thenApply(v -> {
                // Gather statistics
                List<AnnouncementDTO> allAnnouncements = announcementService.getAllAnnouncements().join();
                List<AnnouncementDTO> activeAnnouncements = announcementService.getActiveAnnouncements().join();
                List<AnnouncementTypeDTO> types = announcementService.getAllAnnouncementTypes().join();
                
                // Calculate type distribution
                Map<String, Integer> typeDistribution = allAnnouncements.stream()
                    .collect(Collectors.groupingBy(
                        AnnouncementDTO::getType,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));
                
                AnnouncementSystemStats stats = AnnouncementSystemStats.builder()
                    .totalAnnouncements(allAnnouncements.size())
                    .activeAnnouncements(activeAnnouncements.size())
                    .inactiveAnnouncements(allAnnouncements.size() - activeAnnouncements.size())
                    .totalTypes(types.size())
                    .typeDistribution(typeDistribution)
                    .averagePriority(calculateAveragePriority(allAnnouncements))
                    .lastUpdated(Instant.now())
                    .build();
                
                return ApiResponse.success(stats);
            });
        }, response);
    }
    
    // Helper methods
    private CompletableFuture<ApiResponse<AnnouncementDTO>> toggleAnnouncementStatus(
            String id, boolean active, HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Announcement ID is required"));
            }
            
            return announcementService.getAnnouncement(id)
                .thenCompose(existing -> {
                    if (!existing.isPresent()) {
                        return CompletableFuture.completedFuture(
                            ApiResponse.notFound("Announcement not found: " + id));
                    }
                    
                    AnnouncementDTO updated = existing.get().toBuilder()
                        .active(active)
                        .build();
                    
                    return announcementService.updateAnnouncement(updated)
                        .thenApply(result -> ApiResponse.success(updated));
                });
        }, response);
    }
}
```

### AnnouncementTypeController

```java
package org.fourz.rvnkcore.api.controller;

/**
 * REST API controller for announcement type management.
 */
public class AnnouncementTypeController extends BaseController {
    
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    public AnnouncementTypeController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
        this.logger = LogManager.getInstance(this.getClass());
    }
    
    /**
     * GET /api/v1/types
     * List all announcement types
     */
    public CompletableFuture<ApiResponse<List<AnnouncementTypeDTO>>> listTypes(
            HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            return announcementService.getAllAnnouncementTypes()
                .thenApply(types -> ApiResponse.success(types));
        }, response);
    }
    
    /**
     * POST /api/v1/types
     * Create new announcement type
     */
    public CompletableFuture<ApiResponse<AnnouncementTypeDTO>> createType(
            CreateAnnouncementTypeRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        
        return handleAsync(() -> {
            // Validate request
            List<String> validationErrors = validateCreateTypeRequest(request);
            if (!validationErrors.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Validation failed", validationErrors));
            }
            
            AnnouncementTypeDTO typeDTO = AnnouncementTypeDTO.builder()
                .id(request.getId())
                .name(request.getName())
                .description(request.getDescription())
                .defaultPriority(request.getDefaultPriority())
                .color(request.getColor())
                .enabled(request.isEnabled())
                .build();
            
            return announcementService.createAnnouncementType(typeDTO)
                .thenApply(created -> ApiResponse.created(created));
        }, response);
    }
    
    /**
     * GET /api/v1/types/{id}
     * Get specific announcement type
     */
    public CompletableFuture<ApiResponse<AnnouncementTypeDTO>> getType(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Type ID is required"));
            }
            
            return announcementService.getAnnouncementType(id)
                .thenApply(type -> {
                    if (type.isPresent()) {
                        return ApiResponse.success(type.get());
                    } else {
                        return ApiResponse.notFound("Type not found: " + id);
                    }
                });
        }, response);
    }
    
    /**
     * PUT /api/v1/types/{id}
     * Update announcement type
     */
    public CompletableFuture<ApiResponse<AnnouncementTypeDTO>> updateType(
            String id, UpdateAnnouncementTypeRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Type ID is required"));
            }
            
            List<String> validationErrors = validateUpdateTypeRequest(request);
            if (!validationErrors.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Validation failed", validationErrors));
            }
            
            return announcementService.getAnnouncementType(id)
                .thenCompose(existing -> {
                    if (!existing.isPresent()) {
                        return CompletableFuture.completedFuture(
                            ApiResponse.notFound("Type not found: " + id));
                    }
                    
                    AnnouncementTypeDTO updated = existing.get().toBuilder()
                        .name(request.getName())
                        .description(request.getDescription())
                        .defaultPriority(request.getDefaultPriority())
                        .color(request.getColor())
                        .enabled(request.isEnabled())
                        .build();
                    
                    return announcementService.updateAnnouncementType(updated)
                        .thenApply(result -> ApiResponse.success(updated));
                });
        }, response);
    }
    
    /**
     * DELETE /api/v1/types/{id}
     * Delete announcement type
     */
    public CompletableFuture<ApiResponse<Void>> deleteType(
            String id, HttpServletRequest request, HttpServletResponse response) {
        
        return handleAsync(() -> {
            if (id == null || id.trim().isEmpty()) {
                return CompletableFuture.completedFuture(
                    ApiResponse.badRequest("Type ID is required"));
            }
            
            // Check if type is in use
            return announcementService.getAnnouncementsByType(id)
                .thenCompose(announcements -> {
                    if (!announcements.isEmpty()) {
                        return CompletableFuture.completedFuture(
                            ApiResponse.conflict("Cannot delete type: " + announcements.size() + 
                                                " announcements are using this type"));
                    }
                    
                    return announcementService.deleteAnnouncementType(id)
                        .thenApply(deleted -> {
                            if (deleted) {
                                return ApiResponse.success(null, "Type deleted successfully");
                            } else {
                                return ApiResponse.notFound("Type not found: " + id);
                            }
                        });
                });
        }, response);
    }
}
```

## Request/Response DTOs

### API Request Models

```java
// Create announcement request
public class CreateAnnouncementRequest {
    @NotNull @NotEmpty
    private String message;
    
    @NotNull @NotEmpty
    private String type;
    
    private boolean active = true;
    private String world;
    private String permission;
    
    @Min(1)
    private int displayDurationSeconds = 5;
    
    @Min(0)
    private int priority = 1;
    
    // Constructors, getters, setters
}

// Update announcement request
public class UpdateAnnouncementRequest {
    @NotNull @NotEmpty
    private String message;
    
    @NotNull @NotEmpty
    private String type;
    
    private boolean active;
    private String world;
    private String permission;
    private int displayDurationSeconds;
    private int priority;
    
    // Constructors, getters, setters
}

// Create type request
public class CreateAnnouncementTypeRequest {
    @NotNull @NotEmpty @Size(max = 50)
    private String id;
    
    @NotNull @NotEmpty @Size(max = 100)
    private String name;
    
    private String description;
    
    @Min(0)
    private int defaultPriority = 1;
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color = "#FFFFFF";
    
    private boolean enabled = true;
    
    // Constructors, getters, setters
}
```

### API Response Models

```java
// Generic API response wrapper
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    private long timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "Created", data, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(false, message, null, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> badRequest(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(false, message, null, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> conflict(String message) {
        return new ApiResponse<>(false, message, null, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, System.currentTimeMillis());
    }
}

// Paged result wrapper
public class PagedResult<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    
    public PagedResult(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = (page == 0);
        this.last = (page >= totalPages - 1);
    }
    
    // Getters
}

// System statistics response
public class AnnouncementSystemStats {
    private int totalAnnouncements;
    private int activeAnnouncements;
    private int inactiveAnnouncements;
    private int totalTypes;
    private Map<String, Integer> typeDistribution;
    private double averagePriority;
    private Instant lastUpdated;
    
    // Builder pattern and getters
}
```

## Authentication and Security

### JWT Authentication

```java
// JWT authentication filter
public class JwtAuthenticationFilter implements Filter {
    
    private final JwtTokenProvider tokenProvider;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String token = extractToken(httpRequest);
        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            List<String> authorities = tokenProvider.getAuthoritiesFromToken(token);
            
            // Set authentication context
            httpRequest.setAttribute("authenticated_user", username);
            httpRequest.setAttribute("user_authorities", authorities);
        }
        
        chain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### Permission-Based Access Control

```java
// Authorization annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {
    String value();
}

// Authorization aspect
public class AuthorizationAspect {
    
    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, 
                                RequiresPermission requiresPermission) throws Throwable {
        
        HttpServletRequest request = getCurrentRequest(joinPoint);
        List<String> userAuthorities = getUserAuthorities(request);
        
        String requiredPermission = requiresPermission.value();
        if (!userAuthorities.contains(requiredPermission)) {
            return ApiResponse.forbidden("Insufficient permissions: " + requiredPermission);
        }
        
        return joinPoint.proceed();
    }
}

// Usage in controller
@RequiresPermission("rvnktools.announcement.create")
public CompletableFuture<ApiResponse<AnnouncementDTO>> createAnnouncement(/*...*/) {
    // Implementation
}
```

## Error Handling

### Global Exception Handler

```java
public class GlobalExceptionHandler {
    
    private final LogManager logger = LogManager.getInstance(this.getClass());
    
    public ApiResponse<Void> handleValidationException(ValidationException e) {
        logger.warning("Validation error: " + e.getMessage());
        return ApiResponse.badRequest("Validation failed", e.getValidationErrors());
    }
    
    public ApiResponse<Void> handleServiceException(ServiceException e) {
        logger.error("Service error: " + e.getMessage(), e);
        return ApiResponse.error("Service unavailable: " + e.getMessage());
    }
    
    public ApiResponse<Void> handleAnnouncementNotFoundException(AnnouncementNotFoundException e) {
        logger.warning("Announcement not found: " + e.getAnnouncementId());
        return ApiResponse.notFound("Announcement not found: " + e.getAnnouncementId());
    }
    
    public ApiResponse<Void> handleGenericException(Exception e) {
        logger.error("Unexpected error in API", e);
        return ApiResponse.error("Internal server error");
    }
}
```

## OpenAPI Documentation

### API Documentation Generation

```java
// OpenAPI configuration
@OpenAPIDefinition(
    info = @Info(
        title = "RVNKCore Announcements API",
        version = "1.0.0",
        description = "REST API for managing Minecraft server announcements",
        contact = @Contact(
            name = "RVNK Development",
            url = "https://github.com/fourz/rvnktools"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development Server"),
        @Server(url = "https://api.rvnk.org", description = "Production Server")
    }
)
public class OpenApiConfig {
    // Configuration
}

// Controller method documentation
@Operation(
    summary = "Create new announcement",
    description = "Creates a new announcement with the specified parameters",
    responses = {
        @ApiResponse(responseCode = "201", description = "Announcement created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    }
)
@RequiresPermission("rvnktools.announcement.create")
public CompletableFuture<ApiResponse<AnnouncementDTO>> createAnnouncement(
    @RequestBody @Valid CreateAnnouncementRequest request,
    HttpServletRequest httpRequest, HttpServletResponse response) {
    // Implementation
}
```

## Rate Limiting

### Request Rate Limiting

```java
public class RateLimitFilter implements Filter {
    
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    
    public RateLimitFilter(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientId = getClientIdentifier(httpRequest);
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(clientId, 
            k -> RateLimiter.create(requestsPerMinute / 60.0)); // Requests per second
        
        if (rateLimiter.tryAcquire()) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429); // Too Many Requests
            httpResponse.setContentType("application/json");
            
            ApiResponse<Void> errorResponse = ApiResponse.error("Rate limit exceeded");
            String json = JsonUtils.toJson(errorResponse);
            httpResponse.getWriter().write(json);
        }
    }
    
    private String getClientIdentifier(HttpServletRequest request) {
        // Use authenticated user if available, otherwise IP address
        String user = (String) request.getAttribute("authenticated_user");
        return user != null ? user : request.getRemoteAddr();
    }
}
```

## Integration Testing

### API Integration Tests

```java
@TestMethodOrder(OrderAnnotation.class)
public class AnnouncementControllerIntegrationTest {
    
    private static TestServer testServer;
    private static HttpClient httpClient;
    private static String baseUrl;
    private static String authToken;
    
    @BeforeAll
    static void setUp() throws Exception {
        testServer = new TestServer();
        testServer.start();
        
        baseUrl = "http://localhost:" + testServer.getPort() + "/api/v1";
        httpClient = HttpClient.newHttpClient();
        authToken = obtainAuthToken();
    }
    
    @Test
    @Order(1)
    void testCreateAnnouncement() throws Exception {
        CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
            .message("Test announcement")
            .type("general")
            .active(true)
            .build();
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/announcements"))
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(request)))
            .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, 
            HttpResponse.BodyHandlers.ofString());
        
        assertThat(response.statusCode()).isEqualTo(201);
        
        ApiResponse<AnnouncementDTO> apiResponse = JsonUtils.fromJson(
            response.body(), new TypeReference<ApiResponse<AnnouncementDTO>>() {});
        
        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getData().getMessage()).isEqualTo("Test announcement");
    }
    
    @Test
    @Order(2)
    void testListAnnouncements() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/announcements?page=0&size=10"))
            .header("Authorization", "Bearer " + authToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest,
            HttpResponse.BodyHandlers.ofString());
        
        assertThat(response.statusCode()).isEqualTo(200);
        
        ApiResponse<PagedResult<AnnouncementDTO>> apiResponse = JsonUtils.fromJson(
            response.body(), new TypeReference<ApiResponse<PagedResult<AnnouncementDTO>>>() {});
        
        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getData().getContent()).isNotEmpty();
    }
    
    @Test
    void testRateLimiting() throws Exception {
        // Send multiple requests rapidly
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/announcements"))
                        .header("Authorization", "Bearer " + authToken)
                        .GET()
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofString());
                    
                    return response.statusCode();
                } catch (Exception e) {
                    return -1;
                }
            });
            futures.add(future);
        }
        
        List<Integer> statusCodes = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        // Should have some 429 (Too Many Requests) responses
        long rateLimitedCount = statusCodes.stream()
            .filter(code -> code == 429)
            .count();
        
        assertThat(rateLimitedCount).isGreaterThan(0);
    }
    
    @AfterAll
    static void tearDown() throws Exception {
        if (testServer != null) {
            testServer.stop();
        }
    }
}
```

This comprehensive REST API implementation provides a complete HTTP interface for the RVNKCore announcements system, including authentication, authorization, rate limiting, documentation, and testing capabilities.
