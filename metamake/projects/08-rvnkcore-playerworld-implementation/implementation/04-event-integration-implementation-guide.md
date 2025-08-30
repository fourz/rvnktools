# Event Integration Implementation Guide

**Guide ID**: 04-event-integration-implementation-guide  
**Phase**: Event System Integration  
**Prerequisites**: Service layer implemented, event system configured

## Event Handler Implementation

### PlayerWorld Event Handler Class

Create the main event handler class that integrates with Bukkit's event system:

```java
package org.fourz.rvnkcore.event;

import org.fourz.rvnkcore.service.PlayerWorldService;
import org.fourz.rvnkcore.session.SessionManager;
import org.fourz.rvnkcore.logging.LogManager;
import org.fourz.rvnkcore.util.RateLimiter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event handler for PlayerWorld system integration with Bukkit events.
 * Handles player movement, world changes, and activity tracking.
 */
public class PlayerWorldEventHandler implements Listener {
    
    private final Plugin plugin;
    private final PlayerWorldService playerWorldService;
    private final SessionManager sessionManager;
    private final LogManager logger;
    private final RateLimiter<UUID> locationUpdateLimiter;
    
    // Performance metrics
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong successfulEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);
    
    // Circuit breaker for error resilience
    private volatile boolean isServiceHealthy = true;
    private volatile long lastFailureTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1 minute
    
    public PlayerWorldEventHandler(
            Plugin plugin,
            PlayerWorldService playerWorldService,
            SessionManager sessionManager,
            RateLimiter<UUID> locationUpdateLimiter) {
        
        this.plugin = plugin;
        this.playerWorldService = playerWorldService;
        this.sessionManager = sessionManager;
        this.logger = LogManager.getInstance(plugin);
        this.locationUpdateLimiter = locationUpdateLimiter;
        
        logger.info("PlayerWorldEventHandler initialized");
    }
```

### Core Player Events

#### PlayerJoinEvent Handler

```java
    /**
     * Handle player join events - initialize session and record world entry
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        totalEventsProcessed.incrementAndGet();
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();
        Location location = player.getLocation();
        
        logger.debug("Processing player join: " + playerId + " in world " + worldName);
        
        // Process join asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Record player join
                playerWorldService.recordJoin(playerId, worldName, location)
                    .thenRun(() -> {
                        successfulEvents.incrementAndGet();
                        logger.debug("Successfully recorded join for player " + playerId);
                    })
                    .exceptionally(throwable -> {
                        handleEventError("PlayerJoin", playerId, throwable);
                        return null;
                    });
                    
            } catch (Exception e) {
                handleEventError("PlayerJoin", playerId, e);
            }
        });
    }
```

#### PlayerQuitEvent Handler

```java
    /**
     * Handle player quit events - complete session and update playtime
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        totalEventsProcessed.incrementAndGet();
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();
        
        logger.debug("Processing player quit: " + playerId + " from world " + worldName);
        
        // Process quit asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Record player leave
                playerWorldService.recordLeave(playerId, worldName)
                    .thenRun(() -> {
                        successfulEvents.incrementAndGet();
                        logger.debug("Successfully recorded leave for player " + playerId);
                        
                        // Clean up session data
                        sessionManager.cleanupPlayerSessions(playerId);
                    })
                    .exceptionally(throwable -> {
                        handleEventError("PlayerQuit", playerId, throwable);
                        return null;
                    });
                    
            } catch (Exception e) {
                handleEventError("PlayerQuit", playerId, e);
            }
        });
    }
```

#### PlayerMoveEvent Handler

```java
    /**
     * Handle player movement - track location changes with rate limiting
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        // Only process significant movement (block changes)
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location newLocation = event.getTo();
        
        // Check rate limiting
        if (!locationUpdateLimiter.tryAcquire(playerId)) {
            return; // Skip this update due to rate limiting
        }
        
        totalEventsProcessed.incrementAndGet();
        
        // Process movement asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Update player location
                playerWorldService.updatePlayerLocation(playerId, newLocation)
                    .thenRun(() -> {
                        successfulEvents.incrementAndGet();
                        logger.debug("Updated location for player " + playerId);
                    })
                    .exceptionally(throwable -> {
                        // Don't count location update failures as critical errors
                        logger.debug("Failed to update location for player " + playerId, throwable);
                        return null;
                    });
                    
            } catch (Exception e) {
                logger.debug("Error processing movement for player " + playerId, e);
            }
        });
    }
```

#### PlayerChangedWorldEvent Handler

```java
    /**
     * Handle world changes - manage cross-world sessions
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        totalEventsProcessed.incrementAndGet();
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        Location newLocation = player.getLocation();
        
        logger.info("Player " + playerId + " changed from world " + fromWorld + " to " + toWorld);
        
        // Process world change asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Record world transition
                playerWorldService.recordWorldChange(playerId, fromWorld, toWorld)
                    .thenCompose(v -> {
                        // Start new world session
                        return playerWorldService.recordJoin(playerId, toWorld, newLocation);
                    })
                    .thenRun(() -> {
                        successfulEvents.incrementAndGet();
                        logger.debug("Successfully processed world change for player " + playerId);
                    })
                    .exceptionally(throwable -> {
                        handleEventError("PlayerChangedWorld", playerId, throwable);
                        return null;
                    });
                    
            } catch (Exception e) {
                handleEventError("PlayerChangedWorld", playerId, e);
            }
        });
    }
```

### Activity Tracking Events

#### PlayerDeathEvent Handler

```java
    /**
     * Handle player deaths - track death count per world
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();
        
        // Process death asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // This would require additional repository method
                // worldDataRepository.incrementDeathCount(playerId, worldName);
                logger.info("Player " + playerId + " died in world " + worldName);
                
            } catch (Exception e) {
                logger.error("Error recording death for player " + playerId, e);
            }
        });
    }
```

#### EntityDeathEvent Handler

```java
    /**
     * Handle mob deaths - track player mob kills per world
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        
        UUID playerId = killer.getUniqueId();
        String worldName = killer.getWorld().getName();
        
        // Process mob kill asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // This would require additional repository method
                // worldDataRepository.incrementMobKills(playerId, worldName);
                logger.debug("Player " + playerId + " killed mob in world " + worldName);
                
            } catch (Exception e) {
                logger.error("Error recording mob kill for player " + playerId, e);
            }
        });
    }
```

#### Block Activity Handlers

```java
    /**
     * Handle block breaks - track blocks broken per world
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();
        
        // Process block break asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // This would require additional repository method
                // worldDataRepository.incrementBlocksBroken(playerId, worldName);
                logger.debug("Player " + playerId + " broke block in world " + worldName);
                
            } catch (Exception e) {
                logger.error("Error recording block break for player " + playerId, e);
            }
        });
    }
    
    /**
     * Handle block places - track blocks placed per world
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canProcessEvent()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();
        
        // Process block place asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // This would require additional repository method
                // worldDataRepository.incrementBlocksPlaced(playerId, worldName);
                logger.debug("Player " + playerId + " placed block in world " + worldName);
                
            } catch (Exception e) {
                logger.error("Error recording block place for player " + playerId, e);
            }
        });
    }
```

### Error Handling and Resilience

#### Circuit Breaker Implementation

```java
    /**
     * Check if events can be processed (circuit breaker pattern)
     */
    private boolean canProcessEvent() {
        if (isServiceHealthy) {
            return true;
        }
        
        // Check if we should try again after timeout
        if (System.currentTimeMillis() - lastFailureTime > CIRCUIT_BREAKER_TIMEOUT) {
            isServiceHealthy = true;
            logger.info("Circuit breaker reset - resuming event processing");
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle event processing errors
     */
    private void handleEventError(String eventType, UUID playerId, Throwable error) {
        failedEvents.incrementAndGet();
        logger.error("Failed to process " + eventType + " for player " + playerId, error);
        
        // Check if we should open circuit breaker
        long totalProcessed = totalEventsProcessed.get();
        long failed = failedEvents.get();
        
        if (totalProcessed > 100 && (failed / (double) totalProcessed) > 0.1) {
            // More than 10% failure rate after 100 events
            isServiceHealthy = false;
            lastFailureTime = System.currentTimeMillis();
            logger.warning("Circuit breaker opened due to high failure rate (" + failed + "/" + totalProcessed + ")");
        }
    }
```

#### Performance Monitoring

```java
    /**
     * Get event processing metrics
     */
    public EventMetrics getMetrics() {
        return EventMetrics.builder()
            .totalEventsProcessed(totalEventsProcessed.get())
            .successfulEvents(successfulEvents.get())
            .failedEvents(failedEvents.get())
            .successRate(calculateSuccessRate())
            .isHealthy(isServiceHealthy)
            .build();
    }
    
    private double calculateSuccessRate() {
        long total = totalEventsProcessed.get();
        if (total == 0) {
            return 1.0;
        }
        return successfulEvents.get() / (double) total;
    }
    
    /**
     * Reset metrics (useful for monitoring)
     */
    public void resetMetrics() {
        totalEventsProcessed.set(0);
        successfulEvents.set(0);
        failedEvents.set(0);
        logger.info("Event handler metrics reset");
    }
```

### Session Management Integration

#### Session Update Handler

```java
    /**
     * Periodic task to update active sessions
     */
    public void startSessionUpdateTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                // Update all active sessions
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    String worldName = player.getWorld().getName();
                    Location location = player.getLocation();
                    
                    // Update session with current location
                    sessionManager.updateSession(playerId, worldName, location, System.currentTimeMillis());
                }
                
                // Clean up old sessions
                sessionManager.cleanupInactiveSessions();
                
            } catch (Exception e) {
                logger.error("Error during session update task", e);
            }
        }, 20L * 60, 20L * 60); // Every minute
    }
```

### Event Handler Registration

#### Registration Helper

```java
/**
 * Helper class to register the event handler with proper configuration
 */
public class PlayerWorldEventRegistration {
    
    public static void registerEvents(
            Plugin plugin,
            PlayerWorldService playerWorldService,
            SessionManager sessionManager,
            RateLimiter<UUID> locationUpdateLimiter) {
        
        PlayerWorldEventHandler eventHandler = new PlayerWorldEventHandler(
            plugin,
            playerWorldService,
            sessionManager,
            locationUpdateLimiter
        );
        
        // Register with Bukkit
        plugin.getServer().getPluginManager().registerEvents(eventHandler, plugin);
        
        // Start session update task
        eventHandler.startSessionUpdateTask();
        
        // Log registration
        LogManager logger = LogManager.getInstance(plugin);
        logger.info("PlayerWorld event handler registered successfully");
        
        // Register shutdown hook to clean up sessions
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sessionManager.shutdownAllSessions();
        }));
    }
}
```

#### Plugin Integration

```java
// In your main plugin onEnable method
public void onEnable() {
    // Initialize services first
    initializeServices();
    
    // Create rate limiter for location updates
    RateLimiter<UUID> locationUpdateLimiter = new TokenBucketRateLimiter<>(30000); // 30 seconds
    
    // Register event handlers
    PlayerWorldEventRegistration.registerEvents(
        this,
        ServiceRegistry.getInstance().getService(PlayerWorldService.class),
        ServiceRegistry.getInstance().getService(SessionManager.class),
        locationUpdateLimiter
    );
}

public void onDisable() {
    // Clean up sessions on shutdown
    SessionManager sessionManager = ServiceRegistry.getInstance().getService(SessionManager.class);
    if (sessionManager != null) {
        sessionManager.shutdownAllSessions();
    }
}
```

### Configuration Integration

#### Event Handler Configuration

Add configuration options to control event handling behavior:

```yaml
playerworld:
  events:
    # Rate limiting for location updates
    location-update-interval: 30000  # milliseconds
    
    # Circuit breaker settings
    circuit-breaker:
      failure-threshold: 0.1         # 10% failure rate threshold
      timeout: 60000                 # 1 minute timeout
      min-requests: 100              # minimum requests before checking failure rate
    
    # Session management
    session:
      update-interval: 60000         # 1 minute
      cleanup-interval: 300000       # 5 minutes
      inactive-timeout: 600000       # 10 minutes
    
    # Activity tracking
    track-block-activity: true       # track block break/place
    track-mob-kills: true           # track mob kills
    track-deaths: true              # track player deaths
```

### Testing and Validation

#### Event Handler Testing

```java
@ExtendWith(MockitoExtension.class)
class PlayerWorldEventHandlerTest {
    
    @Mock private Plugin plugin;
    @Mock private PlayerWorldService playerWorldService;
    @Mock private SessionManager sessionManager;
    @Mock private RateLimiter<UUID> rateLimiter;
    
    private PlayerWorldEventHandler eventHandler;
    
    @BeforeEach
    void setUp() {
        eventHandler = new PlayerWorldEventHandler(plugin, playerWorldService, sessionManager, rateLimiter);
    }
    
    @Test
    void testPlayerJoinEvent() {
        // Arrange
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location location = new Location(world, 0, 64, 0);
        
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(player.getLocation()).thenReturn(location);
        when(playerWorldService.recordJoin(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        
        PlayerJoinEvent event = new PlayerJoinEvent(player, "Player joined");
        
        // Act
        eventHandler.onPlayerJoin(event);
        
        // Give time for async processing
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        // Assert
        verify(playerWorldService).recordJoin(any(UUID.class), eq("world"), eq(location));
    }
}
```

---

**Implementation Notes:**
- All event processing is asynchronous to prevent server lag
- Circuit breaker pattern provides resilience against service failures  
- Rate limiting prevents database overload from frequent location updates
- Comprehensive error handling ensures stability
- Performance metrics enable monitoring and optimization
- Session management provides accurate playtime calculations
- Configuration options allow fine-tuning for different server environments
