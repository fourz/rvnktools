package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
import org.fourz.rvnkcore.database.connection.ClusterConnectionProvider;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnkcore.util.ChatFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * RVNKCore command for diagnostics, testing, and system information.
 *
 * Console-first design: all subcommands work from server console for
 * automated testing and remote diagnostics.
 *
 * Subcommands:
 *   debug      - Show comprehensive system diagnostics
 *   services   - List all registered services in ServiceRegistry
 *   db         - Test database connectivity and show connection pool status
 *   version    - Show RVNKCore version and build information
 *   reload     - Reload RVNKCore configuration
 *   plugins    - List loaded RVNK plugins and their status
 *   commands   - List all registered CommandManager commands
 *   health     - Full health check (services + db + memory)
 *   test       - Run automated test suite (all, services, db)
 *   mojang     - Mojang API operations (name, uuid, verify, stats)
 *
 * @since 1.3.1
 * @since 1.4.0 (consolidated from rvnktest and mojangtest commands)
 */
public class RVNKCoreCommand extends BaseCommand {

    /**
     * Bukkit plugins only.
     *
     * <p>{@code MickyHats} was listed here and is <b>not a plugin</b> — it is the server resource
     * pack, set through {@code resource-pack} in {@code server.properties}. Looking it up with
     * {@code PluginManager#getPlugin} could only ever return null, so every tier reported a
     * permanent "not loaded" failure for something that was working correctly, and the
     * not-available count was inflated by one everywhere. The pack is now reported from the server
     * API in its own section by {@link #handleResourcePack(CommandSender)}.</p>
     */
    private static final String[] RVNK_PLUGIN_NAMES = {
        "RVNKCore", "RVNKWorlds", "RVNKLore", "RVNKQuests",
        "BarterShops", "TokenEconomy"
    };

    private final RVNKCore rvnkCore;
    private MojangAPI mojangAPI; // Lazy-loaded from ServiceRegistry

    public RVNKCoreCommand(RVNKCore plugin) {
        super(plugin, "rvnkcore",
              "RVNKCore diagnostics, testing, and system information",
              "/rvnkcore <debug|services|db|version|reload|plugins|commands|health|test|mojang"
                      + "|migrate|netban|help> [args]",
              "rvnktools.admin.test");
        this.rvnkCore = plugin;
    }

    /**
     * Worked examples per verb (#1981).
     *
     * <p>{@code /rvnkcore} dispatches from a switch rather than the subcommand registry, so it
     * cannot inherit {@code BaseCommand.sendVerbHelp}. The examples live here instead of in
     * {@code docs/plugins/commands/rvnkcore.md} for the same reason they do elsewhere: shipped in
     * the jar they are fetched per verb and cannot drift from the build.</p>
     *
     * <p>A line starting with two spaces renders as a note under the example above it.</p>
     */
    private static final java.util.Map<String, java.util.List<String>> VERB_EXAMPLES =
            buildVerbExamples();

    private static java.util.Map<String, java.util.List<String>> buildVerbExamples() {
        java.util.Map<String, java.util.List<String>> m = new java.util.LinkedHashMap<>();
        m.put("debug", java.util.List.of(
                "/rvnkcore debug",
                "  full system diagnostics - also what a bare /rvnkcore runs"));
        m.put("services", java.util.List.of(
                "/rvnkcore services",
                "  every interface registered in the ServiceRegistry, and by which plugin",
                "A plugin whose service is missing here did not finish onEnable."));
        m.put("db", java.util.List.of(
                "/rvnkcore db",
                "  connectivity plus row totals; alias: database",
                "RVNKCore's MySQL is cross-host - a hang here usually means a missing",
                "socketTimeout in connectionParameters, not a dead server."));
        m.put("version", java.util.List.of(
                "/rvnkcore version",
                "  read the running version here, never from a commit message or issue comment"));
        m.put("reload", java.util.List.of(
                "/rvnkcore reload",
                "  re-reads config only",
                "Adding a key to a shipped config.yml does NOT reach a server that already",
                "has the file - saveResource writes only when the file is absent."));
        m.put("plugins", java.util.List.of(
                "/rvnkcore plugins",
                "  which ecosystem plugins are present and what they registered"));
        m.put("commands", java.util.List.of(
                "/rvnkcore commands",
                "  every command RVNKCore registered, with its permission"));
        m.put("health", java.util.List.of(
                "/rvnkcore health",
                "  the same signal the REST /v1/health endpoint serves"));
        m.put("test", java.util.List.of(
                "/rvnkcore test",
                "  runs the all suite",
                "/rvnkcore test all",
                "/rvnkcore test services",
                "/rvnkcore test db"));
        m.put("mojang", java.util.List.of(
                "/rvnkcore mojang name Shad0melt",
                "  resolve a username to a UUID",
                "/rvnkcore mojang uuid 28fc0be8-1afb-4b2f-8557-bc28655a8b06",
                "/rvnkcore mojang verify Shad0melt",
                "  accepts a username or a UUID",
                "/rvnkcore mojang stats",
                "  cache and rate-limit counters - the lookup is rate limited"));
        m.put("migrate", java.util.List.of(
                "/rvnkcore migrate playerstate",
                "/rvnkcore migrate playeridentity",
                "/rvnkcore migrate playerprefs",
                "Migrations are one-way. Take a database backup before running one."));
        m.put("netban", java.util.List.of(
                "/rvnkcore netban check Shad0melt",
                "/rvnkcore netban add Shad0melt",
                "/rvnkcore netban remove Shad0melt",
                "A network ban applies across every server in the cluster, not just this one."));
        return m;
    }

    /**
     * {@code /rvnkcore help <verb>} &mdash; one verb's examples.
     */
    @Override
    protected void sendVerbHelp(CommandSender sender, String verb) {
        java.util.List<String> lines = VERB_EXAMPLES.get(verb);
        if (lines == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ No examples for '" + verb + "'."));
            sender.sendMessage(ChatFormat.colorize(
                    "&7Verbs: &f" + String.join(" ", VERB_EXAMPLES.keySet())));
            return;
        }
        sender.sendMessage(ChatFormat.colorize("&6/rvnkcore " + verb));
        for (String line : lines) {
            if (line.startsWith("  ")) {
                sender.sendMessage(ChatFormat.colorize("&8     " + line.trim()));
            } else if (line.startsWith("/")) {
                sender.sendMessage(ChatFormat.colorize("&f  " + line));
            } else {
                sender.sendMessage(ChatFormat.colorize("&7  " + line));
            }
        }
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Default to debug if no args
            handleDebug(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "debug":
                handleDebug(sender);
                break;
            case "services":
                handleServices(sender);
                break;
            case "db":
            case "database":
                handleDb(sender);
                break;
            case "version":
                handleVersion(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "plugins":
                handlePlugins(sender);
                break;
            case "commands":
                handleCommands(sender);
                break;
            case "health":
                handleHealth(sender);
                break;
            case "test":
                String suite = args.length > 1 ? args[1].toLowerCase() : "all";
                handleTest(sender, suite);
                break;
            case "mojang":
                String[] mojangArgs = args.length > 1
                    ? Arrays.copyOfRange(args, 1, args.length)
                    : new String[0];
                handleMojang(sender, mojangArgs);
                break;
            case "migrate":
                handleMigrate(sender, args.length > 1 ? args[1].toLowerCase() : "");
                break;
            case "netban":
                handleNetBan(sender,
                        args.length > 1 ? args[1].toLowerCase() : "",
                        args.length > 2 ? args[2] : "");
                break;
            // "help" never reaches here — BaseCommand.execute intercepts it and routes to the
            // sendHelp / sendVerbHelp overrides below.
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown subcommand: " + sub));
                sendHelp(sender);
                break;
        }

        return true;
    }

    // ========================================================
    // Subcommand: debug (comprehensive diagnostics)
    // ========================================================

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6=== RVNKCore System Diagnostics ==="));
        sender.sendMessage("");

        // Version information
        sender.sendMessage(ChatFormat.colorize("&aPlugin Version: &f" + rvnkCore.getDescription().getVersion()));
        sender.sendMessage(ChatFormat.colorize("&aAPI Version: &f" + rvnkCore.getDescription().getAPIVersion()));
        sender.sendMessage("");

        // Initialization status
        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return;
        }

        sender.sendMessage(ChatFormat.colorize("&a✓ Core Framework: &fINITIALIZED"));

        // ServiceRegistry status
        ServiceRegistry registry = rvnkCore.getServiceRegistry();
        if (registry != null) {
            String[] services = registry.getRegisteredServices();
            sender.sendMessage(ChatFormat.colorize("&a✓ ServiceRegistry: &f" + services.length + " services registered"));
        } else {
            sender.sendMessage(ChatFormat.colorize("&c✖ ServiceRegistry: NOT AVAILABLE"));
        }

        // Database connectivity (async test)
        try {
            long startMs = System.currentTimeMillis();
            rvnkCore.getPlayerService().getPlayerCount()
                .thenAccept(count -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    sender.sendMessage(ChatFormat.colorize("&a✓ Database: &fConnected (" + elapsed + "ms query, " + count + " players)"));
                })
                .exceptionally(ex -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Database: &fFAILED - " + ex.getMessage()));
                    return null;
                });
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Database: &fNOT CONFIGURED"));
        }

        sender.sendMessage("");

        // RVNK Plugin ecosystem status
        sender.sendMessage(ChatFormat.colorize("&6RVNK Ecosystem:"));
        int loadedCount = 0;
        for (String pluginName : RVNK_PLUGIN_NAMES) {
            Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
            if (p != null && p.isEnabled()) {
                sender.sendMessage(ChatFormat.colorize("&a  ✓ " + pluginName + " &7v" + p.getDescription().getVersion()));
                loadedCount++;
            } else if (p != null) {
                sender.sendMessage(ChatFormat.colorize("&c  ✖ " + pluginName + " &7(disabled)"));
            }
        }
        sender.sendMessage(ChatFormat.colorize("&7  Total: " + loadedCount + "/" + RVNK_PLUGIN_NAMES.length + " plugins loaded"));

        sender.sendMessage("");

        // Server performance
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576; // MB
        long maxMemory = runtime.maxMemory() / 1048576; // MB
        sender.sendMessage(ChatFormat.colorize("&aMemory: &f" + usedMemory + "MB / " + maxMemory + "MB"));

        sender.sendMessage("");
        sender.sendMessage(ChatFormat.colorize("&6=== End Diagnostics ==="));
    }

    // ========================================================
    // Subcommand: services
    // ========================================================

    private void handleServices(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6ServiceRegistry Status"));

        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return;
        }

        ServiceRegistry registry = rvnkCore.getServiceRegistry();
        String[] serviceNames = registry.getRegisteredServices();

        if (serviceNames.length == 0) {
            sender.sendMessage(ChatFormat.colorize("&e⚠ No services registered"));
            return;
        }

        int count = 0;
        for (String serviceName : serviceNames) {
            // Extract simple class name from FQCN
            String simpleName = serviceName.contains(".")
                ? serviceName.substring(serviceName.lastIndexOf('.') + 1)
                : serviceName;
            sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &f" + simpleName));
            count++;
        }

        sender.sendMessage(ChatFormat.colorize("&a✓ Result: " + count + " service(s) registered"));
    }

    // ========================================================
    // Subcommand: db
    // ========================================================

    private void handleDb(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6Database Connectivity Test"));

        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return;
        }

        // Test via PlayerService query (exercises full DB stack)
        try {
            long startMs = System.currentTimeMillis();
            rvnkCore.getPlayerService().getPlayerCount()
                .thenAccept(count -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fConnection: Active"));
                    sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fQuery test: getPlayerCount() = " + count + " (" + elapsed + "ms)"));
                    sender.sendMessage(ChatFormat.colorize("&a✓ Result: Database healthy"));
                })
                .exceptionally(ex -> {
                    sender.sendMessage(ChatFormat.colorize("&7   • &c✖ &fQuery failed: " + ex.getMessage()));
                    sender.sendMessage(ChatFormat.colorize("&c✖ Result: Database unhealthy"));
                    return null;
                });
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to access PlayerService: " + e.getMessage()));
        }

        reportPlayerServerState(sender);
        reportPreferenceRows(sender);
        reportClusterConnectivity(sender);
    }

    /**
     * Views and edits the network-wide ban flag (#1814).
     *
     * <p><b>Why this exists:</b> {@code PlayerBanListener} only ever sets {@code banned = true}, on
     * detecting a kick-due-to-ban. Nothing ever cleared it. Minecraft's {@code /pardon} lifts the
     * local vanilla ban but does not touch the cluster roster, so before this command a network ban
     * was <b>irreversible without a direct database edit</b> — and because
     * {@link org.fourz.rvnkcore.api.event.ClusterBanListener} checks the flag on every server, that
     * included the tier the ban was issued from.</p>
     *
     * <p>Works on offline players: the lookup is by stored name, not by online player.</p>
     *
     * <p>Usage: {@code /rvnkcore netban <add|remove|check> <player>}</p>
     */
    private void handleNetBan(CommandSender sender, String action, String playerName) {
        if (playerName.isEmpty()
                || !(action.equals("add") || action.equals("remove") || action.equals("check"))) {
            sender.sendMessage(ChatFormat.colorize(
                    "&c✖ Usage: /rvnkcore netban <add|remove|check> <player>"));
            sender.sendMessage(ChatFormat.colorize(
                    "&7   Network-wide ban flag. Separate from Minecraft's per-server /ban -"));
            sender.sendMessage(ChatFormat.colorize(
                    "&7   /pardon does NOT clear this, use 'netban remove'."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.fourz.rvnkcore.api.service.PlayerService svc =
                        rvnkCore.getService(org.fourz.rvnkcore.api.service.PlayerService.class);
                var found = svc.getPlayerByName(playerName).get();
                if (found.isEmpty()) {
                    sender.sendMessage(ChatFormat.colorize(
                            "&c✖ No player named '" + playerName + "' in the network roster"));
                    return;
                }
                org.fourz.rvnkcore.api.model.PlayerDTO dto = found.get();

                if (action.equals("check")) {
                    sender.sendMessage(ChatFormat.colorize("&7   " + dto.getCurrentName()
                            + " network ban: " + (dto.isBanned() ? "&cBANNED" : "&anot banned")));
                    return;
                }

                boolean target = action.equals("add");
                if (dto.isBanned() == target) {
                    sender.sendMessage(ChatFormat.colorize("&7   " + dto.getCurrentName()
                            + " is already " + (target ? "banned" : "not banned") + " - no change"));
                    return;
                }

                dto.setBanned(target);
                svc.savePlayer(dto).get();
                sender.sendMessage(ChatFormat.colorize("&a✓ " + dto.getCurrentName()
                        + " network ban " + (target ? "&cSET" : "&aCLEARED")));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Applies on every server in the cluster. Minecraft's own per-server"));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   ban list is unaffected - use /ban and /pardon for that separately."));
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ netban failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Carries this server's local player preferences into the cluster (#1813).
     *
     * <p>Insert-only — see {@code unionPreferencesIntoCluster}. Run after
     * {@code migrate playeridentity} and before enabling
     * {@code cluster.share-player-preferences}.</p>
     */
    private void handleMigratePrefs(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6Unioning player preferences into the cluster..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.fourz.rvnkcore.api.service.PlayerService svc =
                        rvnkCore.getService(org.fourz.rvnkcore.api.service.PlayerService.class);
                int[] r = svc.unionPreferencesIntoCluster();
                sender.sendMessage(ChatFormat.colorize("&a✓ Union complete - &e" + r[0]
                        + " &fpref, &e" + r[1] + " &ftype, &e" + r[2] + " &fchannel row(s) inserted"));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Rows the cluster already had were left as-is - a preference set on two"));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   servers has no objectively correct winner, so none was picked."));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Enable &fcluster.share-player-preferences&7 and restart to cut over."));
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ Union failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Reports preference row counts in the LOCAL database (#1813).
     *
     * <p>Deliberately always queries the local pool, not whichever provider preferences are
     * currently served from. Before the cut-over that is the live data; after it, it is what was
     * left behind — and knowing whether anything was stranded is the whole question. A count taken
     * from the active provider would report the cluster's rows either way and answer nothing.</p>
     */
    private void reportPreferenceRows(CommandSender sender) {
        try (java.sql.Connection conn = rvnkCore.getService(
                org.fourz.rvnkcore.database.connection.ConnectionProvider.class).getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(
                 "SELECT (SELECT COUNT(*) FROM rvnk_player_preferences) AS prefs, "
                 + "(SELECT COUNT(*) FROM rvnk_player_notification_types) AS types, "
                 + "(SELECT COUNT(*) FROM rvnk_player_notification_channels) AS channels")) {
            if (rs.next()) {
                sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &flocal preferences: &e"
                        + rs.getLong("prefs") + " &fpref, &e" + rs.getLong("types")
                        + " &ftype, &e" + rs.getLong("channels") + " &fchannel row(s)"));
            }
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7   • &8- &7local preferences: unavailable (" + e.getMessage() + ")"));
        }
    }

    /**
     * One-shot data migrations. Currently: {@code /rvnkcore migrate playerstate} (#1812).
     *
     * <p>Run as a command rather than automatically at startup so it is an explicit, logged,
     * operator-timed action on live player data — and so it can be run per tier in a chosen order
     * instead of firing on whichever server happens to restart first.</p>
     */
    private void handleMigrate(CommandSender sender, String what) {
        if ("playeridentity".equals(what)) {
            handleMigrateIdentity(sender);
            return;
        }
        if ("playerprefs".equals(what)) {
            handleMigratePrefs(sender);
            return;
        }
        if (!"playerstate".equals(what)) {
            sender.sendMessage(ChatFormat.colorize(
                    "&c✖ Usage: /rvnkcore migrate <playerstate|playeridentity|playerprefs>"));
            sender.sendMessage(ChatFormat.colorize(
                    "&7   playerstate    - copy per-server activity into rvnk_player_server_state"));
            sender.sendMessage(ChatFormat.colorize(
                    "&7   playeridentity - union this server's players into the cluster roster"));
            sender.sendMessage(ChatFormat.colorize(
                    "&7   Both are idempotent and safe with players online."));
            return;
        }

        sender.sendMessage(ChatFormat.colorize("&c▶ &6Backfilling per-server player state..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.fourz.rvnkcore.api.service.PlayerService svc =
                        rvnkCore.getService(org.fourz.rvnkcore.api.service.PlayerService.class);
                int inserted = svc.backfillServerState();
                sender.sendMessage(ChatFormat.colorize("&a✓ Backfill complete - &e" + inserted
                        + " &frow(s) inserted"));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Existing rows were left untouched (they are newer than the legacy columns)."));
                sender.sendMessage(ChatFormat.colorize("&7   Run &f/rvnkcore db&7 to confirm the totals."));
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ Backfill failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Unions this server's player identity into the cluster roster (#1812).
     *
     * <p>Prints every change rather than just totals: this rewrites live player records, and an
     * operator should be able to see exactly which players were inserted and whose
     * {@code first_join} moved, without going to the database to find out.</p>
     */
    private void handleMigrateIdentity(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6Unioning player identity into the cluster..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.fourz.rvnkcore.api.service.PlayerService svc =
                        rvnkCore.getService(org.fourz.rvnkcore.api.service.PlayerService.class);
                var r = svc.unionIdentityIntoCluster();
                for (String note : r.notes) {
                    sender.sendMessage(ChatFormat.colorize("&7   " + note));
                }
                sender.sendMessage(ChatFormat.colorize("&a✓ Union complete - examined &e" + r.examined
                        + "&f, inserted &e" + r.inserted
                        + "&f, first_join corrected &e" + r.firstJoinCorrected));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Nothing was deleted and no name was overwritten."));
                sender.sendMessage(ChatFormat.colorize(
                        "&7   Enable &fcluster.share-player-identity&7 and restart to cut over."));
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ Union failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Reports the per-server player activity mirror (#1811).
     *
     * <p>During the additive phase nothing reads {@code rvnk_player_server_state}, so without this
     * there is no way to tell a working dual-write from a silently failing one until #1812 cuts
     * reads over and finds the table empty. Prints the row count and the newest {@code last_seen}
     * so the mirror can be confirmed to be filling before anything depends on it.</p>
     */
    private void reportPlayerServerState(CommandSender sender) {
        try (java.sql.Connection conn = rvnkCore.getService(
                org.fourz.rvnkcore.database.connection.ConnectionProvider.class).getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) AS n, COUNT(DISTINCT server_id) AS servers, MAX(last_seen) AS newest "
                 + "FROM rvnk_player_server_state")) {
            if (rs.next()) {
                sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fper-server state: &e"
                        + rs.getLong("n") + " &frow(s) across &e" + rs.getInt("servers")
                        + " &fserver(s), newest &7" + rs.getString("newest")));
            }
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7   • &c✖ &fper-server state unavailable: " + e.getMessage()));
        }
    }

    /**
     * Reports cluster-shared database state, if clustering is enabled (#1796 Phase 2).
     *
     * <p>Deliberately runs a <b>real query against the cluster tables</b> and prints the row counts
     * rather than reporting "connected". A provider can hold a valid connection and still be aimed
     * at the wrong database or a schema that was never created — a status line would look identical
     * in every one of those cases. Phase 1 produced two bugs that reported success while the data
     * was not where it claimed to be (#1797, #1804); the counts here are what actually distinguish
     * a working cluster from a plausible-looking one.</p>
     */
    private void reportClusterConnectivity(CommandSender sender) {
        ClusterConnectionProvider cluster;
        try {
            cluster = rvnkCore.getService(ClusterConnectionProvider.class);
        } catch (Exception e) {
            cluster = null;
        }

        if (cluster == null) {
            sender.sendMessage(ChatFormat.colorize("&7   • &8- &7Cluster: disabled (all data local)"));
            return;
        }

        sender.sendMessage(ChatFormat.colorize("&c▶ &6Cluster-Shared Database"));
        sender.sendMessage(ChatFormat.colorize("&7   • &f Role: &b"
                + (cluster.isAuthoritative() ? "authoritative" : "member")
                + " &7- " + cluster.describeTarget()));

        long startMs = System.currentTimeMillis();
        try (java.sql.Connection conn = cluster.getConnection()) {
            for (String table : org.fourz.rvnkcore.database.schema.DatabaseSetup.CLUSTER_SHARED_TABLES) {
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    long rows = rs.next() ? rs.getLong(1) : -1;
                    sender.sendMessage(ChatFormat.colorize(
                            "&7   • &a✓ &f" + table + ": &e" + rows + " &frow(s)"));
                } catch (java.sql.SQLException e) {
                    sender.sendMessage(ChatFormat.colorize(
                            "&7   • &c✖ &f" + table + ": " + e.getMessage()));
                }
            }
            sender.sendMessage(ChatFormat.colorize("&a✓ Result: Cluster reachable ("
                    + (System.currentTimeMillis() - startMs) + "ms)"));
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Result: Cluster unreachable - " + e.getMessage()));
            sender.sendMessage(ChatFormat.colorize("&7   Local database is unaffected."));
        }
    }

    // ========================================================
    // Subcommand: version
    // ========================================================

    private void handleVersion(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6=== RVNKCore Version ==="));
        sender.sendMessage("");
        sender.sendMessage(ChatFormat.colorize("&aVersion: &f" + rvnkCore.getDescription().getVersion()));
        sender.sendMessage(ChatFormat.colorize("&aAPI Version: &f" + rvnkCore.getDescription().getAPIVersion()));
        sender.sendMessage(ChatFormat.colorize("&aAuthors: &f" + String.join(", ", rvnkCore.getDescription().getAuthors())));
        sender.sendMessage(ChatFormat.colorize("&aWebsite: &f" + rvnkCore.getDescription().getWebsite()));
        sender.sendMessage("");
        sender.sendMessage(ChatFormat.colorize("&7Part of the Ravenkraft Network plugin ecosystem"));
    }

    // ========================================================
    // Subcommand: reload
    // ========================================================

    private void handleReload(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Reloading RVNKCore configuration..."));

        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return;
        }

        try {
            rvnkCore.reloadConfig();
            sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fConfig reloaded"));

            // Push refreshed config into the cross-server services so transfer/portal/chat-relay
            // changes take effect without a full restart (#1743). Previously reload only re-read
            // config.yml while these services kept the config captured at construction.
            org.bukkit.configuration.file.FileConfiguration cfg = rvnkCore.getConfig();
            ServiceRegistry reg = rvnkCore.getServiceRegistry();
            int refreshed = 0;
            if (reg.hasService(org.fourz.rvnkcore.service.transfer.TransferService.class)) {
                reg.getService(org.fourz.rvnkcore.service.transfer.TransferService.class).refreshConfig(
                    org.fourz.rvnkcore.api.config.TransferConfig.fromConfigurationSection(
                        cfg.getConfigurationSection("transfer")));
                refreshed++;
            }
            if (reg.hasService(org.fourz.rvnkcore.service.portal.PortalService.class)) {
                reg.getService(org.fourz.rvnkcore.service.portal.PortalService.class).refreshConfig(
                    org.fourz.rvnkcore.api.config.PortalConfig.fromConfigurationSection(
                        cfg.getConfigurationSection("portal")));
                refreshed++;
            }
            if (reg.hasService(org.fourz.rvnkcore.service.chatrelay.ChatRelayService.class)) {
                reg.getService(org.fourz.rvnkcore.service.chatrelay.ChatRelayService.class).refreshConfig(
                    org.fourz.rvnkcore.api.config.ChatRelayConfig.fromConfigurationSection(
                        cfg.getConfigurationSection("chat-relay")));
                refreshed++;
            }
            if (reg.hasService(org.fourz.rvnkcore.service.presence.PresenceService.class)) {
                reg.getService(org.fourz.rvnkcore.service.presence.PresenceService.class).refreshConfig(
                    org.fourz.rvnkcore.api.config.ChatRelayConfig.fromConfigurationSection(
                        cfg.getConfigurationSection("chat-relay")));
                refreshed++;
            }
            sender.sendMessage(ChatFormat.colorize(
                "&7   • &a✓ &fCross-server services refreshed: " + refreshed));

            // Verify services survived
            String[] services = rvnkCore.getServiceRegistry().getRegisteredServices();
            sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fServices still active: " + services.length));

            sender.sendMessage(ChatFormat.colorize("&a✓ Result: Reload successful"));
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Reload failed: " + e.getMessage()));
        }
    }

    // ========================================================
    // Subcommand: plugins
    // ========================================================

    private void handlePlugins(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6RVNK Plugin Status"));

        int loaded = 0;
        int notLoaded = 0;

        for (String name : RVNK_PLUGIN_NAMES) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                String version = p.getDescription().getVersion();
                String extra = "";
                if (name.equals("RVNKCore") && rvnkCore.isInitialized()) {
                    extra = ", initialized";
                }
                sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &f" + name + " v" + version + "&7 (enabled" + extra + ")"));
                loaded++;
            } else if (p != null) {
                // Present but disabled IS a fault — it means the jar loaded and then failed, so this
                // one keeps a warning colour.
                sender.sendMessage(ChatFormat.colorize("&7   • &e⚠ &f" + name + "&7 (loaded but disabled)"));
                notLoaded++;
            } else {
                // Neutral, not a red failure. Several absences are deliberate — RVNKWorlds is never
                // installed on production by design, and not every tier runs every plugin — so a red
                // cross here reported healthy servers as broken.
                sender.sendMessage(ChatFormat.colorize("&7   • &8- &7" + name + " &8(not installed)"));
                notLoaded++;
            }
        }

        sender.sendMessage(ChatFormat.colorize("&a✓ Result: " + loaded + " loaded, " + notLoaded + " not installed"));

        handleResourcePack(sender);
    }

    /**
     * Report the server resource pack, read from the running server rather than any hardcoded name.
     *
     * <p>Nothing here names a specific pack. The previous code hardcoded {@code MickyHats} as if it
     * were a plugin, which meant renaming or replacing the pack would have needed a code change and
     * meanwhile produced a permanent false failure. Reading {@code getResourcePack()} reflects
     * whatever {@code server.properties} actually sets, including nothing at all.</p>
     */
    private void handleResourcePack(CommandSender sender) {
        String url;
        boolean required;
        String hash;
        try {
            url = Bukkit.getResourcePack();
            required = Bukkit.isResourcePackRequired();
            hash = Bukkit.getResourcePackHash();
        } catch (Throwable t) {
            // Never let a diagnostics command die on an optional API.
            sender.sendMessage(ChatFormat.colorize("&c▶ &6Resource Pack"));
            sender.sendMessage(ChatFormat.colorize("&7   • &e⚠ unavailable: " + t.getClass().getSimpleName()));
            return;
        }

        sender.sendMessage(ChatFormat.colorize("&c▶ &6Resource Pack"));

        if (url == null || url.isBlank()) {
            sender.sendMessage(ChatFormat.colorize("&7   • &8- none configured"));
            return;
        }

        String file = url;
        String host = "";
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                file = path.substring(slash + 1);
            }
            if (uri.getHost() != null) {
                host = uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
            // Not a parseable URI — fall back to showing the raw value.
        }

        // required=false means players are prompted and may decline, so the pack is NOT guaranteed
        // to be applied. Saying "enforced" when it is optional would misreport what players see.
        String enforcement = required ? "&crequired" : "&7optional";
        sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &f" + file + " &8(" + enforcement + "&8)"));
        if (!host.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&8     " + host));
        }
        if (hash != null && !hash.isBlank()) {
            String shortHash = hash.length() > 8 ? hash.substring(0, 8) : hash;
            sender.sendMessage(ChatFormat.colorize("&8     sha1 " + shortHash));
        } else {
            sender.sendMessage(ChatFormat.colorize("&8     &eno sha1 - clients re-download every join"));
        }
    }

    // ========================================================
    // Subcommand: commands
    // ========================================================

    private void handleCommands(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6CommandManager Status"));

        CommandManager cm = CommandManager.getInstance();
        if (cm == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ CommandManager not initialized"));
            return;
        }

        sender.sendMessage(ChatFormat.colorize("&7" + cm.getDebugInfo()));
        sender.sendMessage(ChatFormat.colorize("&a✓ Result: " + cm.getCommandCount() + " commands registered"));
    }

    // ========================================================
    // Subcommand: health
    // ========================================================

    private void handleHealth(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6RVNK Health Check"));

        // Server info
        String serverVersion = Bukkit.getVersion();
        String javaVersion = System.getProperty("java.version");
        sender.sendMessage(ChatFormat.colorize("&7   Server: &f" + serverVersion));
        sender.sendMessage(ChatFormat.colorize("&7   Java: &f" + javaVersion));

        // Memory
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        double pct = (double) usedMb / maxMb * 100.0;
        sender.sendMessage(ChatFormat.colorize("&7   Memory: &f" + usedMb + "/" + maxMb + " MB (" + String.format("%.1f", pct) + "%)"));

        // Core status
        sender.sendMessage(ChatFormat.colorize("&7   RVNKCore: &f" + (rvnkCore.isInitialized() ? "&ainitialized" : "&cnot initialized")));

        // Services
        if (rvnkCore.isInitialized()) {
            int svcCount = rvnkCore.getServiceRegistry().getRegisteredServices().length;
            sender.sendMessage(ChatFormat.colorize("&7   Services: &f" + svcCount + " registered"));
        }

        // Commands
        CommandManager cm = CommandManager.getInstance();
        if (cm != null) {
            sender.sendMessage(ChatFormat.colorize("&7   Commands: &f" + cm.getCommandCount() + " registered"));
        }

        // Plugins
        int rvnkLoaded = 0;
        for (String name : RVNK_PLUGIN_NAMES) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) rvnkLoaded++;
        }
        sender.sendMessage(ChatFormat.colorize("&7   RVNK Plugins: &f" + rvnkLoaded + "/" + RVNK_PLUGIN_NAMES.length + " loaded"));

        // Database (async)
        if (rvnkCore.isInitialized()) {
            try {
                long startMs = System.currentTimeMillis();
                rvnkCore.getPlayerService().getPlayerCount()
                    .thenAccept(count -> {
                        long elapsed = System.currentTimeMillis() - startMs;
                        sender.sendMessage(ChatFormat.colorize("&7   Database: &a✓ connected &7(" + elapsed + "ms, " + count + " players)"));
                        sender.sendMessage(ChatFormat.colorize("&a✓ Status: HEALTHY"));
                    })
                    .exceptionally(ex -> {
                        sender.sendMessage(ChatFormat.colorize("&7   Database: &c✖ " + ex.getMessage()));
                        sender.sendMessage(ChatFormat.colorize("&e⚠ Status: DEGRADED"));
                        return null;
                    });
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&7   Database: &c✖ " + e.getMessage()));
                sender.sendMessage(ChatFormat.colorize("&c✖ Status: UNHEALTHY"));
            }
        } else {
            sender.sendMessage(ChatFormat.colorize("&c✖ Status: UNHEALTHY (core not initialized)"));
        }
    }

    // ========================================================
    // Subcommand: test <suite>
    // ========================================================

    private void handleTest(CommandSender sender, String suite) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6Running test suite: " + suite));
        sender.sendMessage("");

        List<TestResult> results = new ArrayList<>();

        switch (suite) {
            case "all":
                results.add(testCoreInit(sender));
                results.add(testServices(sender));
                results.add(testCommands(sender));
                results.add(testPlugins(sender));
                results.add(testMemory(sender));
                // DB test is async — run last and handle summary in callback
                testDbAsync(sender, results);
                return; // async path handles summary
            case "services":
                results.add(testCoreInit(sender));
                results.add(testServices(sender));
                break;
            case "db":
                results.add(testCoreInit(sender));
                testDbAsync(sender, results);
                return; // async path handles summary
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown suite: " + suite + ". Available: all, services, db"));
                return;
        }

        printSummary(sender, results);
    }

    private TestResult testCoreInit(CommandSender sender) {
        boolean pass = rvnkCore.isInitialized();
        String label = "Core initialized";
        sender.sendMessage(formatCheck(1, label, pass, pass ? "ready" : "NOT initialized"));
        return new TestResult(label, pass);
    }

    private TestResult testServices(CommandSender sender) {
        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(formatCheck(2, "Services", false, "core not init"));
            return new TestResult("Services", false);
        }
        int count = rvnkCore.getServiceRegistry().getRegisteredServices().length;
        boolean pass = count > 0;
        sender.sendMessage(formatCheck(2, "Services", pass, count + " registered"));
        return new TestResult("Services", pass);
    }

    private TestResult testCommands(CommandSender sender) {
        CommandManager cm = CommandManager.getInstance();
        boolean pass = cm != null && cm.getCommandCount() > 0;
        int count = cm != null ? cm.getCommandCount() : 0;
        sender.sendMessage(formatCheck(3, "Commands", pass, count + " registered"));
        return new TestResult("Commands", pass);
    }

    private TestResult testPlugins(CommandSender sender) {
        int loaded = 0;
        for (String name : RVNK_PLUGIN_NAMES) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) loaded++;
        }
        boolean pass = loaded > 0;
        sender.sendMessage(formatCheck(4, "Plugins", pass, loaded + "/" + RVNK_PLUGIN_NAMES.length));
        return new TestResult("Plugins", pass);
    }

    private TestResult testMemory(CommandSender sender) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        double pct = (double) usedMb / maxMb * 100.0;
        boolean pass = pct < 90.0;
        sender.sendMessage(formatCheck(5, "Memory", pass, String.format("%.1f%%", pct)));
        return new TestResult("Memory", pass);
    }

    private void testDbAsync(CommandSender sender, List<TestResult> results) {
        if (!rvnkCore.isInitialized()) {
            int idx = results.size() + 1;
            sender.sendMessage(formatCheck(idx, "Database", false, "core not init"));
            results.add(new TestResult("Database", false));
            printSummary(sender, results);
            return;
        }

        int idx = results.size() + 1;
        long startMs = System.currentTimeMillis();

        try {
            rvnkCore.getPlayerService().getPlayerCount()
                .thenAccept(count -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    sender.sendMessage(formatCheck(idx, "Database", true, elapsed + "ms"));
                    results.add(new TestResult("Database", true));
                    printSummary(sender, results);
                })
                .exceptionally(ex -> {
                    sender.sendMessage(formatCheck(idx, "Database", false, ex.getMessage()));
                    results.add(new TestResult("Database", false));
                    printSummary(sender, results);
                    return null;
                });
        } catch (Exception e) {
            sender.sendMessage(formatCheck(idx, "Database", false, e.getMessage()));
            results.add(new TestResult("Database", false));
            printSummary(sender, results);
        }
    }

    private String formatCheck(int num, String label, boolean pass, String detail) {
        String icon = pass ? "&a✓ PASS" : "&c✖ FAIL";
        return ChatFormat.colorize("&7   [" + num + "] " + label + ": " + icon + " &7(" + detail + ")");
    }

    private void printSummary(CommandSender sender, List<TestResult> results) {
        sender.sendMessage("");
        long passed = results.stream().filter(r -> r.pass).count();
        long total = results.size();
        String color = passed == total ? "&a" : "&e";
        sender.sendMessage(ChatFormat.colorize(color + "═══ Result: " + passed + "/" + total + " PASSED ═══"));
    }

    // ========================================================
    // Subcommand: mojang <operation> [args]
    // ========================================================

    private void handleMojang(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendMojangHelp(sender);
            return;
        }

        // Lazy-load MojangAPI from ServiceRegistry
        if (mojangAPI == null) {
            try {
                ServiceRegistry registry = rvnkCore.getServiceRegistry();
                mojangAPI = registry.getService(MojangAPI.class);
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ MojangAPI service not available: " + e.getMessage()));
                return;
            }
        }

        String operation = args[0].toLowerCase();

        switch (operation) {
            case "name":
                if (args.length < 2) {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Usage: /rvnkcore mojang name <username>"));
                    return;
                }
                testNameToUuid(sender, args[1]);
                break;
            case "uuid":
                if (args.length < 2) {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Usage: /rvnkcore mojang uuid <uuid>"));
                    return;
                }
                testUuidToName(sender, args[1]);
                break;
            case "verify":
                if (args.length < 2) {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Usage: /rvnkcore mojang verify <username|uuid>"));
                    return;
                }
                testVerify(sender, args[1]);
                break;
            case "stats":
                showMojangStats(sender);
                break;
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown mojang operation: " + operation));
                sendMojangHelp(sender);
                break;
        }
    }

    private void testNameToUuid(CommandSender sender, String username) {
        sender.sendMessage(ChatFormat.colorize("&6[MojangAPI] &fResolving username: &e" + username));

        long startTime = System.currentTimeMillis();

        mojangAPI.getUuidByName(username).thenAccept(optUuid -> {
            long elapsed = System.currentTimeMillis() - startTime;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (optUuid.isPresent()) {
                    sender.sendMessage(ChatFormat.colorize("&a[MojangAPI] &fResolved: &e" + username + " &f-> &b" + optUuid.get()));
                } else {
                    sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fPlayer not found: &e" + username));
                }
                sender.sendMessage(ChatFormat.colorize("&7[MojangAPI] &7Took " + elapsed + "ms"));
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fError: " + ex.getMessage()));
            });
            return null;
        });
    }

    private void testUuidToName(CommandSender sender, String uuidStr) {
        // Validate UUID format
        if (!MojangAPI.isValidUuidFormat(uuidStr)) {
            sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fInvalid UUID format: &e" + uuidStr));
            return;
        }

        java.util.Optional<UUID> parsedUuid = MojangAPI.parseUuid(uuidStr);
        if (parsedUuid.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fFailed to parse UUID: &e" + uuidStr));
            return;
        }

        UUID uuid = parsedUuid.get();
        sender.sendMessage(ChatFormat.colorize("&6[MojangAPI] &fResolving UUID: &b" + uuid));

        long startTime = System.currentTimeMillis();

        mojangAPI.getNameByUuid(uuid).thenAccept(optName -> {
            long elapsed = System.currentTimeMillis() - startTime;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (optName.isPresent()) {
                    sender.sendMessage(ChatFormat.colorize("&a[MojangAPI] &fResolved: &b" + uuid + " &f-> &e" + optName.get()));
                } else {
                    sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fUUID not found: &b" + uuid));
                }
                sender.sendMessage(ChatFormat.colorize("&7[MojangAPI] &7Took " + elapsed + "ms"));
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fError: " + ex.getMessage()));
            });
            return null;
        });
    }

    private void testVerify(CommandSender sender, String value) {
        // Check if it's a UUID or username
        if (MojangAPI.isValidUuidFormat(value)) {
            java.util.Optional<UUID> parsedUuid = MojangAPI.parseUuid(value);
            if (parsedUuid.isEmpty()) {
                sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fFailed to parse UUID"));
                return;
            }

            UUID uuid = parsedUuid.get();
            sender.sendMessage(ChatFormat.colorize("&6[MojangAPI] &fVerifying UUID: &b" + uuid));

            mojangAPI.verifyUuid(uuid).thenAccept(valid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (valid) {
                        sender.sendMessage(ChatFormat.colorize("&a[MojangAPI] &fUUID is &aVALID &f(exists in Mojang database)"));
                    } else {
                        sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fUUID is &cINVALID &f(not found in Mojang database)"));
                    }
                });
            });
        } else if (MojangAPI.isValidUsername(value)) {
            sender.sendMessage(ChatFormat.colorize("&6[MojangAPI] &fVerifying username: &e" + value));

            mojangAPI.verifyUsername(value).thenAccept(valid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (valid) {
                        sender.sendMessage(ChatFormat.colorize("&a[MojangAPI] &fUsername is &aVALID &f(exists in Mojang database)"));
                    } else {
                        sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fUsername is &cINVALID &f(not found in Mojang database)"));
                    }
                });
            });
        } else {
            sender.sendMessage(ChatFormat.colorize("&c[MojangAPI] &fInvalid format. Provide a valid UUID or username (3-16 chars, alphanumeric + _)"));
        }
    }

    private void showMojangStats(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6[MojangAPI] &fRate Limiter Stats:"));
        sender.sendMessage(ChatFormat.colorize("&7  Remaining requests: &f" + mojangAPI.getRemainingRequests()));
        sender.sendMessage(ChatFormat.colorize("&7  Name cache size: &f" + mojangAPI.getNameCacheSize()));
        sender.sendMessage(ChatFormat.colorize("&7  UUID cache size: &f" + mojangAPI.getUuidCacheSize()));
        sender.sendMessage(ChatFormat.colorize("&7  Source: &fServiceRegistry (shared instance)"));
    }

    // ========================================================
    // Help
    // ========================================================

    /**
     * The verb index, generated from {@link #VERB_EXAMPLES} so it cannot drift.
     *
     * <p>The previous hand-written list omitted {@code migrate}, {@code netban} and {@code help} —
     * three of the twelve verbs — for the same reason every hand-kept list in this repo has
     * drifted: nothing makes you update it when the switch gains a case. Adding a verb to
     * {@code VERB_EXAMPLES} now adds it here, and {@code /rvnkcore help <verb>} serves it. (#1981)</p>
     */
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6=== RVNKCore Commands ==="));
        for (java.util.Map.Entry<String, java.util.List<String>> e : VERB_EXAMPLES.entrySet()) {
            java.util.List<String> body = e.getValue();
            String note = "";
            for (String line : body) {
                if (line.startsWith("  ")) {
                    note = " &7- " + line.trim();
                    break;
                }
            }
            sender.sendMessage(ChatFormat.colorize("&e/rvnkcore " + e.getKey() + note));
        }
        sender.sendMessage(ChatFormat.colorize(
                "&7Examples for one verb: &f/rvnkcore help <verb>"));
    }

    private void sendMojangHelp(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6=== Mojang API Commands ==="));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore mojang name <username> &7- Resolve username to UUID"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore mojang uuid <uuid> &7- Resolve UUID to username"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore mojang verify <username|uuid> &7- Verify player exists"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore mojang stats &7- Show rate limiter stats"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("debug", "services", "db", "database", "version",
                                "reload", "plugins", "commands", "health", "test", "mojang");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("test")) {
                return Arrays.asList("all", "services", "db");
            } else if (args[0].equalsIgnoreCase("mojang")) {
                return Arrays.asList("name", "uuid", "verify", "stats");
            }
        }
        return Collections.emptyList();
    }

    // ========================================================
    // Internal types
    // ========================================================

    private static class TestResult {
        final String name;
        final boolean pass;

        TestResult(String name, boolean pass) {
            this.name = name;
            this.pass = pass;
        }
    }
}
