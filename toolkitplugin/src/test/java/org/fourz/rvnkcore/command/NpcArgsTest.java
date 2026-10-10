package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.service.npc.harness.NpcZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Quoted-argument splitting and admin-verb argument parsing (#2248). */
class NpcArgsTest {

    @Test
    void quotedNameStaysOneArgument() {
        assertArrayEquals(new String[]{"create", "guide_test", "Warden Tolla", "journey", "1", "2", "3"},
                QuotedArgs.tokenize("create guide_test \"Warden Tolla\" journey 1 2 3".split(" ")));
        assertArrayEquals(new String[]{"rename", "k", "Say \"hi\""},
                QuotedArgs.tokenize("rename k \"Say \\\"hi\\\"\"".split(" ")));
        assertArrayEquals(new String[]{"a", "b c"}, QuotedArgs.tokenize("a \"b c".split(" ")), "unclosed quote runs to the end");
        assertArrayEquals(new String[]{"a", ""}, QuotedArgs.tokenize("a \"\"".split(" ")), "empty quotes are an empty argument");
        assertArrayEquals(new String[0], QuotedArgs.tokenize(new String[0]));
    }

    @Test
    void parsesPosition() {
        NpcArgs.Parsed<NpcArgs.Position> pos = NpcArgs.position("k journey 10.5 65 -20.5".split(" "), 1);
        assertTrue(pos.ok(), pos.error());
        assertEquals("journey", pos.value().world());
        assertEquals(10.5, pos.value().x());
        assertEquals(-20.5, pos.value().z());
        assertNull(pos.value().yaw());

        NpcArgs.Position rotated = NpcArgs.position("w 0 64 0 270 -10".split(" "), 0).value();
        assertEquals(-90f, rotated.yaw(), "yaw folded into (-180, 180]");
        assertEquals(-10f, rotated.pitch());
    }

    @Test
    void rejectsBadPositions() {
        assertTrue(NpcArgs.position("w 0 64".split(" "), 0).error().contains("expected"));
        assertTrue(NpcArgs.position("w 0 64 0 0 0 0".split(" "), 0).error().contains("expected"));
        assertTrue(NpcArgs.position("w x 64 0".split(" "), 0).error().contains("x must be a number"));
        assertTrue(NpcArgs.position("w 0 9000 0".split(" "), 0).error().contains("y out of range"));
        assertTrue(NpcArgs.position("w 0 64 0 500".split(" "), 0).error().contains("yaw"));
        assertTrue(NpcArgs.position("w 0 64 0 0 95".split(" "), 0).error().contains("pitch"));
        assertFalse(NpcArgs.position("w NaN 64 0".split(" "), 0).ok());
    }

    @Test
    void parsesZoneOptions() {
        assertEquals(NpcZone.defaults(), NpcArgs.zone(new String[]{"k"}, 1).value());
        assertEquals(new NpcZone(4, 5), NpcArgs.zone("k 4 5".split(" "), 1).value());
        assertEquals(new NpcZone(3, 3), NpcArgs.zone("k radius=3".split(" "), 1).value());
        assertEquals(new NpcZone(2, 6), NpcArgs.zone("k height=6".split(" "), 1).value());
        assertTrue(NpcArgs.zone("k 99".split(" "), 1).error().contains("radius must be"));
        assertTrue(NpcArgs.zone("k 2 0".split(" "), 1).error().contains("height must be"));
        assertTrue(NpcArgs.zone("k 2 3 4".split(" "), 1).error().contains("too many"));
        assertTrue(NpcArgs.zone("k depth=2".split(" "), 1).error().contains("unknown option"));
        assertTrue(NpcArgs.zone("k two".split(" "), 1).error().contains("whole number"));
    }
}
