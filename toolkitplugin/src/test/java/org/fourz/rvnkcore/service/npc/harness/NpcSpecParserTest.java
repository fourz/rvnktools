package org.fourz.rvnkcore.service.npc.harness;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec parsing and validation (#2248). */
class NpcSpecParserTest {

    static final Set<String> WORLDS = Set.of("journey", "sotw_city", "sotw_sky_0", "world");
    static final Set<String> MATERIALS = Set.of("lantern", "iron_sword", "book");

    static NpcSpecParser parser() {
        return new NpcSpecParser(WORLDS::contains, MATERIALS::contains);
    }

    static final String FULL = """
            description: test
            npcs:
              guide_ruins:
                name: "Warden Tolla"
                world: sotw_city
                pos: [-92.5, 67, 2.5]
                yaw: 180
                pitch: 0
                skin: Notch
                lookclose: true
                protected: true
                pose: stand
                hold: lantern
                nameplate: on
                zone: { radius: 2, height: 3 }
              guide_min:
                name: Plain
                world: journey
                pos: [1, 2, 3]
            """;

    @Test
    void parsesEveryField() {
        NpcSpecParser.Result result = parser().parse(FULL);
        assertTrue(result.ok(), () -> String.join("\n", result.errors()));
        assertEquals(2, result.npcs().size());
        NpcSpec tolla = result.npcs().get(0);
        assertEquals("guide_ruins", tolla.key());
        assertEquals("Warden Tolla", tolla.name());
        assertEquals("sotw_city", tolla.world());
        assertEquals(-92.5, tolla.x());
        assertEquals(67, tolla.y());
        assertEquals(2.5, tolla.z());
        assertEquals(180f, tolla.yaw());
        assertEquals(0f, tolla.pitch());
        assertEquals("Notch", tolla.skin());
        assertEquals(Boolean.TRUE, tolla.lookClose());
        assertEquals(Boolean.TRUE, tolla.protect());
        assertEquals(NpcPose.STAND, tolla.pose());
        assertEquals("lantern", tolla.hold());
        assertEquals(NpcNameplate.ON, tolla.nameplate(), "YAML 1.1 reads bare 'on' as a boolean; it must still parse");
        assertEquals(new NpcZone(2, 3), tolla.zone());
    }

    @Test
    void omittedOptionalFieldsAreUnmanaged() {
        NpcSpec plain = parser().parse(FULL).npcs().get(1);
        assertNull(plain.yaw());
        assertNull(plain.pitch());
        assertNull(plain.skin());
        assertNull(plain.lookClose());
        assertNull(plain.protect());
        assertNull(plain.pose());
        assertNull(plain.hold());
        assertNull(plain.nameplate());
        assertNull(plain.zone());
    }

    @Test
    void acceptsTextAndBooleanForms() {
        NpcSpecParser.Result result = parser().parse("""
                npcs:
                  a:
                    name: A
                    world: world
                    pos: [0, 64, 0]
                    lookclose: "off"
                    protected: no
                    nameplate: hover
                    pose: sitting
                    hold: minecraft:iron_sword
                    zone: true
                    skin: https://example.org/skin.png
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.errors()));
        NpcSpec a = result.npcs().get(0);
        assertEquals(Boolean.FALSE, a.lookClose());
        assertEquals(Boolean.FALSE, a.protect());
        assertEquals(NpcNameplate.HOVER, a.nameplate());
        assertEquals(NpcPose.SIT, a.pose());
        assertEquals("iron_sword", a.hold());
        assertEquals(NpcZone.defaults(), a.zone());
        assertEquals("https://example.org/skin.png", a.skin());
    }

    @Test
    void holdNoneMeansEmptyHand() {
        NpcSpec a = parser().parse(one("hold: none")).npcs().get(0);
        assertEquals("none", a.hold());
        assertEquals("none", parser().parse(one("hold: air")).npcs().get(0).hold());
    }

    @Test
    void rejectsUnknownWorld() {
        NpcSpecParser.Result result = parser().parse("""
                npcs:
                  a:
                    name: A
                    world: nowhere
                    pos: [0, 64, 0]
                """);
        assertFalse(result.ok());
        assertTrue(result.npcs().isEmpty(), "a spec with errors yields no NPCs");
        assertTrue(result.errors().get(0).startsWith("npcs.a.world: unknown world 'nowhere'"), result.errors().toString());
    }

    @Test
    void rejectsBadMaterial() {
        NpcSpecParser.Result result = parser().parse(one("hold: diamond_shovelz"));
        assertFalse(result.ok());
        assertTrue(result.errors().get(0).contains("npcs.a.hold: unknown or non-item material"), result.errors().toString());
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertError("pos: [0, 5000, 0]", "npcs.a.pos.y: out of range");
        assertError("pos: [40000000, 64, 0]", "npcs.a.pos.x: out of range");
        assertError("yaw: 400", "npcs.a.yaw: out of range");
        assertError("pitch: -91", "npcs.a.pitch: out of range");
        assertError("zone: { radius: 50 }", "npcs.a.zone: radius must be");
        assertError("zone: { radius: 2, height: 0 }", "npcs.a.zone: height must be");
    }

    @Test
    void rejectsMalformedValues() {
        assertError("pose: lying", "npcs.a.pose: must be stand, sit or sneak");
        assertError("nameplate: maybe", "npcs.a.nameplate: must be on, off or hover");
        assertError("lookclose: sometimes", "npcs.a.lookclose: must be true/false or on/off");
        assertError("skin: \"not a name!\"", "npcs.a.skin: must be a player name");
        assertError("zone: { radius: 2, depth: 3 }", "npcs.a.zone.depth: unknown field");
        assertError("yaw: north", "npcs.a.yaw: must be a number");
    }

    @Test
    void rejectsUnknownFieldsSoTyposCannotHideDrift() {
        assertError("lookClose: true", "npcs.a.lookClose: unknown field");
    }

    @Test
    void rejectsBadStructure() {
        assertFalse(parser().parse("npcs: {}").ok());
        assertFalse(parser().parse("other: 1").ok());
        assertTrue(parser().parse("npcs: [").errors().get(0).startsWith("YAML syntax"));
        assertErrorText("""
                npcs:
                  Bad Key:
                    name: A
                    world: world
                    pos: [0, 64, 0]
                """, "invalid key");
        assertErrorText("""
                npcs:
                  a:
                    world: world
                    pos: [0, 64]
                """, "npcs.a.name: required");
        assertErrorText("""
                npcs:
                  a:
                    name: A
                    world: world
                    pos: [0, 64]
                """, "npcs.a.pos: must be a list of three numbers");
        assertErrorText("""
                npcs:
                  a: hello
                """, "npcs.a: must be a map");
        assertErrorText("""
                extra: 1
                npcs:
                  a:
                    name: A
                    world: world
                    pos: [0, 64, 0]
                """, "extra: unknown top-level field");
    }

    @Test
    void reportsEveryErrorAtOnce() {
        NpcSpecParser.Result result = parser().parse("""
                npcs:
                  a:
                    name: A
                    world: nowhere
                    pos: [0, 9999, 0]
                    hold: bedrockz
                """);
        assertEquals(3, result.errors().size(), result.errors().toString());
    }

    @Test
    void tfahSampleInTheDocsParses() throws java.io.IOException {
        String doc = java.nio.file.Files.readString(java.nio.file.Path.of("docs/api/npc-harness.md"));
        int section = doc.indexOf("## Sample spec: TFAH guides");
        assertTrue(section >= 0, "sample section missing from docs/api/npc-harness.md");
        int start = doc.indexOf("```yaml", section) + "```yaml".length();
        String yaml = doc.substring(start, doc.indexOf("```", start));
        NpcSpecParser.Result result = new NpcSpecParser(
                Set.of("skyblock", "world", "alphac", "sotw_city", "sotw_sky_0", "sotw_deep_0")::contains,
                MATERIALS::contains).parse(yaml);
        assertTrue(result.ok(), () -> String.join("\n", result.errors()));
        assertEquals(List.of("wayfarer_greeter", "guide_koz", "guide_sol", "guide_ruins", "guide_aether", "guide_cavern"),
                result.npcs().stream().map(NpcSpec::key).toList());
        NpcSpec vance = result.npcs().get(5);
        assertEquals("Hollowkeeper Vance", vance.name());
        assertEquals(243, vance.y());
        assertEquals(NpcZone.defaults(), vance.zone());
    }

    @Test
    void yawIsFolded() {
        assertEquals(-90f, NpcSpecParser.normalizeYaw(270f));
        assertEquals(180f, NpcSpecParser.normalizeYaw(-180f));
        assertEquals(0f, NpcSpecParser.normalizeYaw(360f));
    }

    private static String one(String extraLine) {
        return "npcs:\n  a:\n    name: A\n    world: world\n    pos: [0, 64, 0]\n    " + extraLine + "\n";
    }

    private static void assertError(String extraLine, String expectedStart) {
        String yaml = extraLine.startsWith("pos:")
                ? "npcs:\n  a:\n    name: A\n    world: world\n    " + extraLine + "\n"
                : one(extraLine);
        assertErrorText(yaml, expectedStart);
    }

    private static void assertErrorText(String yaml, String expected) {
        List<String> errors = parser().parse(yaml).errors();
        assertTrue(errors.stream().anyMatch(e -> e.contains(expected)), "expected '" + expected + "' in " + errors);
    }
}
