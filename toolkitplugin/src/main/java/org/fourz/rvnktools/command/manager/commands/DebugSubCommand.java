package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.CommandManager;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for debugging and diagnostic information.
 * Provides information about plugin state, loaded components, and system status.
 */
public class DebugSubCommand extends BaseSubCommand {
    
    public DebugSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "debug", 
              "Show debug information about the plugin", 
              "/rvnktools debug",
              "rvnktools.admin.debug", false);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
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
        sender.sendMessage("§eLinkMaker: §f" + (plugin.linkMaker != null ? "§aLoaded" : "§cNot loaded"));
        sender.sendMessage("§eCycle Commands: §f" + (plugin.getCycleCommands() != null ? "§aLoaded" : "§cNot loaded"));
        
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
        
        logger.info("Debug information displayed to " + sender.getName());
        return true;
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
