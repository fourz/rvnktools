package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for teleport-related functionality.
 * Currently supports world swapping features through Multiverse integration.
 */
public class TeleportSubCommand extends BaseSubCommand {
    
    private final WorldSwapSubCommand worldSwapSubCommand;
    
    public TeleportSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "teleport", 
              "Teleport management commands", 
              "/rvnktools teleport <worldswap> [args]",
              "rvnktools.command.teleport", false);
        
        this.worldSwapSubCommand = new WorldSwapSubCommand(plugin, parent);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("worldswap")) {
            // Pass remaining arguments to the worldswap subcommand
            String[] worldSwapArgs = Arrays.copyOfRange(args, 1, args.length);
            return worldSwapSubCommand.execute(sender, worldSwapArgs);
        }
        
        showHelp(sender);
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6Teleport Commands");
        sender.sendMessage("§f/rvnktools teleport worldswap [world] §7- Teleport between worlds preserving locations");
        sender.sendMessage("§7   Use specific world name or default to 'event' world");
        sender.sendMessage("§7   Your location in the current world will be saved");
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("rvnktools.command.teleport.worldswap")) {
                return Arrays.asList("worldswap");
            }
        } else if (args.length > 1 && args[0].equalsIgnoreCase("worldswap")) {
            // Delegate tab completion to worldswap subcommand
            String[] worldSwapArgs = Arrays.copyOfRange(args, 1, args.length);
            return worldSwapSubCommand.tabComplete(sender, worldSwapArgs);
        }
        return Collections.emptyList();
    }
}
