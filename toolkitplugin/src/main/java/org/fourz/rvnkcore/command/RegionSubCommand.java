package org.fourz.rvnkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.service.region.IRegionService.RegionInfo;
import org.fourz.rvnkcore.service.region.IRegionService.RegionResult;
import org.fourz.rvnkcore.service.region.RegionArgs;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * {@code /rvnk region <define|flag|remove|info>} - console-safe WorldGuard regions (#2248).
 *
 * <p>WorldGuard's {@code /rg define} needs a WorldEdit selection, which the console cannot make.
 * These verbs take explicit corners and a world instead. Without WorldGuard every verb answers
 * "WorldGuard not installed" and changes nothing. Permission {@code rvnkcore.region.admin}.</p>
 *
 * @since 1.5.100-alpha
 */
public class RegionSubCommand extends BaseSubCommand {

    static final String PERM_ADMIN = "rvnkcore.region.admin";

    private static final List<String> VERBS = List.of("define", "flag", "remove", "info");
    private static final List<String> STATE_VALUES = List.of("allow", "deny");

    public RegionSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "region",
                "Define and flag WorldGuard regions from explicit corners (console-safe)",
                "/rvnk region <define|flag|remove|info> <name> <world> [args]",
                null, false);
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/rvnk region define sotw_tolla sotw_city -94 65 0 -90 69 4 interact=allow use=allow mob-spawning=deny",
                "  corners in any order; priority=N sets the priority; a second define updates the bounds",
                "/rvnk region flag sotw_tolla sotw_city greeting Welcome to the ruins",
                "/rvnk region flag sotw_tolla sotw_city greeting clear",
                "/rvnk region info sotw_tolla sotw_city",
                "/rvnk region remove sotw_tolla sotw_city");
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] rawArgs) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        String[] args = QuotedArgs.tokenize(rawArgs);
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        IRegionService regions = RVNKCore.getServiceSafe(IRegionService.class);
        if (regions == null || !regions.isAvailable()) {
            sendErrorMessage(sender, regions == null ? IRegionService.NOT_INSTALLED : regions.unavailableReason());
            return true;
        }
        String verb = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (verb) {
            case "define" -> handleDefine(sender, regions, rest);
            case "flag" -> handleFlag(sender, regions, rest);
            case "remove" -> handleRemove(sender, regions, rest);
            case "info" -> handleInfo(sender, regions, rest);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleDefine(CommandSender sender, IRegionService regions, String[] args) {
        RegionArgs.Parsed<RegionArgs.DefineArgs> parsed = RegionArgs.parseDefine(args, regions::isFlag);
        if (!parsed.ok()) {
            parsed.errors().forEach(error -> sendErrorMessage(sender, error));
            return;
        }
        RegionArgs.DefineArgs define = parsed.value();
        if (!worldLoaded(sender, define.world())) {
            return;
        }
        report(sender, regions.define(define.world(), define.id(), define.box(), define.flags(), define.priority(), sender));
    }

    private void handleFlag(CommandSender sender, IRegionService regions, String[] args) {
        RegionArgs.Parsed<RegionArgs.FlagArgs> parsed = RegionArgs.parseFlag(args, regions::isFlag);
        if (!parsed.ok()) {
            parsed.errors().forEach(error -> sendErrorMessage(sender, error));
            return;
        }
        RegionArgs.FlagArgs flag = parsed.value();
        if (!worldLoaded(sender, flag.world())) {
            return;
        }
        report(sender, regions.setFlag(flag.world(), flag.id(), flag.flag(), flag.value(), sender));
    }

    private void handleRemove(CommandSender sender, IRegionService regions, String[] args) {
        RegionArgs.Parsed<String[]> parsed = RegionArgs.parseNameWorld(args, "remove");
        if (!parsed.ok()) {
            parsed.errors().forEach(error -> sendErrorMessage(sender, error));
            return;
        }
        if (!worldLoaded(sender, parsed.value()[1])) {
            return;
        }
        report(sender, regions.remove(parsed.value()[1], parsed.value()[0]));
    }

    private void handleInfo(CommandSender sender, IRegionService regions, String[] args) {
        RegionArgs.Parsed<String[]> parsed = RegionArgs.parseNameWorld(args, "info");
        if (!parsed.ok()) {
            parsed.errors().forEach(error -> sendErrorMessage(sender, error));
            return;
        }
        String id = parsed.value()[0];
        String world = parsed.value()[1];
        if (!worldLoaded(sender, world)) {
            return;
        }
        Optional<RegionInfo> found = regions.info(world, id);
        if (found.isEmpty()) {
            sendErrorMessage(sender, "No region '" + id + "' in world " + world + ".");
            return;
        }
        RegionInfo info = found.get();
        sender.sendMessage(ChatColor.GOLD + "Region '" + info.id() + "'" + ChatColor.GRAY + " in " + info.world()
                + " (" + info.type() + ")");
        sender.sendMessage(ChatColor.YELLOW + "  Bounds: " + ChatColor.WHITE + info.bounds()
                + (info.bounds() == null ? "" : ChatColor.GRAY + "  (" + info.bounds().volume() + " blocks)"));
        sender.sendMessage(ChatColor.YELLOW + "  Priority: " + ChatColor.WHITE + info.priority());
        if (info.flags().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "  Flags: " + ChatColor.GRAY + "none");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "  Flags:");
            for (Map.Entry<String, String> flag : info.flags().entrySet()) {
                sender.sendMessage("    " + ChatColor.WHITE + flag.getKey() + ChatColor.GRAY + " = " + flag.getValue());
            }
        }
        sender.sendMessage(ChatColor.YELLOW + "  Owners: " + ChatColor.WHITE + (info.owners().isEmpty() ? "none" : info.owners()));
        sender.sendMessage(ChatColor.YELLOW + "  Members: " + ChatColor.WHITE + (info.members().isEmpty() ? "none" : info.members()));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean worldLoaded(CommandSender sender, String world) {
        if (Bukkit.getWorld(world) == null) {
            sendErrorMessage(sender, "World '" + world + "' is not loaded.");
            return false;
        }
        return true;
    }

    private void report(CommandSender sender, RegionResult result) {
        // Plain sendMessage: region values (greetings) may hold '&' that must not be re-coloured.
        sender.sendMessage((result.ok() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void sendUsage(CommandSender sender) {
        sendInfoMessage(sender, "Usage: /rvnk region define <name> <world> <x1> <y1> <z1> <x2> <y2> <z2> [flag=value ...]");
        sendInfoMessage(sender, "       /rvnk region flag <name> <world> <flag> <value|clear>");
        sendInfoMessage(sender, "       /rvnk region remove|info <name> <world>");
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            return Collections.emptyList();
        }
        String last = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return filter(VERBS, last);
        }
        IRegionService regions = RVNKCore.getServiceSafe(IRegionService.class);
        if (regions == null || !regions.isAvailable()) {
            return Collections.emptyList();
        }
        String verb = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (verb.equals("define")) {
                return Collections.emptyList();
            }
            TreeSet<String> ids = new TreeSet<>();
            for (World world : Bukkit.getWorlds()) {
                ids.addAll(regions.regionIds(world.getName()));
            }
            ids.remove("__global__");
            return limit(filter(new ArrayList<>(ids), last), 100);
        }
        if (args.length == 3) {
            if (!verb.equals("define")) {
                List<String> worlds = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    if (regions.regionIds(world.getName()).contains(args[1].toLowerCase(Locale.ROOT))) {
                        worlds.add(world.getName());
                    }
                }
                if (!worlds.isEmpty()) {
                    return filter(worlds, last);
                }
            }
            return filter(worldNames(), last);
        }
        if (verb.equals("define")) {
            if (args.length <= 9) {
                return coordinate(sender, (args.length - 4) % 3);
            }
            return flagPairs(regions, last);
        }
        if (verb.equals("flag")) {
            if (args.length == 4) {
                return limit(filter(regions.flagNames(), last), 100);
            }
            if (args.length == 5) {
                List<String> values = new ArrayList<>(regions.isStateFlag(args[3]) ? STATE_VALUES : List.of());
                values.add(RegionArgs.CLEAR);
                return filter(values, last);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> flagPairs(IRegionService regions, String last) {
        int eq = last.indexOf('=');
        if (eq < 0) {
            List<String> pairs = new ArrayList<>();
            for (String flag : regions.flagNames()) {
                pairs.add(flag + "=");
            }
            pairs.add(RegionArgs.PRIORITY + "=");
            return limit(filter(pairs, last), 100);
        }
        String flag = last.substring(0, eq);
        if (!regions.isStateFlag(flag)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String value : STATE_VALUES) {
            values.add(flag + "=" + value);
        }
        return filter(values, last);
    }

    static List<String> coordinate(CommandSender sender, int axis) {
        if (sender instanceof Player player) {
            org.bukkit.Location at = player.getLocation();
            int value = axis == 0 ? at.getBlockX() : axis == 1 ? at.getBlockY() : at.getBlockZ();
            return List.of(String.valueOf(value));
        }
        return Collections.emptyList();
    }

    static List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    static List<String> limit(List<String> values, int max) {
        return values.size() <= max ? values : values.subList(0, max);
    }
}
