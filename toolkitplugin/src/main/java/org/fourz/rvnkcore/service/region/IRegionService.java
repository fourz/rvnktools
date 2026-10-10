package org.fourz.rvnkcore.service.region;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Console-safe region tool (#2248). WorldGuard sits behind it; this interface names no WorldGuard
 * type, so callers and tests load without WorldGuard.
 *
 * <p><b>Why it exists.</b> WorldGuard's own {@code /rg define} needs a WorldEdit selection, and the
 * console cannot make one. This service builds the region from explicit corners instead.</p>
 *
 * <p><b>Always registered.</b> Without WorldGuard RVNKCore registers {@link UnavailableRegionService}:
 * {@link #isAvailable()} is false and every write returns a failed {@link RegionResult} with the
 * message "WorldGuard not installed". Nothing throws.</p>
 *
 * <p>Main thread only.</p>
 *
 * @since 1.5.100-alpha
 */
public interface IRegionService {

    /** Message every call returns when WorldGuard is missing. */
    String NOT_INSTALLED = "WorldGuard not installed";

    /**
     * Outcome of a write.
     *
     * @param ok      whether the change was applied
     * @param created true when {@link #define} made a new region rather than updating one
     * @param message a one-line, human-readable result
     */
    record RegionResult(boolean ok, boolean created, String message) {
        public static RegionResult fail(String message) {
            return new RegionResult(false, false, message);
        }

        public static RegionResult ok(String message) {
            return new RegionResult(true, false, message);
        }
    }

    /**
     * Read-only view of one region.
     *
     * @param id       region id (WorldGuard stores ids lower-case)
     * @param world    world name
     * @param type     WorldGuard region type, e.g. "cuboid", "polygon", "global"
     * @param bounds   bounding box; for a polygon this is its bounding box
     * @param priority region priority
     * @param flags    flag name to display value, sorted by name
     * @param owners   owner summary, "" when none
     * @param members  member summary, "" when none
     */
    record RegionInfo(String id, String world, String type, Cuboid bounds, int priority,
                      Map<String, String> flags, String owners, String members) {
    }

    /** @return true when WorldGuard is installed, enabled and serving */
    boolean isAvailable();

    /** @return why the service is unavailable, or "" when it is available */
    default String unavailableReason() {
        return isAvailable() ? "" : NOT_INSTALLED;
    }

    /**
     * Creates a cuboid region, or redefines an existing one with new bounds. A redefine keeps the
     * existing flags, owners, members, priority and parent, then applies {@code flags} on top.
     *
     * @param world    world name; must be loaded
     * @param id       region id
     * @param box      the bounds
     * @param flags    flag name to raw value (WorldGuard syntax, e.g. "allow"); empty for none
     * @param priority when non-null, the region's priority (new or existing); null keeps an
     *                 existing region's priority, or WorldGuard's default 0 for a new one
     * @param sender   who runs the command; used to parse player-dependent flag values
     * @return the outcome
     */
    RegionResult define(String world, String id, Cuboid box, Map<String, String> flags, Integer priority,
                        CommandSender sender);

    /**
     * Sets or clears one flag.
     *
     * @param value raw value, or null to clear the flag
     */
    RegionResult setFlag(String world, String id, String flag, String value, CommandSender sender);

    /** Removes a region; child regions are unset from it, not removed. */
    RegionResult remove(String world, String id);

    /** @return the region, or empty when the world, the region or WorldGuard is missing */
    Optional<RegionInfo> info(String world, String id);

    /** @return the ids of every region in a world, sorted; empty when unavailable */
    List<String> regionIds(String world);

    /** @return every registered flag name, sorted; empty when unavailable */
    List<String> flagNames();

    /** @return true when {@code name} is a registered flag (case-insensitive) */
    default boolean isFlag(String name) {
        if (name == null) {
            return false;
        }
        for (String flag : flagNames()) {
            if (flag.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** @return true when {@code name} is a state flag (allow/deny); used for tab completion */
    default boolean isStateFlag(String name) {
        return false;
    }
}
