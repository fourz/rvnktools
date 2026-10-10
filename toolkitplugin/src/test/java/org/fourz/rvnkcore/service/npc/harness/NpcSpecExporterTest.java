package org.fourz.rvnkcore.service.npc.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Export round trip and the spec store (#2248). */
class NpcSpecExporterTest {

    @Test
    void exportParsesBackAndAppliesAsNoop() {
        FakeNpcHarness harness = new FakeNpcHarness();
        FakeRegionService regions = new FakeRegionService();
        harness.put("wayfarer_greeter", "Wayfarer Ines", "journey", 10.9, 65, 31);
        harness.put("guide_ruins", "Warden Tolla", "sotw_city", -92.0, 67, 2.0);
        harness.replace("guide_ruins", s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(),
                179.96f, -2.04f, true, "Notch", true, true, NpcPose.SIT, "lantern", NpcNameplate.HOVER));
        NpcSpecExecutor.defineZone(regions, "guide_ruins", "sotw_city", -92, 67, 2, new NpcZone(2, 3), null);

        NpcSpecExporter.Export export = NpcSpecExporter.export(harness.snapshot(), regions, "test export");
        assertEquals(List.of("guide_ruins", "wayfarer_greeter"), export.exported());
        assertTrue(export.notes().isEmpty(), export.notes().toString());

        NpcSpecParser.Result parsed = new NpcSpecParser(Set.of("journey", "sotw_city")::contains,
                Set.of("lantern")::contains).parse(export.yaml());
        assertTrue(parsed.ok(), () -> parsed.errors() + "\n" + export.yaml());
        NpcSpec tolla = parsed.npcs().get(0);
        assertEquals("Warden Tolla", tolla.name());
        assertEquals(NpcNameplate.HOVER, tolla.nameplate());
        assertEquals(NpcPose.SIT, tolla.pose());
        assertEquals(new NpcZone(2, 3), tolla.zone());
        assertEquals(180f, tolla.yaw(), 0.001);

        List<NpcPlanStep> plan = NpcApplyPlanner.plan(parsed.npcs(), harness.snapshot(),
                Set.of("journey", "sotw_city")::contains, regions);
        assertTrue(plan.stream().allMatch(s -> s.action() == NpcPlanStep.Action.NOOP), plan::toString);
    }

    @Test
    void exportSkipsNpcsWithoutLocationAndDuplicates() {
        FakeNpcHarness harness = new FakeNpcHarness();
        harness.put("a", "A", "journey", 0, 65, 0);
        harness.put("a", "A2", "journey", 5, 65, 0);
        harness.put("b", "B", "journey", 0, 65, 0);
        harness.replace("b", s -> new NpcState(s.key(), s.backingId(), s.name(), null, null, null, null, 0f, 0f, false,
                null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        NpcSpecExporter.Export export = NpcSpecExporter.export(harness.snapshot(), new FakeRegionService(), null);
        assertEquals(List.of("a"), export.exported());
        assertEquals(2, export.notes().size(), export.notes().toString());
    }

    @Test
    void storeRejectsPathsAndKeepsFiles(@TempDir File dir) throws IOException {
        NpcSpecStore store = new NpcSpecStore(new File(dir, "npc"));
        assertEquals("tfah", NpcSpecStore.normalizeName("tfah.yml"));
        assertNull(NpcSpecStore.normalizeName("../secrets"));
        assertNull(NpcSpecStore.normalizeName("a/b"));
        assertNull(store.file("..\\x"));
        assertEquals(List.of(), store.list());

        File written = store.write("tfah", "npcs: {}\n", false);
        assertTrue(written.isFile());
        assertEquals(List.of("tfah"), store.list());
        assertEquals("npcs: {}\n", store.read("tfah.yml"));
        IOException exists = assertThrows(IOException.class, () -> store.write("tfah", "x", false));
        assertTrue(exists.getMessage().contains("--force"));
        store.write("tfah", "y", true);
        assertEquals("y", store.read("tfah"));
        assertThrows(IOException.class, () -> store.read("missing"));
        assertThrows(IOException.class, () -> store.read("../tfah"));
    }
}
