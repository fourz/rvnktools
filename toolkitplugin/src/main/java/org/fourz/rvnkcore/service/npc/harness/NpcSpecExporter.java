package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.service.region.IRegionService.RegionInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes live keyed NPCs as a spec file (#2248), so a server's existing NPCs become the starting
 * spec: export, review, then apply. The output parses with {@link NpcSpecParser} and applying it at
 * once is all NOOP.
 *
 * <p>Positions are rounded to 2 decimals and angles to 1, which is inside verify's tolerance.
 * A zone is exported only when region {@code npc_<key>} has the zone shape around the NPC. The
 * radius and height come from the region bounds, and the zone's feet may sit up to
 * {@link NpcGround#SETTLE_TOLERANCE} above the NPC's feet: a zone built at the spec Y around an NPC
 * that then fell and settled is still its zone. The apply diff accepts the same offset, so
 * export then apply stays all NOOP.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcSpecExporter {

    private NpcSpecExporter() {
    }

    /**
     * @param yaml     the spec text
     * @param exported keys written
     * @param notes    keys skipped or fields left out, with the reason
     */
    public record Export(String yaml, List<String> exported, List<String> notes) {
    }

    public static Export export(List<NpcState> states, IRegionService regions, String description) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "RVNK NPC spec (#2248). Apply: /rvnk npc apply <name> [--dry-run]. Verify: /rvnk npc verify <name>.",
                "Fields: name, world, pos [x, y, z], yaw, pitch, skin, lookclose, protected, pose, hold,",
                "nameplate, zone { radius, height }. Leave a field out to leave it unmanaged."));
        if (description != null && !description.isBlank()) {
            yaml.set("description", description);
        }
        ConfigurationSection npcs = yaml.createSection("npcs");

        List<String> exported = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        List<NpcState> sorted = new ArrayList<>(states);
        sorted.sort(Comparator.comparing(NpcState::key).thenComparingInt(NpcState::backingId));

        for (NpcState state : sorted) {
            if (!seen.add(state.key())) {
                notes.add(state.key() + ": also on #" + state.backingId() + " - only the lowest id was exported");
                continue;
            }
            if (!state.hasLocation() || state.world() == null) {
                notes.add(state.key() + ": #" + state.backingId() + " has no location or its world is not loaded - skipped");
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", state.name());
            entry.put("world", state.world());
            entry.put("pos", List.of(round(state.x(), 100), round(state.y(), 100), round(state.z(), 100)));
            entry.put("yaw", round(state.yaw(), 10));
            entry.put("pitch", round(state.pitch(), 10));
            if (state.skin() != null) {
                entry.put("skin", state.skin());
            }
            entry.put("lookclose", state.lookClose());
            entry.put("protected", state.protect());
            entry.put("pose", (state.pose() == null ? NpcPose.STAND : state.pose()).id());
            if (state.hold() != null) {
                entry.put("hold", state.hold());
            }
            entry.put("nameplate", (state.nameplate() == null ? NpcNameplate.ON : state.nameplate()).id());

            if (regions != null && regions.isAvailable()) {
                Optional<RegionInfo> zone = regions.info(state.world(), NpcZone.regionId(state.key()));
                if (zone.isPresent()) {
                    NpcZone inferred = NpcZone.infer(zone.get().bounds(), state.x(), state.y(), state.z());
                    if (inferred != null) {
                        Map<String, Object> z = new LinkedHashMap<>();
                        z.put("radius", inferred.radius());
                        z.put("height", inferred.height());
                        entry.put("zone", z);
                    } else {
                        notes.add(state.key() + ": region " + NpcZone.regionId(state.key())
                                + " is not a zone shape around the NPC - zone left out");
                    }
                }
            }
            npcs.createSection(state.key(), entry);
            exported.add(state.key());
        }
        return new Export(yaml.saveToString(), exported, notes);
    }

    static double round(double value, int scale) {
        double rounded = Math.round(value * scale) / (double) scale;
        return rounded == 0 ? 0.0 : rounded; // no -0.0 in the file
    }
}
