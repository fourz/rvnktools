# RVNKCore REST API Implementation Guide

**Version**: 1.0  
**Last Updated**: August 22, 2025  
**Status**: Production Ready - Implementation Complete

## Overview

This guide provides comprehensive documentation for implementing and working with the RVNKCore REST API infrastructure. The implementation includes a fully operational Jetty server with SSL/HTTPS support, authentication, rate limiting, and comprehensive error handling.

**Implementation Status**: ✅ **FULLY OPERATIONAL** - The REST API infrastructure has been successfully implemented and tested in production.

## Architecture Overview

The REST API follows a layered architecture with clear separation of concerns:

```text
┌─────────────────────────────────────────────────────────────┐
│                    Client Applications                      │
│  Web Apps │ Mobile Apps │ External Tools │ Dashboards      │
└─────────────┬───────────────────────────────────────────────┘
              │ HTTP/HTTPS Requests
              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Jetty Server Framework                      │
├─────────────┬─────────────┬─────────────┬─────────────────┤
│ SSL/TLS     │ Rate        │ CORS        │ Authentication  │
│ Security    │ Limiting    │ Support     │ & Authorization │
└─────────────┴─────────────┴─────────────┴─────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Controller Layer                          │
├─────────────┬─────────────┬─────────────┬─────────────────┤
│ Player      │Announcement │ Health      │ Administrative  │
│ Controller  │ Controller  │ Controller  │ Controller      │
└─────────────┴─────────────┴─────────────┴─────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                             │
│        ServiceRegistry + Business Services                  │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Repository Pattern                          │
│          Database Abstraction Layer                         │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. CoreServer - Main Server Implementation

**Location**: `org.fourz.rvnkcore.api.server.jetty.CoreServer`

The CoreServer is the main entry point for the REST API infrastructure:

```java
/**
 * RVNKCore Jetty server for REST API services.
 * Provides HTTP/HTTPS endpoints with comprehensive security and monitoring.
 */
public class CoreServer {
    private Server server;
    private final ApiConfig config;
    private final PlayerService playerService;
    private final Gson gson;
    
    // Specialized factories for different aspects of server setup
    private final ServerConnectorFactory connectorFactory;
    private final ServletFactory servletFactory;
    private final ServerLifecycle serverLifecycle;
    
    public CoreServer(ApiConfig config, PlayerService playerService, Plugin plugin) {
        // Initialization with factory pattern for modularity
    }
    
    // Server lifecycle methods
    public void start() throws Exception;
    public void stop() throws Exception;
    public boolean isRunning();
}
```

#### Key Features:
- **Factory Pattern Architecture**: Modular design with specialized factories
- **SSL/HTTPS Support**: Full certificate management and secure connections
- **Graceful Shutdown**: Proper resource cleanup and connection handling
- **Error Recovery**: Automatic restart capabilities and health monitoring

### 2. Server Factory Components

#### ServerConnectorFactory
**Purpose**: Creates and configures HTTP/HTTPS connectors

```java
public class ServerConnectorFactory {
    /**
     * Creates HTTP connector with specified configuration.
     */
    public ServerConnector createHttpConnector(Server server, String host, int port);
    
    /**
     * Creates HTTPS connector with SSL configuration.
     */
    public ServerConnector createHttpsConnector(Server server, String host, int port, 
                                              String keystorePath, String keystorePassword);
}
```

#### ServletFactory
**Purpose**: Creates and configures servlet contexts and handlers

```java
public class ServletFactory {
    /**
     * Creates servlet context with security and CORS configuration.
     */
    public ServletContextHandler createContext(String contextPath, ApiConfig config);
    
    /**
     * Registers REST API controllers with proper routing.
     */
    public void registerControllers(ServletContextHandler context, PlayerService playerService, Gson gson);
}
```

#### ServerSecurityFactory
**Purpose**: Implements authentication and authorization

```java
public class ServerSecurityFactory {
    /**
     * Creates API key authentication filter.
     */
    public Filter createApiKeyFilter(String apiKey, String[] allowedIPs);
    
    /**
     * Creates CORS filter for cross-origin requests.
     */
    public Filter createCorsFilter(String allowedOrigins, String allowedMethods);
}
```

### 3. API Configuration

**Location**: `org.fourz.rvnkcore.api.config.ApiConfig`

Comprehensive configuration management with environment-specific overrides:

```java
public class ApiConfig {
    // Server Configuration
    private final int httpPort;           // Default: 8080
    private final int httpsPort;          // Default: 8443
    private final String host;            // Default: "0.0.0.0"
    private final boolean enabled;        // Default: true
    
    // Security Configuration
    private final String apiKey;          // Required for authentication
    private final boolean httpsEnabled;   // Default: true
    private final String keystorePath;    // SSL certificate path
    private final String keystorePassword; // SSL certificate password
    private final String[] allowedIPs;   // IP whitelist (optional)
    
    // Performance Configuration
    private final int maxThreads;         // Default: 50
    private final int idleTimeout;        // Default: 30000ms
    private final int connectionTimeout;  // Default: 10000ms
    
    // CORS Configuration
    private final boolean corsEnabled;    // Default: true
    private final String corsAllowedOrigins; // Default: "*"
    private final String corsAllowedMethods; // Default: "GET,POST,PUT,DELETE"
    
    // Monitoring Configuration
    private final Level apiLogLevel;      // Default: INFO
    private final boolean sendServerVersion; // Default: false
}
```

#### Configuration Sources

1. **Primary**: `config-core.yml` (loaded via ConfigLoader)
2. **Fallback**: `config.yml` (plugin default configuration)
3. **Environment Variables**: Override for containerized deployments

**Example Configuration**:

```yaml
# config-core.yml
api:
  enabled: true
  httpPort: 8080
  httpsPort: 8443
  host: "0.0.0.0"
  apiKey: "your-secure-api-key-here"
  
  # SSL Configuration
  httpsEnabled: true
  keystorePath: "certificates/rvnkcore.jks"
  keystorePassword: "your-keystore-password"
  
  # Performance Settings
  maxThreads: 50
  idleTimeout: 30000
  connectionTimeout: 10000
  
  # Security Settings
  allowedIPs:
    - "127.0.0.1"
    - "192.168.1.0/24"
  
  # CORS Settings
  corsEnabled: true
  corsAllowedOrigins: "*"
  corsAllowedMethods: "GET,POST,PUT,DELETE"
  
  # Monitoring
  apiLogLevel: "INFO"
  sendServerVersion: false
```

### 4. Controller Implementation

**Location**: `org.fourz.rvnkcore.api.controller.*`

Controllers handle HTTP requests and implement REST endpoint logic:

#### PlayerController Example

```java
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController extends HttpServlet {
    private final PlayerService playerService;
    private final Gson gson;
    private final LogManager logger;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // GET /api/v1/players - List all players with pagination
                handleListPlayers(req, resp);
            } else if (pathInfo.equals("/online")) {
                // GET /api/v1/players/online - Get online players
                handleOnlinePlayers(req, resp);
            } else if (pathInfo.equals("/recent")) {
                // GET /api/v1/players/recent?hours=24 - Get recent players
                handleRecentPlayers(req, resp);
            } else if (pathInfo.startsWith("/") && pathInfo.contains("/")) {
                // Handle sub-resources like /uuid/history
                handlePlayerSubResource(req, resp, pathInfo);
            } else {
                // GET /api/v1/players/{uuid} - Get specific player
                handleGetPlayer(req, resp, pathInfo.substring(1));
            }
        } catch (Exception e) {
            handleError(resp, e, "Error processing player request");
        }
    }

    private void handleListPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int limit = parseIntParameter(req, "limit", 50);
        int offset = parseIntParameter(req, "offset", 0);
        String group = req.getParameter("group");
        
        CompletableFuture<List<PlayerDTO>> future;
        if (group != null) {
            future = playerService.getPlayersByGroup(group);
        } else {
            future = playerService.getAllPlayers();
        }
        
        future.thenAccept(players -> {
            // Apply pagination
            List<PlayerDTO> pagedPlayers = players.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
                
            PagedResponse<PlayerDTO> response = new PagedResponse<>(
                pagedPlayers, players.size(), limit, offset
            );
            sendJsonResponse(resp, response);
        }).exceptionally(throwable -> {
            handleError(resp, throwable, "Failed to retrieve players");
            return null;
        });
    }
}
```

#### Response Models

**Location**: `org.fourz.rvnkcore.api.model.response.*`

Standardized response formats for all API endpoints:

```java
// Base response wrapper
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;
    private final LocalDateTime timestamp;
    private final String version;
    
    // Factory methods for success/error responses
    public static <T> ApiResponse<T> success(T data);
    public static <T> ApiResponse<T> error(String message);
}

// Paged response for list endpoints
public class PagedResponse<T> {
    private final List<T> items;
    private final int totalCount;
    private final int limit;
    private final int offset;
    private final boolean hasNext;
    
    // Pagination utility methods
    public boolean hasNext() { return offset + limit < totalCount; }
    public boolean hasPrevious() { return offset > 0; }
}
```

## Implementation Examples

### Starting the Server

```java
// Basic server startup
public class ServerManager {
    private CoreServer apiServer;
    
    public void startServer() {
        try {
            // Load configuration
            ApiConfig config = ConfigLoader.getInstance(plugin).getApiConfig();
            
            // Get services from registry
            PlayerService playerService = serviceRegistry.getService(PlayerService.class);
            
            // Create and start server
            apiServer = new CoreServer(config, playerService, plugin);
            apiServer.start();
            
            logger.info("RVNKCore API server started on port " + config.getHttpPort());
            
        } catch (Exception e) {
            logger.error("Failed to start API server", e);
            throw new RuntimeException("API server startup failed", e);
        }
    }
    
    public void stopServer() {
        if (apiServer != null && apiServer.isRunning()) {
            try {
                apiServer.stop();
                logger.info("RVNKCore API server stopped");
            } catch (Exception e) {
                logger.error("Error stopping API server", e);
            }
        }
    }
}
```

### Custom Controller Implementation

```java
@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController extends HttpServlet {
    private final AnnouncementService announcementService;
    private final Gson gson;
    private final LogManager logger;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Parse request body
            AnnouncementDTO announcement = gson.fromJson(
                req.getReader(), AnnouncementDTO.class
            );
            
            // Validate input
            if (announcement.getMessage() == null || announcement.getMessage().trim().isEmpty()) {
                sendErrorResponse(resp, 400, "Message is required");
                return;
            }
            
            // Create announcement
            announcementService.createAnnouncement(announcement)
                .thenAccept(created -> {
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                    sendJsonResponse(resp, ApiResponse.success(created));
                })
                .exceptionally(throwable -> {
                    handleError(resp, throwable, "Failed to create announcement");
                    return null;
                });
                
        } catch (Exception e) {
            handleError(resp, e, "Error processing announcement creation");
        }
    }
}
```

### SSL/HTTPS Configuration

```java
public class ServerSSLFactory {
    /**
     * Configure SSL/HTTPS with proper certificate management.
     */
    public SslContextFactory.Server createSslContextFactory(String keystorePath, String keystorePassword) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        
        // Set keystore path and password
        sslContextFactory.setKeyStorePath(keystorePath);
        sslContextFactory.setKeyStorePassword(keystorePassword);
        
        // Security settings
        sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
        
        // Protocol settings
        sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
        
        return sslContextFactory;
    }
}
```

## Security Implementation

### API Key Authentication

```java
public class ApiKeyAuthFilter implements Filter {
    private final String validApiKey;
    private final Set<String> allowedIPs;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Extract API key from Authorization header
        String authHeader = httpRequest.getHeader("Authorization");
        String apiKey = null;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            apiKey = authHeader.substring(7);
        } else {
            apiKey = httpRequest.getParameter("api_key");
        }
        
        // Validate API key
        if (!validApiKey.equals(apiKey)) {
            sendErrorResponse(httpResponse, 401, "Invalid API key");
            return;
        }
        
        // IP whitelist check (if configured)
        if (!allowedIPs.isEmpty()) {
            String clientIP = getClientIP(httpRequest);
            if (!isAllowedIP(clientIP)) {
                sendErrorResponse(httpResponse, 403, "IP not allowed");
                return;
            }
        }
        
        // Continue filter chain
        chain.doFilter(request, response);
    }
}
```

### Rate Limiting Implementation

```java
public class RateLimitingFilter implements Filter {
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIP = getClientIP(httpRequest);
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(clientIP, 
            ip -> RateLimiter.create(requestsPerMinute / 60.0)); // per second rate
        
        if (!rateLimiter.tryAcquire()) {
            // Add rate limit headers
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            httpResponse.setHeader("X-RateLimit-Reset", 
                String.valueOf(System.currentTimeMillis() / 1000 + 60));
            
            sendErrorResponse(httpResponse, 429, "Rate limit exceeded");
            return;
        }
        
        chain.doFilter(request, response);
    }
}
```

## Performance Optimization

### Connection Pooling

The CoreServer uses Jetty's built-in connection pooling with optimized settings:

```java
// Server connector with connection pooling
ServerConnector connector = new ServerConnector(server);
connector.setPort(port);
connector.setHost(host);
connector.setIdleTimeout(config.getIdleTimeout());
connector.setConnectionTimeout(config.getConnectionTimeout());

// Thread pool configuration
QueuedThreadPool threadPool = new QueuedThreadPool();
threadPool.setMaxThreads(config.getMaxThreads());
threadPool.setMinThreads(10);
threadPool.setName("RVNKCore-API");
server.setThreadPool(threadPool);
```

### Async Request Processing

All database operations use CompletableFuture for non-blocking request processing:

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Start async processing
    AsyncContext asyncContext = req.startAsync();
    asyncContext.setTimeout(30000); // 30 second timeout
    
    // Perform async database operation
    playerService.getPlayer(playerId)
        .thenAccept(player -> {
            try {
                if (player.isPresent()) {
                    sendJsonResponse(resp, ApiResponse.success(player.get()));
                } else {
                    sendErrorResponse(resp, 404, "Player not found");
                }
            } finally {
                asyncContext.complete();
            }
        })
        .exceptionally(throwable -> {
            try {
                handleError(resp, throwable, "Failed to retrieve player");
            } finally {
                asyncContext.complete();
            }
            return null;
        });
}
```

## Monitoring and Health Checks

### Server Health Monitoring

```java
public class ServerHealthMonitor {
    private final CoreServer server;
    private final ScheduledExecutorService scheduler;
    
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::checkServerHealth, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkServerHealth() {
        try {
            if (!server.isRunning()) {
                logger.warning("API server is not running, attempting restart...");
                server.start();
            }
            
            // Additional health checks
            checkDatabaseConnections();
            checkMemoryUsage();
            checkThreadPoolStatus();
            
        } catch (Exception e) {
            logger.error("Server health check failed", e);
        }
    }
}
```

### Health Endpoint

```java
@RestController
@RequestMapping("/api/v1/health")
public class HealthController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(LocalDateTime.now());
        status.setStatus("healthy");
        status.setUptime(getServerUptime());
        status.setMemoryUsage(getMemoryUsage());
        status.setDatabaseStatus(checkDatabaseHealth());
        
        sendJsonResponse(resp, ApiResponse.success(status));
    }
}
```

## Troubleshooting

### Common Issues

1. **SSL Certificate Issues**
   ```
   Error: Cannot load SSL certificate
   Solution: Verify keystorePath and keystorePassword in config
   ```

2. **Port Already in Use**
   ```
   Error: Address already in use
   Solution: Change port in config or stop conflicting service
   ```

3. **API Key Authentication Failures**
   ```
   Error: 401 Unauthorized
   Solution: Verify API key in Authorization header: "Bearer YOUR_API_KEY"
   ```

### Debugging Configuration

```yaml
# Enable debug logging for troubleshooting
api:
  apiLogLevel: "DEBUG"
  globalLogLevel: "DEBUG"
  
# Test configuration
test:
  enabled: true
  skipAuthentication: false  # Only for development
```

## Integration Examples

### Web Application Integration

```javascript
// JavaScript client example
class RVNKCoreClient {
    constructor(baseUrl, apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }
    
    async getPlayer(uuid) {
        const response = await fetch(`${this.baseUrl}/api/v1/players/${uuid}`, {
            headers: {
                'Authorization': `Bearer ${this.apiKey}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    }
    
    async searchPlayers(query, limit = 50) {
        const url = new URL(`${this.baseUrl}/api/v1/players/search`);
        url.searchParams.set('q', query);
        url.searchParams.set('limit', limit);
        
        const response = await fetch(url, {
            headers: {
                'Authorization': `Bearer ${this.apiKey}`,
                'Content-Type': 'application/json'
            }
        });
        
        return await response.json();
    }
}
```

### Plugin Integration

```java
// Other plugins accessing RVNKCore API
public class ExamplePluginIntegration {
    private final String apiKey;
    private final String baseUrl;
    
    public CompletableFuture<PlayerData> getPlayerData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(baseUrl + "/api/v1/players/" + playerId.toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                
                // Process response
                if (connection.getResponseCode() == 200) {
                    // Parse JSON response
                    return parsePlayerResponse(connection.getInputStream());
                } else {
                    throw new RuntimeException("API request failed: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve player data", e);
            }
        });
    }
}
```

## Production Deployment

### Server Configuration

```yaml
# Production API configuration
api:
  enabled: true
  host: "0.0.0.0"
  httpPort: 8080
  httpsPort: 8443
  httpsEnabled: true
  
  # Production SSL certificate
  keystorePath: "/opt/rvnkcore/certificates/production.jks"
  keystorePassword: "${SSL_KEYSTORE_PASSWORD}"
  
  # Security settings
  apiKey: "${API_KEY}"
  allowedIPs:
    - "10.0.0.0/8"     # Internal network
    - "172.16.0.0/12"  # Docker network
    - "192.168.0.0/16" # Private network
  
  # Performance settings
  maxThreads: 100
  idleTimeout: 60000
  connectionTimeout: 15000
  
  # CORS for production
  corsEnabled: true
  corsAllowedOrigins: "https://yourdomain.com,https://api.yourdomain.com"
  corsAllowedMethods: "GET,POST,PUT,DELETE"
  
  # Monitoring
  apiLogLevel: "WARN"
  sendServerVersion: false
```

### Docker Deployment

```dockerfile
# Dockerfile for containerized deployment
FROM openjdk:17-jre-slim

# Copy plugin and certificates
COPY rvnkcore.jar /opt/minecraft/plugins/
COPY certificates/ /opt/rvnkcore/certificates/

# Expose API ports
EXPOSE 8080 8443

# Environment variables
ENV API_KEY=""
ENV SSL_KEYSTORE_PASSWORD=""

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

CMD ["java", "-jar", "/opt/minecraft/server.jar"]
```

This comprehensive guide provides everything needed to implement, configure, and maintain the RVNKCore REST API infrastructure. The implementation is production-ready with extensive security features, performance optimizations, and monitoring capabilities.
