package org.fourz.rvnkcore.api.dungeon;

/**
 * Why a dungeon reset (#2367).
 *
 * @since 1.5.112-alpha
 */
public enum ResetReason {
    /** A staff command. */
    STAFF,
    /** A quest step or reward. */
    QUEST,
    /** The box stayed empty for the dungeon's cooldown after a clear. */
    EMPTY_COOLDOWN
}
