package org.fourz.rvnkcore.service.region.worldguard;

import com.sk89q.worldguard.WorldGuard;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Entry point into the WorldGuard adapter (#2248). Only {@code RegionBridge} calls this, and only
 * after {@code isPluginEnabled("WorldGuard")} returned true. The signature names no WorldGuard type.
 *
 * @since 1.5.100-alpha
 */
public final class WorldGuardRegionAdapter {

    private WorldGuardRegionAdapter() {
    }

    /**
     * @param logger RVNKCore's logger
     * @return the WorldGuard-backed service
     */
    public static IRegionService create(LogManager logger) {
        // Touch the API now so a WorldGuard build that does not match fails here, inside
        // RegionBridge's LinkageError guard, not later inside a command.
        WorldGuard.getInstance().getFlagRegistry();
        return new WorldGuardRegionService(logger == null ? message -> { } : logger::warning);
    }
}
