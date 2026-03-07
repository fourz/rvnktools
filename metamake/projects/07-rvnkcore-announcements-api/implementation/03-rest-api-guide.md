# Implementation Guide: REST API Controllers

**Guide ID**: 03-rest-api-controllers-guide  
**Feature Reference**: 03-rest-api-controllers.md  
**Implementation Phase**: Week 2-3  
**Prerequisites**: Service layer implementation, Database integration

## Implementation Overview

This guide provides step-by-step instructions for implementing the REST API controllers for the RVNKCore announcements system, including authentication, authorization, and comprehensive endpoint coverage.

## Project Structure Setup

### 1. Create REST API Package Structure

```
src/main/java/org/fourz/rvnkcore/
└── api/
    ├── controller/
    │   ├── AnnouncementController.java
    │   ├── AnnouncementTypeController.java
    │   ├── ScheduleController.java
    │   └── PlayerPreferenceController.java
    ├── dto/
    │   ├── request/
    │   │   ├── CreateAnnouncementRequest.java
    │   │   ├── UpdateAnnouncementRequest.java
    │   │   └── ScheduleRequest.java
    │   └── response/
    │       ├── AnnouncementResponse.java
    │       ├── PagedResponse.java
    │       └── ErrorResponse.java
    ├── security/
    │   ├── JwtAuthenticationFilter.java
    │   ├── RateLimitingFilter.java
    │   └── PermissionValidator.java
    └── config/
        ├── RestApiConfig.java
        ├── SecurityConfig.java
        └── CorsConfig.java
```

### 2. Add Required Dependencies

Add to `pom.xml`:

```xml
<dependencies>
    <!-- Jetty Web Server -->
    <dependency>
        <groupId>org.eclipse.jetty</groupId>
        <artifactId>jetty-server</artifactId>
        <version>11.0.15</version>
    </dependency>
    <dependency>
        <groupId>org.eclipse.jetty</groupId>
        <artifactId>jetty-servlet</artifactId>
        <version>11.0.15</version>
    </dependency>
    
    <!-- JAX-RS Implementation (Jersey) -->
    <dependency>
        <groupId>org.glassfish.jersey.core</groupId>
        <artifactId>jersey-server</artifactId>
        <version>3.1.3</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish.jersey.containers</groupId>
        <artifactId>jersey-container-servlet-core</artifactId>
        <version>3.1.3</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish.jersey.inject</groupId>
        <artifactId>jersey-hk2</artifactId>
        <version>3.1.3</version>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>org.glassfish.jersey.media</groupId>
        <artifactId>jersey-media-json-jackson</artifactId>
        <version>3.1.3</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.2</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.15.2</version>
    </dependency>
    
    <!-- JWT Authentication -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

## Step 1: Configuration Implementation

### RestApiConfig.java

```java
package org.fourz.rvnkcore.api.config;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Configuration class for the REST API server.
 */
@Singleton
public class RestApiConfig {
    
    private final LogManager logger;
    private Server jettyServer;
    private final int port;
    private final String contextPath;
    
    @Inject
    public RestApiConfig(LogManager logger) {
        this.logger = logger;
        this.port = getConfigPort();
        this.contextPath = getConfigContextPath();
    }
    
    /**
     * Starts the REST API server.
     */
    public void startServer() {
        try {
            jettyServer = new Server(port);
            
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath(contextPath);
            
            ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/*");
            jerseyServlet.setInitOrder(0);
            
            // Configure Jersey
            jerseyServlet.setInitParameter(
                "jersey.config.server.provider.packages", 
                "org.fourz.rvnkcore.api.controller"
            );
            jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                "org.glassfish.jersey.media.json.JsonJacksonFeature"
            );
            
            jettyServer.setHandler(context);
            jettyServer.start();
            
            logger.info("REST API server started on port " + port + " with context " + contextPath);
            
        } catch (Exception e) {
            logger.error("Failed to start REST API server", e);
            throw new RuntimeException("Failed to start REST API server", e);
        }
    }
    
    /**
     * Stops the REST API server.
     */
    public void stopServer() {
        if (jettyServer != null) {
            try {
                jettyServer.stop();
                jettyServer.destroy();
                logger.info("REST API server stopped");
            } catch (Exception e) {
                logger.error("Error stopping REST API server", e);
            }
        }
    }
    
    private int getConfigPort() {
        // Get from configuration file or default to 8080
        return 8080;
    }
    
    private String getConfigContextPath() {
        // Get from configuration file or default to /api
        return "/api";
    }
}
```

### SecurityConfig.java

```java
package org.fourz.rvnkcore.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.Key;
import java.util.Date;
import java.util.Set;

/**
 * Security configuration for JWT authentication and authorization.
 */
@Singleton
public class SecurityConfig {
    
    private final LogManager logger;
    private final Key jwtKey;
    private final long jwtExpirationMs = 86400000; // 24 hours
    
    @Inject
    public SecurityConfig(LogManager logger) {
        this.logger = logger;
        this.jwtKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
    }
    
    /**
     * Generates a JWT token for a user with specified permissions.
     */
    public String generateToken(String username, Set<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);
        
        return Jwts.builder()
            .setSubject(username)
            .claim("permissions", permissions)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(jwtKey)
            .compact();
    }
    
    /**
     * Validates and parses a JWT token.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(jwtKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (Exception e) {
            logger.warning("Invalid JWT token: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts username from JWT token.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    /**
     * Extracts permissions from JWT token.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsFromToken(String token) {
        Claims claims = validateToken(token);
        if (claims != null && claims.get("permissions") instanceof Set) {
            return (Set<String>) claims.get("permissions");
        }
        return Set.of();
    }
}
```

## Step 2: Security Implementation

### JwtAuthenticationFilter.java

```java
package org.fourz.rvnkcore.api.security;

import org.fourz.rvnkcore.api.config.SecurityConfig;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter for JWT authentication on REST API endpoints.
 */
public class JwtAuthenticationFilter implements Filter {
    
    @Inject
    private SecurityConfig securityConfig;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Skip authentication for OPTIONS requests and public endpoints
        if ("OPTIONS".equals(httpRequest.getMethod()) || isPublicEndpoint(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        String authHeader = httpRequest.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }
        
        String token = authHeader.substring(7);
        String username = securityConfig.getUsernameFromToken(token);
        
        if (username == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Invalid token\"}");
            return;
        }
        
        // Add user context to request
        httpRequest.setAttribute("username", username);
        httpRequest.setAttribute("permissions", securityConfig.getPermissionsFromToken(token));
        
        chain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Public endpoints that don't require authentication
        return (path.equals("/api/health") && method.equals("GET")) ||
               (path.equals("/api/auth/login") && method.equals("POST"));
    }
}
```

### PermissionValidator.java

```java
package org.fourz.rvnkcore.api.security;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * Utility class for validating user permissions.
 */
public class PermissionValidator {
    
    public static final String ANNOUNCEMENT_READ = "announcement.read";
    public static final String ANNOUNCEMENT_WRITE = "announcement.write";
    public static final String ANNOUNCEMENT_DELETE = "announcement.delete";
    public static final String SCHEDULE_READ = "schedule.read";
    public static final String SCHEDULE_WRITE = "schedule.write";
    public static final String ADMIN = "admin";
    
    /**
     * Validates that the current user has the required permission.
     */
    public static boolean hasPermission(HttpServletRequest request, String requiredPermission) {
        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) request.getAttribute("permissions");
        
        if (permissions == null) {
            return false;
        }
        
        return permissions.contains(requiredPermission) || permissions.contains(ADMIN);
    }
    
    /**
     * Validates that the current user has any of the specified permissions.
     */
    public static boolean hasAnyPermission(HttpServletRequest request, String... requiredPermissions) {
        for (String permission : requiredPermissions) {
            if (hasPermission(request, permission)) {
                return true;
            }
        }
        return false;
    }
}
```

## Step 3: Controller Implementation

### AnnouncementController.java

```java
package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.dto.request.CreateAnnouncementRequest;
import org.fourz.rvnkcore.api.dto.request.UpdateAnnouncementRequest;
import org.fourz.rvnkcore.api.dto.response.AnnouncementResponse;
import org.fourz.rvnkcore.api.dto.response.PagedResponse;
import org.fourz.rvnkcore.api.dto.response.ErrorResponse;
import org.fourz.rvnkcore.api.security.PermissionValidator;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.dto.AnnouncementDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for announcement management operations.
 */
@Path("/announcements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnnouncementController {
    
    @Inject
    private AnnouncementService announcementService;
    
    @Inject
    private LogManager logger;
    
    @Context
    private HttpServletRequest request;
    
    /**
     * Gets all announcements with pagination and filtering.
     */
    @GET
    public CompletableFuture<Response> getAnnouncements(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("type") String type,
            @QueryParam("active") Boolean active,
            @QueryParam("world") String world) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_READ)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        return announcementService.getAnnouncements(page, size, type, active, world)
            .thenApply(announcements -> {
                PagedResponse<AnnouncementResponse> response = new PagedResponse<>();
                response.setContent(announcements.stream()
                    .map(this::toResponse)
                    .toList());
                response.setPage(page);
                response.setSize(size);
                response.setTotalElements(announcements.size()); // In real implementation, get actual total
                
                return Response.ok(response).build();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get announcements", throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve announcements"))
                    .build();
            });
    }
    
    /**
     * Gets a specific announcement by ID.
     */
    @GET
    @Path("/{id}")
    public CompletableFuture<Response> getAnnouncement(@PathParam("id") String id) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_READ)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        return announcementService.getAnnouncement(id)
            .thenApply(announcement -> {
                if (announcement == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Announcement not found"))
                        .build();
                }
                
                return Response.ok(toResponse(announcement)).build();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get announcement: " + id, throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve announcement"))
                    .build();
            });
    }
    
    /**
     * Creates a new announcement.
     */
    @POST
    public CompletableFuture<Response> createAnnouncement(CreateAnnouncementRequest createRequest) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_WRITE)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        // Validate request
        if (createRequest.getMessage() == null || createRequest.getMessage().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Message is required"))
                    .build()
            );
        }
        
        String username = (String) request.getAttribute("username");
        AnnouncementDTO dto = toDto(createRequest, username);
        
        return announcementService.createAnnouncement(dto)
            .thenApply(announcementId -> {
                logger.info("Created announcement: " + announcementId + " by " + username);
                return Response.status(Response.Status.CREATED)
                    .header("Location", "/api/announcements/" + announcementId)
                    .entity(Map.of("id", announcementId))
                    .build();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create announcement", throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create announcement"))
                    .build();
            });
    }
    
    /**
     * Updates an existing announcement.
     */
    @PUT
    @Path("/{id}")
    public CompletableFuture<Response> updateAnnouncement(
            @PathParam("id") String id, 
            UpdateAnnouncementRequest updateRequest) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_WRITE)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        return announcementService.getAnnouncement(id)
            .thenCompose(existing -> {
                if (existing == null) {
                    return CompletableFuture.completedFuture(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity(new ErrorResponse("Announcement not found"))
                            .build()
                    );
                }
                
                AnnouncementDTO updated = updateDto(existing, updateRequest);
                
                return announcementService.updateAnnouncement(updated)
                    .thenApply(v -> {
                        String username = (String) request.getAttribute("username");
                        logger.info("Updated announcement: " + id + " by " + username);
                        return Response.ok().build();
                    });
            })
            .exceptionally(throwable -> {
                logger.error("Failed to update announcement: " + id, throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to update announcement"))
                    .build();
            });
    }
    
    /**
     * Deletes an announcement.
     */
    @DELETE
    @Path("/{id}")
    public CompletableFuture<Response> deleteAnnouncement(@PathParam("id") String id) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_DELETE)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        return announcementService.deleteAnnouncement(id)
            .thenApply(deleted -> {
                if (deleted) {
                    String username = (String) request.getAttribute("username");
                    logger.info("Deleted announcement: " + id + " by " + username);
                    return Response.noContent().build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Announcement not found"))
                        .build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to delete announcement: " + id, throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete announcement"))
                    .build();
            });
    }
    
    /**
     * Sends an announcement immediately.
     */
    @POST
    @Path("/{id}/send")
    public CompletableFuture<Response> sendAnnouncement(
            @PathParam("id") String id,
            @QueryParam("world") String world,
            @QueryParam("permission") String permission) {
        
        if (!PermissionValidator.hasPermission(request, PermissionValidator.ANNOUNCEMENT_WRITE)) {
            return CompletableFuture.completedFuture(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions"))
                    .build()
            );
        }
        
        return announcementService.sendAnnouncement(id, world, permission)
            .thenApply(result -> {
                String username = (String) request.getAttribute("username");
                logger.info("Sent announcement: " + id + " by " + username + 
                           " - notified " + result.getPlayersNotified() + " players");
                
                return Response.ok(Map.of(
                    "playersNotified", result.getPlayersNotified(),
                    "playersFiltered", result.getPlayersFiltered(),
                    "executionTimeMs", result.getExecutionTimeMs()
                )).build();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to send announcement: " + id, throwable);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to send announcement"))
                    .build();
            });
    }
    
    // === Helper Methods ===
    
    private AnnouncementResponse toResponse(AnnouncementDTO dto) {
        return AnnouncementResponse.builder()
            .id(dto.getId())
            .message(dto.getMessage())
            .type(dto.getType())
            .active(dto.isActive())
            .world(dto.getWorld())
            .permission(dto.getPermission())
            .displayDurationSeconds(dto.getDisplayDurationSeconds())
            .priority(dto.getPriority())
            .createdBy(dto.getCreatedBy())
            .createdAt(dto.getCreatedAt())
            .updatedAt(dto.getUpdatedAt())
            .build();
    }
    
    private AnnouncementDTO toDto(CreateAnnouncementRequest request, String createdBy) {
        return AnnouncementDTO.builder()
            .message(request.getMessage())
            .type(request.getType())
            .active(request.isActive() != null ? request.isActive() : true)
            .world(request.getWorld())
            .permission(request.getPermission())
            .displayDurationSeconds(request.getDisplayDurationSeconds())
            .priority(request.getPriority() != null ? request.getPriority() : 0)
            .createdBy(createdBy)
            .build();
    }
    
    private AnnouncementDTO updateDto(AnnouncementDTO existing, UpdateAnnouncementRequest request) {
        if (request.getMessage() != null) {
            existing.setMessage(request.getMessage());
        }
        if (request.getType() != null) {
            existing.setType(request.getType());
        }
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }
        if (request.getWorld() != null) {
            existing.setWorld(request.getWorld());
        }
        if (request.getPermission() != null) {
            existing.setPermission(request.getPermission());
        }
        if (request.getDisplayDurationSeconds() != null) {
            existing.setDisplayDurationSeconds(request.getDisplayDurationSeconds());
        }
        if (request.getPriority() != null) {
            existing.setPriority(request.getPriority());
        }
        
        existing.setUpdatedAt(Timestamp.from(Instant.now()));
        return existing;
    }
}
```

## Step 4: DTO Implementation

### Request DTOs

```java
// CreateAnnouncementRequest.java
package org.fourz.rvnkcore.api.dto.request;

import lombok.Data;

@Data
public class CreateAnnouncementRequest {
    private String message;
    private String type;
    private Boolean active;
    private String world;
    private String permission;
    private Integer displayDurationSeconds;
    private Integer priority;
}

// UpdateAnnouncementRequest.java
package org.fourz.rvnkcore.api.dto.request;

import lombok.Data;

@Data
public class UpdateAnnouncementRequest {
    private String message;
    private String type;
    private Boolean active;
    private String world;
    private String permission;
    private Integer displayDurationSeconds;
    private Integer priority;
}
```

### Response DTOs

```java
// AnnouncementResponse.java
package org.fourz.rvnkcore.api.dto.response;

import lombok.Builder;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Builder
public class AnnouncementResponse {
    private String id;
    private String message;
    private String type;
    private boolean active;
    private String world;
    private String permission;
    private int displayDurationSeconds;
    private int priority;
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

// PagedResponse.java
package org.fourz.rvnkcore.api.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}

// ErrorResponse.java
package org.fourz.rvnkcore.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private long timestamp;
    
    public ErrorResponse(String message) {
        this.message = message;
        this.error = "API_ERROR";
        this.timestamp = System.currentTimeMillis();
    }
}
```

## Step 5: Integration with RVNKCore

### Update RVNKCore Main Class

```java
// Add to RVNKCore.java onEnable method
private RestApiConfig restApiConfig;

@Override
public void onEnable() {
    // ... existing initialization ...
    
    // Initialize REST API
    try {
        restApiConfig = serviceRegistry.getService(RestApiConfig.class);
        restApiConfig.startServer();
        logger.info("REST API server started successfully");
    } catch (Exception e) {
        logger.error("Failed to start REST API server", e);
    }
}

@Override
public void onDisable() {
    // Stop REST API server
    if (restApiConfig != null) {
        restApiConfig.stopServer();
    }
    
    // ... existing cleanup ...
}
```

## Step 6: Testing Implementation

### Integration Test Example

```java
package org.fourz.rvnkcore.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class AnnouncementControllerTest {
    
    private AnnouncementController controller;
    private MockAnnouncementService mockService;
    private MockHttpServletRequest mockRequest;
    
    @BeforeEach
    void setUp() {
        mockService = new MockAnnouncementService();
        controller = new AnnouncementController();
        // Inject dependencies
    }
    
    @Test
    void testGetAnnouncements_Success() {
        // Setup test data
        mockRequest.setAttribute("permissions", Set.of("announcement.read"));
        
        // Execute
        CompletableFuture<Response> future = controller.getAnnouncements(0, 20, null, null, null);
        Response response = future.join();
        
        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Additional assertions...
    }
    
    @Test
    void testCreateAnnouncement_InsufficientPermissions() {
        // Setup - no permissions
        mockRequest.setAttribute("permissions", Set.of());
        
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setMessage("Test message");
        
        // Execute
        CompletableFuture<Response> future = controller.createAnnouncement(request);
        Response response = future.join();
        
        // Verify
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }
}
```

## Step 7: Documentation

### OpenAPI Specification

Create `openapi.yaml`:

```yaml
openapi: 3.0.0
info:
  title: RVNKCore Announcements API
  version: 1.0.0
  description: REST API for managing announcements in RVNKCore
servers:
  - url: http://localhost:8080/api
paths:
  /announcements:
    get:
      summary: Get all announcements
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PagedAnnouncementResponse'
    post:
      summary: Create new announcement
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateAnnouncementRequest'
      responses:
        '201':
          description: Announcement created
components:
  schemas:
    CreateAnnouncementRequest:
      type: object
      required:
        - message
      properties:
        message:
          type: string
        type:
          type: string
        active:
          type: boolean
          default: true
```

## Validation Checklist

- [ ] JWT authentication implemented and tested
- [ ] Permission-based authorization working
- [ ] All CRUD operations functional
- [ ] Error handling comprehensive
- [ ] Rate limiting configured
- [ ] CORS properly configured
- [ ] OpenAPI documentation complete
- [ ] Integration tests passing
- [ ] Server startup/shutdown working
- [ ] Logging properly implemented

## Troubleshooting

### Common Issues

1. **Server won't start**: Check port availability and configuration
2. **Authentication fails**: Verify JWT key generation and token format
3. **CORS issues**: Ensure proper CORS headers are set
4. **JSON serialization errors**: Check Jackson configuration
5. **Permission errors**: Verify permission constants and validation logic

### Debug Commands

```bash
# Check server status
curl -X GET http://localhost:8080/api/health

# Test authentication
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# Test announcement creation
curl -X POST http://localhost:8080/api/announcements \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Test announcement","type":"info"}'
```

This implementation guide provides a comprehensive framework for building the REST API controllers with proper security, error handling, and integration with the RVNKCore service layer.
