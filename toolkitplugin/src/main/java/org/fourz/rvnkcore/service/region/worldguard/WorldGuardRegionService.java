package org.fourz.rvnkcore.service.region.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.service.region.Cuboid;
import org.fourz.rvnkcore.service.region.IRegionService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * {@link IRegionService} backed by WorldGuard 7 (#2248).
 *
 * <p>Regions are built from explicit corners with {@link ProtectedCuboidRegion}, so no WorldEdit
 * selection is needed and the console can run every verb. Flag values go through each flag's own
 * {@code parseInput}, the same parser {@code /rg flag} uses, so the accepted syntax is WorldGuard's.
 * Every write ends with {@link RegionManager#saveChanges()}; when the save fails the change still
 * stands in memory and WorldGuard's autosave writes it later, and the result says so.</p>
 *
 * <p>All flags of a {@code define} are parsed onto the new region object BEFORE it replaces the old
 * one, so a bad flag value leaves the existing region untouched.</p>
 *
 * @since 1.5.100-alpha
 */
public class WorldGuardRegionService implements IRegionService {

    private final Consumer<String> warn;

    public WorldGuardRegionService(Consumer<String> warn) {
        this.warn = warn;
    }

    @Override
    public boolean isAvailable() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
                    && WorldGuard.getInstance().getPlatform() != null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public RegionResult define(String worldName, String id, Cuboid box, Map<String, String> flags, Integer priority,
                               CommandSender sender) {
        Lookup lookup = lookup(worldName);
        if (lookup.error != null) {
            return RegionResult.fail(lookup.error);
        }
        RegionManager manager = lookup.manager;
        ProtectedRegion existing = manager.getRegion(id);
        if (existing != null && existing.getType() != null
                && "global".equalsIgnoreCase(existing.getType().getName())) {
            return RegionResult.fail("'" + id + "' is a global region and cannot be redefined as a cuboid.");
        }

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(id,
                BlockVector3.at(box.minX(), box.minY(), box.minZ()),
                BlockVector3.at(box.maxX(), box.maxY(), box.maxZ()));
        if (existing != null) {
            region.copyFrom(existing); // flags, owners, members, priority, parent
        }
        if (priority != null) {
            region.setPriority(priority);
        }

        Actor actor = actor(sender);
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            String error = applyFlag(region, entry.getKey(), entry.getValue(), actor);
            if (error != null) {
                return RegionResult.fail(error + (existing != null ? " Region left unchanged." : " Region not created."));
            }
        }

        manager.addRegion(region); // replaces a region with the same id
        String saved = save(manager, worldName);
        String verb = existing == null ? "Created" : "Updated";
        return new RegionResult(true, existing == null, verb + " region '" + id + "' in " + worldName + ": " + box
                + (flags.isEmpty() ? "" : ", flags " + flags) + saved);
    }

    @Override
    public RegionResult setFlag(String worldName, String id, String flag, String value, CommandSender sender) {
        Lookup lookup = lookup(worldName);
        if (lookup.error != null) {
            return RegionResult.fail(lookup.error);
        }
        ProtectedRegion region = lookup.manager.getRegion(id);
        if (region == null) {
            return RegionResult.fail("No region '" + id + "' in world " + worldName + ".");
        }
        String error = applyFlag(region, flag, value, actor(sender));
        if (error != null) {
            return RegionResult.fail(error);
        }
        String saved = save(lookup.manager, worldName);
        return RegionResult.ok(value == null
                ? "Cleared flag " + flag + " on '" + id + "'." + saved
                : "Set flag " + flag + " on '" + id + "' to " + display(region.getFlags().get(flagOf(flag))) + "." + saved);
    }

    @Override
    public RegionResult remove(String worldName, String id) {
        Lookup lookup = lookup(worldName);
        if (lookup.error != null) {
            return RegionResult.fail(lookup.error);
        }
        Set<ProtectedRegion> removed = lookup.manager.removeRegion(id, RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        if (removed == null || removed.isEmpty()) {
            return RegionResult.fail("No region '" + id + "' in world " + worldName + ".");
        }
        String saved = save(lookup.manager, worldName);
        return RegionResult.ok("Removed region '" + id + "' from " + worldName + "." + saved);
    }

    @Override
    public Optional<RegionInfo> info(String worldName, String id) {
        Lookup lookup = lookup(worldName);
        if (lookup.error != null) {
            return Optional.empty();
        }
        ProtectedRegion region = lookup.manager.getRegion(id);
        if (region == null) {
            return Optional.empty();
        }
        Map<String, String> flags = new TreeMap<>();
        for (Map.Entry<Flag<?>, Object> entry : region.getFlags().entrySet()) {
            flags.put(entry.getKey().getName(), display(entry.getValue()));
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        Cuboid bounds = min == null || max == null ? null
                : Cuboid.of(min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ());
        String type = region.getType() == null ? "unknown" : region.getType().getName();
        return Optional.of(new RegionInfo(region.getId(), worldName, type, bounds, region.getPriority(), flags,
                domain(region.getOwners()), domain(region.getMembers())));
    }

    @Override
    public List<String> regionIds(String worldName) {
        Lookup lookup = lookup(worldName);
        if (lookup.error != null) {
            return List.of();
        }
        return List.copyOf(new TreeSet<>(lookup.manager.getRegions().keySet()));
    }

    @Override
    public List<String> flagNames() {
        try {
            TreeSet<String> names = new TreeSet<>();
            for (Flag<?> flag : WorldGuard.getInstance().getFlagRegistry().getAll()) {
                names.add(flag.getName());
            }
            return List.copyOf(names);
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    @Override
    public boolean isFlag(String name) {
        return name != null && flagOf(name) != null;
    }

    @Override
    public boolean isStateFlag(String name) {
        return name != null && flagOf(name) instanceof StateFlag;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private record Lookup(RegionManager manager, String error) {
    }

    private Lookup lookup(String worldName) {
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return new Lookup(null, "World '" + worldName + "' is not loaded.");
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return new Lookup(null, "WorldGuard region support is disabled in world " + worldName + ".");
        }
        return new Lookup(manager, null);
    }

    private static Flag<?> flagOf(String name) {
        try {
            return WorldGuard.getInstance().getFlagRegistry().get(name.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return null on success, else the error message */
    private static String applyFlag(ProtectedRegion region, String name, String value, Actor actor) {
        Flag<?> flag = flagOf(name);
        if (flag == null) {
            return "Unknown flag: " + name + ".";
        }
        try {
            setParsed(region, flag, value, actor);
            return null;
        } catch (Exception e) {
            // InvalidFlagFormat (7.0.9) / InvalidFlagFormatException (7.0.10+): both land here.
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "Bad value '" + value + "' for flag " + flag.getName() + ": " + message;
        }
    }

    private static <V> void setParsed(ProtectedRegion region, Flag<V> flag, String value, Actor actor) throws Exception {
        if (value == null) {
            region.setFlag(flag, null);
            return;
        }
        FlagContext context = FlagContext.create()
                .setSender(actor)
                .setInput(value)
                .setObject("region", region)
                .build();
        region.setFlag(flag, flag.parseInput(context));
    }

    private static Actor actor(CommandSender sender) {
        return WorldGuardPlugin.inst().wrapCommandSender(sender != null ? sender : Bukkit.getConsoleSender());
    }

    private String save(RegionManager manager, String worldName) {
        try {
            manager.saveChanges();
            return "";
        } catch (Exception e) {
            warn.accept("WorldGuard could not save regions for " + worldName + " now: " + e.getMessage());
            return " (applied in memory; WorldGuard could not save now and will retry at its next autosave: "
                    + e.getMessage() + ")";
        }
    }

    static String display(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof StateFlag.State state) {
            return state.name().toLowerCase(Locale.ROOT);
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object o : collection) {
                parts.add(display(o));
            }
            return String.join(", ", parts);
        }
        return String.valueOf(value);
    }

    private static String domain(DefaultDomain domain) {
        if (domain == null || domain.size() == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (!domain.getPlayers().isEmpty()) {
            parts.add(String.join(", ", domain.getPlayers()));
        }
        if (!domain.getUniqueIds().isEmpty()) {
            parts.add(domain.getUniqueIds().size() + " uuid(s)");
        }
        if (!domain.getGroups().isEmpty()) {
            parts.add("groups " + String.join(", ", domain.getGroups()));
        }
        return String.join("; ", parts);
    }
}
