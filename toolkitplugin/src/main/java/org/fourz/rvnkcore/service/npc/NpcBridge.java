package org.fourz.rvnkcore.service.npc;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.fourz.rvnkcore.api.service.INpcService;
import org.fourz.rvnkcore.service.npc.harness.NpcHarness;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.function.Supplier;

/**
 * Installs the RVNK NPC bridge (#2213): picks the {@link INpcService} implementation, registers it
 * in the ServiceRegistry, registers the write-side {@link NpcHarness} when Citizens is available
 * (#2248), and registers the {@code %rvnknpc_*%} PlaceholderAPI expansion.
 *
 * <p><b>Classloading guard.</b> This class must never name a Citizens or PlaceholderAPI type in a
 * field, a method signature or a catch clause. Every Citizens class lives in
 * {@code org.fourz.rvnkcore.service.npc.citizens} and every PlaceholderAPI class in
 * {@code org.fourz.rvnkcore.service.npc.papi}; this class reaches them only through a static call
 * that runs after {@code isPluginEnabled} returned true. The JVM resolves a static call lazily, so
 * on a server without Citizens or PlaceholderAPI those classes are never loaded and no
 * {@code NoClassDefFoundError} can occur. {@code NpcBridgeClassLoadingTest} proves it with a class
 * loader that hides both plugins.</p>
 *
 * @since 1.5.99-alpha
 */
public final class NpcBridge {

    /** Bukkit plugin name of Citizens. */
    public static final String CITIZENS = "Citizens";
    /** Bukkit plugin name of PlaceholderAPI. */
    public static final String PLACEHOLDER_API = "PlaceholderAPI";

    private final INpcService service;
    private final NpcHarness harness;
    private final Runnable unregisterPlaceholders;

    private NpcBridge(INpcService service, NpcHarness harness, Runnable unregisterPlaceholders) {
        this.service = service;
        this.harness = harness;
        this.unregisterPlaceholders = unregisterPlaceholders;
    }

    /**
     * Installs the bridge. Never throws: any adapter failure falls back to
     * {@link UnavailableNpcService}.
     *
     * @param plugin   RVNKCore
     * @param registry the service registry
     * @param logger   RVNKCore's logger
     * @return the installed bridge; call {@link #shutdown()} on disable
     */
    public static NpcBridge install(Plugin plugin, ServiceRegistry registry, LogManager logger) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        NpcInteractionTracker tracker = new NpcInteractionTracker();

        INpcService service = selectService(pluginManager.isPluginEnabled(CITIZENS),
                () -> org.fourz.rvnkcore.service.npc.citizens.CitizensNpcAdapter.start(plugin, tracker, logger),
                logger);
        registry.registerService(INpcService.class, service);
        NpcHarness harness = null;
        if (service.isAvailable()) {
            logger.info("NPC bridge registered: provider=" + service.getProviderName());
            harness = selectHarness(
                    () -> org.fourz.rvnkcore.service.npc.citizens.CitizensNpcAdapter.harness(plugin, logger), logger);
            if (harness != null) {
                registry.registerService(NpcHarness.class, harness);
                logger.info("NPC harness registered: /rvnk npc create|move|apply|verify|export (#2248)");
            }
        } else {
            logger.info("NPC bridge registered as unavailable ("
                    + (service instanceof UnavailableNpcService u ? u.getReason() : "unknown") + ")");
        }

        Runnable unregister = null;
        if (pluginManager.isPluginEnabled(PLACEHOLDER_API)) {
            try {
                unregister = org.fourz.rvnkcore.service.npc.papi.RvnkNpcPlaceholderExpansion.registerFor(plugin, service);
                if (unregister != null) {
                    logger.info("PlaceholderAPI expansion registered: %rvnknpc_*%");
                } else {
                    logger.warning("PlaceholderAPI refused the %rvnknpc_*% expansion");
                }
            } catch (RuntimeException | LinkageError e) {
                logger.warning("PlaceholderAPI expansion %rvnknpc_*% not registered: " + e);
            }
        } else {
            logger.debug("PlaceholderAPI not enabled - %rvnknpc_*% not registered");
        }

        return new NpcBridge(service, harness, unregister);
    }

    /**
     * Builds the write-side harness (#2248). Never throws.
     *
     * @return the harness, or null when the adapter failed to link
     */
    public static NpcHarness selectHarness(Supplier<NpcHarness> factory, LogManager logger) {
        try {
            return factory.get();
        } catch (RuntimeException | LinkageError e) {
            if (logger != null) {
                logger.warning("Citizens NPC harness failed to start; /rvnk npc create|apply are unavailable: " + e);
            }
            return null;
        }
    }

    /**
     * Chooses the implementation. Public and free of Bukkit state so the classloading test can call
     * it through an isolated class loader.
     *
     * @param citizensEnabled  whether Citizens is enabled
     * @param citizensFactory  builds the Citizens-backed service; only called when Citizens is enabled
     * @param logger           may be null
     * @return the Citizens-backed service, or an {@link UnavailableNpcService}; never null
     */
    public static INpcService selectService(boolean citizensEnabled, Supplier<INpcService> citizensFactory,
                                            LogManager logger) {
        if (!citizensEnabled) {
            return new UnavailableNpcService("Citizens is not installed or not enabled");
        }
        try {
            INpcService service = citizensFactory.get();
            return service != null ? service : new UnavailableNpcService("Citizens adapter returned nothing");
        } catch (RuntimeException | LinkageError e) {
            // LinkageError covers a Citizens build whose API no longer matches the one compiled against.
            if (logger != null) {
                logger.warning("Citizens is enabled but the NPC bridge adapter failed to start: " + e);
            }
            return new UnavailableNpcService("Citizens adapter failed: " + e.getClass().getSimpleName());
        }
    }

    /** @return the registered service */
    public INpcService getService() {
        return service;
    }

    /** @return the write-side harness, or null when Citizens is unavailable (#2248) */
    public NpcHarness getHarness() {
        return harness;
    }

    /** Unregisters the PlaceholderAPI expansion. Bukkit removes the click listener itself on disable. */
    public void shutdown() {
        if (unregisterPlaceholders != null) {
            try {
                unregisterPlaceholders.run();
            } catch (RuntimeException | LinkageError ignored) {
                // PlaceholderAPI may already be disabled during shutdown.
            }
        }
    }
}
