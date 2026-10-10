package org.fourz.rvnkcore.service.npc.harness;

import java.util.Locale;

/**
 * Standable-ground rules for NPC positions (#2248).
 *
 * <p><b>Why.</b> A Citizens player NPC falls under gravity. A spec Y that is not exactly the
 * standing Y (for example {@code pos: [-95.5, 68, 2.5]} over ground whose top is Y 67) makes the
 * NPC settle one block lower, and then every apply "moved" it back and verify reported
 * {@code (1.00 off)} forever.</p>
 *
 * <p><b>Snap.</b> On create and move the target Y snaps to a standable Y at that X/Z: a feet block
 * where the block below is solid and the feet and head blocks are passable. The search starts at the
 * requested feet block, goes down {@value #SEARCH_DOWN} blocks, then up {@value #SEARCH_UP}. When the
 * requested feet block is itself standable, the requested Y is kept as it is. When nothing in the
 * window is standable, the requested Y is kept and the caller warns.</p>
 *
 * <p><b>Tolerance.</b> {@link #yMatches} accepts a live Y within {@value NpcDiff#POSITION_TOLERANCE}
 * of the spec Y or of the standable Y, or up to {@value #SETTLE_TOLERANCE} blocks below the spec Y
 * (the NPC settled).</p>
 *
 * <p>Pure: the block reads come through {@link Terrain}, which the Citizens adapter implements with
 * Bukkit blocks and the tests implement in memory.</p>
 *
 * @since 1.5.101-alpha
 */
public final class NpcGround {

    /** Blocks searched below the requested feet block. */
    public static final int SEARCH_DOWN = 4;
    /** Blocks searched above the requested feet block, after the downward search. */
    public static final int SEARCH_UP = 2;
    /** How far below the spec Y a live NPC may stand and still match (it fell and settled). */
    public static final double SETTLE_TOLERANCE = 1.5;

    private NpcGround() {
    }

    /** Block reads for one world. Main thread only in the Bukkit implementation. */
    public interface Terrain {
        /** @return true when an NPC can stand on top of this block (it has collision) */
        boolean solid(String world, int x, int y, int z);

        /** @return true when an NPC's feet or head can occupy this block */
        boolean passable(String world, int x, int y, int z);
    }

    /**
     * Result of a snap.
     *
     * @param requestedY the Y that was asked for
     * @param y          the Y to use: the standable Y, or {@code requestedY} when none was found
     * @param found      true when a standable spot was found in the window
     */
    public record Snap(double requestedY, double y, boolean found) {

        /** @return true when the Y to use differs from the requested Y */
        public boolean changed() {
            return found && Double.compare(requestedY, y) != 0;
        }

        /** @return the standable Y, or null when none was found */
        public Double standY() {
            return found ? y : null;
        }

        /**
         * @return "snapped 68 -> 67", a warning when nothing was found, or null when the requested Y
         *         was already standable
         */
        public String note() {
            if (!found) {
                return "WARNING no standable ground within " + SEARCH_DOWN + " below / " + SEARCH_UP
                        + " above y " + NpcDiff.num(requestedY) + "; kept the requested y";
            }
            return changed() ? "snapped " + NpcDiff.num(requestedY) + " -> " + NpcDiff.num(y) : null;
        }
    }

    /**
     * @return true when an NPC can stand with its feet in block {@code feetY}: solid below, feet and
     *         head passable
     */
    public static boolean standable(Terrain terrain, String world, int x, int feetY, int z) {
        return terrain.solid(world, x, feetY - 1, z)
                && terrain.passable(world, x, feetY, z)
                && terrain.passable(world, x, feetY + 1, z);
    }

    /**
     * Finds the standable Y nearest the requested position: the requested feet block, then down
     * {@value #SEARCH_DOWN}, then up {@value #SEARCH_UP}.
     *
     * @param terrain block reads, or null (then nothing is found and the Y is kept)
     */
    public static Snap snap(Terrain terrain, String world, double x, double y, double z) {
        if (terrain == null || world == null) {
            return new Snap(y, y, false);
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        try {
            if (standable(terrain, world, bx, by, bz)) {
                return new Snap(y, y, true); // already standing on something in this block
            }
            for (int down = 1; down <= SEARCH_DOWN; down++) {
                if (standable(terrain, world, bx, by - down, bz)) {
                    return new Snap(y, by - down, true);
                }
            }
            for (int up = 1; up <= SEARCH_UP; up++) {
                if (standable(terrain, world, bx, by + up, bz)) {
                    return new Snap(y, by + up, true);
                }
            }
        } catch (RuntimeException e) {
            return new Snap(y, y, false); // a failed block read must not block a create or move
        }
        return new Snap(y, y, false);
    }

    /**
     * Whether a live Y matches the spec.
     *
     * @param specY  the spec Y
     * @param liveY  the NPC's live Y
     * @param standY the snapped standable Y for the spec position, or null when unknown
     * @return true when the live Y is within {@value NpcDiff#POSITION_TOLERANCE} of the spec Y or of
     *         the standable Y, or at most {@value #SETTLE_TOLERANCE} below the spec Y
     */
    public static boolean yMatches(double specY, double liveY, Double standY) {
        if (Math.abs(liveY - specY) <= NpcDiff.POSITION_TOLERANCE) {
            return true;
        }
        if (standY != null && Math.abs(liveY - standY) <= NpcDiff.POSITION_TOLERANCE) {
            return true;
        }
        return liveY < specY && specY - liveY <= SETTLE_TOLERANCE;
    }

    /** @return the standable-Y note for a dry run, or "" when the spec Y is already standable */
    static String standsAt(double specY, Double standY) {
        if (standY == null || Double.compare(standY, specY) == 0) {
            return "";
        }
        return String.format(Locale.ROOT, " (stands at y %s)", NpcDiff.num(standY));
    }
}
