package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.service.region.Cuboid;
import org.fourz.rvnkcore.service.region.IRegionService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory {@link IRegionService}: regions keyed by world + id; knows a fixed flag set. */
class FakeRegionService implements IRegionService {

    static final List<String> FLAGS = List.of("build", "greeting", "interact", "mob-spawning", "pvp", "use");

    final Map<String, RegionInfo> regions = new HashMap<>();
    int defines;
    boolean available = true;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public RegionResult define(String world, String id, Cuboid box, Map<String, String> flags, Integer priority,
                               CommandSender sender) {
        if (!available) {
            return RegionResult.fail(NOT_INSTALLED);
        }
        defines++;
        RegionInfo old = regions.get(world + "/" + id);
        Map<String, String> merged = new TreeMap<>(old == null ? Map.of() : old.flags());
        merged.putAll(flags);
        int p = priority != null ? priority : old != null ? old.priority() : 0;
        regions.put(world + "/" + id, new RegionInfo(id, world, "cuboid", box, p, merged, "", ""));
        return new RegionResult(true, old == null, (old == null ? "Created " : "Updated ") + id);
    }

    @Override
    public RegionResult setFlag(String world, String id, String flag, String value, CommandSender sender) {
        RegionInfo old = regions.get(world + "/" + id);
        if (old == null) {
            return RegionResult.fail("no region");
        }
        Map<String, String> flags = new LinkedHashMap<>(old.flags());
        if (value == null) {
            flags.remove(flag);
        } else {
            flags.put(flag, value);
        }
        regions.put(world + "/" + id, new RegionInfo(id, world, old.type(), old.bounds(), old.priority(), flags, "", ""));
        return RegionResult.ok("set");
    }

    @Override
    public RegionResult remove(String world, String id) {
        return regions.remove(world + "/" + id) != null ? RegionResult.ok("removed") : RegionResult.fail("no region");
    }

    @Override
    public Optional<RegionInfo> info(String world, String id) {
        return available ? Optional.ofNullable(regions.get(world + "/" + id)) : Optional.empty();
    }

    @Override
    public List<String> regionIds(String world) {
        return regions.values().stream().filter(r -> r.world().equals(world)).map(RegionInfo::id).sorted().toList();
    }

    @Override
    public List<String> flagNames() {
        return available ? FLAGS : List.of();
    }
}
