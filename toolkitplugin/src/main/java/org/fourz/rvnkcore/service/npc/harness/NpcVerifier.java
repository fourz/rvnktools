package org.fourz.rvnkcore.service.npc.harness;

import org.fourz.rvnkcore.service.region.IRegionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The pure drift check behind {@code /rvnk npc verify} (#2248).
 *
 * <ul>
 *   <li><b>With a spec:</b> per spec key - missing NPC, a key on two NPCs, world not loaded, and
 *       every field {@link NpcDiff} reports (position over 0.5 blocks, world, name, skin, traits,
 *       zone). Then every tagged key the spec does not declare, as ORPHAN.</li>
 *   <li><b>Without a spec:</b> every tagged key must resolve to one NPC with a location in a
 *       loaded world.</li>
 * </ul>
 *
 * <p>DESPAWNED and ZONE_UNCHECKED are informational: a Citizens NPC despawns whenever its chunk
 * unloads, so "not spawned" alone is not drift.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcVerifier {

    private NpcVerifier() {
    }

    /**
     * @param specs       spec entries, or null to check only that every key resolves
     * @param states      every keyed NPC on the server
     * @param worldLoaded true for a loaded world
     * @param regions     region service
     * @return findings, spec keys first in spec order, then orphans sorted by key
     */
    public static List<NpcDrift> verify(List<NpcSpec> specs, List<NpcState> states, Predicate<String> worldLoaded,
                                        IRegionService regions) {
        Map<String, List<NpcState>> byKey = NpcApplyPlanner.index(states);
        List<NpcDrift> drifts = new ArrayList<>();

        if (specs == null) {
            for (Map.Entry<String, List<NpcState>> entry : new java.util.TreeMap<>(byKey).entrySet()) {
                checkResolves(entry.getKey(), entry.getValue(), worldLoaded, drifts);
            }
            return drifts;
        }

        Set<String> declared = new HashSet<>();
        for (NpcSpec spec : specs) {
            declared.add(spec.key());
            List<NpcState> live = byKey.getOrDefault(spec.key(), List.of());
            if (live.isEmpty()) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.MISSING, null,
                        "no NPC carries this key (apply creates it)"));
                continue;
            }
            if (live.size() > 1) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.DUPLICATE, null,
                        "key is on " + live.size() + " NPCs: " + NpcApplyPlanner.ids(live)));
                continue;
            }
            NpcState state = live.get(0);
            if (!worldLoaded.test(spec.world())) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.WORLD_UNLOADED, null,
                        "spec world '" + spec.world() + "' is not loaded; not checked"));
                continue;
            }
            for (NpcChange change : NpcDiff.diff(spec, state, regions)) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.FIELD, change, change.toString()));
            }
            if (spec.zone() != null && (regions == null || !regions.isAvailable())) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.ZONE_UNCHECKED, null,
                        "zone not checked: " + (regions == null ? IRegionService.NOT_INSTALLED : regions.unavailableReason())));
            }
            if (!state.spawned()) {
                drifts.add(new NpcDrift(spec.key(), NpcDrift.Kind.DESPAWNED, null,
                        "#" + state.backingId() + " not spawned (chunk unloaded, or despawned)"));
            }
        }

        for (String key : new java.util.TreeSet<>(byKey.keySet())) {
            if (!declared.contains(key)) {
                List<NpcState> live = byKey.get(key);
                drifts.add(new NpcDrift(key, NpcDrift.Kind.ORPHAN, null,
                        "tagged on " + NpcApplyPlanner.ids(live) + " but not in the spec"));
            }
        }
        return drifts;
    }

    private static void checkResolves(String key, List<NpcState> live, Predicate<String> worldLoaded,
                                      List<NpcDrift> drifts) {
        if (live.size() > 1) {
            drifts.add(new NpcDrift(key, NpcDrift.Kind.DUPLICATE, null,
                    "key is on " + live.size() + " NPCs: " + NpcApplyPlanner.ids(live)));
            return;
        }
        NpcState state = live.get(0);
        if (!state.hasLocation()) {
            drifts.add(new NpcDrift(key, NpcDrift.Kind.NO_LOCATION, null, "#" + state.backingId() + " has no location"));
            return;
        }
        if (state.world() == null || !worldLoaded.test(state.world())) {
            drifts.add(new NpcDrift(key, NpcDrift.Kind.WORLD_UNLOADED, null, "#" + state.backingId() + " world "
                    + (state.world() == null ? "unknown" : "'" + state.world() + "'") + " is not loaded"));
            return;
        }
        if (!state.spawned()) {
            drifts.add(new NpcDrift(key, NpcDrift.Kind.DESPAWNED, null,
                    "#" + state.backingId() + " not spawned (chunk unloaded, or despawned)"));
        }
    }

    /** @return the number of findings that are real drift */
    public static long problemCount(List<NpcDrift> drifts) {
        return drifts.stream().filter(d -> d.kind().isProblem()).count();
    }
}
