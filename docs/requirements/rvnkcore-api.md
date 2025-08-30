# RVNKCore API Requirements

**Document Version**: 1.0  
**Last Updated**: August 1, 2025  
**Status**: Draft - Subject to Revision

## Purpose

This document defines the API requirements for RVNKCore, including internal Java APIs for plugin integration and external REST APIs for web-based access. The API framework supports the RVNK plugin ecosystem's need for both in-game functionality and external web integrations.

## API Architecture Overview

RVNKCore provides two distinct API layers designed for different access patterns and security requirements:

```text
┌─────────────────────────────────────────────────────────────┐
│                    External Access Layer                    │
├─────────────────────────────────────────────────────────────┤
│  REST API (HTTP/HTTPS)                                     │
│  • Web Applications (Shops, Tools, Lore)                   │
│  • Third-party Integrations                                │
│  • Administrative Dashboards                               │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                    Internal Service Layer                   │
├─────────────────────────────────────────────────────────────┤
│  Java API (In-Process)                                     │
│  • Plugin-to-Plugin Communication                          │
│  • Real-time Game Mechanics (Quests, Worlds)              │
│  • High-Performance Operations                             │
└─────────────────────────────────────────────────────────────┘
```

## Java API Requirements

### Service Discovery Framework

#### ServiceRegistry Interface

```java
package org.fourz.rvnkcore.api.service;

/**
 * Central service discovery and dependency injection container.
 * Provides type-safe service access across the RVNK plugin ecosystem.
 */
public interface ServiceRegistry {
    /**
     * Registers a service implementation with the registry.
     */
    <T> void registerService(Class<T> serviceInterface, T implementation);
    
    /**
     * Retrieves a service implementation by interface type.
     */
    <T> T getService(Class<T> serviceInterface);
    
    /**
     * Checks if a service is available.
     */
    <T> boolean hasService(Class<T> serviceInterface);
    
    /**
     * Lists all registered service interfaces.
     */
    Set<Class<?>> getRegisteredServices();
    
    /**
     * Shuts down all registered services gracefully.
     */
    void shutdown();
}
```

#### Service Lifecycle Management

**Requirements:**

- All services must implement `AutoCloseable` for proper resource cleanup
- Service initialization must be performed in dependency order
- Services must support graceful shutdown during plugin disable
- Service registration must be thread-safe for concurrent access
- Circular dependency detection and prevention

### Core Service Interfaces

#### PlayerService Interface

```java
package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import java.util.concurrent.CompletableFuture;

/**
 * Centralized player data management service.
 * Provides async operations for player tracking, preferences, and metadata.
 */
public interface PlayerService {
    // Player Lifecycle
    CompletableFuture<PlayerDTO> createPlayer(UUID playerId, String playerName, 
                                            String world, double x, double y, double z);
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    CompletableFuture<Boolean> playerExists(UUID playerId);
    
    // Player Data Updates
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String world, 
                                                double x, double y, double z);
    CompletableFuture<Void> updatePlayerName(UUID playerId, String newName);
    CompletableFuture<Void> updatePlayerGroups(UUID playerId, String primaryGroup, 
                                              List<String> allGroups);
    
    // Query Operations
    CompletableFuture<List<PlayerDTO>> getRecentPlayers(int hoursAgo);
    CompletableFuture<List<PlayerDTO>> getPlayersByGroup(String groupName);
    CompletableFuture<List<PlayerDTO>> searchPlayersByName(String namePattern);
    CompletableFuture<Long> getPlayerCount();
}
```

#### AnnouncementService Interface

```java
package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;

/**
 * Centralized announcement management service.
 * Handles announcement creation, scheduling, and delivery.
 */
public interface AnnouncementService {
    // Announcement Management
    CompletableFuture<AnnouncementDTO> createAnnouncement(String title, String message, 
                                                         String category, boolean enabled);
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    CompletableFuture<AnnouncementDTO> updateAnnouncement(AnnouncementDTO announcement);
    CompletableFuture<Void> deleteAnnouncement(String id);
    
    // Query Operations
    CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements();
    CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements();
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByCategory(String category);
    
    // Scheduling Operations
    CompletableFuture<Void> scheduleAnnouncement(String id, LocalDateTime scheduledTime);
    CompletableFuture<Void> cancelScheduledAnnouncement(String id);
}
```

### Data Transfer Objects (DTOs)

#### PlayerDTO Requirements

```java
package org.fourz.rvnkcore.api.model;

/**
 * Player data transfer object with comprehensive tracking information.
 */
public class PlayerDTO {
    // Identity
    private UUID id;
    private String currentName;
    private List<String> nameHistory;
    
    // Activity Tracking
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private long timesJoined;
    
    // Location Data
    private String lastWorld;
    private double lastX;
    private double lastY;
    private double lastZ;
    
    // Permission Integration
    private String primaryGroup;
    private List<String> allGroups;
    
    // Metadata
    private Map<String, Object> metadata;
    
    // Builder pattern for construction
    public static class Builder { /* implementation */ }
}
```

#### AnnouncementDTO Requirements

```java
package org.fourz.rvnkcore.api.model;

/**
 * Announcement data transfer object with scheduling and categorization.
 */
public class AnnouncementDTO {
    private String id;
    private String title;
    private String message;
    private String category;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime scheduledAt;
    private int priority;
    private Map<String, Object> properties;
    
    // Builder pattern for construction
    public static class Builder { /* implementation */ }
}
```

### Event System Requirements

#### Event Interface Hierarchy

```java
package org.fourz.rvnkcore.api.event;

/**
 * Base interface for all RVNKCore events.
 */
public interface RVNKEvent {
    String getEventType();
    LocalDateTime getTimestamp();
    UUID getEventId();
    boolean isCancellable();
}

/**
 * Event for player-related changes.
 */
public interface PlayerEvent extends RVNKEvent {
    UUID getPlayerId();
    String getPlayerName();
}

/**
 * Event for data changes across the system.
 */
public interface DataChangeEvent extends RVNKEvent {
    String getEntityType();
    String getEntityId();
    String getChangeType(); // CREATE, UPDATE, DELETE
    Map<String, Object> getChangedFields();
}
```

#### Event Bus Requirements

```java
package org.fourz.rvnkcore.api.event;

/**
 * Event distribution system for cross-plugin communication.
 */
public interface EventBus {
    // Event Registration
    <T extends RVNKEvent> void registerListener(Class<T> eventType, 
                                               EventListener<T> listener, 
                                               EventPriority priority);
    void unregisterListener(EventListener<?> listener);
    
    // Event Publishing
    <T extends RVNKEvent> CompletableFuture<Void> publishEvent(T event);
    <T extends RVNKEvent> CompletableFuture<Void> publishEventAsync(T event);
    
    // Event Persistence (Optional)
    CompletableFuture<List<RVNKEvent>> getEventHistory(String eventType, 
                                                      LocalDateTime since);
}
```

## REST API Requirements

### API Server Infrastructure

#### Server Configuration

```yaml
# API server configuration requirements
api:
  enabled: true
  host: "0.0.0.0"
  port: 8080
  ssl:
    enabled: false
    port: 8443
    keystore: "rvnkcore-keystore.jks"
    keystore-password: "changeit"
  
  authentication:
    type: "api-key"  # or "oauth", "jwt"
    api-keys:
      admin: "admin-key-here"
      readonly: "readonly-key-here"
  
  rate-limiting:
    enabled: true
    requests-per-minute: 60
    burst-limit: 10
  
  cors:
    enabled: true
    allowed-origins: ["*"]
    allowed-methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
```

#### Security Requirements

**Authentication:**

- API key-based authentication for external access
- Role-based access control (admin, readonly, plugin-specific)
- Request signing for sensitive operations
- IP-based access restrictions (optional)

**Authorization:**

- Service-level permissions (player data, announcements, etc.)
- Operation-level permissions (read, write, delete)
- Resource-level permissions (own data vs. all data)

**Data Protection:**

- HTTPS support with proper certificate management
- Request/response logging for audit trails
- Rate limiting to prevent abuse
- Input validation and sanitization

### Plugin-Specific API Endpoints

#### Web-Enabled Plugins (Full REST API)

**RVNKShops API Requirements:**

```http
# Product Management
GET    /api/v1/shops
POST   /api/v1/shops
GET    /api/v1/shops/{shopId}
PUT    /api/v1/shops/{shopId}
DELETE /api/v1/shops/{shopId}

# Inventory Management
GET    /api/v1/shops/{shopId}/inventory
POST   /api/v1/shops/{shopId}/inventory
PUT    /api/v1/shops/{shopId}/inventory/{itemId}
DELETE /api/v1/shops/{shopId}/inventory/{itemId}

# Transaction Management
GET    /api/v1/shops/{shopId}/transactions
POST   /api/v1/shops/{shopId}/transactions
GET    /api/v1/transactions/{transactionId}
```

**RVNKTools API Requirements:**

```http
# Announcement Management
GET    /api/v1/announcements
POST   /api/v1/announcements
GET    /api/v1/announcements/{announcementId}
PUT    /api/v1/announcements/{announcementId}
DELETE /api/v1/announcements/{announcementId}

# Server Management
GET    /api/v1/server/status
GET    /api/v1/server/metrics
POST   /api/v1/server/commands
```

**RVNKLore API Requirements:**

```http
# Lore Content Management
GET    /api/v1/lore
POST   /api/v1/lore
GET    /api/v1/lore/{loreId}
PUT    /api/v1/lore/{loreId}
DELETE /api/v1/lore/{loreId}

# Item Management
GET    /api/v1/lore/items
GET    /api/v1/lore/items/{itemId}
POST   /api/v1/lore/items
```

#### Core Player API Endpoints

```http
# Player Data Access
GET    /api/v1/players                    # List all players with pagination
GET    /api/v1/players/online             # Current online players
GET    /api/v1/players/recent?hours=24    # Recent players
GET    /api/v1/players/{uuid}             # Get player by UUID
GET    /api/v1/player/name/{name}        # Get player by name
GET    /api/v1/player/name/{name}/history    # Get player name history
GET    /api/v1/players/group/{group}      # Players by permission group
GET    /api/v1/players/search?name=pattern # Search players

# Player Data Updates
PUT    /api/v1/players/{uuid}/location    # Update player location
PUT    /api/v1/players/{uuid}/groups      # Update player groups
PUT    /api/v1/players/{uuid}/metadata    # Update player metadata
```

### API Response Standards

#### Standard Response Format

```json
{
  "success": true,
  "data": { /* response data */ },
  "pagination": {
    "page": 1,
    "pageSize": 50,
    "totalItems": 250,
    "totalPages": 5
  },
  "meta": {
    "timestamp": "2025-08-01T12:00:00Z",
    "requestId": "req-12345",
    "apiVersion": "1.0.0"
  }
}
```

#### Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "PLAYER_NOT_FOUND",
    "message": "Player with UUID 12345 not found",
    "details": {
      "uuid": "12345",
      "searchedAt": "2025-08-01T12:00:00Z"
    }
  },
  "meta": {
    "timestamp": "2025-08-01T12:00:00Z",
    "requestId": "req-12345",
    "apiVersion": "1.0.0"
  }
}
```

### API Documentation Requirements

#### OpenAPI Specification

- Complete OpenAPI 3.0 specification for all endpoints
- Interactive API documentation (Swagger UI)
- Code generation support for client libraries
- Example requests and responses for all operations

#### Developer Resources

- Getting started guide with authentication setup
- SDK/client libraries for popular languages (JavaScript, Python, Java)
- Postman collection for API testing
- Rate limiting and best practices documentation

## API Versioning Strategy

### Version Management

**URL-Based Versioning:**

- All APIs include version in URL path: `/api/v1/`, `/api/v2/`
- Major version changes for breaking changes
- Minor version changes for backward-compatible additions
- Patch version changes for bug fixes

**Backward Compatibility:**

- Maintain at least 2 major versions simultaneously
- Deprecation warnings for 6 months before removal
- Compatibility layer for smooth transitions
- Client-side version negotiation support

### Migration Support

**API Evolution:**

- Clear migration guides for version upgrades
- Side-by-side version comparison documentation
- Automated testing for version compatibility
- Client notification system for deprecated endpoints

## Performance Requirements

### Response Time Targets

- **Player Data Queries**: < 100ms average, < 500ms 95th percentile
- **Bulk Operations**: < 2 seconds for up to 1000 records
- **Real-time Updates**: < 50ms for event propagation
- **External API Calls**: < 1 second average response time

### Throughput Requirements

- **Concurrent Users**: Support 500+ concurrent players
- **API Requests**: 1000+ requests per minute sustained
- **Database Operations**: 10,000+ operations per minute
- **Event Processing**: 5,000+ events per minute

### Scalability Considerations

- **Connection Pooling**: Efficient database connection management
- **Caching Strategy**: Redis integration for high-frequency data
- **Load Balancing**: Support for multiple API server instances
- **Resource Monitoring**: Built-in performance metrics and alerting

This API specification will be revised as implementation progresses and additional requirements are identified during development and testing phases.
