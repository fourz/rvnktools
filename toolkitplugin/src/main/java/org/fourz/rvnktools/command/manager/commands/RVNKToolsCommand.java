package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Main RVNKTools command that serves as a container for administrative subcommands.
 * Provides access to plugin configuration, debugging, and utility functions.
 */
public class RVNKToolsCommand extends BaseCommand {
    
    public RVNKToolsCommand(RVNKTools plugin) {
        super(plugin, "rvnktools", 
              "Main administrative command for RVNKTools plugin", 
              "/rvnktools <subcommand> [args]",
              "rvnktools.command");
        
        // Register all administrative subcommands
        registerSubCommand("links", new LinksSubCommand(plugin, this));
        registerSubCommand("cycle", new CycleSubCommand(plugin, this));
        registerSubCommand("teleport", new TeleportSubCommand(plugin, this));
        registerSubCommand("reload", new ReloadSubCommand(plugin, this));
        registerSubCommand("debug", new DebugSubCommand(plugin, this));
        
        // Register convenience aliases for teleport functionality
        registerSubCommand("worldswap", new WorldSwapAliasSubCommand(plugin, this));
        registerSubCommand("event", new WorldSwapAliasSubCommand(plugin, this));
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // This will be called when no subcommands match
        sendHelp(sender);
        return true;
    }
    
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6RVNKTools Administrative Commands");
        sender.sendMessage("§7   Use /rvnktools <subcommand> for detailed help");
        sender.sendMessage("");
        sender.sendMessage("§f/rvnktools links reload §7- Reload links configuration");
        sender.sendMessage("§f/rvnktools cycle reload §7- Reload cycle commands configuration");
        sender.sendMessage("§f/rvnktools teleport worldswap [world] §7- Teleport between worlds");
        sender.sendMessage("§f/rvnktools reload §7- Reload plugin configuration");
        sender.sendMessage("§f/rvnktools debug §7- Show debug information");
        sender.sendMessage("");
        sender.sendMessage("§e⚠ §7Convenience aliases:");
        sender.sendMessage("§f/rvnktools worldswap [world] §7- Direct world swap");
        sender.sendMessage("§f/rvnktools event [world] §7- Event world shortcut");
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return getMatchingSubCommands(sender, args.length == 0 ? "" : args[0]);
        }
        
        return super.tabComplete(sender, args);
    }
}
