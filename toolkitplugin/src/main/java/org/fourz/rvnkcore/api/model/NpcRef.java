package org.fourz.rvnkcore.api.model;

import org.bukkit.Location;

import java.util.Objects;

/**
 * A snapshot of one RVNK-keyed NPC, as returned by
 * {@link org.fourz.rvnkcore.api.service.INpcService#findByKey(String)} (#2213).
 *
 * <p>Consumers reference an NPC by its {@link #getKey() RVNK key}. The {@link #getBackingId()
 * backing id} is the NPC plugin's own number (the Citizens NPC id today); it is here for staff
 * tooling and logs, and must never be stored by a quest or an event, because it changes when the NPC
 * plugin is swapped or the NPC is re-created.</p>
 *
 * <p>Immutable. The location is copied on the way in and on the way out.</p>
 *
 * @since 1.5.99-alpha
 */
public final class NpcRef {

    private final String key;
    private final String displayName;
    private final String world;
    private final Location location;
    private final int backingId;
    private final boolean spawned;

    /**
     * @param key         the RVNK key, lower-case {@code [a-z0-9_-]{1,48}}
     * @param displayName the NPC's name without colour codes
     * @param world       the world name, or null when the NPC has no stored location
     * @param location    the NPC's live location when spawned, else its stored location; may be null
     * @param backingId   the NPC plugin's id for this NPC (Citizens NPC id)
     * @param spawned     whether the NPC entity is currently spawned
     */
    public NpcRef(String key, String displayName, String world, Location location, int backingId, boolean spawned) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? "" : displayName;
        this.world = world;
        this.location = location == null ? null : location.clone();
        this.backingId = backingId;
        this.spawned = spawned;
    }

    /** @return the RVNK key, lower-case */
    public String getKey() {
        return key;
    }

    /** @return the NPC's name without colour codes; never null */
    public String getDisplayName() {
        return displayName;
    }

    /** @return the world name, or null when the NPC has no stored location */
    public String getWorld() {
        return world;
    }

    /** @return a copy of the NPC's location, or null when unknown */
    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    /** @return the NPC plugin's id (Citizens NPC id); for tooling and logs only */
    public int getBackingId() {
        return backingId;
    }

    /** @return whether the NPC entity is spawned right now */
    public boolean isSpawned() {
        return spawned;
    }

    @Override
    public String toString() {
        return "NpcRef{key=" + key + ", name=" + displayName + ", world=" + world
                + ", backingId=" + backingId + ", spawned=" + spawned + "}";
    }
}
