package org.fourz.rvnkcore.service.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #2434: a server portal whose frame and sign are intact can lose its portal blocks. The interior check
 * that verify() and repair() share, and the axis a refill uses, without a server.
 */
@DisplayName("portal interior: classify the registered blocks and pick the refill axis (#2434)")
class PortalInteriorTest {

    /** The Dev portal 7b1818c7: a Z-Y frame at x = -3, interior y 80-82, z 21-23. */
    private static final List<int[]> DEV_PORTAL = List.of(
            new int[] {-3, 80, 21}, new int[] {-3, 81, 21}, new int[] {-3, 82, 21},
            new int[] {-3, 80, 22}, new int[] {-3, 81, 22}, new int[] {-3, 82, 22},
            new int[] {-3, 80, 23}, new int[] {-3, 81, 23}, new int[] {-3, 82, 23});

    private static String key(int[] b) {
        return b[0] + "," + b[1] + "," + b[2];
    }

    @Test
    @DisplayName("all air: every block is empty, the portal is unlit (the Dev report)")
    void allAir() {
        PortalService.InteriorState s = PortalService.classifyInterior(DEV_PORTAL, b -> Material.AIR);
        assertEquals(9, s.empty().size());
        assertEquals(0, s.lit());
        assertFalse(s.isLit());
    }

    @Test
    @DisplayName("all portal blocks: lit")
    void allLit() {
        PortalService.InteriorState s = PortalService.classifyInterior(DEV_PORTAL, b -> Material.NETHER_PORTAL);
        assertEquals(9, s.lit());
        assertTrue(s.isLit());
    }

    @Test
    @DisplayName("a player's block is reported, never put in the refill list; unloaded blocks are not read")
    void mixed() {
        Map<String, Material> world = new HashMap<>();
        for (int[] b : DEV_PORTAL) world.put(key(b), Material.NETHER_PORTAL);
        world.put("-3,80,21", Material.CAVE_AIR);
        world.put("-3,81,21", Material.DIRT);
        world.put("-3,82,21", null); // unloaded chunk
        PortalService.InteriorState s = PortalService.classifyInterior(DEV_PORTAL, b -> world.get(key(b)));
        assertEquals(6, s.lit());
        assertEquals(1, s.empty().size());
        assertEquals("-3,80,21", key(s.empty().get(0)));
        assertEquals(1, s.blocked());
        assertEquals(1, s.unloaded());
        assertFalse(s.isLit(), "a blocked interior block is not lit");
    }

    @Test
    @DisplayName("no registered interior: nothing to report, counts as lit")
    void none() {
        assertTrue(PortalService.classifyInterior(null, b -> Material.AIR).isLit());
        assertTrue(PortalService.classifyInterior(List.of(), b -> Material.AIR).isLit());
    }

    @Test
    @DisplayName("axis: one X is a Z-Y frame (Axis.Z), one Z is an X-Y frame (Axis.X), a single column is unknown")
    void axis() {
        assertEquals(Axis.Z, PortalService.axisOf(DEV_PORTAL));
        assertEquals(Axis.X, PortalService.axisOf(List.of(new int[] {4, 70, 9}, new int[] {5, 70, 9}, new int[] {4, 71, 9})));
        assertNull(PortalService.axisOf(List.of(new int[] {1, 70, 1}, new int[] {1, 71, 1})), "one column fits both planes");
        assertNull(PortalService.axisOf(List.of(new int[] {1, 70, 1}, new int[] {2, 70, 2})), "not in one vertical plane");
        assertNull(PortalService.axisOf(List.of()));
    }
}
