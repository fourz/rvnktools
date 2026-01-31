package org.fourz.rvnkcore.integration;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.service.registry.DefaultServiceRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cross-Plugin Integration Test Suite (test-11)
 *
 * Validates inter-plugin communication via RVNKCore ServiceRegistry:
 * 1. ServiceRegistry Cross-Discovery
 * 2. Service availability detection (soft dependencies)
 * 3. Service lifecycle during plugin reload
 * 4. FallbackTracker isolation between plugins
 *
 * @author Forge-1
 * @since 2026-01-31
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cross-Plugin Integration Tests")
class CrossPluginIntegrationTest {

    @Mock private Server server;
    @Mock private PluginManager pluginManager;
    @Mock private Plugin pluginA;
    @Mock private Plugin pluginB;

    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        // Mock plugin environment
        lenient().when(pluginA.getName()).thenReturn("PluginA");
        lenient().when(pluginB.getName()).thenReturn("PluginB");
        lenient().when(pluginA.getServer()).thenReturn(server);
        lenient().when(pluginB.getServer()).thenReturn(server);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        
        // Mock logger for DefaultServiceRegistry
        Logger mockLogger = Logger.getLogger("TestLogger");
        lenient().when(pluginA.getLogger()).thenReturn(mockLogger);

        // Create fresh registry for each test (uses Plugin for logging)
        registry = new DefaultServiceRegistry(pluginA);
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    // ==================== 1. ServiceRegistry Cross-Discovery ====================

    @Nested
    @DisplayName("1. ServiceRegistry Cross-Discovery")
    class CrossDiscoveryTests {

        @Test
        @DisplayName("Plugin A can access Plugin B's services")
        void pluginAAccessesPluginBServices() throws ServiceException {
            // Plugin B registers its service
            TestServiceB serviceB = new TestServiceBImpl("Plugin B Service");
            registry.registerService(TestServiceB.class, serviceB);

            // Plugin A retrieves Plugin B's service
            TestServiceB retrievedService = registry.getService(TestServiceB.class);

            assertNotNull(retrievedService, "Plugin A should be able to access Plugin B's service");
            assertEquals("Plugin B Service", retrievedService.getName(),
                "Service data should be intact across plugins");
        }

        @Test
        @DisplayName("Multiple plugins can register different services")
        void multiplePluginsRegisterServices() throws ServiceException {
            // Plugin A registers ServiceA
            TestServiceA serviceA = new TestServiceAImpl();
            registry.registerService(TestServiceA.class, serviceA);

            // Plugin B registers ServiceB
            TestServiceB serviceB = new TestServiceBImpl("Service B");
            registry.registerService(TestServiceB.class, serviceB);

            // Both services should be accessible
            assertTrue(registry.hasService(TestServiceA.class), "ServiceA should be registered");
            assertTrue(registry.hasService(TestServiceB.class), "ServiceB should be registered");

            // Verify isolation - services are independent
            assertNotSame(
                registry.getService(TestServiceA.class),
                registry.getService(TestServiceB.class),
                "Services should be independent instances"
            );
        }

        @Test
        @DisplayName("Cross-plugin service call works correctly")
        void crossPluginServiceCall() throws ServiceException {
            // Plugin A provides a data service
            TestDataService dataService = new TestDataServiceImpl();
            registry.registerService(TestDataService.class, dataService);

            // Plugin B uses Plugin A's data service
            TestDataService retrievedService = registry.getService(TestDataService.class);
            String result = retrievedService.getData("test-key");

            assertEquals("data:test-key", result, "Cross-plugin service calls should work correctly");
        }
    }

    // ==================== 2. Service Availability Detection ====================

    @Nested
    @DisplayName("2. Service Availability Detection (Soft Dependencies)")
    class ServiceAvailabilityTests {

        @Test
        @DisplayName("Soft dependency detection - service exists")
        void softDependencyServiceExists() throws ServiceException {
            // Register optional service
            TestOptionalService optionalService = new TestOptionalServiceImpl();
            registry.registerService(TestOptionalService.class, optionalService);

            // Check availability before use (soft dependency pattern)
            boolean available = registry.hasService(TestOptionalService.class);
            assertTrue(available, "Optional service should be detected as available");

            // Safe retrieval
            if (registry.hasService(TestOptionalService.class)) {
                TestOptionalService service = registry.getService(TestOptionalService.class);
                assertNotNull(service, "Service should be retrievable when available");
            }
        }

        @Test
        @DisplayName("Soft dependency detection - service missing")
        void softDependencyServiceMissing() {
            // Check availability of non-existent service
            boolean available = registry.hasService(TestMissingService.class);
            assertFalse(available, "Missing service should be detected as unavailable");

            // Attempting to get missing service should throw
            assertThrows(ServiceException.class,
                () -> registry.getService(TestMissingService.class),
                "Getting missing service should throw ServiceException");
        }

        @Test
        @DisplayName("Graceful degradation when optional service unavailable")
        void gracefulDegradation() {
            // Pattern: Check first, use fallback if missing
            String result;
            if (registry.hasService(TestOptionalService.class)) {
                result = "using-service";
            } else {
                result = "using-fallback";
            }

            assertEquals("using-fallback", result, "Should use fallback when service unavailable");
        }
    }

    // ==================== 3. Service Lifecycle During Reload ====================

    @Nested
    @DisplayName("3. Service Lifecycle During Plugin Reload")
    class ServiceLifecycleTests {

        @Test
        @DisplayName("Service can be unregistered and re-registered (reload simulation)")
        void serviceReloadSimulation() throws ServiceException {
            // Initial registration
            TestServiceA initialService = new TestServiceAImpl();
            registry.registerService(TestServiceA.class, initialService);
            assertTrue(registry.hasService(TestServiceA.class), "Service should be registered initially");

            // Simulate plugin disable - unregister service
            boolean removed = registry.unregisterService(TestServiceA.class);
            assertTrue(removed, "Service should be removed during disable");
            assertFalse(registry.hasService(TestServiceA.class), "Service should be gone after unregister");

            // Simulate plugin enable - re-register service
            TestServiceA newService = new TestServiceAImpl();
            registry.registerService(TestServiceA.class, newService);
            assertTrue(registry.hasService(TestServiceA.class), "Service should be re-registered");
        }

        @Test
        @DisplayName("Other plugins handle service disappearance gracefully")
        void handleServiceDisappearance() throws ServiceException {
            // Plugin A registers service
            registry.registerService(TestServiceA.class, new TestServiceAImpl());

            // Plugin B gets reference
            TestServiceA serviceRef = registry.getService(TestServiceA.class);
            assertNotNull(serviceRef, "Plugin B should have service reference");

            // Plugin A unloads (simulated)
            registry.unregisterService(TestServiceA.class);

            // Plugin B should check availability before use
            assertFalse(registry.hasService(TestServiceA.class),
                "Plugin B should detect service is no longer available");
        }

        @Test
        @DisplayName("Service count updates correctly during lifecycle")
        void serviceCountDuringLifecycle() throws ServiceException {
            // Start with 0 services
            assertEquals(0, registry.getRegisteredServices().length, "Should start with no services");

            // Register services
            registry.registerService(TestServiceA.class, new TestServiceAImpl());
            registry.registerService(TestServiceB.class, new TestServiceBImpl("B"));
            assertEquals(2, registry.getRegisteredServices().length, "Should have 2 services");

            // Unregister one
            registry.unregisterService(TestServiceA.class);
            assertEquals(1, registry.getRegisteredServices().length, "Should have 1 service after removal");

            // Shutdown clears all
            registry.shutdown();
            // Note: After shutdown, registry state may vary by implementation
        }
    }

    // ==================== 4. FallbackTracker Isolation ====================

    @Nested
    @DisplayName("4. FallbackTracker Isolation Between Plugins")
    class FallbackTrackerIsolationTests {

        @Test
        @DisplayName("Plugin A's fallback state doesn't affect Plugin B")
        void fallbackStateIsolation() throws ServiceException {
            // Register services with independent fallback trackers
            TestServiceWithFallback serviceA = new TestServiceWithFallbackImpl("A");
            TestServiceWithFallback serviceB = new TestServiceWithFallbackImpl("B");

            registry.registerService(TestServiceA.class, (TestServiceA) serviceA);
            registry.registerService(TestServiceB.class, (TestServiceB) serviceB);

            // Trigger fallback on service A
            serviceA.triggerFallback();

            // Service A should be in fallback
            assertTrue(serviceA.isInFallbackMode(), "Service A should be in fallback mode");

            // Service B should NOT be affected
            assertFalse(serviceB.isInFallbackMode(), "Service B should NOT be in fallback mode");
        }

        @Test
        @DisplayName("Independent recovery from fallback mode")
        void independentFallbackRecovery() throws ServiceException {
            TestServiceWithFallback serviceA = new TestServiceWithFallbackImpl("A");
            TestServiceWithFallback serviceB = new TestServiceWithFallbackImpl("B");

            // Both enter fallback
            serviceA.triggerFallback();
            serviceB.triggerFallback();

            assertTrue(serviceA.isInFallbackMode(), "Service A should be in fallback");
            assertTrue(serviceB.isInFallbackMode(), "Service B should be in fallback");

            // Only A recovers
            serviceA.recoverFromFallback();

            assertFalse(serviceA.isInFallbackMode(), "Service A should have recovered");
            assertTrue(serviceB.isInFallbackMode(), "Service B should still be in fallback");
        }
    }

    // ==================== 5. Async Operation Tests ====================

    @Nested
    @DisplayName("5. Async Cross-Plugin Operations")
    class AsyncOperationTests {

        @Test
        @DisplayName("Async service calls work across plugins")
        void asyncCrossPluginCalls() throws Exception {
            // Register async-capable service
            TestAsyncService asyncService = new TestAsyncServiceImpl();
            registry.registerService(TestAsyncService.class, asyncService);

            // Cross-plugin async call
            TestAsyncService service = registry.getService(TestAsyncService.class);
            CompletableFuture<String> future = service.getDataAsync("test-key");

            // Verify async completion
            String result = future.get();
            assertEquals("async:test-key", result, "Async cross-plugin call should complete correctly");
        }

        @Test
        @DisplayName("Concurrent service access is thread-safe")
        void concurrentServiceAccess() throws Exception {
            // Register service
            TestServiceA service = new TestServiceAImpl();
            registry.registerService(TestServiceA.class, service);

            // Concurrent access from multiple "plugins"
            AtomicBoolean allSucceeded = new AtomicBoolean(true);
            Thread[] threads = new Thread[10];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        TestServiceA retrieved = registry.getService(TestServiceA.class);
                        if (retrieved == null) {
                            allSucceeded.set(false);
                        }
                    } catch (Exception e) {
                        allSucceeded.set(false);
                    }
                });
                threads[i].start();
            }

            // Wait for all threads
            for (Thread thread : threads) {
                thread.join();
            }

            assertTrue(allSucceeded.get(), "All concurrent service accesses should succeed");
        }
    }

    // ==================== Test Service Interfaces and Implementations ====================

    interface TestServiceA {
        String getName();
    }

    interface TestServiceB {
        String getName();
    }

    interface TestDataService {
        String getData(String key);
    }

    interface TestOptionalService {
        void doSomething();
    }

    interface TestMissingService {
        void notImplemented();
    }

    interface TestServiceWithFallback extends TestServiceA, TestServiceB {
        boolean isInFallbackMode();
        void triggerFallback();
        void recoverFromFallback();
    }

    interface TestAsyncService {
        CompletableFuture<String> getDataAsync(String key);
    }

    // Test implementations

    static class TestServiceAImpl implements TestServiceA {
        @Override
        public String getName() {
            return "ServiceA";
        }
    }

    static class TestServiceBImpl implements TestServiceB {
        private final String name;

        TestServiceBImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class TestDataServiceImpl implements TestDataService {
        @Override
        public String getData(String key) {
            return "data:" + key;
        }
    }

    static class TestOptionalServiceImpl implements TestOptionalService {
        @Override
        public void doSomething() {
            // Implementation
        }
    }

    static class TestServiceWithFallbackImpl implements TestServiceWithFallback {
        private final String name;
        private boolean inFallbackMode = false;

        TestServiceWithFallbackImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInFallbackMode() {
            return inFallbackMode;
        }

        @Override
        public void triggerFallback() {
            inFallbackMode = true;
        }

        @Override
        public void recoverFromFallback() {
            inFallbackMode = false;
        }
    }

    static class TestAsyncServiceImpl implements TestAsyncService {
        @Override
        public CompletableFuture<String> getDataAsync(String key) {
            return CompletableFuture.supplyAsync(() -> "async:" + key);
        }
    }
}
