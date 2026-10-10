package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The apply planner and executor (#2248): create / update / noop, and idempotent on a second pass. */
class NpcApplyPlannerTest {

    private FakeNpcHarness harness;
    private FakeRegionService regions;
    private final Set<String> loaded = Set.of("journey", "sotw_city");
    private final Map<String, World> worlds = new HashMap<>();
    private final List<String> later = new ArrayList<>();

    @BeforeEach
    void setUp() {
        harness = new FakeNpcHarness();
        regions = new FakeRegionService();
        for (String name : loaded) {
            World world = mock(World.class);
            when(world.getName()).thenReturn(name);
            worlds.put(name, world);
        }
    }

    private List<NpcPlanStep> plan(List<NpcSpec> specs) {
        return NpcApplyPlanner.plan(specs, harness.snapshot(), loaded::contains, regions);
    }

    private NpcSpecExecutor executor() {
        return new NpcSpecExecutor(harness, regions,
                (w, x, y, z, yaw, pitch) -> worlds.containsKey(w) ? new Location(worlds.get(w), x, y, z, yaw, pitch) : null,
                null, later::add);
    }

    private void applyAll(List<NpcSpec> specs) {
        Map<String, NpcSpec> byKey = new HashMap<>();
        specs.forEach(s -> byKey.put(s.key(), s));
        for (NpcPlanStep step : plan(specs)) {
            NpcState current = harness.state(step.key()).orElse(null);
            NpcSpecExecutor.StepResult result = executor().execute(step, byKey.get(step.key()), current);
            assertTrue(result.ok(), () -> step.key() + " failed: " + result.lines());
        }
    }

    private static NpcSpec full(String key, String world, double x, double y, double z) {
        return new NpcSpec(key, "Warden " + key, world, x, y, z, 90f, 0f, "Notch", true, true, NpcPose.SIT,
                "lantern", NpcNameplate.HOVER, NpcZone.defaults());
    }

    @Test
    void missingKeyPlansCreateWithEveryManagedField() {
        List<NpcPlanStep> steps = plan(List.of(full("guide_a", "journey", 10.5, 65, 20.5)));
        assertEquals(1, steps.size());
        NpcPlanStep step = steps.get(0);
        assertEquals(NpcPlanStep.Action.CREATE, step.action());
        List<NpcField> fields = step.changes().stream().map(NpcChange::field).toList();
        assertEquals(List.of(NpcField.NAME, NpcField.POSITION, NpcField.SKIN, NpcField.LOOKCLOSE, NpcField.PROTECTED,
                NpcField.POSE, NpcField.HOLD, NpcField.NAMEPLATE, NpcField.ZONE), fields);
    }

    @Test
    void applyTwiceIsIdempotent() {
        List<NpcSpec> specs = List.of(full("guide_a", "journey", 10.5, 65, 20.5),
                NpcSpec.basic("guide_b", "Plain", "sotw_city", -92, 67, 2));
        applyAll(specs);
        assertEquals(2, harness.npcs.size());
        assertTrue(regions.info("journey", "npc_guide_a").isPresent(), "zone created");
        assertEquals(NpcZone.NEW_ZONE_PRIORITY, regions.info("journey", "npc_guide_a").get().priority());

        int writes = harness.writes.size();
        int defines = regions.defines;
        List<NpcPlanStep> second = plan(specs);
        assertTrue(second.stream().allMatch(s -> s.action() == NpcPlanStep.Action.NOOP),
                () -> "second pass must be all NOOP: " + second);
        applyAll(specs);
        assertEquals(writes, harness.writes.size(), "a NOOP pass writes nothing to the NPC plugin");
        assertEquals(defines, regions.defines, "a NOOP pass writes no region");
        assertEquals(2, harness.npcs.size(), "never duplicated");
    }

    @Test
    void handTaggedNpcIsAdoptedNotRecreated() {
        harness.put("guide_koz", "Warden Halvard", "journey", -133.4, 78, -4.1);
        List<NpcPlanStep> steps = plan(List.of(new NpcSpec("guide_koz", "Warden Halvard", "journey", -133.4, 78, -4.1,
                null, null, null, true, null, null, null, null, null)));
        assertEquals(NpcPlanStep.Action.UPDATE, steps.get(0).action());
        assertEquals(List.of(NpcField.LOOKCLOSE), steps.get(0).changes().stream().map(NpcChange::field).toList());
        assertEquals(1, steps.get(0).backingId());
    }

    @Test
    void matchingNpcIsNoop() {
        harness.put("guide_koz", "Warden Halvard", "journey", -133.4, 78, -4.1);
        assertEquals(NpcPlanStep.Action.NOOP,
                plan(List.of(NpcSpec.basic("guide_koz", "Warden Halvard", "journey", -133.4, 78, -4.1))).get(0).action());
    }

    @Test
    void positionToleranceIsHalfABlock() {
        harness.put("k", "K", "journey", 10.3, 65, 20.3);
        assertEquals(NpcPlanStep.Action.NOOP,
                plan(List.of(NpcSpec.basic("k", "K", "journey", 10.0, 65, 20.0))).get(0).action(), "0.42 off is in tolerance");
        NpcPlanStep moved = plan(List.of(NpcSpec.basic("k", "K", "journey", 11.0, 65, 20.0))).get(0);
        assertEquals(NpcPlanStep.Action.UPDATE, moved.action());
        assertEquals(NpcField.POSITION, moved.changes().get(0).field());
    }

    @Test
    void worldChangeIsAWorldUpdate() {
        harness.put("k", "K", "sotw_city", 0, 65, 0);
        NpcPlanStep step = plan(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0))).get(0);
        assertEquals(List.of(NpcField.WORLD), step.changes().stream().map(NpcChange::field).toList());
        applyAll(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0)));
        assertEquals("journey", harness.state("k").orElseThrow().world());
    }

    @Test
    void yawIsIgnoredWhileLookCloseIsOn() {
        harness.put("k", "K", "journey", 0, 65, 0);
        harness.replace("k", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), 37f, 5f,
                true, null, true, true, NpcPose.STAND, null, NpcNameplate.ON));
        NpcSpec spec = new NpcSpec("k", "K", "journey", 0, 65, 0, 180f, 0f, null, true, null, null, null, null, null);
        assertEquals(NpcPlanStep.Action.NOOP, plan(List.of(spec)).get(0).action(), "LookClose turns the head; yaw is not drift");

        NpcSpec still = new NpcSpec("k", "K", "journey", 0, 65, 0, 180f, 0f, null, false, null, null, null, null, null);
        List<NpcField> fields = plan(List.of(still)).get(0).changes().stream().map(NpcChange::field).toList();
        assertTrue(fields.contains(NpcField.POSITION), "with LookClose off, a 143-degree turn is drift: " + fields);
    }

    @Test
    void unloadedWorldBlocksTheKeyOnly() {
        List<NpcPlanStep> steps = plan(List.of(NpcSpec.basic("a", "A", "sotw_deep_0", 6, 243, 8),
                NpcSpec.basic("b", "B", "journey", 0, 65, 0)));
        assertEquals(NpcPlanStep.Action.BLOCKED, steps.get(0).action());
        assertTrue(steps.get(0).notes().get(0).contains("not loaded"));
        assertEquals(NpcPlanStep.Action.CREATE, steps.get(1).action());
    }

    @Test
    void keyOnTwoNpcsIsBlocked() {
        harness.put("k", "K", "journey", 0, 65, 0);
        harness.put("k", "K", "journey", 5, 65, 0);
        NpcPlanStep step = plan(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0))).get(0);
        assertEquals(NpcPlanStep.Action.BLOCKED, step.action());
        assertTrue(step.notes().get(0).contains("#1, #2"));
    }

    @Test
    void zoneDriftIsDetectedAndRepaired() {
        harness.put("k", "K", "journey", 10.5, 65, 20.5);
        NpcSpec spec = NpcSpec.basic("k", "K", "journey", 10.5, 65, 20.5).withZone(new NpcZone(2, 3));
        NpcPlanStep step = plan(List.of(spec)).get(0);
        assertEquals(List.of(NpcField.ZONE), step.changes().stream().map(NpcChange::field).toList());
        assertEquals("missing", step.changes().get(0).current());

        applyAll(List.of(spec));
        assertEquals(NpcPlanStep.Action.NOOP, plan(List.of(spec)).get(0).action());

        regions.setFlag("journey", "npc_k", "interact", null, null); // someone cleared a flag by hand
        NpcPlanStep flags = plan(List.of(spec)).get(0);
        assertEquals(NpcPlanStep.Action.UPDATE, flags.action());
        assertTrue(flags.changes().get(0).current().contains("interact=unset"), flags.changes().toString());
    }

    @Test
    void zoneIsSkippedWithANoteWithoutWorldGuard() {
        regions.available = false;
        harness.put("k", "K", "journey", 10.5, 65, 20.5);
        NpcSpec spec = NpcSpec.basic("k", "K", "journey", 10.5, 65, 20.5).withZone(NpcZone.defaults());
        NpcPlanStep step = plan(List.of(spec)).get(0);
        assertEquals(NpcPlanStep.Action.NOOP, step.action());
        assertTrue(step.notes().get(0).contains("WorldGuard not installed"), step.notes().toString());
    }

    @Test
    void planDoesNotWrite() {
        plan(List.of(full("guide_a", "journey", 10.5, 65, 20.5)));
        assertTrue(harness.writes.isEmpty(), "a dry run (plan only) must not touch the NPC plugin");
        assertEquals(0, regions.defines);
    }

    @Test
    void zoneShapeAroundTheNpc() {
        assertEquals(org.fourz.rvnkcore.service.region.Cuboid.of(-94, 67, 0, -90, 69, 4),
                new NpcZone(2, 3).around(-92, 67, 2));
        assertEquals(org.fourz.rvnkcore.service.region.Cuboid.of(-94, 67, 0, -90, 69, 4),
                new NpcZone(2, 3).around(-91.5, 67.2, 2.9), "block coordinates, floored");
        assertEquals(new NpcZone(2, 3), NpcZone.infer(new NpcZone(2, 3).around(5, 64, 5), 5, 64, 5));
        assertNull(NpcZone.infer(org.fourz.rvnkcore.service.region.Cuboid.of(0, 0, 0, 3, 3, 3), 5, 64, 5));
    }
}
