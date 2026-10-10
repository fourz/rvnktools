package org.fourz.rvnkcore.service.region;

import org.bukkit.Bukkit;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.function.Supplier;

/**
 * Installs the region tool (#2248): picks the {@link IRegionService} implementation and registers it.
 *
 * <p><b>Classloading guard</b>, same rule as {@code NpcBridge}: this class never names a WorldGuard
 * or WorldEdit type. Every such class lives in {@code org.fourz.rvnkcore.service.region.worldguard}
 * and is reached only through a static call that runs after {@code isPluginEnabled("WorldGuard")}
 * returned true. {@code NpcBridgeClassLoadingTest} hides {@code com.sk89q} and that package and
 * proves the startup path still loads.</p>
 *
 * @since 1.5.100-alpha
 */
public final class RegionBridge {

    /** Bukkit plugin name of WorldGuard. */
    public static final String WORLDGUARD = "WorldGuard";

    private RegionBridge() {
    }

    /**
     * Installs the service. Never throws.
     *
     * @return the registered service
     */
    public static IRegionService install(ServiceRegistry registry, LogManager logger) {
        // A lambda, not a method reference: a method reference resolves the adapter class when the
        // Supplier is built, which happens even when WorldGuard is absent. The lambda body is a
        // synthetic method whose invokestatic resolves only if it runs.
        IRegionService service = selectService(Bukkit.getPluginManager().isPluginEnabled(WORLDGUARD),
                () -> org.fourz.rvnkcore.service.region.worldguard.WorldGuardRegionAdapter.create(logger), logger);
        registry.registerService(IRegionService.class, service);
        if (service.isAvailable()) {
            logger.info("Region tool registered: provider=WorldGuard");
        } else {
            logger.info("Region tool registered as unavailable (" + service.unavailableReason() + ")");
        }
        return service;
    }

    /**
     * Chooses the implementation; free of Bukkit state so the classloading test can call it.
     *
     * @param worldGuardEnabled whether WorldGuard is enabled
     * @param factory           builds the WorldGuard-backed service; only called when enabled
     * @param logger            may be null
     */
    public static IRegionService selectService(boolean worldGuardEnabled, Supplier<IRegionService> factory,
                                               LogManager logger) {
        if (!worldGuardEnabled) {
            return new UnavailableRegionService(IRegionService.NOT_INSTALLED);
        }
        try {
            IRegionService service = factory.get();
            return service != null ? service : new UnavailableRegionService("WorldGuard adapter returned nothing");
        } catch (RuntimeException | LinkageError e) {
            if (logger != null) {
                logger.warning("WorldGuard is enabled but the region adapter failed to start: " + e);
            }
            return new UnavailableRegionService("WorldGuard adapter failed: " + e.getClass().getSimpleName());
        }
    }
}
