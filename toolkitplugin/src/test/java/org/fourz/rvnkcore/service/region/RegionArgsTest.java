package org.fourz.rvnkcore.service.region;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Region argument parsing and flag validation (#2248). */
class RegionArgsTest {

    private static final Predicate<String> FLAGS =
            Set.of("interact", "use", "mob-spawning", "greeting", "pvp")::contains;

    private static String[] a(String line) {
        return line.split(" ");
    }

    @Test
    void parsesDefineWithFlagsAndNormalisesCorners() {
        RegionArgs.Parsed<RegionArgs.DefineArgs> parsed = RegionArgs.parseDefine(
                a("Sotw_Tolla sotw_city -90 69 4 -94 65 0 interact=allow use=allow mob-spawning=deny"), FLAGS);
        assertTrue(parsed.ok(), parsed.errors()::toString);
        RegionArgs.DefineArgs define = parsed.value();
        assertEquals("sotw_tolla", define.id(), "WorldGuard ids are lower-case");
        assertEquals("sotw_city", define.world());
        assertEquals(Cuboid.of(-94, 65, 0, -90, 69, 4), define.box());
        assertEquals(-94, define.box().minX());
        assertEquals(69, define.box().maxY());
        assertEquals(Map.of("interact", "allow", "use", "allow", "mob-spawning", "deny"), define.flags());
        assertNull(define.priority());
        assertEquals(125, define.box().volume());
    }

    @Test
    void priorityIsAPseudoFlag() {
        RegionArgs.Parsed<RegionArgs.DefineArgs> parsed = RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 priority=10 pvp=deny"), FLAGS);
        assertTrue(parsed.ok(), parsed.errors()::toString);
        assertEquals(10, parsed.value().priority());
        assertEquals(Map.of("pvp", "deny"), parsed.value().flags());
        assertFalse(RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 priority=high"), FLAGS).ok());
    }

    @Test
    void rejectsBadDefineArguments() {
        assertFalse(RegionArgs.parseDefine(a("r w 0 0 0 1 1"), FLAGS).ok(), "too few corners");
        assertTrue(RegionArgs.parseDefine(a("r w 0 0 x 1 1 1"), FLAGS).errors().get(0).contains("z1 must be a whole number"));
        assertTrue(RegionArgs.parseDefine(a("r w 0 0 0 1.5 1 1"), FLAGS).errors().get(0).contains("x2 must be a whole number"));
        assertTrue(RegionArgs.parseDefine(a("r w 0 3000 0 1 1 1"), FLAGS).errors().get(0).contains("y out of range"));
        assertTrue(RegionArgs.parseDefine(a("r w 0 0 0 31000000 1 1"), FLAGS).errors().get(0).contains("x out of range"));
        assertTrue(RegionArgs.parseDefine(a("bad!name w 0 0 0 1 1 1"), FLAGS).errors().get(0).contains("Invalid region name"));
        assertFalse(RegionArgs.parseDefine(a("__global__ w 0 0 0 1 1 1"), FLAGS).ok(), "the global region is not definable");
    }

    @Test
    void validatesFlagNamesAndPairs() {
        assertEquals("Unknown flag: interactt",
                RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 interactt=allow"), FLAGS).errors().get(0));
        assertEquals("Flag must be written flag=value: interact",
                RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 interact"), FLAGS).errors().get(0));
        assertEquals("Flag must be written flag=value: interact=",
                RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 interact="), FLAGS).errors().get(0));
        assertEquals("Flag given twice: use",
                RegionArgs.parseDefine(a("r w 0 0 0 1 1 1 use=allow USE=deny"), FLAGS).errors().get(0));
        assertFalse(RegionArgs.parseFlagPairs(a("priority=5"), FLAGS, false).ok(), "priority only where allowed");
    }

    @Test
    void parsesFlagVerb() {
        RegionArgs.Parsed<RegionArgs.FlagArgs> parsed = RegionArgs.parseFlag(a("r w greeting Welcome to the ruins"), FLAGS);
        assertTrue(parsed.ok());
        assertEquals("Welcome to the ruins", parsed.value().value(), "the rest of the line is the value");
        assertNull(RegionArgs.parseFlag(a("r w pvp clear"), FLAGS).value().value(), "clear means remove the flag");
        assertNull(RegionArgs.parseFlag(a("r w pvp CLEAR"), FLAGS).value().value());
        assertEquals("Unknown flag: nope", RegionArgs.parseFlag(a("r w nope allow"), FLAGS).errors().get(0));
        assertFalse(RegionArgs.parseFlag(a("r w pvp"), FLAGS).ok(), "value required");
    }

    @Test
    void parsesNameWorld() {
        RegionArgs.Parsed<String[]> parsed = RegionArgs.parseNameWorld(a("NPC_Guide journey"), "info");
        assertArrayEquals(new String[]{"npc_guide", "journey"}, parsed.value());
        assertFalse(RegionArgs.parseNameWorld(a("only"), "info").ok());
        assertFalse(RegionArgs.parseNameWorld(a("a b c"), "remove").ok());
    }

    @Test
    void unavailableServiceReportsWorldGuardMissing() {
        IRegionService none = RegionBridge.selectService(false, () -> {
            throw new AssertionError("factory must not run without WorldGuard");
        }, null);
        assertFalse(none.isAvailable());
        assertEquals("WorldGuard not installed", none.define("w", "r", Cuboid.of(0, 0, 0, 1, 1, 1), Map.of(), null, null).message());
        assertFalse(none.setFlag("w", "r", "pvp", "deny", null).ok());
        assertFalse(none.remove("w", "r").ok());
        assertTrue(none.info("w", "r").isEmpty());
        assertTrue(none.flagNames().isEmpty());
        assertFalse(none.isFlag("pvp"));
    }

    @Test
    void adapterLinkageFailureFallsBackToUnavailable() {
        IRegionService broken = RegionBridge.selectService(true, () -> {
            throw new NoClassDefFoundError("com/sk89q/worldguard/WorldGuard");
        }, null);
        assertFalse(broken.isAvailable());
        assertTrue(broken.unavailableReason().contains("NoClassDefFoundError"));
    }
}
