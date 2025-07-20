package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.List;

/**
 * Alias subcommand that provides direct access to world swap functionality.
 * Provides shortcuts like "worldswap" and "event" that directly invoke the teleport worldswap command.
 */
public class WorldSwapAliasSubCommand extends BaseSubCommand {
    
    private final WorldSwapSubCommand worldSwapSubCommand;
    
    public WorldSwapAliasSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "worldswap", 
              "Quick access to world swap functionality", 
              "/rvnktools worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);
        
        this.worldSwapSubCommand = new WorldSwapSubCommand(plugin, parent);
    }
    
    /**
     * Constructor with shared WorldSwapSubCommand instance to avoid duplicate instantiation.
     */
    public WorldSwapAliasSubCommand(RVNKTools plugin, BaseCommand parent, WorldSwapSubCommand sharedWorldSwap) {
        super(plugin, parent, "worldswap", 
              "Quick access to world swap functionality", 
              "/rvnktools worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);
        
        this.worldSwapSubCommand = sharedWorldSwap;
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Directly delegate to the world swap subcommand
        return worldSwapSubCommand.execute(sender, args);
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        // Delegate tab completion to the world swap subcommand
        return worldSwapSubCommand.tabComplete(sender, args);
    }
}
