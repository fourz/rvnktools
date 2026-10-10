package org.fourz.rvnkcore.service.npc;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the NPC bridge's classloading guard (#2213): with Citizens and PlaceholderAPI absent from
 * the classpath, RVNKCore's startup path loads, links, verifies and runs, and registers the
 * unavailable service, with no {@code NoClassDefFoundError}. Since #2248 the same holds for
 * WorldGuard/WorldEdit ({@code com.sk89q}) and the region tool's adapter package.
 *
 * <p>Runs RVNKCore's own classes through a child-first loader that refuses {@code net.citizensnpcs},
 * {@code me.clip}, and also RVNKCore's own adapter packages ({@code service.npc.citizens},
 * {@code service.npc.papi}). Hiding the adapter packages is the strict form: it proves the startup
 * path never even loads the adapters, not just that the adapters happen to load lazily.</p>
 *
 * <p>The control tests check the loader really hides the classes: in the same loader, reflecting on
 * the Citizens listener (what Bukkit's {@code registerEvents} does) and loading the PlaceholderAPI
 * expansion both fail. Without that control a passing test could mean the loader hid nothing.</p>
 */
class NpcBridgeClassLoadingTest {

    private static final List<String> THIRD_PARTY = List.of("net.citizensnpcs.", "me.clip.", "com.sk89q.");
    private static final List<String> ADAPTERS = List.of(
            "org.fourz.rvnkcore.service.npc.citizens.", "org.fourz.rvnkcore.service.npc.papi.",
            "org.fourz.rvnkcore.service.region.worldguard.");

    /** Classes on RVNKCore's enable path for the bridge. */
    private static final List<String> STARTUP_CLASSES = List.of(
            "org.fourz.rvnkcore.api.service.INpcService",
            "org.fourz.rvnkcore.api.service.INpcService$TagResult",
            "org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent",
            "org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent$ClickType",
            "org.fourz.rvnkcore.api.model.NpcRef",
            "org.fourz.rvnkcore.api.model.NpcInteraction",
            "org.fourz.rvnkcore.service.npc.NpcKeys",
            "org.fourz.rvnkcore.service.npc.NpcInteractionTracker",
            "org.fourz.rvnkcore.service.npc.UnavailableNpcService",
            "org.fourz.rvnkcore.service.npc.NpcBridge",
            "org.fourz.rvnkcore.command.RvnkCommand",
            "org.fourz.rvnkcore.command.NpcSubCommand",
            "org.fourz.rvnkcore.init.RVNKToolsInitializer",
            // #2248: NPC harness, spec tooling and the region tool
            "org.fourz.rvnkcore.service.npc.harness.NpcHarness",
            "org.fourz.rvnkcore.service.npc.harness.NpcSpecParser",
            "org.fourz.rvnkcore.service.npc.harness.NpcApplyPlanner",
            "org.fourz.rvnkcore.service.npc.harness.NpcVerifier",
            "org.fourz.rvnkcore.service.npc.harness.NpcSpecExecutor",
            "org.fourz.rvnkcore.service.npc.harness.NpcSpecExporter",
            "org.fourz.rvnkcore.service.npc.harness.NpcGround",
            "org.fourz.rvnkcore.service.region.IRegionService",
            "org.fourz.rvnkcore.service.region.UnavailableRegionService",
            "org.fourz.rvnkcore.service.region.RegionBridge",
            "org.fourz.rvnkcore.service.region.RegionArgs",
            "org.fourz.rvnkcore.command.NpcAdminVerbs",
            "org.fourz.rvnkcore.command.RegionSubCommand",
            // #2255: QA click simulator
            "org.fourz.rvnkcore.service.npc.NpcClickDispatcher",
            "org.fourz.rvnkcore.service.npc.NpcClickSimulator",
            "org.fourz.rvnkcore.service.npc.NpcClickSimulator$Outcome",
            "org.fourz.rvnkcore.command.NpcClickGate",
            "org.fourz.rvnkcore.command.NpcArgs");

    /** Child-first for org.fourz; refuses every hidden prefix. */
    static final class HidingLoader extends URLClassLoader {
        private final List<String> hidden;

        HidingLoader(List<String> hidden) {
            super(new URL[]{NpcBridge.class.getProtectionDomain().getCodeSource().getLocation()},
                    NpcBridgeClassLoadingTest.class.getClassLoader());
            this.hidden = hidden;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                for (String prefix : hidden) {
                    if (name.startsWith(prefix)) {
                        throw new ClassNotFoundException(name + " (hidden by test)");
                    }
                }
                if (name.startsWith("org.fourz.")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }
    }

    private HidingLoader strict;
    private HidingLoader thirdPartyOnly;

    @BeforeEach
    void setUp() {
        strict = new HidingLoader(concat(THIRD_PARTY, ADAPTERS));
        thirdPartyOnly = new HidingLoader(THIRD_PARTY);
    }

    @AfterEach
    void tearDown() throws Exception {
        strict.close();
        thirdPartyOnly.close();
    }

    @Test
    void controlCitizensListenerCannotBeReflectedWithoutCitizens() throws Exception {
        Class<?> listener = Class.forName(
                "org.fourz.rvnkcore.service.npc.citizens.CitizensNpcListener", false, thirdPartyOnly);
        // Bukkit's registerEvents reflects on the handler methods; without Citizens that must fail.
        assertThrows(NoClassDefFoundError.class, listener::getDeclaredMethods);
    }

    @Test
    void controlPlaceholderExpansionCannotLoadWithoutPlaceholderApi() {
        assertThrows(NoClassDefFoundError.class, () -> Class.forName(
                "org.fourz.rvnkcore.service.npc.papi.RvnkNpcPlaceholderExpansion", true, thirdPartyOnly));
    }

    @Test
    void controlWorldGuardServiceCannotBeReflectedWithoutWorldGuard() throws Exception {
        Class<?> service = Class.forName(
                "org.fourz.rvnkcore.service.region.worldguard.WorldGuardRegionService", false, thirdPartyOnly);
        assertThrows(NoClassDefFoundError.class, service::getDeclaredMethods);
    }

    @Test
    void regionSelectReturnsUnavailableInTheIsolatedLoader() throws Exception {
        Class<?> bridge = Class.forName("org.fourz.rvnkcore.service.region.RegionBridge", true, strict);
        Class<?> logManager = Class.forName("org.fourz.rvnkcore.util.log.LogManager", true, strict);
        Method select = bridge.getMethod("selectService", boolean.class, java.util.function.Supplier.class, logManager);
        Object service = select.invoke(null, false, null, null);
        Class<?> api = Class.forName("org.fourz.rvnkcore.service.region.IRegionService", false, strict);
        assertEquals(false, api.getMethod("isAvailable").invoke(service));
        assertEquals("WorldGuard not installed", api.getMethod("unavailableReason").invoke(service));
    }

    @Test
    void regionInstallRegistersUnavailableServiceWithoutWorldGuard() throws Exception {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled(anyString())).thenReturn(false);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCore-test");

        Class<?> bridge = Class.forName("org.fourz.rvnkcore.service.region.RegionBridge", true, strict);
        Class<?> registryType = Class.forName("org.fourz.rvnkcore.service.registry.ServiceRegistry", true, strict);
        Class<?> logManager = Class.forName("org.fourz.rvnkcore.util.log.LogManager", true, strict);
        Class<?> api = Class.forName("org.fourz.rvnkcore.service.region.IRegionService", false, strict);
        Object registry = Mockito.mock(registryType);
        Object logger = logManager.getMethod("getInstance", Plugin.class, Class.class)
                .invoke(null, plugin, NpcBridgeClassLoadingTest.class);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            Object service = bridge.getMethod("install", registryType, logManager).invoke(null, registry, logger);
            assertEquals(false, api.getMethod("isAvailable").invoke(service));
        }
        Invocation register = Mockito.mockingDetails(registry).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("registerService"))
                .findFirst().orElseThrow(() -> new AssertionError("IRegionService was not registered"));
        assertSame(api, register.getArgument(0));
        assertEquals("org.fourz.rvnkcore.service.region.UnavailableRegionService",
                register.getArgument(1).getClass().getName());
    }

    @Test
    void controlLoaderReallyIsIsolated() throws Exception {
        Class<?> isolated = Class.forName("org.fourz.rvnkcore.service.npc.NpcBridge", false, strict);
        assertNotSame(NpcBridge.class, isolated, "the test must exercise a separately loaded copy");
    }

    @Test
    void startupClassesLinkAndInitialiseWithoutCitizensOrPlaceholderApi() {
        for (String name : STARTUP_CLASSES) {
            assertDoesNotThrow(() -> {
                Class<?> type = Class.forName(name, true, strict); // links, verifies, initialises
                type.getDeclaredMethods();                        // resolves every signature type
                type.getDeclaredFields();
            }, name + " must load with Citizens, PlaceholderAPI and the adapter packages hidden");
        }
    }

    @Test
    void selectServiceReturnsUnavailableInTheIsolatedLoader() throws Exception {
        Class<?> bridge = Class.forName("org.fourz.rvnkcore.service.npc.NpcBridge", true, strict);
        Class<?> logManager = Class.forName("org.fourz.rvnkcore.util.log.LogManager", true, strict);
        Method select = bridge.getMethod("selectService", boolean.class, java.util.function.Supplier.class, logManager);

        Object service = select.invoke(null, false, null, null);
        Class<?> api = Class.forName("org.fourz.rvnkcore.api.service.INpcService", false, strict);
        assertEquals(false, api.getMethod("isAvailable").invoke(service));
        assertEquals(List.of(), api.getMethod("listKeys").invoke(service));
        assertEquals(java.util.Optional.empty(), api.getMethod("findByKey", String.class).invoke(service, "guide"));
        assertEquals(java.util.Optional.empty(), api.getMethod("lastInteraction", UUID.class)
                .invoke(service, UUID.randomUUID()));
        assertEquals("UNAVAILABLE", String.valueOf(api.getMethod("tag", int.class, String.class)
                .invoke(service, 1, "guide")));
    }

    @Test
    void installRegistersUnavailableServiceWithNeitherPluginPresent() throws Exception {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled(anyString())).thenReturn(false);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCore-test");

        Class<?> bridge = Class.forName("org.fourz.rvnkcore.service.npc.NpcBridge", true, strict);
        Class<?> registryType = Class.forName("org.fourz.rvnkcore.service.registry.ServiceRegistry", true, strict);
        Class<?> logManager = Class.forName("org.fourz.rvnkcore.util.log.LogManager", true, strict);
        Class<?> api = Class.forName("org.fourz.rvnkcore.api.service.INpcService", false, strict);

        Object registry = Mockito.mock(registryType);
        Object logger = logManager.getMethod("getInstance", Plugin.class, Class.class)
                .invoke(null, plugin, NpcBridgeClassLoadingTest.class);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            Object installed = bridge.getMethod("install", Plugin.class, registryType, logManager)
                    .invoke(null, plugin, registry, logger);
            Object service = bridge.getMethod("getService").invoke(installed);
            assertEquals(false, api.getMethod("isAvailable").invoke(service));
            assertDoesNotThrow(() -> bridge.getMethod("shutdown").invoke(installed));
        }

        Invocation register = Mockito.mockingDetails(registry).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("registerService"))
                .findFirst().orElseThrow(() -> new AssertionError("INpcService was not registered"));
        assertSame(api, register.getArgument(0), "registered under the INpcService interface");
        assertEquals("org.fourz.rvnkcore.service.npc.UnavailableNpcService",
                register.getArgument(1).getClass().getName());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        java.util.ArrayList<String> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
