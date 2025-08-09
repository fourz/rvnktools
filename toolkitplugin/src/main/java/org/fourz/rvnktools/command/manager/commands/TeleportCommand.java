package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Main teleport command that serves as a container for teleportation-related subcommands.
 * Provides organized access to world swapping and other teleportation utilities.
 */
public class TeleportCommand extends BaseCommand {
    
    public TeleportCommand(RVNKTools plugin) {
        super(plugin, "teleport", 
              "Teleportation utilities and world management", 
              "/teleport <subcommand> [args]",
              "rvnktools.command.teleport");
        
        // Create default world swap instance if not provided
        WorldSwapSubCommand worldSwap = new WorldSwapSubCommand(plugin, this);
        registerSubCommand("worldswap", new TeleportWorldSwapSubCommand(plugin, this, worldSwap));
    }
    
    public TeleportCommand(RVNKTools plugin, WorldSwapSubCommand sharedWorldSwap) {
        super(plugin, "teleport", 
              "Teleportation utilities and world management", 
              "/teleport <subcommand> [args]",
              "rvnktools.command.teleport");
        
        // Register teleport subcommands using the provided shared instance
        registerSubCommand("worldswap", new TeleportWorldSwapSubCommand(plugin, this, sharedWorldSwap));
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // This will be called when no subcommands match
        sendHelp(sender);
        return true;
    }
    
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6Teleportation Commands");
        sender.sendMessage("§7   Use /teleport <subcommand> for teleportation utilities");
        sender.sendMessage("");
        sender.sendMessage("§f/teleport worldswap [world] §7- Teleport between worlds preserving locations");
        sender.sendMessage("§7   Your location in each world will be saved and restored");
        sender.sendMessage("§7   Use specific world name or default to 'event' world");
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return getMatchingSubCommands(sender, args.length == 0 ? "" : args[0]);
        }
        
        return super.tabComplete(sender, args);
    }
}