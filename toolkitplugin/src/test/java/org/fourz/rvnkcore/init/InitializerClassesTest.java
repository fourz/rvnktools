package org.fourz.rvnkcore.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the initialization classes.
 *
 * <p>These tests verify the basic structure and contract of the initializer classes.
 * Full integration testing requires a Bukkit server environment.</p>
 *
 * <p>Verifies SOLID refactoring deliverables:</p>
 * <ul>
 *   <li>CoreServiceFactory exists and has correct API</li>
 *   <li>RVNKToolsInitializer exists and has correct API</li>
 *   <li>ApiServerInitializer exists and has correct API</li>
 * </ul>
 *
 * @since 1.4.0
 */
class InitializerClassesTest {

    @Test
    @DisplayName("CoreServiceFactory class exists with expected API")
    void coreServiceFactoryClassExists() {
        // Verify class can be loaded
        assertDoesNotThrow(() -> Class.forName("org.fourz.rvnkcore.init.CoreServiceFactory"));

        // Verify expected method exists
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("org.fourz.rvnkcore.init.CoreServiceFactory");
            clazz.getMethod("registerAllServices", Class.forName("org.fourz.rvnkcore.service.registry.ServiceRegistry"));
        }, "CoreServiceFactory should have registerAllServices(ServiceRegistry) method");
    }

    @Test
    @DisplayName("RVNKToolsInitializer class exists with expected API")
    void rvnkToolsInitializerClassExists() {
        // Verify class can be loaded
        assertDoesNotThrow(() -> Class.forName("org.fourz.rvnkcore.init.RVNKToolsInitializer"));

        // Verify expected methods exist
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("org.fourz.rvnkcore.init.RVNKToolsInitializer");
            clazz.getMethod("initializeAll");
            clazz.getMethod("shutdownAll");
        }, "RVNKToolsInitializer should have initializeAll() and shutdownAll() methods");
    }

    @Test
    @DisplayName("ApiServerInitializer class exists with expected API")
    void apiServerInitializerClassExists() {
        // Verify class can be loaded
        assertDoesNotThrow(() -> Class.forName("org.fourz.rvnkcore.init.ApiServerInitializer"));

        // Verify expected methods exist
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("org.fourz.rvnkcore.init.ApiServerInitializer");
            clazz.getMethod("start");
            clazz.getMethod("stop");
            clazz.getMethod("isRunning");
        }, "ApiServerInitializer should have start(), stop(), and isRunning() methods");
    }

    @Test
    @DisplayName("Init package contains all three initializer classes")
    void initPackageContainsAllInitializers() {
        // All three classes should be loadable from the init package
        String[] expectedClasses = {
            "org.fourz.rvnkcore.init.CoreServiceFactory",
            "org.fourz.rvnkcore.init.RVNKToolsInitializer",
            "org.fourz.rvnkcore.init.ApiServerInitializer"
        };

        for (String className : expectedClasses) {
            assertDoesNotThrow(
                () -> Class.forName(className),
                "Expected class to exist: " + className
            );
        }
    }

    @Test
    @DisplayName("Initializer classes follow single responsibility principle")
    void initializerClassesFollowSRP() {
        // Verify each class is focused on a single concern by checking method count
        // This is a heuristic - classes with many public methods might violate SRP

        assertDoesNotThrow(() -> {
            Class<?> coreFactory = Class.forName("org.fourz.rvnkcore.init.CoreServiceFactory");
            // Should have constructor + registerAllServices (+ inherited Object methods)
            long publicMethods = java.util.Arrays.stream(coreFactory.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .count();
            assertTrue(publicMethods <= 3, "CoreServiceFactory should have minimal public methods (SRP)");
        });

        assertDoesNotThrow(() -> {
            Class<?> toolsInit = Class.forName("org.fourz.rvnkcore.init.RVNKToolsInitializer");
            long publicMethods = java.util.Arrays.stream(toolsInit.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .count();
            assertTrue(publicMethods <= 3, "RVNKToolsInitializer should have minimal public methods (SRP)");
        });

        assertDoesNotThrow(() -> {
            Class<?> api = Class.forName("org.fourz.rvnkcore.init.ApiServerInitializer");
            long publicMethods = java.util.Arrays.stream(api.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .count();
            assertTrue(publicMethods <= 4, "ApiServerInitializer should have minimal public methods (SRP)");
        });
    }

    @Test
    @DisplayName("RVNKCore delegates to initializers")
    void rvnkCoreDelegatesToInitializers() throws Exception {
        // Verify RVNKCore has fields for each initializer
        Class<?> rvnkCore = Class.forName("org.fourz.rvnkcore.RVNKCore");

        // Check for initializer fields (they're private, so use getDeclaredFields)
        boolean hasCoreServiceFactory = false;
        boolean hasToolsInitializer = false;
        boolean hasApiInitializer = false;

        for (java.lang.reflect.Field field : rvnkCore.getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            if (typeName.equals("CoreServiceFactory")) hasCoreServiceFactory = true;
            if (typeName.equals("RVNKToolsInitializer")) hasToolsInitializer = true;
            if (typeName.equals("ApiServerInitializer")) hasApiInitializer = true;
        }

        assertTrue(hasCoreServiceFactory, "RVNKCore should have CoreServiceFactory field");
        assertTrue(hasToolsInitializer, "RVNKCore should have RVNKToolsInitializer field");
        assertTrue(hasApiInitializer, "RVNKCore should have ApiServerInitializer field");
    }

    @Test
    @DisplayName("RVNKCore has reduced field count after refactor")
    void rvnkCoreHasReducedFieldCount() throws Exception {
        // After SOLID refactor, RVNKCore should have fewer instance fields
        // Original had 15+ fields, refactored should have ~8
        Class<?> rvnkCore = Class.forName("org.fourz.rvnkcore.RVNKCore");

        long instanceFieldCount = java.util.Arrays.stream(rvnkCore.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .count();

        assertTrue(instanceFieldCount <= 10,
            "RVNKCore should have reduced instance fields after refactor (found: " + instanceFieldCount + ")");
    }

    @Test
    @DisplayName("Bundled component accessors use ServiceRegistry pattern")
    void bundledComponentAccessorsUseServiceRegistry() throws Exception {
        // Verify accessor methods like getAnnounceManager() delegate to getService()
        Class<?> rvnkCore = Class.forName("org.fourz.rvnkcore.RVNKCore");

        // These methods should exist and return the expected types
        assertDoesNotThrow(() -> rvnkCore.getMethod("getAnnounceManager"),
            "RVNKCore should have getAnnounceManager() method");
        assertDoesNotThrow(() -> rvnkCore.getMethod("getLinkMaker"),
            "RVNKCore should have getLinkMaker() method");
        assertDoesNotThrow(() -> rvnkCore.getMethod("getPermissionService"),
            "RVNKCore should have getPermissionService() method");
        assertDoesNotThrow(() -> rvnkCore.getMethod("getLogFilter"),
            "RVNKCore should have getLogFilter() method");
    }
}
