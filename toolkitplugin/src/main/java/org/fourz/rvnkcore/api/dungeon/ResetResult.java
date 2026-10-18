package org.fourz.rvnkcore.api.dungeon;

/**
 * Outcome of {@link IDungeonService#reset} (#2367).
 *
 * @param playersMoved players moved to the exit before the reset
 * @param mobsSpawned  roster mobs spawned by the reset
 * @param message      short human-readable detail; never null
 * @since 1.5.112-alpha
 */
public record ResetResult(Status status, int playersMoved, int mobsSpawned, String message) {

    public enum Status {
        /** The dungeon is back to its saved state with a fresh roster. */
        RESET,
        /** No dungeon has that id. */
        NOT_FOUND,
        /** A reset is already running for it. */
        BUSY,
        /** Its policy does not allow this reason (for example a quest reset on a staff-only dungeon). */
        REFUSED,
        /** No provider (RVNKWorlds is not installed or not enabled). */
        UNAVAILABLE,
        /** The reset started and failed; the message says where. */
        FAILED
    }

    public ResetResult {
        message = message == null ? "" : message;
    }

    public boolean ok() {
        return status == Status.RESET;
    }

    public static ResetResult of(Status status, String message) {
        return new ResetResult(status, 0, 0, message);
    }
}
