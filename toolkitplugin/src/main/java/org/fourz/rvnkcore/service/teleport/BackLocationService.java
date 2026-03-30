package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Location;

import java.util.UUID;
import java.util.Optional;

/**
 * Tracks the pre-teleport location for each player so they can /back.
 * In-memory only — data is lost on restart (acceptable for /back).
 */
public interface BackLocationService {

    void setBackLocation(UUID playerUUID, Location location);

    Optional<Location> getBackLocation(UUID playerUUID);

    void clearBackLocation(UUID playerUUID);
}
