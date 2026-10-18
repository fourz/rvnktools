package org.fourz.rvnkcore.api.dungeon;

/**
 * Where a dungeon is in its cycle (#2367).
 *
 * @since 1.5.112-alpha
 */
public enum DungeonState {
    /** Reset and waiting; its roster is alive. */
    READY,
    /** At least one player is inside and the roster is not yet cleared. */
    ACTIVE,
    /** The last roster mob is dead; the dungeon waits for its reset. */
    CLEARED,
    /** A reset is running. */
    RESETTING,
    /** No such dungeon, or no provider. */
    UNKNOWN
}
