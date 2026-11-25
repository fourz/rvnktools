package org.fourz.rvnkcore.api.dungeon;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The dungeon contract's plain values (#2368). */
class DungeonContractTest {

    private static DungeonInfo pyramid() {
        // corners given in the "wrong" order on purpose
        return new DungeonInfo("koz_pyramid", "world", 84, 80, -652, 64, 50, -672, 74.5, 66, -648.5, 30);
    }

    @Test
    void boxIsNormalisedAndInclusive() {
        DungeonInfo d = pyramid();
        assertEquals(64, d.minX());
        assertEquals(84, d.maxX());
        assertEquals(-672, d.minZ());
        assertTrue(d.contains("world", 64, 50, -672), "min corner");
        assertTrue(d.contains("WORLD", 84, 80, -652), "max corner, world ignoring case");
        assertFalse(d.contains("world", 85, 60, -660));
        assertFalse(d.contains("sotw_city", 70, 60, -660), "other world");
        assertFalse(d.contains(null, 70, 60, -660));
        assertEquals(21L * 31 * 21, d.volume());
    }

    @Test
    void cooldownNeverNegative() {
        assertEquals(0, new DungeonInfo("a", "w", 0, 0, 0, 1, 1, 1, 0, 0, 0, -5).emptyCooldownMinutes());
    }

    @Test
    void idRules() {
        assertEquals("koz_pyramid", DungeonInfo.normaliseId(" KOZ_Pyramid "));
        assertThrows(IllegalArgumentException.class, () -> DungeonInfo.normaliseId("koz pyramid"));
        assertThrows(IllegalArgumentException.class, () -> DungeonInfo.normaliseId(""));
    }

    @Test
    void unavailableServiceNeverThrows() throws Exception {
        IDungeonService s = Dungeons.UNAVAILABLE;
        assertFalse(s.isAvailable());
        assertTrue(s.find("x").isEmpty());
        assertTrue(s.list().isEmpty());
        assertTrue(s.dungeonAt("world", 0, 0, 0).isEmpty());
        assertEquals(DungeonState.UNKNOWN, s.state("x"));
        assertEquals(0, s.remainingRoster("x"));
        assertTrue(s.participants("x").isEmpty());
        ResetResult r = s.reset("x", ResetReason.QUEST, "quest:test").get(1, TimeUnit.SECONDS);
        assertEquals(ResetResult.Status.UNAVAILABLE, r.status());
        assertFalse(r.ok());
    }

    @Test
    void resetResultOk() {
        assertTrue(new ResetResult(ResetResult.Status.RESET, 1, 6, null).ok());
        assertEquals("", new ResetResult(ResetResult.Status.BUSY, 0, 0, null).message());
    }
}
