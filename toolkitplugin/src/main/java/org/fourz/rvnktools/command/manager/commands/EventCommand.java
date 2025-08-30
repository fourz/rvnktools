package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Simple Event command that provides the /event alias for world swapping.
 * Delegates to WorldSwapSubCommand with default behavior for event world.
 */
public class EventCommand extends BaseCommand {
    
    private final WorldSwapSubCommand implementation;
    
    public EventCommand(RVNKTools plugin, WorldSwapSubCommand sharedImplementation) {
        super(plugin, "event", 
              "Teleport between worlds preserving location history", 
              "/event [world]",
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
