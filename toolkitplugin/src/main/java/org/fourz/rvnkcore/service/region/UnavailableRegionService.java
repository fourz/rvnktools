package org.fourz.rvnkcore.service.region;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link IRegionService} registered when WorldGuard is absent or failed to link (#2248).
 * Every write fails with "WorldGuard not installed"; every read is empty. Nothing throws.
 *
 * @since 1.5.100-alpha
 */
public class UnavailableRegionService implements IRegionService {

    private final String reason;

    public UnavailableRegionService(String reason) {
        this.reason = reason == null ? NOT_INSTALLED : reason;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return reason;
    }

    @Override
    public RegionResult define(String world, String id, Cuboid box, Map<String, String> flags, Integer priority,
                               CommandSender sender) {
        return RegionResult.fail(reason);
    }

    @Override
    public RegionResult setFlag(String world, String id, String flag, String value, CommandSender sender) {
        return RegionResult.fail(reason);
    }

    @Override
    public RegionResult remove(String world, String id) {
        return RegionResult.fail(reason);
    }

    @Override
    public Optional<RegionInfo> info(String world, String id) {
        return Optional.empty();
    }

    @Override
    public List<String> regionIds(String world) {
        return List.of();
    }

    @Override
    public List<String> flagNames() {
        return List.of();
    }
}
