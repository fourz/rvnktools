package org.fourz.rvnkcore.service.npc.harness;

import org.fourz.rvnkcore.service.region.IRegionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The pure planner behind {@code /rvnk npc apply} (#2248): spec plus live state in, one
 * {@link NpcPlanStep} per spec key out. It never touches Citizens or WorldGuard, so a dry run and
 * the tests see exactly what a real run would do.
 *
 * <p><b>Idempotent.</b> A key that a live NPC already carries is updated, never created again,
 * whether the harness created it or staff tagged it by hand with {@code /rvnk npc tag} (adoption).
 * Applying the same spec twice gives NOOP for every key on the second pass. With a
 * {@link NpcGround.Terrain} the planner also knows each spec position's standable Y, so an NPC that
 * fell from the spec Y onto the ground is in sync, not a move.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcApplyPlanner {

    private NpcApplyPlanner() {
    }

    /**
     * @param specs       the spec entries, in file order
     * @param states      every keyed NPC on the server
     * @param worldLoaded true for a loaded world
     * @param regions     region service; zones are skipped with a note when unavailable
     * @return one step per spec entry, in spec order
     */
    public static List<NpcPlanStep> plan(List<NpcSpec> specs, List<NpcState> states, Predicate<String> worldLoaded,
                                         IRegionService regions) {
        return plan(specs, states, worldLoaded, regions, null);
    }

    /**
     * @param terrain block reads for the standable-Y snap, or null to compare against the spec Y
     *                and the settle tolerance only
     */
    public static List<NpcPlanStep> plan(List<NpcSpec> specs, List<NpcState> states, Predicate<String> worldLoaded,
                                         IRegionService regions, NpcGround.Terrain terrain) {
        Map<String, List<NpcState>> byKey = index(states);
        boolean zonesAvailable = regions != null && regions.isAvailable();
        List<NpcPlanStep> steps = new ArrayList<>();

        for (NpcSpec spec : specs) {
            List<String> notes = new ArrayList<>();
            List<NpcState> live = byKey.getOrDefault(spec.key(), List.of());

            if (live.size() > 1) {
                notes.add("key is on " + live.size() + " NPCs (" + ids(live) + "); remove the extras by hand first");
                steps.add(new NpcPlanStep(spec.key(), NpcPlanStep.Action.BLOCKED, List.of(), -1, notes));
                continue;
            }
            if (!worldLoaded.test(spec.world())) {
                notes.add("world '" + spec.world() + "' is not loaded; load it first (/world load " + spec.world() + ")");
                steps.add(new NpcPlanStep(spec.key(), NpcPlanStep.Action.BLOCKED, List.of(),
                        live.isEmpty() ? -1 : live.get(0).backingId(), notes));
                continue;
            }
            if (spec.zone() != null && !zonesAvailable) {
                notes.add("zone skipped: " + (regions == null ? IRegionService.NOT_INSTALLED : regions.unavailableReason()));
            }

            Double standY = standY(spec, terrain);
            if (live.isEmpty()) {
                steps.add(new NpcPlanStep(spec.key(), NpcPlanStep.Action.CREATE,
                        NpcDiff.createChanges(spec, regions, standY), -1, notes));
                continue;
            }
            NpcState state = live.get(0);
            List<NpcChange> changes = NpcDiff.diff(spec, state, regions, standY);
            steps.add(new NpcPlanStep(spec.key(), changes.isEmpty() ? NpcPlanStep.Action.NOOP : NpcPlanStep.Action.UPDATE,
                    changes, state.backingId(), notes));
        }
        return steps;
    }

    /** @return the standable Y at the spec position, or null without terrain or when none is found */
    static Double standY(NpcSpec spec, NpcGround.Terrain terrain) {
        return terrain == null ? null : NpcGround.snap(terrain, spec.world(), spec.x(), spec.y(), spec.z()).standY();
    }

    static Map<String, List<NpcState>> index(List<NpcState> states) {
        Map<String, List<NpcState>> byKey = new LinkedHashMap<>();
        for (NpcState state : states) {
            byKey.computeIfAbsent(state.key(), k -> new ArrayList<>()).add(state);
        }
        return byKey;
    }

    static String ids(List<NpcState> states) {
        List<String> ids = new ArrayList<>();
        for (NpcState state : states) {
            ids.add("#" + state.backingId());
        }
        return String.join(", ", ids);
    }
}
