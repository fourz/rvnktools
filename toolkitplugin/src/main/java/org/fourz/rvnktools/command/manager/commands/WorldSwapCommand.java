package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Simple WorldSwap command that directly delegates to WorldSwapSubCommand.
 * Provides the standalone /worldswap interface using the shared implementation.
 */
public class WorldSwapCommand extends BaseCommand {
    
    private final WorldSwapSubCommand implementation;
    
    public WorldSwapCommand(RVNKTools plugin, WorldSwapSubCommand sharedImplementation) {
        super(plugin, "worldswap", 
              "Teleport to your last known location in a specific world", 
              "/worldswap <world_name>",
              "rvnktools.command.worldswap");
        
        this.implementation = sharedImplementation;
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Directly delegate to the shared WorldSwap implementation
        return implementation.execute(sender, args);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Delegate tab completion to the WorldSwap implementation
        return implementation.tabComplete(sender, args);
    }
}
