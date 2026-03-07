package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
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

    private static final String[] RVNK_PLUGIN_NAMES = {
        "RVNKCore", "RVNKWorlds", "RVNKLore", "RVNKQuests",
        "BarterShops", "TokenEconomy", "MickyHats"
    };

    private final RVNKCore rvnkCore;
    private MojangAPI mojangAPI; // Lazy-loaded from ServiceRegistry

    public RVNKCoreCommand(RVNKCore plugin) {
        super(plugin, "rvnkcore",
              "RVNKCore diagnostics, testing, and system information",
              "/rvnkcore <debug|services|db|version|reload|plugins|commands|health|test|mojang> [args]",
              "rvnktools.admin.test");
        this.rvnkCore = plugin;
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

    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6=== RVNKCore Commands ==="));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore debug &7- Comprehensive system diagnostics"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore services &7- List registered services"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore db &7- Test database connectivity"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore version &7- Show version information"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore reload &7- Reload configuration"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore plugins &7- List RVNK plugin status"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore commands &7- Show CommandManager state"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore health &7- Full system health check"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore test [all|services|db] &7- Run test suite"));
        sender.sendMessage(ChatFormat.colorize("&e/rvnkcore mojang <op> [args] &7- Mojang API operations"));
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
