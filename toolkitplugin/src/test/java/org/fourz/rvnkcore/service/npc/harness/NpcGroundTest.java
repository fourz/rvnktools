package org.fourz.rvnkcore.service.npc.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The standable-Y snap and the settle tolerance (#2248, 1.5.101). */
class NpcGroundTest {

    private static final String W = "journey";
    // -95.5, 2.5 is block -96, 2 (floor), as on Dev
    private static final double X = -95.5;
    private static final double Z = 2.5;
    private static final int BX = -96;
    private static final int BZ = 2;

    @Test
    void standableNeedsSolidBelowAndPassableFeetAndHead() {
        FakeTerrain terrain = new FakeTerrain().ground(W, BX, BZ, 66);
        assertTrue(NpcGround.standable(terrain, W, BX, 67, BZ), "on top of the ground");
        assertFalse(NpcGround.standable(terrain, W, BX, 68, BZ), "air below the feet");
        assertFalse(NpcGround.standable(terrain, W, BX, 66, BZ), "feet inside the ground");

        terrain.block(W, BX, 68, BZ); // a ceiling at head height
        assertFalse(NpcGround.standable(terrain, W, BX, 67, BZ), "head blocked");
    }

    @Test
    void specYOneAboveTheGroundSnapsDownOne() {
        FakeTerrain terrain = new FakeTerrain().ground(W, BX, BZ, 66);
        NpcGround.Snap snap = NpcGround.snap(terrain, W, X, 68, Z);
        assertTrue(snap.found());
        assertEquals(67.0, snap.y());
        assertTrue(snap.changed());
        assertEquals("snapped 68 -> 67", snap.note());
        assertEquals(67.0, snap.standY());
    }

    @Test
    void alreadyStandableKeepsTheRequestedY() {
        FakeTerrain terrain = new FakeTerrain().ground(W, BX, BZ, 66);
        NpcGround.Snap exact = NpcGround.snap(terrain, W, X, 67, Z);
        assertEquals(67.0, exact.y());
        assertFalse(exact.changed());
        assertNull(exact.note(), "nothing to report");

        NpcGround.Snap fraction = NpcGround.snap(terrain, W, X, 67.4, Z);
        assertEquals(67.4, fraction.y(), "same block: the requested Y is kept, the NPC settles inside tolerance");
        assertNull(fraction.note());
    }

    @Test
    void searchWindowIsFourDown() {
        NpcGround.Snap four = NpcGround.snap(new FakeTerrain().ground(W, BX, BZ, 63), W, X, 68, Z);
        assertTrue(four.found());
        assertEquals(64.0, four.y(), "feet 4 below the requested block");

        NpcGround.Snap five = NpcGround.snap(new FakeTerrain().ground(W, BX, BZ, 62), W, X, 68, Z);
        assertFalse(five.found(), "feet 5 below is outside the window");
        assertEquals(68.0, five.y(), "kept");
        assertTrue(five.note().startsWith("WARNING no standable ground"), five.note());
    }

    @Test
    void searchWindowIsTwoUpAfterDown() {
        // the spec Y is inside the ground: nothing below is standable, so it goes up
        NpcGround.Snap two = NpcGround.snap(new FakeTerrain().ground(W, BX, BZ, 69), W, X, 68, Z);
        assertTrue(two.found());
        assertEquals(70.0, two.y());
        assertEquals("snapped 68 -> 70", two.note());

        NpcGround.Snap three = NpcGround.snap(new FakeTerrain().ground(W, BX, BZ, 70), W, X, 68, Z);
        assertFalse(three.found(), "3 up is outside the window");
    }

    @Test
    void downIsPreferredOverUp() {
        // ground top 66 plus a block at 68: feet 67 has its head blocked, so the only spot is 69 (up)
        FakeTerrain terrain = new FakeTerrain().ground(W, BX, BZ, 66).block(W, BX, 68, BZ);
        NpcGround.Snap blocked = NpcGround.snap(terrain, W, X, 68, Z);
        assertEquals(69.0, blocked.y(), "a low ceiling at 67 sends it up to 69");

        FakeTerrain open = new FakeTerrain().ground(W, BX, BZ, 66).block(W, BX, 70, BZ);
        // standable at 67 and at 71; requested 69: down first finds 67 (2 down)
        assertEquals(67.0, NpcGround.snap(open, W, X, 69, Z).y());
    }

    @Test
    void noTerrainOrAFailedReadKeepsTheY() {
        NpcGround.Snap none = NpcGround.snap(null, W, X, 68, Z);
        assertFalse(none.found());
        assertEquals(68.0, none.y());

        NpcGround.Terrain broken = new NpcGround.Terrain() {
            @Override
            public boolean solid(String world, int x, int y, int z) {
                throw new IllegalStateException("chunk read failed");
            }

            @Override
            public boolean passable(String world, int x, int y, int z) {
                return true;
            }
        };
        NpcGround.Snap failed = NpcGround.snap(broken, W, X, 68, Z);
        assertFalse(failed.found());
        assertEquals(68.0, failed.y());
    }

    @Test
    void yMatchesAcceptsTheSettledAndTheStandableY() {
        assertTrue(NpcGround.yMatches(68, 68, null));
        assertTrue(NpcGround.yMatches(68, 67.6, null), "inside the 0.5 tolerance");
        assertTrue(NpcGround.yMatches(68, 67, null), "settled 1 below (the Dev case)");
        assertTrue(NpcGround.yMatches(68, 66.5, null), "settled 1.5 below");
        assertFalse(NpcGround.yMatches(68, 66.4, null), "1.6 below is more than settling");
        assertFalse(NpcGround.yMatches(68, 69, null), "above the spec is never settling");
        assertFalse(NpcGround.yMatches(68, 64, null));
        assertTrue(NpcGround.yMatches(68, 64, 64.0), "the snapped standable Y is in sync");
        assertTrue(NpcGround.yMatches(68, 64.4, 64.0));
        assertFalse(NpcGround.yMatches(68, 63, 64.0));
    }
}
