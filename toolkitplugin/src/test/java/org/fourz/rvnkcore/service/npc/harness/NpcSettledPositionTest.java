package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.Location;
import org.bukkit.World;
import org.fourz.rvnkcore.service.region.Cuboid;
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

/**
 * The Dev QA defects of 1.5.100 (#2248): a spec Y one block above the ground. The NPC settles to
 * Y 67, the spec says 68. Apply must not move it back every pass, verify must be clean, and export
 * must keep the zone and apply as NOOP.
 */
class NpcSettledPositionTest {

    private static final String W = "journey";
    private static final double X = -95.5;
    private static final double Z = 2.5;

    private FakeNpcHarness harness;
    private FakeRegionService regions;
    private FakeTerrain terrain;
    private final Set<String> loaded = Set.of(W);
    private final Map<String, World> worlds = new HashMap<>();
    private final List<String> later = new ArrayList<>();

    @BeforeEach
    void setUp() {
        harness = new FakeNpcHarness();
        regions = new FakeRegionService();
        terrain = new FakeTerrain().ground(W, -96, 2, 66); // podzol top at 66: feet stand at 67
        World world = mock(World.class);
        when(world.getName()).thenReturn(W);
        worlds.put(W, world);
    }

    private static NpcSpec qaGuide(double y) {
        return NpcSpec.basic("qa_guide", "QA Guide", W, X, y, Z).withZone(NpcZone.defaults());
    }

    private List<NpcPlanStep> plan(List<NpcSpec> specs, NpcGround.Terrain t) {
        return NpcApplyPlanner.plan(specs, harness.snapshot(), loaded::contains, regions, t);
    }

    private List<NpcDrift> verify(List<NpcSpec> specs, NpcGround.Terrain t) {
        return NpcVerifier.verify(specs, harness.snapshot(), loaded::contains, regions, t);
    }

    private List<NpcSpecExecutor.StepResult> applyAll(List<NpcSpec> specs) {
        NpcSpecExecutor executor = new NpcSpecExecutor(harness, regions,
                (w, x, y, z, yaw, pitch) -> worlds.containsKey(w) ? new Location(worlds.get(w), x, y, z, yaw, pitch) : null,
                null, later::add);
        Map<String, NpcSpec> byKey = new HashMap<>();
        specs.forEach(s -> byKey.put(s.key(), s));
        List<NpcSpecExecutor.StepResult> results = new ArrayList<>();
        for (NpcPlanStep step : plan(specs, harness.terrain())) {
            NpcSpecExecutor.StepResult result = executor.execute(step, byKey.get(step.key()),
                    harness.state(step.key()).orElse(null));
            assertTrue(result.ok(), () -> step.key() + " failed: " + result.lines());
            results.add(result);
        }
        return results;
    }

    private static void assertAllNoop(List<NpcPlanStep> plan) {
        assertTrue(plan.stream().allMatch(s -> s.action() == NpcPlanStep.Action.NOOP), plan::toString);
    }

    // ── defect 1: idempotent with a settled Y ─────────────────────────────────────

    @Test
    void settledOneBelowTheSpecIsNoopAndCleanWithoutTerrain() {
        harness.put("qa_guide", "QA Guide", W, X, 67, Z); // the Dev state: settled 1 below the spec
        List<NpcSpec> specs = List.of(NpcSpec.basic("qa_guide", "QA Guide", W, X, 68, Z));
        assertAllNoop(plan(specs, null));
        List<NpcDrift> drifts = verify(specs, null);
        assertEquals(0, NpcVerifier.problemCount(drifts), drifts::toString);
    }

    @Test
    void snappedStandableYIsInSyncOnlyWithTerrain() {
        // ground 4 below the spec: more than the 1.5 settle tolerance, so only the snap explains it
        FakeTerrain deep = new FakeTerrain().ground(W, -96, 2, 63);
        harness.put("qa_guide", "QA Guide", W, X, 64, Z);
        List<NpcSpec> specs = List.of(NpcSpec.basic("qa_guide", "QA Guide", W, X, 68, Z));

        assertEquals(NpcPlanStep.Action.UPDATE, plan(specs, null).get(0).action(), "4 below without terrain is drift");
        assertAllNoop(plan(specs, deep));
        assertEquals(0, NpcVerifier.problemCount(verify(specs, deep)));
    }

    @Test
    void realDriftIsStillDrift() {
        harness.put("qa_guide", "QA Guide", W, X, 69, Z); // above the spec
        List<NpcSpec> specs = List.of(NpcSpec.basic("qa_guide", "QA Guide", W, X, 68, Z));
        NpcPlanStep above = plan(specs, terrain).get(0);
        assertEquals(NpcPlanStep.Action.UPDATE, above.action());
        assertEquals(NpcField.POSITION, above.changes().get(0).field());
        assertTrue(above.changes().get(0).wanted().contains("(stands at y 67)"), above.changes().toString());

        harness.replace("qa_guide", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), X + 0.6, 67.0, Z,
                0f, 0f, true, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        assertEquals(NpcPlanStep.Action.UPDATE, plan(specs, terrain).get(0).action(), "0.6 off in X");
        assertEquals(1, NpcVerifier.problemCount(verify(specs, terrain)));
    }

    @Test
    void createSnapsToTheGroundAndReportsIt() {
        harness.terrain = terrain;
        List<NpcSpec> specs = List.of(qaGuide(68));

        NpcPlanStep dry = plan(specs, terrain).get(0);
        assertEquals(NpcPlanStep.Action.CREATE, dry.action());
        assertTrue(dry.changes().get(1).wanted().endsWith("(stands at y 67)"), dry.changes().toString());

        List<NpcSpecExecutor.StepResult> results = applyAll(specs);
        assertTrue(results.get(0).lines().contains("position: snapped 68 -> 67"), results.get(0).lines().toString());
        assertEquals(67.0, harness.state("qa_guide").orElseThrow().y(), "created on the ground");
        assertEquals(new NpcZone(2, 3).around(X, 67, Z), regions.info(W, "npc_qa_guide").orElseThrow().bounds(),
                "zone built from the snapped position");

        int writes = harness.writes.size();
        int defines = regions.defines;
        assertAllNoop(plan(specs, terrain));
        assertAllNoop(plan(specs, null));
        applyAll(specs);
        assertEquals(writes, harness.writes.size(), "second apply writes nothing");
        assertEquals(defines, regions.defines);
        assertEquals(0, NpcVerifier.problemCount(verify(specs, terrain)));
        assertEquals(0, NpcVerifier.problemCount(verify(specs, null)));
    }

    @Test
    void moveSnapsToTheGroundAndReportsIt() {
        harness.terrain = terrain;
        harness.put("qa_guide", "QA Guide", W, X + 10, 80, Z);
        List<NpcSpec> specs = List.of(NpcSpec.basic("qa_guide", "QA Guide", W, X, 68, Z));
        List<NpcSpecExecutor.StepResult> results = applyAll(specs);
        String line = results.get(0).lines().get(0);
        assertTrue(line.startsWith("position: moved") && line.endsWith("(snapped 68 -> 67)"), line);
        assertEquals(67.0, harness.state("qa_guide").orElseThrow().y());
        assertAllNoop(plan(specs, terrain));
    }

    @Test
    void noStandableSpotKeepsTheYAndWarns() {
        harness.terrain = new FakeTerrain(); // all air
        List<NpcSpecExecutor.StepResult> results = applyAll(List.of(NpcSpec.basic("qa_guide", "QA Guide", W, X, 68, Z)));
        assertTrue(results.get(0).ok());
        assertTrue(results.get(0).lines().stream().anyMatch(l -> l.startsWith("position: WARNING no standable ground")),
                results.get(0).lines().toString());
        assertEquals(68.0, harness.state("qa_guide").orElseThrow().y(), "requested Y kept");
    }

    @Test
    void zoneBuiltAtTheSpecYAroundASettledNpcIsInSync() {
        // the 1.5.100 Dev state: zone built at feet 68, NPC settled to 67
        harness.put("qa_guide", "QA Guide", W, X, 67, Z);
        NpcSpecExecutor.defineZone(regions, "qa_guide", W, X, 68, Z, NpcZone.defaults(), null);
        List<NpcSpec> specs = List.of(qaGuide(68));
        assertAllNoop(plan(specs, null));
        assertAllNoop(plan(specs, terrain));
        assertEquals(0, NpcVerifier.problemCount(verify(specs, terrain)));
    }

    // ── defect 2: export keeps the zone of a settled NPC ──────────────────────────

    @Test
    void exportKeepsTheZoneOfASettledNpcAndAppliesAsNoop() {
        harness.put("qa_guide", "QA Guide", W, X, 67, Z);
        NpcSpecExecutor.defineZone(regions, "qa_guide", W, X, 68, Z, NpcZone.defaults(), null);

        NpcSpecExporter.Export export = NpcSpecExporter.export(harness.snapshot(), regions, null);
        assertTrue(export.notes().isEmpty(), export.notes().toString());
        List<NpcSpec> parsed = parse(export.yaml());
        assertEquals(NpcZone.defaults(), parsed.get(0).zone(), "zone kept");
        assertEquals(67.0, parsed.get(0).y(), "the live (standing) Y is exported");

        assertAllNoop(plan(parsed, null));
        assertAllNoop(plan(parsed, terrain));
        assertEquals(0, NpcVerifier.problemCount(verify(parsed, terrain)));
    }

    @Test
    void exportAfterASnappedCreateAppliesAsNoop() {
        harness.terrain = terrain;
        applyAll(List.of(qaGuide(68)));
        NpcSpecExporter.Export export = NpcSpecExporter.export(harness.snapshot(), regions, null);
        assertTrue(export.notes().isEmpty(), export.notes().toString());
        List<NpcSpec> parsed = parse(export.yaml());
        assertEquals(NpcZone.defaults(), parsed.get(0).zone());
        int defines = regions.defines;
        assertAllNoop(plan(parsed, terrain));
        applyAll(parsed);
        assertEquals(defines, regions.defines, "export then apply writes no region");
    }

    @Test
    void zoneFarAboveTheNpcIsStillLeftOut() {
        harness.put("qa_guide", "QA Guide", W, X, 67, Z);
        NpcSpecExecutor.defineZone(regions, "qa_guide", W, X, 69, Z, NpcZone.defaults(), null); // 2 above
        NpcSpecExporter.Export export = NpcSpecExporter.export(harness.snapshot(), regions, null);
        assertEquals(1, export.notes().size(), export.notes().toString());
        assertTrue(export.notes().get(0).contains("zone left out"));
    }

    @Test
    void zoneInferToleratesTheSettleOffsetOnly() {
        Cuboid atSpec = new NpcZone(2, 3).around(X, 68, Z);
        assertEquals(new NpcZone(2, 3), NpcZone.infer(atSpec, X, 68, Z), "exact");
        assertEquals(new NpcZone(2, 3), NpcZone.infer(atSpec, X, 67, Z), "NPC settled 1 below the zone feet");
        assertNull(NpcZone.infer(atSpec, X, 66, Z), "2 below is not settling");
        assertNull(NpcZone.infer(atSpec, X, 69, Z), "a zone below the NPC is not its zone");
        assertNull(NpcZone.infer(atSpec, X + 1, 68, Z), "off-centre");
    }

    private static List<NpcSpec> parse(String yaml) {
        NpcSpecParser.Result parsed = new NpcSpecParser(Set.of(W)::contains, m -> true).parse(yaml);
        assertTrue(parsed.ok(), () -> parsed.errors() + "\n" + yaml);
        return parsed.npcs();
    }
}
