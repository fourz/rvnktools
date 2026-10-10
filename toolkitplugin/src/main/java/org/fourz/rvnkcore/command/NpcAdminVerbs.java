package org.fourz.rvnkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.service.npc.NpcClickSimulator;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.fourz.rvnkcore.service.npc.harness.NpcApplyPlanner;
import org.fourz.rvnkcore.service.npc.harness.NpcChange;
import org.fourz.rvnkcore.service.npc.harness.NpcDrift;
import org.fourz.rvnkcore.service.npc.harness.NpcGround;
import org.fourz.rvnkcore.service.npc.harness.NpcHarness;
import org.fourz.rvnkcore.service.npc.harness.NpcHarness.Result;
import org.fourz.rvnkcore.service.npc.harness.NpcNameplate;
import org.fourz.rvnkcore.service.npc.harness.NpcPlanStep;
import org.fourz.rvnkcore.service.npc.harness.NpcPose;
import org.fourz.rvnkcore.service.npc.harness.NpcSkins;
import org.fourz.rvnkcore.service.npc.harness.NpcSpec;
import org.fourz.rvnkcore.service.npc.harness.NpcSpecExecutor;
import org.fourz.rvnkcore.service.npc.harness.NpcSpecExporter;
import org.fourz.rvnkcore.service.npc.harness.NpcSpecParser;
import org.fourz.rvnkcore.service.npc.harness.NpcSpecStore;
import org.fourz.rvnkcore.service.npc.harness.NpcState;
import org.fourz.rvnkcore.service.npc.harness.NpcVerifier;
import org.fourz.rvnkcore.service.npc.harness.NpcZone;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.service.region.IRegionService.RegionResult;
import org.fourz.rvnkcore.service.region.UnavailableRegionService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The admin verbs of {@code /rvnk npc} (#2248): console-safe place and edit, appearance, the
 * WorldGuard protect zone, the YAML spec harness (apply, verify, export), and the QA click
 * simulator ({@code click}, #2255). All keyed by RVNK key; none needs the sender's position.
 * Permission {@code rvnkcore.npc.admin}.
 *
 * <p>Split out of {@link NpcSubCommand} to keep that class to dispatch and the #2213 verbs.</p>
 *
 * @since 1.5.100-alpha
 */
final class NpcAdminVerbs {

    static final String PERM_ADMIN = "rvnkcore.npc.admin";

    static final List<String> VERBS = List.of("create", "move", "rename", "remove", "skin", "lookclose", "pose",
            "hold", "protected", "nameplate", "protect", "apply", "verify", "export", "click");

    /** Verbs whose second argument is an existing key. */
    static final Set<String> KEY_VERBS = Set.of("move", "rename", "remove", "skin", "lookclose", "pose", "hold",
            "protected", "nameplate", "protect", "untag", "info", "click");

    private static final int MAX_ERROR_LINES = 40;

    private final RVNKCore plugin;

    NpcAdminVerbs(RVNKCore plugin) {
        this.plugin = plugin;
    }

    /** @return true when the verb is an admin verb (handled here) */
    static boolean handles(String verb) {
        return VERBS.contains(verb);
    }

    void execute(CommandSender sender, String verb, String[] args) {
        switch (verb) {
            case "create" -> create(sender, args);
            case "move" -> move(sender, args);
            case "rename" -> rename(sender, args);
            case "remove" -> remove(sender, args);
            case "skin" -> skin(sender, args);
            case "lookclose" -> toggle(sender, args, "lookclose", (h, k, on) -> h.lookClose(k, on));
            case "protected" -> toggle(sender, args, "protected", (h, k, on) -> h.setProtected(k, on));
            case "pose" -> pose(sender, args);
            case "hold" -> hold(sender, args);
            case "nameplate" -> nameplate(sender, args);
            case "protect" -> protect(sender, args);
            case "apply" -> apply(sender, args);
            case "verify" -> verify(sender, args);
            case "export" -> export(sender, args);
            case "click" -> click(sender, args);
            default -> error(sender, "Unknown verb " + verb);
        }
    }

    // ── place and edit ─────────────────────────────────────────────────────────

    private void create(CommandSender sender, String[] args) {
        if (args.length < 6 || args.length > 8) {
            error(sender, "Usage: /rvnk npc create <key> <name> <world> <x> <y> <z> [yaw] [pitch]  (quote a name with spaces)");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        Optional<NpcState> existing = harness.state(key);
        if (existing.isPresent()) {
            error(sender, "Key '" + key + "' already exists on NPC #" + existing.get().backingId()
                    + ". To reposition it: /rvnk npc move " + key + " <world> <x> <y> <z> [yaw] [pitch]");
            return;
        }
        NpcArgs.Parsed<NpcArgs.Position> pos = NpcArgs.position(args, 2);
        if (!pos.ok()) {
            error(sender, "Usage: /rvnk npc create <key> <name> <world> <x> <y> <z> [yaw] [pitch]: " + pos.error());
            return;
        }
        Location at = location(sender, pos.value(), 0f, 0f);
        if (at == null) {
            return;
        }
        NpcGround.Snap snap = snapToGround(harness, at);
        Result result = harness.create(key, args[1], at);
        report(sender, key, result);
        reportSnap(sender, key, result, snap);
    }

    private void move(CommandSender sender, String[] args) {
        if (args.length < 5 || args.length > 7) {
            error(sender, "Usage: /rvnk npc move <key> <world> <x> <y> <z> [yaw] [pitch]");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        Optional<NpcState> state = existing(sender, harness, key);
        if (state.isEmpty()) {
            return;
        }
        NpcArgs.Parsed<NpcArgs.Position> pos = NpcArgs.position(args, 1);
        if (!pos.ok()) {
            error(sender, "Usage: /rvnk npc move <key> <world> <x> <y> <z> [yaw] [pitch]: " + pos.error());
            return;
        }
        Location to = location(sender, pos.value(), state.get().yaw(), state.get().pitch());
        if (to == null) {
            return;
        }
        NpcGround.Snap snap = snapToGround(harness, to);
        Result result = harness.move(key, to);
        report(sender, key, result);
        reportSnap(sender, key, result, snap);
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc rename <key> <name>  (quote a name with spaces)");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness != null && key != null) {
            report(sender, key, harness.rename(key, args[1]));
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 1) {
            error(sender, "Usage: /rvnk npc remove <key>");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        Optional<NpcState> state = harness.state(key);
        Result result = harness.remove(key);
        report(sender, key, result);
        if (result.ok() && state.isPresent() && state.get().world() != null) {
            IRegionService regions = regions();
            if (regions.info(state.get().world(), NpcZone.regionId(key)).isPresent()) {
                info(sender, "Zone region " + NpcZone.regionId(key) + " was left in place. Remove it with: /rvnk region remove "
                        + NpcZone.regionId(key) + " " + state.get().world());
            }
        }
    }

    private void skin(CommandSender sender, String[] args) {
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc skin <key> <playerName|url>");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        if (!NpcSkins.isValid(args[1])) {
            error(sender, "Skin must be a player name (1-16 of A-Z a-z 0-9 _) or an http(s) URL: " + args[1]);
            return;
        }
        report(sender, key, harness.skin(key, args[1], later(sender)));
    }

    @FunctionalInterface
    private interface Toggle {
        Result apply(NpcHarness harness, String key, boolean on);
    }

    private void toggle(CommandSender sender, String[] args, String verb, Toggle toggle) {
        String values = verb.equals("protected") ? "true|false" : "on|off";
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc " + verb + " <key> " + values);
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        Boolean on = NpcSpecParser.parseBool(args[1]);
        if (on == null) {
            error(sender, verb + " must be " + values + ": " + args[1]);
            return;
        }
        report(sender, key, toggle.apply(harness, key, on));
    }

    private void pose(CommandSender sender, String[] args) {
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc pose <key> stand|sit|sneak");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        NpcPose pose = NpcPose.parse(args[1]);
        if (pose == null) {
            error(sender, "pose must be stand, sit or sneak: " + args[1]);
            return;
        }
        report(sender, key, harness.pose(key, pose));
    }

    private void hold(CommandSender sender, String[] args) {
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc hold <key> <material|none>");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        String material = NpcSpecParser.normalizeMaterial(args[1]);
        if (!"none".equals(material) && !materialValid(material)) {
            error(sender, "Unknown or non-item material: " + args[1]);
            return;
        }
        report(sender, key, harness.hold(key, material));
    }

    private void nameplate(CommandSender sender, String[] args) {
        if (args.length != 2) {
            error(sender, "Usage: /rvnk npc nameplate <key> on|off|hover");
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        NpcNameplate mode = NpcNameplate.parse(args[1]);
        if (mode == null) {
            error(sender, "nameplate must be on, off or hover: " + args[1]);
            return;
        }
        report(sender, key, harness.nameplate(key, mode));
    }

    private void protect(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 3) {
            error(sender, "Usage: /rvnk npc protect <key> [radius=" + NpcZone.DEFAULT_RADIUS + "] [height="
                    + NpcZone.DEFAULT_HEIGHT + "]");
            return;
        }
        IRegionService regions = regions();
        if (!regions.isAvailable()) {
            error(sender, regions.unavailableReason());
            return;
        }
        NpcHarness harness = harness(sender);
        String key = key(sender, args[0]);
        if (harness == null || key == null) {
            return;
        }
        Optional<NpcState> state = existing(sender, harness, key);
        if (state.isEmpty()) {
            return;
        }
        NpcArgs.Parsed<NpcZone> zone = NpcArgs.zone(args, 1);
        if (!zone.ok()) {
            error(sender, zone.error());
            return;
        }
        NpcState npc = state.get();
        if (!npc.hasLocation() || npc.world() == null || Bukkit.getWorld(npc.world()) == null) {
            error(sender, "NPC #" + npc.backingId() + " has no location in a loaded world; cannot place a zone.");
            return;
        }
        RegionResult result = NpcSpecExecutor.defineZone(regions, key, npc.world(), npc.x(), npc.y(), npc.z(),
                zone.value(), sender);
        sender.sendMessage((result.ok() ? ChatColor.GREEN : ChatColor.RED) + "[" + key + "] zone: " + result.message());
    }

    // ── spec harness ───────────────────────────────────────────────────────────

    private void apply(CommandSender sender, String[] args) {
        boolean dryRun = Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--dry-run"));
        String[] rest = Arrays.stream(args).filter(a -> !a.equalsIgnoreCase("--dry-run")).toArray(String[]::new);
        if (rest.length != 1) {
            error(sender, "Usage: /rvnk npc apply <spec> [--dry-run]   (spec = plugins/RVNKCore/npc/<spec>.yml)");
            return;
        }
        NpcHarness harness = harness(sender);
        if (harness == null) {
            return;
        }
        List<NpcSpec> specs = loadSpec(sender, rest[0]);
        if (specs == null) {
            return;
        }
        IRegionService regions = regions();
        List<NpcState> states = harness.snapshot();
        List<NpcPlanStep> plan = NpcApplyPlanner.plan(specs, states, NpcAdminVerbs::worldLoaded, regions, harness.terrain());

        Map<String, Integer> counts = new HashMap<>();
        plan.forEach(step -> counts.merge(step.action().name(), 1, Integer::sum));
        sender.sendMessage(ChatColor.GOLD + "NPC apply '" + rest[0] + "'" + (dryRun ? " (dry run)" : "") + ": "
                + plan.size() + " keys - " + counts.getOrDefault("CREATE", 0) + " create, "
                + counts.getOrDefault("UPDATE", 0) + " update, " + counts.getOrDefault("NOOP", 0) + " noop, "
                + counts.getOrDefault("BLOCKED", 0) + " blocked");

        Map<String, NpcSpec> byKey = new HashMap<>();
        specs.forEach(spec -> byKey.put(spec.key(), spec));
        Map<String, NpcState> stateByKey = new HashMap<>();
        states.forEach(state -> stateByKey.putIfAbsent(state.key(), state));

        NpcSpecExecutor executor = new NpcSpecExecutor(harness, regions, NpcAdminVerbs::locate, sender, later(sender));
        int failed = 0;
        for (NpcPlanStep step : plan) {
            printStepHeader(sender, step);
            if (dryRun || step.action() == NpcPlanStep.Action.NOOP || step.action() == NpcPlanStep.Action.BLOCKED) {
                for (NpcChange change : step.changes()) {
                    sender.sendMessage(ChatColor.GRAY + "      " + change);
                }
            } else {
                NpcSpecExecutor.StepResult result = executor.execute(step, byKey.get(step.key()), stateByKey.get(step.key()));
                for (String line : result.lines()) {
                    sender.sendMessage((result.ok() ? ChatColor.GRAY : ChatColor.RED) + "      " + line);
                }
                if (!result.ok()) {
                    failed++;
                }
            }
            for (String note : step.notes()) {
                sender.sendMessage(ChatColor.YELLOW + "      note: " + note);
            }
        }
        if (!dryRun) {
            String summary = "NPC apply '" + rest[0] + "' done: " + (failed == 0 ? "all steps ok" : failed + " step(s) had failures");
            sender.sendMessage((failed == 0 ? ChatColor.GREEN : ChatColor.RED) + summary);
            plugin.getLogger().info(summary + " (by " + sender.getName() + ")");
        }
    }

    private void verify(CommandSender sender, String[] args) {
        if (args.length > 1) {
            error(sender, "Usage: /rvnk npc verify [spec]");
            return;
        }
        NpcHarness harness = harness(sender);
        if (harness == null) {
            return;
        }
        List<NpcSpec> specs = null;
        if (args.length == 1) {
            specs = loadSpec(sender, args[0]);
            if (specs == null) {
                return;
            }
        }
        List<NpcState> states = harness.snapshot();
        List<NpcDrift> drifts = NpcVerifier.verify(specs, states, NpcAdminVerbs::worldLoaded, regions(), harness.terrain());
        long problems = NpcVerifier.problemCount(drifts);
        int keys = specs != null ? specs.size() : (int) states.stream().map(NpcState::key).distinct().count();
        String label = specs != null ? "'" + args[0] + "'" : "(all tagged keys)";
        sender.sendMessage((problems == 0 ? ChatColor.GREEN : ChatColor.RED) + "NPC verify " + label + ": "
                + (problems == 0 ? "clean" : "DRIFT - " + problems + " problem(s)") + " (" + keys + " keys)");
        for (NpcDrift drift : drifts) {
            ChatColor color = drift.kind().isProblem() ? ChatColor.RED : ChatColor.GRAY;
            sender.sendMessage(color + "  " + (drift.kind().isProblem() ? "" : "(info) ") + drift.key() + " "
                    + drift.kind() + " " + drift.detail());
        }
    }

    private void export(CommandSender sender, String[] args) {
        boolean force = Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--force"));
        String[] rest = Arrays.stream(args).filter(a -> !a.equalsIgnoreCase("--force")).toArray(String[]::new);
        if (rest.length != 1) {
            error(sender, "Usage: /rvnk npc export <spec> [--force]");
            return;
        }
        NpcHarness harness = harness(sender);
        if (harness == null) {
            return;
        }
        List<NpcState> states = harness.snapshot();
        if (states.isEmpty()) {
            error(sender, "No tagged NPCs to export. Tag one with /rvnk npc tag <key> <npcId>.");
            return;
        }
        NpcSpecExporter.Export export = NpcSpecExporter.export(states, regions(),
                "Exported by /rvnk npc export on " + java.time.LocalDate.now());
        try {
            File file = store().write(rest[0], export.yaml(), force);
            success(sender, "Exported " + export.exported().size() + " NPC(s) to " + file.getPath() + ": "
                    + String.join(", ", export.exported()));
        } catch (IOException e) {
            error(sender, "Export failed: " + e.getMessage());
            return;
        }
        for (String note : export.notes()) {
            info(sender, "  note: " + note);
        }
    }

    private List<NpcSpec> loadSpec(CommandSender sender, String name) {
        String text;
        try {
            text = store().read(name);
        } catch (IOException e) {
            error(sender, "Cannot read spec: " + e.getMessage());
            return null;
        }
        NpcSpecParser.Result parsed = new NpcSpecParser(NpcAdminVerbs::worldExists, NpcAdminVerbs::materialValid).parse(text);
        if (!parsed.ok()) {
            error(sender, "Spec '" + name + "' has " + parsed.errors().size() + " error(s); nothing was done:");
            parsed.errors().stream().limit(MAX_ERROR_LINES).forEach(e -> sender.sendMessage(ChatColor.RED + "  " + e));
            if (parsed.errors().size() > MAX_ERROR_LINES) {
                sender.sendMessage(ChatColor.RED + "  ... " + (parsed.errors().size() - MAX_ERROR_LINES) + " more");
            }
            return null;
        }
        return parsed.npcs();
    }

    private static void printStepHeader(CommandSender sender, NpcPlanStep step) {
        ChatColor color = switch (step.action()) {
            case CREATE -> ChatColor.AQUA;
            case UPDATE -> ChatColor.YELLOW;
            case NOOP -> ChatColor.GRAY;
            case BLOCKED -> ChatColor.RED;
        };
        sender.sendMessage(color + "  " + String.format(Locale.ROOT, "%-7s", step.action()) + " " + ChatColor.WHITE
                + step.key() + (step.backingId() >= 0 ? ChatColor.GRAY + " #" + step.backingId() : ""));
    }

    // ── QA click simulator (#2255) ─────────────────────────────────────────────

    /**
     * {@code click <key> <player> [right|left]}: fires {@link org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent}
     * for an online player as if they had clicked the NPC. Gated by {@link NpcClickGate}; every
     * attempt past argument parsing is logged at INFO as an audit trail.
     */
    private void click(CommandSender sender, String[] args) {
        NpcArgs.Parsed<NpcArgs.Click> parsed = NpcArgs.click(args, 0);
        if (!parsed.ok()) {
            error(sender, "Usage: " + NpcArgs.CLICK_USAGE + ": " + parsed.error());
            return;
        }
        NpcArgs.Click request = parsed.value();
        Player target = Bukkit.getPlayerExact(request.player());
        if (target == null) {
            error(sender, "Player '" + request.player() + "' is not online. A simulated click needs an online player.");
            return;
        }

        String tier = serverTier();
        NpcClickGate.Decision gate = NpcClickGate.evaluate(tier, sender.hasPermission(PERM_ADMIN),
                target.hasPermission(NpcClickGate.PERM_QA_SUBJECT));
        String audit = "[npc click] sender=" + sender.getName() + " target=" + target.getName()
                + " key=" + request.key() + " click=" + request.click() + " tier=" + (tier == null ? "unknown" : tier);
        if (!gate.allowed()) {
            plugin.getLogger().info(audit + " REFUSED: " + gate.reason());
            error(sender, "Refused: " + gate.reason() + ".");
            return;
        }

        NpcClickSimulator simulator = RVNKCore.getServiceSafe(NpcClickSimulator.class);
        if (simulator == null) {
            plugin.getLogger().info(audit + " NOT RUN: NPC bridge unavailable");
            error(sender, "NPC bridge unavailable: Citizens is not installed, not enabled, or its adapter failed to start.");
            return;
        }
        NpcClickSimulator.Outcome outcome = simulator.click(target, request.key(), request.click());
        switch (outcome.status()) {
            case UNAVAILABLE -> {
                plugin.getLogger().info(audit + " NOT RUN: NPC bridge unavailable");
                error(sender, "NPC bridge unavailable: Citizens is not installed or not enabled.");
            }
            case NOT_FOUND -> {
                plugin.getLogger().info(audit + " NOT RUN: no NPC carries the key");
                error(sender, "No NPC carries key '" + request.key() + "'. See /rvnk npc list.");
            }
            case FIRED, CANCELLED -> {
                boolean cancelled = outcome.status() == NpcClickSimulator.Status.CANCELLED;
                plugin.getLogger().info(audit + " npc=#" + outcome.npc().getBackingId() + " gate=\"" + gate.reason()
                        + "\" result=" + (cancelled ? "fired, CANCELLED by a listener" : "fired"));
                String npcLabel = "'" + outcome.npc().getDisplayName() + "' (#" + outcome.npc().getBackingId()
                        + (outcome.npc().isSpawned() ? "" : ", despawned") + ")";
                if (cancelled) {
                    sender.sendMessage(ChatColor.YELLOW + "[" + request.key() + "] Simulated " + request.click()
                            + " click by " + target.getName() + " on " + npcLabel
                            + ": fired, CANCELLED by a listener. Not recorded as the last interaction.");
                } else {
                    success(sender, "[" + request.key() + "] Simulated " + request.click() + " click by "
                            + target.getName() + " on " + npcLabel + ": fired, not cancelled.");
                }
                info(sender, "  Gate: " + gate.reason() + ". Any dialogue goes to " + target.getName()
                        + ", not to you.");
            }
        }
    }

    /** @return this server's id from RVNKCore config, or null when it cannot be read */
    private String serverTier() {
        try {
            return ConfigLoader.getInstance(plugin).getServerId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── tab completion ─────────────────────────────────────────────────────────

    /**
     * @param args tokens after {@code npc}, verb first; the last one is the partial word
     */
    List<String> complete(CommandSender sender, String[] args) {
        String verb = args[0].toLowerCase(Locale.ROOT);
        int index = args.length - 1; // 1 = first argument after the verb
        String last = args[index];
        if (index == 1) {
            if (KEY_VERBS.contains(verb)) {
                return RegionSubCommand.filter(keys(), last);
            }
            if (verb.equals("apply") || verb.equals("verify") || verb.equals("export")) {
                return RegionSubCommand.filter(store().list(), last);
            }
            return Collections.emptyList();
        }
        switch (verb) {
            case "create" -> {
                return position(sender, index - 3, last);
            }
            case "move" -> {
                return position(sender, index - 2, last);
            }
            case "skin" -> {
                if (index == 2) {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return RegionSubCommand.filter(names, last);
                }
            }
            case "click" -> {
                if (index == 2) {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return RegionSubCommand.filter(names, last);
                }
                if (index == 3) {
                    return RegionSubCommand.filter(List.of("right", "left"), last);
                }
            }
            case "lookclose", "nameplate", "protected", "pose" -> {
                if (index == 2) {
                    List<String> values = switch (verb) {
                        case "lookclose" -> List.of("on", "off");
                        case "nameplate" -> List.of("on", "off", "hover");
                        case "protected" -> List.of("true", "false");
                        default -> List.of("stand", "sit", "sneak");
                    };
                    return RegionSubCommand.filter(values, last);
                }
            }
            case "hold" -> {
                if (index == 2) {
                    List<String> materials = new ArrayList<>();
                    materials.add("none");
                    String prefix = last.toLowerCase(Locale.ROOT);
                    for (Material material : Material.values()) {
                        if (materials.size() >= 60) {
                            break;
                        }
                        String id = material.name().toLowerCase(Locale.ROOT);
                        if (id.startsWith(prefix) && !material.name().startsWith("LEGACY_") && material.isItem()
                                && !material.isAir()) {
                            materials.add(id);
                        }
                    }
                    return RegionSubCommand.filter(materials, last);
                }
            }
            case "protect" -> {
                if (index == 2) {
                    return RegionSubCommand.filter(List.of("radius=" + NpcZone.DEFAULT_RADIUS), last);
                }
                if (index == 3) {
                    return RegionSubCommand.filter(List.of("height=" + NpcZone.DEFAULT_HEIGHT), last);
                }
            }
            case "apply" -> {
                if (index == 2) {
                    return RegionSubCommand.filter(List.of("--dry-run"), last);
                }
            }
            case "export" -> {
                if (index == 2) {
                    return RegionSubCommand.filter(List.of("--force"), last);
                }
            }
            default -> {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /** Completes {@code <world> <x> <y> <z>} where {@code slot} 0 is the world. */
    private static List<String> position(CommandSender sender, int slot, String last) {
        if (slot == 0) {
            return RegionSubCommand.filter(RegionSubCommand.worldNames(), last);
        }
        if (slot >= 1 && slot <= 3) {
            if (sender instanceof Player player) {
                Location at = player.getLocation();
                double value = slot == 1 ? at.getX() : slot == 2 ? at.getY() : at.getZ();
                return List.of(String.format(Locale.ROOT, "%.1f", value));
            }
        }
        if (slot == 4 && sender instanceof Player player) {
            return List.of(String.format(Locale.ROOT, "%.0f", NpcSpecParser.normalizeYaw(player.getLocation().getYaw())));
        }
        return Collections.emptyList();
    }

    static List<String> keys() {
        NpcHarness harness = RVNKCore.getServiceSafe(NpcHarness.class);
        if (harness != null) {
            return harness.snapshot().stream().map(NpcState::key).distinct().sorted().toList();
        }
        return Collections.emptyList();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private NpcSpecStore store() {
        return new NpcSpecStore(new File(plugin.getDataFolder(), "npc"));
    }

    private static NpcHarness harness(CommandSender sender) {
        NpcHarness harness = RVNKCore.getServiceSafe(NpcHarness.class);
        if (harness == null) {
            error(sender, "NPC harness unavailable: Citizens is not installed, not enabled, or its adapter failed to start.");
        }
        return harness;
    }

    private static IRegionService regions() {
        IRegionService regions = RVNKCore.getServiceSafe(IRegionService.class);
        return regions != null ? regions : new UnavailableRegionService(IRegionService.NOT_INSTALLED);
    }

    private static String key(CommandSender sender, String raw) {
        String key = NpcKeys.normalize(raw);
        if (key == null) {
            error(sender, "Invalid key '" + raw + "'. Use lower-case a-z, 0-9, _ or -, 1-" + NpcKeys.MAX_LENGTH + " characters.");
        }
        return key;
    }

    private static Optional<NpcState> existing(CommandSender sender, NpcHarness harness, String key) {
        List<NpcState> matches = harness.snapshot().stream().filter(s -> s.key().equals(key)).toList();
        if (matches.isEmpty()) {
            error(sender, "No NPC carries key '" + key + "'. Create it: /rvnk npc create " + key + " <name> <world> <x> <y> <z>");
            return Optional.empty();
        }
        if (matches.size() > 1) {
            error(sender, "Key '" + key + "' is on " + matches.size() + " NPCs; remove the extras first.");
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    private static Location location(CommandSender sender, NpcArgs.Position pos, float defaultYaw, float defaultPitch) {
        Location at = locate(pos.world(), pos.x(), pos.y(), pos.z(),
                pos.yaw() != null ? pos.yaw() : defaultYaw, pos.pitch() != null ? pos.pitch() : defaultPitch);
        if (at == null) {
            error(sender, "World '" + pos.world() + "' is not loaded.");
        }
        return at;
    }

    /**
     * Snaps a target location's Y to the standable Y at its X/Z ({@link NpcGround#snap}, #2248).
     * Changes {@code at} in place; keeps the Y when no standable spot is in the search window.
     */
    private static NpcGround.Snap snapToGround(NpcHarness harness, Location at) {
        String world = at.getWorld() == null ? null : at.getWorld().getName();
        NpcGround.Snap snap = NpcGround.snap(harness.terrain(), world, at.getX(), at.getY(), at.getZ());
        at.setY(snap.y());
        return snap;
    }

    private static void reportSnap(CommandSender sender, String key, Result result, NpcGround.Snap snap) {
        if (result.ok() && snap.note() != null) {
            sender.sendMessage((snap.found() ? ChatColor.GRAY : ChatColor.YELLOW) + "[" + key + "] position: " + snap.note());
        }
    }

    static Location locate(String world, double x, double y, double z, float yaw, float pitch) {
        World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    static boolean worldLoaded(String world) {
        return world != null && Bukkit.getWorld(world) != null;
    }

    /**
     * True for a loaded world, or one whose folder exists: {@code <container>/<name>}, or the
     * Paper 26 dimension layout {@code <container>/<primary>/dimensions/<namespace>/<name>}.
     */
    static boolean worldExists(String world) {
        if (worldLoaded(world)) {
            return true;
        }
        if (world == null || world.contains("..") || world.contains("/") || world.contains("\\")) {
            return false;
        }
        File container = Bukkit.getWorldContainer();
        if (new File(container, world).isDirectory()) {
            return true;
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return false;
        }
        File[] namespaces = new File(new File(container, worlds.get(0).getName()), "dimensions").listFiles(File::isDirectory);
        if (namespaces != null) {
            for (File namespace : namespaces) {
                if (new File(namespace, world).isDirectory()) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean materialValid(String key) {
        Material material = Material.matchMaterial(key);
        return material != null && material.isItem() && !material.isAir();
    }

    /**
     * Sends an async result (skins) to the log, which is the console, and to a player sender if
     * still online. A remote sender such as RCON gets it directly too.
     */
    private Consumer<String> later(CommandSender sender) {
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        boolean console = sender instanceof org.bukkit.command.ConsoleCommandSender;
        return line -> {
            if (line.contains("FAILED")) {
                plugin.getLogger().warning("[npc] " + line);
            } else {
                plugin.getLogger().info("[npc] " + line);
            }
            ChatColor color = line.contains("FAILED") ? ChatColor.RED
                    : line.contains(NpcSkins.NOT_YET) ? ChatColor.YELLOW : ChatColor.GREEN;
            if (playerId != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(color + line);
                }
            } else if (sender != null && !console) {
                sender.sendMessage(color + line);
            }
        };
    }

    private static void report(CommandSender sender, String key, Result result) {
        sender.sendMessage((result.ok() ? ChatColor.GREEN : ChatColor.RED) + "[" + key + "] " + result.message());
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + message);
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + message);
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.YELLOW + message);
    }
}
