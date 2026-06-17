package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of BackLocationService.
 * Stores one "back" location per player.
 */
public class DefaultBackLocationService implements BackLocationService {

    private final ConcurrentHashMap<UUID, Location> backLocations = new ConcurrentHashMap<>();

    @Override
    public void setBackLocation(UUID playerUUID, Location location) {
        if (location != null && location.getWorld() != null) {
            backLocations.put(playerUUID, location.clone());
        }
    }

    @Override
    public Optional<Location> getBackLocation(UUID playerUUID) {
        Location loc = backLocations.get(playerUUID);
        if (loc != null && loc.getWorld() != null) {
            return Optional.of(loc);
        }
        return Optional.empty();
    }

    @Override
    public void clearBackLocation(UUID playerUUID) {
        backLocations.remove(playerUUID);
    }

    /**
     * Clean up data for a disconnected player to prevent memory leaks.
     */
    public void handlePlayerQuit(UUID playerUUID) {
        backLocations.remove(playerUUID);
    }
}
