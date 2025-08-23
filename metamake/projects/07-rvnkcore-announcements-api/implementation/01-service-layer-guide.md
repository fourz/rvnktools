# Implementation Guide: Service Layer Development

**Guide ID**: 01-service-layer-guide  
**Related Feature**: [Service Architecture Design](../features/01-service-architecture.md)  
**Prerequisites**: RVNKCore foundation infrastructure, database setup  
**Estimated Time**: 1-2 days

## Overview

This guide provides step-by-step instructions for implementing the announcement service layer within RVNKTools codebase, using RVNKCore base classes and patterns while keeping announcement-specific logic in RVNKTools.

## Code Location Strategy

**RVNKCore Provides**:
- Base service interfaces and abstract classes
- Database connection patterns and repository base classes
- REST API framework and base controller classes

**RVNKTools Contains**:
- AnnouncementService implementation (extends RVNKCore base)
- Announcement-specific business logic
- AnnounceManager integration and command handling
- Domain-specific data models and processing

## Prerequisites

### Required RVNKCore Infrastructure
- BaseService abstract class
- BaseRepository pattern available
- Database ConnectionProvider configured
- ServiceRegistry framework operational

### Development Environment Setup
```bash
# Verify RVNKCore base classes are available:
# - org.fourz.rvnkcore.service.BaseService
# - org.fourz.rvnkcore.database.repository.BaseRepository
# - org.fourz.rvnkcore.database.ConnectionProvider
```

## Step 1: Review Existing Service Interface

### 1.1 Examine Current AnnouncementService
```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/service/AnnouncementService.java

public interface AnnouncementService {
    // Core CRUD Operations (already implemented)
    CompletableFuture<String> createAnnouncement(AnnouncementDTO announcement);
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements();
    CompletableFuture<Void> updateAnnouncement(AnnouncementDTO announcement);
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    
    // Query Operations (already implemented)
    CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements();
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type);
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByWorld(String world);
    // ... additional methods
}
```

**Action Items:**
- [ ] Review all 17 existing service methods
- [ ] Identify any missing methods for migration support
- [ ] Verify method signatures match requirements
- [ ] Check JavaDoc documentation completeness

### 1.2 Verify Service Registry Integration
```java
// Check ServiceRegistry availability
ServiceRegistry registry = RVNKCoreBootstrap.getServiceRegistry();
Optional<AnnouncementService> service = registry.getService(AnnouncementService.class);

if (service.isPresent()) {
    logger.info("AnnouncementService is registered and available");
} else {
    logger.warning("AnnouncementService not found in registry");
}
```

## Step 2: Enhance DefaultAnnouncementService Implementation

### 2.1 Review Current Implementation Structure
```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/service/impl/DefaultAnnouncementService.java

public class DefaultAnnouncementService implements AnnouncementService {
    private final AnnouncementRepository repository;
    private final ConcurrentHashMap<String, AnnouncementDTO> cache;
    private final LogManager logger;
    
    // Current implementation includes:
    // - Basic CRUD operations
    // - Simple caching with ConcurrentHashMap
    // - Repository integration
    // - Error handling
}
```

### 2.2 Enhance Caching Implementation
```java
// Add advanced caching with TTL and eviction
public class DefaultAnnouncementService implements AnnouncementService {
    private final ConcurrentHashMap<String, CachedAnnouncement> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheMaintenanceExecutor;
    private static final long CACHE_TTL_MS = 300_000; // 5 minutes
    private static final int MAX_CACHE_SIZE = 1000;
    
    // Performance monitoring
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    private static class CachedAnnouncement {
        private final AnnouncementDTO announcement;
        private final long timestamp;
        private volatile long lastAccessed;
        
        public CachedAnnouncement(AnnouncementDTO announcement) {
            this.announcement = announcement;
            this.timestamp = System.currentTimeMillis();
            this.lastAccessed = timestamp;
        }
        
        public boolean isExpired(long currentTime) {
            return (currentTime - timestamp) > CACHE_TTL_MS;
        }
        
        public AnnouncementDTO getAnnouncement() {
            this.lastAccessed = System.currentTimeMillis();
            return announcement;
        }
    }
}
```

**Implementation Steps:**
1. **Add Cache Configuration**
   ```java
   // In DefaultAnnouncementService constructor
   this.cacheMaintenanceExecutor = Executors.newSingleThreadScheduledExecutor(
       r -> new Thread(r, "AnnouncementService-CacheMaintenance"));
   
   // Schedule cache cleanup every 60 seconds
   cacheMaintenanceExecutor.scheduleAtFixedRate(
       this::evictExpiredEntries, 60, 60, TimeUnit.SECONDS);
   ```

2. **Implement Cache Eviction**
   ```java
   private void evictExpiredEntries() {
       long currentTime = System.currentTimeMillis();
       int evicted = 0;
       
       Iterator<Map.Entry<String, CachedAnnouncement>> iterator = cache.entrySet().iterator();
       while (iterator.hasNext()) {
           Map.Entry<String, CachedAnnouncement> entry = iterator.next();
           if (entry.getValue().isExpired(currentTime)) {
               iterator.remove();
               evicted++;
           }
       }
       
       // Size-based eviction (LRU)
       if (cache.size() > MAX_CACHE_SIZE) {
           evicted += evictLeastRecentlyUsed();
       }
       
       if (evicted > 0) {
           logger.debug("Evicted " + evicted + " expired cache entries");
       }
   }
   ```

### 2.3 Add Performance Monitoring
```java
// Add method to get cache statistics
public CacheStatistics getCacheStatistics() {
    return new CacheStatistics(
        cache.size(),
        cacheHits.get(),
        cacheMisses.get(),
        calculateHitRate()
    );
}

private double calculateHitRate() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    return total == 0 ? 0.0 : (double) hits / total;
}

// Update cache access methods
private Optional<AnnouncementDTO> getCached(String id) {
    CachedAnnouncement cached = cache.get(id);
    if (cached != null && !cached.isExpired(System.currentTimeMillis())) {
        cacheHits.incrementAndGet();
        return Optional.of(cached.getAnnouncement());
    }
    cacheMisses.incrementAndGet();
    return Optional.empty();
}
```

## Step 3: Service Registry Integration

### 3.1 Service Registration in RVNKCore Bootstrap
```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/RVNKCoreBootstrap.java

public class RVNKCoreBootstrap {
    private ServiceRegistry serviceRegistry;
    private DefaultAnnouncementService announcementService;
    
    public void initializeServices() {
        try {
            // Initialize dependencies
            ConnectionProvider connectionProvider = createConnectionProvider();
            AnnouncementRepository repository = new AnnouncementRepository(
                connectionProvider, queryBuilder, plugin);
            
            // Create service implementation
            announcementService = new DefaultAnnouncementService(repository, logger);
            
            // Register service
            serviceRegistry.registerService(AnnouncementService.class, announcementService);
            
            logger.info("AnnouncementService registered successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize AnnouncementService", e);
            throw new ServiceInitializationException("AnnouncementService initialization failed", e);
        }
    }
    
    public void shutdownServices() {
        if (announcementService != null) {
            try {
                announcementService.shutdown();
                logger.info("AnnouncementService shutdown completed");
            } catch (Exception e) {
                logger.error("Error during AnnouncementService shutdown", e);
            }
        }
    }
}
```

### 3.2 Add Service Lifecycle Management
```java
// Add to DefaultAnnouncementService
public class DefaultAnnouncementService implements AnnouncementService {
    private volatile boolean shutdown = false;
    
    public void initialize() {
        logger.info("Initializing DefaultAnnouncementService");
        // Warm up cache with frequently accessed announcements
        preloadCache();
    }
    
    public void shutdown() {
        logger.info("Shutting down DefaultAnnouncementService");
        shutdown = true;
        
        // Stop cache maintenance
        if (cacheMaintenanceExecutor != null) {
            cacheMaintenanceExecutor.shutdown();
            try {
                if (!cacheMaintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cacheMaintenanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cacheMaintenanceExecutor.shutdownNow();
            }
        }
        
        // Clear cache
        cache.clear();
        logger.info("DefaultAnnouncementService shutdown complete");
    }
    
    private void preloadCache() {
        // Load active announcements into cache
        repository.findByActive(true)
            .thenAccept(announcements -> {
                for (AnnouncementDTO announcement : announcements) {
                    cache.put(announcement.getId(), new CachedAnnouncement(announcement));
                }
                logger.info("Preloaded " + announcements.size() + " active announcements into cache");
            })
            .exceptionally(throwable -> {
                logger.error("Failed to preload cache", throwable);
                return null;
            });
    }
}
```

## Step 4: Error Handling Enhancement

### 4.1 Create Service Exception Hierarchy
```java
// Location: toolkitplugin/src/main/java/org/fourz/rvnkcore/api/exception/

// Base service exception
public class ServiceException extends RuntimeException {
    public ServiceException(String message) {
        super(message);
    }
    
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Specific announcement exceptions
public class AnnouncementNotFoundException extends ServiceException {
    private final String announcementId;
    
    public AnnouncementNotFoundException(String id) {
        super("Announcement not found: " + id);
        this.announcementId = id;
    }
    
    public String getAnnouncementId() { return announcementId; }
}

public class AnnouncementValidationException extends ServiceException {
    private final List<String> validationErrors;
    
    public AnnouncementValidationException(List<String> errors) {
        super("Announcement validation failed: " + String.join(", ", errors));
        this.validationErrors = new ArrayList<>(errors);
    }
    
    public List<String> getValidationErrors() { return validationErrors; }
}
```

### 4.2 Update Service Methods with Enhanced Error Handling
```java
@Override
public CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id) {
    if (shutdown) {
        return CompletableFuture.failedFuture(new ServiceException("Service is shutdown"));
    }
    
    if (id == null || id.trim().isEmpty()) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("Announcement ID cannot be null or empty"));
    }
    
    // Check cache first
    Optional<AnnouncementDTO> cached = getCached(id);
    if (cached.isPresent()) {
        return CompletableFuture.completedFuture(cached);
    }
    
    // Query repository
    return repository.findById(id)
        .thenApply(result -> {
            result.ifPresent(announcement -> 
                cache.put(id, new CachedAnnouncement(announcement)));
            return result;
        })
        .exceptionally(throwable -> {
            logger.error("Failed to get announcement: " + id, throwable);
            throw new ServiceException("Failed to retrieve announcement", throwable);
        });
}
```

## Step 5: Testing Implementation

### 5.1 Unit Test Setup
```java
// Location: toolkitplugin/src/test/java/org/fourz/rvnkcore/api/service/impl/DefaultAnnouncementServiceTest.java

@ExtendWith(MockitoExtension.class)
class DefaultAnnouncementServiceTest {
    
    @Mock
    private AnnouncementRepository repository;
    
    @Mock
    private LogManager logger;
    
    private DefaultAnnouncementService service;
    
    @BeforeEach
    void setUp() {
        service = new DefaultAnnouncementService(repository, logger);
        service.initialize();
    }
    
    @AfterEach
    void tearDown() {
        service.shutdown();
    }
    
    @Test
    void testCreateAnnouncement() {
        // Test implementation
        AnnouncementDTO announcement = createTestAnnouncement();
        when(repository.save(any(AnnouncementDTO.class)))
            .thenReturn(CompletableFuture.completedFuture(announcement.getId()));
        
        CompletableFuture<String> result = service.createAnnouncement(announcement);
        
        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
        assertThat(result.join()).isEqualTo(announcement.getId());
    }
    
    @Test
    void testCacheHitPerformance() {
        // Setup cache with test data
        AnnouncementDTO announcement = createTestAnnouncement();
        service.cache.put(announcement.getId(), new CachedAnnouncement(announcement));
        
        // First call should hit cache
        Optional<AnnouncementDTO> result = service.getAnnouncement(announcement.getId()).join();
        
        assertThat(result).isPresent();
        assertThat(service.getCacheStatistics().getHitRate()).isGreaterThan(0.0);
        verify(repository, never()).findById(any());
    }
}
```

### 5.2 Integration Test Setup
```java
@TestMethodOrder(OrderAnnotation.class)
class AnnouncementServiceIntegrationTest {
    
    private static RVNKCoreTestBootstrap testBootstrap;
    private static AnnouncementService announcementService;
    
    @BeforeAll
    static void setUpClass() throws Exception {
        testBootstrap = new RVNKCoreTestBootstrap();
        testBootstrap.initializeTestEnvironment();
        
        ServiceRegistry registry = testBootstrap.getServiceRegistry();
        announcementService = registry.getService(AnnouncementService.class)
            .orElseThrow(() -> new IllegalStateException("AnnouncementService not available"));
    }
    
    @Test
    @Order(1)
    void testServiceRegistration() {
        assertThat(announcementService).isNotNull();
        assertThat(announcementService).isInstanceOf(DefaultAnnouncementService.class);
    }
    
    @Test
    @Order(2)
    void testFullCRUDOperations() throws Exception {
        // Create
        AnnouncementDTO announcement = createTestAnnouncement();
        String id = announcementService.createAnnouncement(announcement).get(5, TimeUnit.SECONDS);
        assertThat(id).isNotEmpty();
        
        // Read
        Optional<AnnouncementDTO> retrieved = announcementService.getAnnouncement(id).get(5, TimeUnit.SECONDS);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMessage()).isEqualTo(announcement.getMessage());
        
        // Update
        AnnouncementDTO updated = retrieved.get().toBuilder().message("Updated message").build();
        announcementService.updateAnnouncement(updated).get(5, TimeUnit.SECONDS);
        
        Optional<AnnouncementDTO> afterUpdate = announcementService.getAnnouncement(id).get(5, TimeUnit.SECONDS);
        assertThat(afterUpdate.get().getMessage()).isEqualTo("Updated message");
        
        // Delete
        boolean deleted = announcementService.deleteAnnouncement(id).get(5, TimeUnit.SECONDS);
        assertThat(deleted).isTrue();
        
        Optional<AnnouncementDTO> afterDelete = announcementService.getAnnouncement(id).get(5, TimeUnit.SECONDS);
        assertThat(afterDelete).isEmpty();
    }
}
```

## Step 6: Validation and Verification

### 6.1 Service Registry Verification
```java
// Create verification utility
public class ServiceVerificationUtil {
    public static void verifyAnnouncementServiceRegistration() {
        ServiceRegistry registry = RVNKCoreBootstrap.getServiceRegistry();
        
        // Verify service is registered
        Optional<AnnouncementService> service = registry.getService(AnnouncementService.class);
        if (!service.isPresent()) {
            throw new IllegalStateException("AnnouncementService not registered in ServiceRegistry");
        }
        
        // Verify service type
        if (!(service.get() instanceof DefaultAnnouncementService)) {
            throw new IllegalStateException("AnnouncementService is not DefaultAnnouncementService instance");
        }
        
        // Verify basic functionality
        CompletableFuture<List<AnnouncementDTO>> activeAnnouncements = service.get().getActiveAnnouncements();
        try {
            List<AnnouncementDTO> result = activeAnnouncements.get(5, TimeUnit.SECONDS);
            logger.info("AnnouncementService verification successful. Found " + result.size() + " active announcements");
        } catch (Exception e) {
            throw new IllegalStateException("AnnouncementService basic operation failed", e);
        }
    }
}
```

### 6.2 Performance Verification
```java
// Performance benchmark test
@Test
void testServicePerformance() {
    int numOperations = 1000;
    long startTime = System.currentTimeMillis();
    
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (int i = 0; i < numOperations; i++) {
        AnnouncementDTO announcement = createTestAnnouncement("Test " + i);
        CompletableFuture<Void> future = announcementService.createAnnouncement(announcement)
            .thenCompose(id -> announcementService.getAnnouncement(id))
            .thenAccept(result -> assertThat(result).isPresent());
        futures.add(future);
    }
    
    // Wait for all operations to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    long duration = System.currentTimeMillis() - startTime;
    double operationsPerSecond = (numOperations * 1000.0) / duration;
    
    logger.info(String.format("Completed %d operations in %d ms (%.2f ops/sec)", 
        numOperations, duration, operationsPerSecond));
    
    // Verify performance requirements
    assertThat(operationsPerSecond).isGreaterThan(100); // At least 100 ops/sec
}
```

## Completion Checklist

### Implementation Verification
- [ ] **Service Interface**: All 17 AnnouncementService methods implemented and functional
- [ ] **Service Registry**: Service successfully registered and discoverable
- [ ] **Caching**: Cache implementation with TTL and eviction working properly
- [ ] **Error Handling**: Comprehensive exception hierarchy and proper error propagation
- [ ] **Lifecycle Management**: Service initialization and shutdown procedures working
- [ ] **Performance Monitoring**: Cache statistics and performance metrics available

### Testing Verification
- [ ] **Unit Tests**: All service methods covered with >90% code coverage
- [ ] **Integration Tests**: Full CRUD operations working with real database
- [ ] **Performance Tests**: Service meeting performance requirements (>100 ops/sec)
- [ ] **Error Tests**: All exception scenarios properly handled and tested
- [ ] **Concurrency Tests**: Thread safety verified under concurrent access

### Documentation Verification
- [ ] **JavaDoc**: All public methods documented with examples
- [ ] **Architecture Documentation**: Service patterns and design decisions documented
- [ ] **Integration Guide**: Clear instructions for consuming services in other plugins
- [ ] **Performance Guide**: Configuration and tuning recommendations documented

## Next Steps

After completing this service layer implementation:

1. **Proceed to Database Integration** - Follow [Database Integration Guide](02-database-integration-guide.md)
2. **Implement REST API** - Follow [REST API Development Guide](03-rest-api-guide.md)  
3. **Set Up Migration Framework** - Follow [Migration Framework Guide](04-migration-framework-guide.md)

This service layer implementation establishes the foundation for the entire RVNKCore announcement system and serves as the template pattern for other RVNK plugin service implementations.
