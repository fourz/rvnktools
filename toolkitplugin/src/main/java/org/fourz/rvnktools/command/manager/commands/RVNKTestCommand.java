package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.util.ChatFormat;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Automated test command for RVNK plugin ecosystem.
 *
 * Console-first design: all subcommands work from server console for
 * automated testing via rvnkdev-mcp-server MCP tools.
 *
 * Subcommands:
 *   services  - List all registered services in ServiceRegistry
 *   db        - Test database connectivity
 *   plugins   - List loaded RVNK plugins and their status
 *   reload    - Reload RVNKCore config without server restart
 *   commands  - List all registered CommandManager commands
 *   health    - Full health check (services + db + memory)
 *   run [suite] - Run automated test suite (all, services, db)
 *
 * @since 1.4.0
 */
public class RVNKTestCommand extends BaseCommand {

    private static final String[] RVNK_PLUGIN_NAMES = {
        "RVNKCore", "RVNKWorlds", "RVNKLore", "RVNKQuests",
        "BarterShops", "TokenEconomy", "MickyHats"
    };

    private final RVNKCore rvnkCore;

    public RVNKTestCommand(RVNKCore plugin) {
        super(plugin, "rvnktest",
              "Automated test commands for RVNK plugin ecosystem",
              "/rvnktest <services|db|plugins|reload|commands|health|run> [args]",
              "rvnktools.admin.test");
        this.rvnkCore = plugin;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "services":
                handleServices(sender);
                break;
            case "db":
                handleDb(sender);
                break;
            case "plugins":
                handlePlugins(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "commands":
                handleCommands(sender);
                break;
            case "health":
                handleHealth(sender);
                break;
            case "run":
                String suite = args.length > 1 ? args[1].toLowerCase() : "all";
                handleRun(sender, suite);
                break;
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown subcommand: " + sub));
                sendHelp(sender);
                break;
        }

        return true;
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
                sender.sendMessage(ChatFormat.colorize("&7   • &e⚠ &f" + name + "&7 (loaded but disabled)"));
                notLoaded++;
            } else {
                sender.sendMessage(ChatFormat.colorize("&7   • &c✖ &f" + name + "&7 (not loaded)"));
                notLoaded++;
            }
        }

        sender.sendMessage(ChatFormat.colorize("&a✓ Result: " + loaded + " loaded, " + notLoaded + " not available"));
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

            // Verify services survived
            String[] services = rvnkCore.getServiceRegistry().getRegisteredServices();
            sender.sendMessage(ChatFormat.colorize("&7   • &a✓ &fServices still active: " + services.length));

            sender.sendMessage(ChatFormat.colorize("&a✓ Result: Reload successful"));
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Reload failed: " + e.getMessage()));
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
    // Subcommand: run <suite>
    // ========================================================

    private void handleRun(CommandSender sender, String suite) {
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
    // Help
    // ========================================================

    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6RVNK Test Commands"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest services &7- List registered services"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest db &7- Test database connectivity"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest plugins &7- List RVNK plugin status"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest reload &7- Reload RVNKCore config"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest commands &7- List CommandManager state"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest health &7- Full health check"));
        sender.sendMessage(ChatFormat.colorize("&7   &f/rvnktest run [all|services|db] &7- Run test suite"));
    }

    // ========================================================
    // Tab Completion
    // ========================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("services", "db", "plugins", "reload", "commands", "health", "run");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("run")) {
            return Arrays.asList("all", "services", "db");
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
