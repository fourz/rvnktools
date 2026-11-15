package org.fourz.rvnkcore.service.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #2447: verify() reads the frame ring around the registered interior. The ring, the gap check and the
 * state decision, without a server.
 */
@DisplayName("portal frame: ring, gaps and state precedence (#2447)")
class PortalFrameTest {

    /** Material.isSolid() needs a running server; the test passes its own idea of solid. */
    private static final Predicate<Material> SOLID = m -> m != Material.OAK_SIGN && m != Material.TORCH;

    /** The Dev portal 7b1818c7: a Z-Y frame at x = -3, interior y 80-82, z 21-23. */
    private static final List<int[]> Z_PORTAL = List.of(
            new int[] {-3, 80, 21}, new int[] {-3, 81, 21}, new int[] {-3, 82, 21},
            new int[] {-3, 80, 22}, new int[] {-3, 81, 22}, new int[] {-3, 82, 22},
            new int[] {-3, 80, 23}, new int[] {-3, 81, 23}, new int[] {-3, 82, 23});

    /** An X-Y frame at z = 9: interior x 4-5, y 70-72. */
    private static final List<int[]> X_PORTAL = List.of(
            new int[] {4, 70, 9}, new int[] {5, 70, 9},
            new int[] {4, 71, 9}, new int[] {5, 71, 9},
            new int[] {4, 72, 9}, new int[] {5, 72, 9});

    private static String key(int[] b) {
        return b[0] + "," + b[1] + "," + b[2];
    }

    private static Set<String> keys(List<int[]> blocks) {
        Set<String> out = new HashSet<>();
        for (int[] b : blocks) out.add(key(b));
        return out;
    }

    @Test
    @DisplayName("Z-Y ring: 3x3 interior gives 12 blocks, all at x = -3, none inside, no corners")
    void ringZ() {
        List<int[]> ring = PortalService.frameRing(Z_PORTAL, Axis.Z);
        assertEquals(12, ring.size());
        Set<String> inside = keys(Z_PORTAL);
        for (int[] b : ring) {
            assertEquals(-3, b[0], "the ring stays in the portal plane");
            assertFalse(inside.contains(key(b)));
        }
        Set<String> ringKeys = keys(ring);
        assertTrue(ringKeys.contains("-3,79,22"), "bottom");
        assertTrue(ringKeys.contains("-3,83,22"), "top");
        assertTrue(ringKeys.contains("-3,81,20"), "side");
        assertTrue(ringKeys.contains("-3,81,24"), "side");
        assertFalse(ringKeys.contains("-3,79,20"), "corners are not frame");
        assertFalse(ringKeys.contains("-2,81,22"), "never out of the plane");
    }

    @Test
    @DisplayName("X-Y ring: 2x3 interior gives 10 blocks, all at z = 9")
    void ringX() {
        List<int[]> ring = PortalService.frameRing(X_PORTAL, Axis.X);
        assertEquals(10, ring.size());
        for (int[] b : ring) assertEquals(9, b[2]);
        Set<String> ringKeys = keys(ring);
        assertTrue(ringKeys.contains("3,71,9"));
        assertTrue(ringKeys.contains("6,71,9"));
        assertTrue(ringKeys.contains("4,69,9"));
        assertTrue(ringKeys.contains("5,73,9"));
    }

    @Test
    @DisplayName("ring without an axis or an interior is empty")
    void ringEmpty() {
        assertTrue(PortalService.frameRing(Z_PORTAL, null).isEmpty());
        assertTrue(PortalService.frameRing(List.of(), Axis.X).isEmpty());
        assertTrue(PortalService.frameRing(null, Axis.Z).isEmpty());
    }

    @Test
    @DisplayName("closed diamond frame: no gap, verified")
    void noGap() {
        PortalService.FrameState frame = PortalService.classifyFrame(
                PortalService.frameRing(Z_PORTAL, Axis.Z), b -> Material.DIAMOND_BLOCK, SOLID);
        assertTrue(frame.isIntact());
        assertEquals(12, frame.solid());
        PortalService.InteriorState lit = PortalService.classifyInterior(Z_PORTAL, b -> Material.NETHER_PORTAL);
        assertEquals(PortalService.Verification.VERIFIED, PortalService.decide(lit, frame));
    }

    @Test
    @DisplayName("one broken frame block (the Event report): one gap, frame-broken, named by coordinate")
    void oneGap() {
        PortalService.FrameState frame = PortalService.classifyFrame(PortalService.frameRing(Z_PORTAL, Axis.Z),
                b -> key(b).equals("-3,83,22") ? Material.AIR : Material.DIAMOND_BLOCK, SOLID);
        assertFalse(frame.isIntact());
        assertEquals(1, frame.gaps().size());
        assertEquals("-3,83,22", frame.describeGaps(8));
        PortalService.InteriorState lit = PortalService.classifyInterior(Z_PORTAL, b -> Material.NETHER_PORTAL);
        assertEquals(PortalService.Verification.FRAME_BROKEN, PortalService.decide(lit, frame));
    }

    @Test
    @DisplayName("X-Y frame: water, a sign and a torch are gaps; unloaded blocks are not read")
    void gapsX() {
        PortalService.FrameState frame = PortalService.classifyFrame(PortalService.frameRing(X_PORTAL, Axis.X), b -> {
            switch (key(b)) {
                case "3,70,9": return Material.WATER;
                case "3,71,9": return Material.OAK_SIGN;
                case "6,72,9": return Material.TORCH;
                case "4,73,9": return null; // unloaded chunk
                default: return Material.OBSIDIAN;
            }
        }, SOLID);
        assertEquals(3, frame.gaps().size());
        assertEquals(1, frame.unloaded());
        assertEquals(6, frame.solid());
        assertEquals("3,70,9 3,71,9 6,72,9", frame.describeGaps(8));
        assertEquals("3,70,9 +2 more", frame.describeGaps(1));
    }

    @Test
    @DisplayName("precedence: frame-broken wins over unlit; unlit alone is unlit")
    void precedence() {
        PortalService.FrameState broken = new PortalService.FrameState(
                new ArrayList<>(List.<int[]>of(new int[] {0, 0, 0})), 11, 0);
        PortalService.FrameState intact = new PortalService.FrameState(List.of(), 12, 0);
        PortalService.InteriorState unlit = PortalService.classifyInterior(Z_PORTAL, b -> Material.AIR);
        PortalService.InteriorState lit = PortalService.classifyInterior(Z_PORTAL, b -> Material.NETHER_PORTAL);
        assertEquals(PortalService.Verification.FRAME_BROKEN, PortalService.decide(unlit, broken));
        assertEquals(PortalService.Verification.UNLIT, PortalService.decide(unlit, intact));
        assertEquals(PortalService.Verification.VERIFIED, PortalService.decide(lit, intact));
        assertEquals(PortalService.Verification.VERIFIED, PortalService.decide(null, null));
    }

    @Test
    @DisplayName("plane: from the blocks, and from the anchor for a single column")
    void plane() {
        assertEquals(Axis.Z, PortalService.planeAxis(Z_PORTAL, -3, 20));
        assertEquals(Axis.X, PortalService.planeAxis(X_PORTAL, 3, 9));
        List<int[]> column = List.of(new int[] {1, 70, 1}, new int[] {1, 71, 1});
        assertEquals(Axis.X, PortalService.planeAxis(column, 0, 1), "anchor beside on X: an X-Y frame");
        assertEquals(Axis.Z, PortalService.planeAxis(column, 1, 2), "anchor beside on Z: a Z-Y frame");
        assertNull(PortalService.planeAxis(column, 1, 1), "anchor above or below: no plane");
    }
}
