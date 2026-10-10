package org.fourz.rvnkcore.service.npc.harness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The verify diff (#2248). */
class NpcVerifierTest {

    private FakeNpcHarness harness;
    private FakeRegionService regions;
    private final Set<String> loaded = Set.of("journey", "sotw_city");

    @BeforeEach
    void setUp() {
        harness = new FakeNpcHarness();
        regions = new FakeRegionService();
    }

    private List<NpcDrift> verify(List<NpcSpec> specs) {
        return NpcVerifier.verify(specs, harness.snapshot(), loaded::contains, regions);
    }

    private static List<NpcDrift.Kind> kinds(List<NpcDrift> drifts) {
        return drifts.stream().map(NpcDrift::kind).toList();
    }

    @Test
    void matchingSpecIsClean() {
        harness.put("guide_ruins", "Warden Tolla", "sotw_city", -92, 67, 2);
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("guide_ruins", "Warden Tolla", "sotw_city", -92, 67, 2)));
        assertEquals(List.of(), drifts);
        assertEquals(0, NpcVerifier.problemCount(drifts));
    }

    @Test
    void manualMoveShowsAsPositionDrift() {
        harness.put("guide_ruins", "Warden Tolla", "sotw_city", -92, 67, 2);
        harness.replace("guide_ruins", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), -89.0, 67.0, 2.0,
                0f, 0f, true, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("guide_ruins", "Warden Tolla", "sotw_city", -92, 67, 2)));
        assertEquals(List.of(NpcDrift.Kind.FIELD), kinds(drifts));
        assertEquals(NpcField.POSITION, drifts.get(0).change().field());
        assertTrue(drifts.get(0).detail().contains("(3.00 off)"), drifts.get(0).detail());
    }

    @Test
    void reportsEveryDifferingField() {
        harness.put("k", "Old Name", "journey", 0, 65, 0);
        NpcSpec spec = new NpcSpec("k", "New Name", "journey", 0, 65, 0, null, null, "Notch", true, false,
                NpcPose.SNEAK, "lantern", NpcNameplate.OFF, NpcZone.defaults());
        List<NpcField> fields = verify(List.of(spec)).stream().map(d -> d.change().field()).toList();
        assertEquals(List.of(NpcField.NAME, NpcField.SKIN, NpcField.LOOKCLOSE, NpcField.PROTECTED, NpcField.POSE,
                NpcField.HOLD, NpcField.NAMEPLATE, NpcField.ZONE), fields);
    }

    @Test
    void otherWorldIsWorldDrift() {
        harness.put("k", "K", "sotw_city", 0, 65, 0);
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0)));
        assertEquals(NpcField.WORLD, drifts.get(0).change().field());
    }

    @Test
    void missingNpcAndOrphanKey() {
        harness.put("stray", "Stray", "journey", 0, 65, 0);
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("guide_sol", "Keeper Soli", "journey", 1, 65, 1)));
        assertEquals(List.of(NpcDrift.Kind.MISSING, NpcDrift.Kind.ORPHAN), kinds(drifts));
        assertEquals("guide_sol", drifts.get(0).key());
        assertEquals("stray", drifts.get(1).key());
        assertEquals(2, NpcVerifier.problemCount(drifts));
    }

    @Test
    void duplicateKey() {
        harness.put("k", "K", "journey", 0, 65, 0);
        harness.put("k", "K", "journey", 9, 65, 0);
        assertEquals(List.of(NpcDrift.Kind.DUPLICATE), kinds(verify(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0)))));
        assertEquals(List.of(NpcDrift.Kind.DUPLICATE), kinds(verify(null)));
    }

    @Test
    void despawnedIsInformationalOnly() {
        harness.put("k", "K", "journey", 0, 65, 0);
        harness.replace("k", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), 0f, 0f,
                false, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0)));
        assertEquals(List.of(NpcDrift.Kind.DESPAWNED), kinds(drifts));
        assertEquals(0, NpcVerifier.problemCount(drifts), "an unloaded chunk is not drift");
    }

    @Test
    void unloadedSpecWorldIsNotCompared() {
        harness.put("k", "K", "sotw_deep_0", 6, 243, 8);
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("k", "Other", "sotw_deep_0", 0, 0, 0)));
        assertEquals(List.of(NpcDrift.Kind.WORLD_UNLOADED), kinds(drifts));
    }

    @Test
    void zoneUncheckedWithoutWorldGuard() {
        regions.available = false;
        harness.put("k", "K", "journey", 0, 65, 0);
        List<NpcDrift> drifts = verify(List.of(NpcSpec.basic("k", "K", "journey", 0, 65, 0).withZone(NpcZone.defaults())));
        assertEquals(List.of(NpcDrift.Kind.ZONE_UNCHECKED), kinds(drifts));
        assertEquals(0, NpcVerifier.problemCount(drifts));
    }

    @Test
    void zoneBoundsDriftAfterTheNpcMoves() {
        harness.put("k", "K", "journey", 0, 65, 0);
        NpcSpec spec = NpcSpec.basic("k", "K", "journey", 0, 65, 0).withZone(NpcZone.defaults());
        NpcSpecExecutor.defineZone(regions, "k", "journey", 0, 65, 0, NpcZone.defaults(), null);
        assertEquals(List.of(), verify(List.of(spec)));
        NpcSpec moved = spec.withPosition("journey", 10, 65, 0);
        harness.replace("k", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), 10.0, 65.0, 0.0, 0f, 0f,
                true, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        List<NpcDrift> drifts = verify(List.of(moved));
        assertEquals(1, drifts.size());
        assertEquals(NpcField.ZONE, drifts.get(0).change().field());
    }

    @Test
    void withoutSpecEveryKeyMustResolve() {
        harness.put("ok", "Ok", "journey", 0, 65, 0);
        harness.put("gone", "Gone", "journey", 0, 65, 0);
        harness.replace("gone", s -> new NpcState(s.key(), s.backingId(), s.name(), null, null, null, null, 0f, 0f,
                false, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        harness.put("far", "Far", "sotw_deep_0", 6, 243, 8);
        List<NpcDrift> drifts = verify(null);
        assertEquals(List.of(NpcDrift.Kind.WORLD_UNLOADED, NpcDrift.Kind.NO_LOCATION), kinds(drifts));
        assertEquals(List.of("far", "gone"), drifts.stream().map(NpcDrift::key).toList());
    }
}
