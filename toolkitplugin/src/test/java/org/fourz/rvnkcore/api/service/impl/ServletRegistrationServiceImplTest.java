package org.fourz.rvnkcore.api.service.impl;

import jakarta.servlet.http.HttpServlet;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ServletRegistrationServiceImpl.
 *
 * <p>Tests cover all aspects of the servlet registration API including:
 * registration before/after server start, unregistration, query methods,
 * input validation, server lifecycle, and thread safety.</p>
 *
 * @see ServletRegistrationServiceImpl
 * @see org.fourz.rvnkcore.api.service.IServletRegistrationService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServletRegistrationService Tests")
class ServletRegistrationServiceImplTest {

    @Mock
    private ApiConfig mockConfig;

    @Mock
    private Plugin mockPlugin;

    @Mock
    private ServletContextHandler mockServletContext;

    private ServletRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        // Setup mock config defaults
        when(mockConfig.getHttpPort()).thenReturn(8080);
        when(mockConfig.getHttpsPort()).thenReturn(8443);
        when(mockConfig.isHttpsEnabled()).thenReturn(false);

        // Setup mock plugin with logger
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestPlugin"));

        service = new ServletRegistrationServiceImpl(mockConfig, mockPlugin, new com.google.gson.Gson());
    }

    // ==================== Registration Before Server Start ====================

    @Nested
    @DisplayName("Registration Before Server Start")
    class RegistrationBeforeServerStart {

        @Test
        @DisplayName("Servlet is queued when registered before server start")
        void testServletQueuedBeforeServerStart() {
            HttpServlet servlet = mock(HttpServlet.class);

            boolean result = service.registerServlet("/api/test/*", servlet);

            assertTrue(result, "Registration should succeed");
            assertTrue(service.isRegistered("/api/test/*"), "Servlet should be in registry");
            assertFalse(service.isServerRunning(), "Server should not be running");
            assertEquals(1, service.getRegisteredCount(), "Count should be 1");
        }

        @Test
        @DisplayName("Multiple servlets can be queued before server start")
        void testMultipleServletsQueued() {
            HttpServlet servlet1 = mock(HttpServlet.class);
            HttpServlet servlet2 = mock(HttpServlet.class);
            HttpServlet servlet3 = mock(HttpServlet.class);

            service.registerServlet("/api/plugin1/*", servlet1);
            service.registerServlet("/api/plugin2/*", servlet2);
            service.registerServlet("/api/plugin3/*", servlet3);

            assertEquals(3, service.getRegisteredCount());
            assertTrue(service.isRegistered("/api/plugin1/*"));
            assertTrue(service.isRegistered("/api/plugin2/*"));
            assertTrue(service.isRegistered("/api/plugin3/*"));
        }

        @Test
        @DisplayName("Pending registrations are applied when server starts")
        void testPendingRegistrationsAppliedOnStart() {
            HttpServlet servlet = mock(HttpServlet.class);

            // Queue registration before server start
            service.registerServlet("/api/test/*", servlet, "Test API", true);
            assertFalse(service.isServerRunning());

            // Start server
            service.setServletContext(mockServletContext);

            assertTrue(service.isServerRunning());
            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/test/*").contains("applied=true"));
        }
    }

    // ==================== Registration After Server Start ====================

    @Nested
    @DisplayName("Registration After Server Start")
    class RegistrationAfterServerStart {

        @BeforeEach
        void startServer() {
            service.setServletContext(mockServletContext);
        }

        @Test
        @DisplayName("Servlet is applied immediately when server is running")
        void testImmediateRegistration() {
            HttpServlet servlet = mock(HttpServlet.class);

            boolean result = service.registerServlet("/api/immediate/*", servlet);

            assertTrue(result);
            assertTrue(service.isRegistered("/api/immediate/*"));

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/immediate/*").contains("applied=true"));
        }

        @Test
        @DisplayName("Custom display name is preserved")
        void testCustomDisplayName() {
            HttpServlet servlet = mock(HttpServlet.class);

            service.registerServlet("/api/named/*", servlet, "My Custom API", true);

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/named/*").contains("My Custom API"));
        }

        @Test
        @DisplayName("Public endpoints can be registered without authentication")
        void testPublicEndpointRegistration() {
            HttpServlet servlet = mock(HttpServlet.class);

            service.registerServlet("/api/public/*", servlet, "Public API", false);

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/public/*").contains("auth=false"));
        }
    }

    // ==================== Servlet Unregistration ====================

    @Nested
    @DisplayName("Servlet Unregistration")
    class ServletUnregistration {

        @Test
        @DisplayName("Unregister existing servlet returns true")
        void testUnregisterExisting() {
            HttpServlet servlet = mock(HttpServlet.class);
            service.registerServlet("/api/removable/*", servlet);

            boolean result = service.unregisterServlet("/api/removable/*");

            assertTrue(result);
            assertFalse(service.isRegistered("/api/removable/*"));
            assertEquals(0, service.getRegisteredCount());
        }

        @Test
        @DisplayName("Unregister non-existent servlet returns false")
        void testUnregisterNonExistent() {
            boolean result = service.unregisterServlet("/api/nonexistent/*");

            assertFalse(result);
        }

        @Test
        @DisplayName("Unregister active servlet with warning")
        void testUnregisterActiveServlet() {
            HttpServlet servlet = mock(HttpServlet.class);
            service.setServletContext(mockServletContext);
            service.registerServlet("/api/active/*", servlet);

            boolean result = service.unregisterServlet("/api/active/*");

            assertTrue(result);
            assertFalse(service.isRegistered("/api/active/*"));
        }

        @Test
        @DisplayName("Unregister with normalized path")
        void testUnregisterWithNormalization() {
            HttpServlet servlet = mock(HttpServlet.class);
            service.registerServlet("/api/normalized/*", servlet);

            // Unregister without leading slash
            boolean result = service.unregisterServlet("api/normalized/*");

            assertTrue(result, "Should unregister with normalized path");
            assertFalse(service.isRegistered("/api/normalized/*"));
        }
    }

    // ==================== Plugin Servlet Management ====================

    @Nested
    @DisplayName("Plugin Servlet Management")
    class PluginServletManagement {

        @Test
        @DisplayName("Two-argument convenience method with null servlet")
        void testTwoArgConvenienceMethodNullServlet() {
            assertThrows(IllegalArgumentException.class, () ->
                service.registerServlet("/api/test/*", null, true));
        }

        @Test
        @DisplayName("Two-argument convenience method registers with auth")
        void testTwoArgConvenienceMethodWithAuth() {
            HttpServlet servlet = mock(HttpServlet.class);

            boolean result = service.registerServlet("/api/plugin/*", servlet, true);

            assertTrue(result);
            assertTrue(service.isRegistered("/api/plugin/*"));

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/plugin/*").contains("auth=true"));
        }

        @Test
        @DisplayName("Two-argument convenience method registers public endpoint")
        void testTwoArgConvenienceMethodPublic() {
            HttpServlet servlet = mock(HttpServlet.class);

            boolean result = service.registerServlet("/api/public/*", servlet, false);

            assertTrue(result);
            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/public/*").contains("auth=false"));
        }

        @Test
        @DisplayName("Single-argument convenience method defaults to auth required")
        void testSingleArgConvenienceMethod() {
            HttpServlet servlet = mock(HttpServlet.class);

            boolean result = service.registerServlet("/api/default/*", servlet);

            assertTrue(result);
            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/default/*").contains("auth=true"));
        }

        @Test
        @DisplayName("Multiple plugins can register different paths")
        void testMultiplePluginRegistrations() {
            service.setServletContext(mockServletContext);

            HttpServlet plugin1Servlet = mock(HttpServlet.class);
            HttpServlet plugin2Servlet = mock(HttpServlet.class);
            HttpServlet plugin3Servlet = mock(HttpServlet.class);

            service.registerServlet("/api/plugin1/*", plugin1Servlet, "Plugin1 API", true);
            service.registerServlet("/api/plugin2/*", plugin2Servlet, "Plugin2 API", false);
            service.registerServlet("/api/plugin3/health", plugin3Servlet, "Plugin3 Health", false);

            assertEquals(3, service.getRegisteredCount());

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/plugin1/*").contains("Plugin1 API"));
            assertTrue(details.get("/api/plugin2/*").contains("Plugin2 API"));
            assertTrue(details.get("/api/plugin3/health").contains("Plugin3 Health"));
        }
    }

    // ==================== Query Methods ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("isRegistered correctly reports registration status")
        void testIsRegistered() {
            HttpServlet servlet = mock(HttpServlet.class);

            assertFalse(service.isRegistered("/api/check/*"));

            service.registerServlet("/api/check/*", servlet);

            assertTrue(service.isRegistered("/api/check/*"));
        }

        @Test
        @DisplayName("Path normalization adds leading slash")
        void testPathNormalization() {
            HttpServlet servlet = mock(HttpServlet.class);

            service.registerServlet("api/noslash/*", servlet);

            assertTrue(service.isRegistered("/api/noslash/*"));
            assertTrue(service.isRegistered("api/noslash/*"));
        }

        @Test
        @DisplayName("getRegisteredPaths returns all paths")
        void testGetRegisteredPaths() {
            service.registerServlet("/api/path1/*", mock(HttpServlet.class));
            service.registerServlet("/api/path2/*", mock(HttpServlet.class));
            service.registerServlet("/api/path3/*", mock(HttpServlet.class));

            String[] paths = service.getRegisteredPaths();

            assertEquals(3, paths.length);
        }

        @Test
        @DisplayName("getRegisteredCount returns correct count")
        void testGetRegisteredCount() {
            assertEquals(0, service.getRegisteredCount());

            service.registerServlet("/api/one/*", mock(HttpServlet.class));
            assertEquals(1, service.getRegisteredCount());

            service.registerServlet("/api/two/*", mock(HttpServlet.class));
            assertEquals(2, service.getRegisteredCount());

            service.unregisterServlet("/api/one/*");
            assertEquals(1, service.getRegisteredCount());
        }

        @Test
        @DisplayName("getBaseUrl returns HTTP URL when HTTPS disabled")
        void testGetBaseUrlHttp() {
            when(mockConfig.isHttpsEnabled()).thenReturn(false);
            when(mockConfig.getHttpPort()).thenReturn(8080);

            assertEquals("http://localhost:8080", service.getBaseUrl());
        }

        @Test
        @DisplayName("getBaseUrl returns HTTPS URL when HTTPS enabled")
        void testGetBaseUrlHttps() {
            when(mockConfig.isHttpsEnabled()).thenReturn(true);
            when(mockConfig.getHttpsPort()).thenReturn(8443);

            assertEquals("https://localhost:8443", service.getBaseUrl());
        }
    }

    // ==================== Input Validation ====================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Duplicate registration is rejected")
        void testDuplicateRegistration() {
            HttpServlet servlet1 = mock(HttpServlet.class);
            HttpServlet servlet2 = mock(HttpServlet.class);

            assertTrue(service.registerServlet("/api/duplicate/*", servlet1));
            assertFalse(service.registerServlet("/api/duplicate/*", servlet2));

            assertEquals(1, service.getRegisteredCount());
        }

        @Test
        @DisplayName("Null servlet throws IllegalArgumentException")
        void testNullServlet() {
            assertThrows(IllegalArgumentException.class, () ->
                service.registerServlet("/api/null/*", null));
        }

        @Test
        @DisplayName("Null path throws IllegalArgumentException")
        void testNullPath() {
            HttpServlet servlet = mock(HttpServlet.class);

            assertThrows(IllegalArgumentException.class, () ->
                service.registerServlet(null, servlet));
        }

        @Test
        @DisplayName("Empty path throws IllegalArgumentException")
        void testEmptyPath() {
            HttpServlet servlet = mock(HttpServlet.class);

            assertThrows(IllegalArgumentException.class, () ->
                service.registerServlet("", servlet));

            assertThrows(IllegalArgumentException.class, () ->
                service.registerServlet("   ", servlet));
        }
    }

    // ==================== Server Lifecycle ====================

    @Nested
    @DisplayName("Server Lifecycle")
    class ServerLifecycle {

        @Test
        @DisplayName("isServerRunning tracks state correctly")
        void testServerRunningState() {
            assertFalse(service.isServerRunning());

            service.setServletContext(mockServletContext);
            assertTrue(service.isServerRunning());

            service.onServerStop();
            assertFalse(service.isServerRunning());
        }

        @Test
        @DisplayName("onServerStop marks registrations as not applied")
        void testOnServerStopClearsAppliedState() {
            HttpServlet servlet = mock(HttpServlet.class);

            service.setServletContext(mockServletContext);
            service.registerServlet("/api/lifecycle/*", servlet);

            Map<String, String> beforeStop = service.getRegistrationDetails();
            assertTrue(beforeStop.get("/api/lifecycle/*").contains("applied=true"));

            service.onServerStop();

            Map<String, String> afterStop = service.getRegistrationDetails();
            assertTrue(afterStop.get("/api/lifecycle/*").contains("applied=false"));
        }

        @Test
        @DisplayName("Server restart re-applies registrations")
        void testServerRestart() {
            HttpServlet servlet = mock(HttpServlet.class);

            // Initial start
            service.setServletContext(mockServletContext);
            service.registerServlet("/api/restart/*", servlet);

            // Stop
            service.onServerStop();
            assertFalse(service.isServerRunning());

            // Restart
            service.setServletContext(mockServletContext);
            assertTrue(service.isServerRunning());

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get("/api/restart/*").contains("applied=true"));
        }

        @Test
        @DisplayName("Null context stops server")
        void testNullContext() {
            service.setServletContext(mockServletContext);
            assertTrue(service.isServerRunning());

            service.setServletContext(null);
            assertFalse(service.isServerRunning());
        }
    }

    // ==================== Thread Safety ====================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Concurrent registrations from multiple threads")
        void testConcurrentRegistrations() throws InterruptedException {
            int threadCount = 10;
            int registrationsPerThread = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < registrationsPerThread; i++) {
                            HttpServlet servlet = mock(HttpServlet.class);
                            String path = "/api/thread" + threadId + "/servlet" + i + "/*";
                            if (service.registerServlet(path, servlet)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(threadCount * registrationsPerThread, successCount.get());
            assertEquals(threadCount * registrationsPerThread, service.getRegisteredCount());
        }

        @Test
        @DisplayName("Concurrent registration during setServletContext")
        void testRegistrationDuringContextSet() throws InterruptedException {
            int registrationCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            // Thread 1: Register servlets
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < registrationCount; i++) {
                        service.registerServlet("/api/race" + i + "/*", mock(HttpServlet.class));
                        Thread.yield();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();

            // Thread 2: Start server
            new Thread(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(5);
                    service.setServletContext(mockServletContext);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

            // All registrations should be present
            assertEquals(registrationCount, service.getRegisteredCount());
        }

        @Test
        @DisplayName("Concurrent unregistration")
        void testConcurrentUnregistration() throws InterruptedException {
            // Pre-register servlets
            for (int i = 0; i < 100; i++) {
                service.registerServlet("/api/unregister" + i + "/*", mock(HttpServlet.class));
            }

            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger unregisteredCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = threadId; i < 100; i += threadCount) {
                            if (service.unregisterServlet("/api/unregister" + i + "/*")) {
                                unregisteredCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(100, unregisteredCount.get());
            assertEquals(0, service.getRegisteredCount());
        }
    }

    // ==================== Full Lifecycle Integration ====================

    @Nested
    @DisplayName("Full Lifecycle Integration")
    class FullLifecycleIntegration {

        @Test
        @DisplayName("Complete servlet lifecycle")
        void testCompleteLifecycle() {
            HttpServlet servlet = mock(HttpServlet.class);
            String path = "/api/lifecycle/*";

            // 1. Register before server start
            assertTrue(service.registerServlet(path, servlet, "Lifecycle API", true));
            assertTrue(service.isRegistered(path));
            assertFalse(service.isServerRunning());

            // 2. Start server
            service.setServletContext(mockServletContext);
            assertTrue(service.isServerRunning());

            Map<String, String> details = service.getRegistrationDetails();
            assertTrue(details.get(path).contains("applied=true"));

            // 3. Stop server
            service.onServerStop();
            assertFalse(service.isServerRunning());
            assertTrue(service.isRegistered(path));

            // 4. Restart server
            service.setServletContext(mockServletContext);
            assertTrue(service.isServerRunning());

            // 5. Unregister
            assertTrue(service.unregisterServlet(path));
            assertFalse(service.isRegistered(path));
            assertEquals(0, service.getRegisteredCount());
        }

        @Test
        @DisplayName("Mixed authenticated and public endpoints")
        void testMixedAuthModes() {
            service.setServletContext(mockServletContext);

            service.registerServlet("/api/auth/*", mock(HttpServlet.class), "Auth API", true);
            service.registerServlet("/api/public/*", mock(HttpServlet.class), "Public API", false);
            service.registerServlet("/api/health", mock(HttpServlet.class), "Health Check", false);

            Map<String, String> details = service.getRegistrationDetails();

            assertTrue(details.get("/api/auth/*").contains("auth=true"));
            assertTrue(details.get("/api/public/*").contains("auth=false"));
            assertTrue(details.get("/api/health").contains("auth=false"));
        }

        @Test
        @DisplayName("getRegistrationDetails provides complete info")
        void testRegistrationDetails() {
            service.setServletContext(mockServletContext);

            service.registerServlet("/api/detailed/*", mock(HttpServlet.class), "Detailed API", true);

            Map<String, String> details = service.getRegistrationDetails();
            String info = details.get("/api/detailed/*");

            assertNotNull(info);
            assertTrue(info.contains("Detailed API"));
            assertTrue(info.contains("auth=true"));
            assertTrue(info.contains("applied=true"));
        }
    }
}
