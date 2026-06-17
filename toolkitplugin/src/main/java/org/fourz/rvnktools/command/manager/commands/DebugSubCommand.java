package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.CommandManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for debugging and diagnostic information.
 * Provides information about plugin state, loaded components, and system status.
 */
public class DebugSubCommand extends BaseSubCommand {

    public DebugSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "debug",
              "Show debug information about the plugin",
              "/rvnktools debug [setup]",
              "rvnktools.admin.debug", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("setup")) {
            return executeSetup(sender);
        }

        sender.sendMessage("§c▶ §6RVNKTools Debug Information");
        sender.sendMessage("");

        // Plugin information
        sender.sendMessage("§ePlugin Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§ePlugin Enabled: §f" + plugin.isEnabled());

        // Command Manager information
        CommandManager commandManager = CommandManager.getInstance();
        if (commandManager != null) {
            sender.sendMessage("§eRegistered Commands: §f" + commandManager.getCommandCount());
            sender.sendMessage("§eCommand Manager Status: §aInitialized");
        } else {
            sender.sendMessage("§eCommand Manager Status: §cNot initialized");
        }

        // Component status
        sender.sendMessage("");
        sender.sendMessage("§6Component Status:");
        sender.sendMessage("§eLinkMaker: §f" + (plugin.getLinkMaker() != null ? "§aLoaded" : "§cNot loaded"));

        // Dependency status
        sender.sendMessage("");
        sender.sendMessage("§6Dependencies:");
        sender.sendMessage("§ePlaceholderAPI: §f" + (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null ? "§aLoaded" : "§cNot loaded"));
        sender.sendMessage("§eMultiverse-Core: §f" + (plugin.getServer().getPluginManager().getPlugin("Multiverse-Core") != null ? "§aLoaded" : "§cNot loaded"));
        sender.sendMessage("§eVault: §f" + (plugin.getServer().getPluginManager().getPlugin("Vault") != null ? "§aLoaded" : "§cNot loaded"));

        // Memory information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage("");
        sender.sendMessage("§6System Information:");
        sender.sendMessage("§eMemory Usage: §f" + usedMemory + "MB / " + totalMemory + "MB (Max: " + maxMemory + "MB)");
        sender.sendMessage("§eJava Version: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7Use §f/rvnktools debug setup§7 to bootstrap LuckPerms permission defaults.");

        logger.info("Debug information displayed to " + sender.getName());
        return true;
    }

    private boolean executeSetup(CommandSender sender) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            sender.sendMessage("§c✖ LuckPerms is not installed — cannot apply permission defaults.");
            sender.sendMessage("§7Install LuckPerms and run this command again.");
            return true;
        }

        sender.sendMessage("§6=== RVNKCore Permission Setup ===");
        sender.sendMessage("§7Applying LuckPerms defaults...");

        String[][] assignments = {
            {"admin",   "rvnktools.admin.*",              "true"},
            {"admin",   "rvnktools.announce.*",           "true"},
            {"default", "rvnktools.command.tpa",          "true"},
            {"default", "rvnktools.command.tpahere",      "true"},
            {"default", "rvnktools.command.tpaccept",     "true"},
            {"default", "rvnktools.command.tpdeny",       "true"},
            {"default", "rvnktools.command.back",         "true"},
            {"default", "rvnktools.command.tp",           "true"},
            {"default", "rvnkcore.prefs",                 "true"},
        };

        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        int ok = 0, fail = 0;

        for (String[] row : assignments) {
            String cmd = "lp group " + row[0] + " permission set " + row[1] + " " + row[2];
            try {
                Bukkit.dispatchCommand(console, cmd);
                sender.sendMessage("§a✓ §7" + row[0] + " §8← §f" + row[1]);
                ok++;
            } catch (Exception e) {
                logger.error("LuckPerms command failed: " + cmd, e);
                sender.sendMessage("§c✖ §7Failed to apply: §f" + row[0] + " §8← §f" + row[1]);
                fail++;
            }
        }

        sender.sendMessage("§7Done. §f" + ok + " applied" +
                (fail > 0 ? "§c, " + fail + " failed" : "") + ".");
        sender.sendMessage("§8Run §7lp editor§8 to review or adjust group assignments.");
        return true;
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setup");
        }
        return Collections.emptyList();
    }
}
