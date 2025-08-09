package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.List;

/**
 * Subcommand specifically for teleport worldswap functionality.
 * This provides the worldswap command within the teleport command hierarchy.
 */
public class TeleportWorldSwapSubCommand extends BaseSubCommand {
    
    private final WorldSwapSubCommand worldSwapImplementation;
    
    public TeleportWorldSwapSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "worldswap", 
              "Teleport between worlds while preserving locations", 
              "/rvnktools teleport worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);
        
        this.worldSwapImplementation = new WorldSwapSubCommand(plugin, parent);
    }
    
    /**
     * Constructor with shared WorldSwapSubCommand instance to avoid duplicate instantiation.
     */
    public TeleportWorldSwapSubCommand(RVNKTools plugin, BaseCommand parent, WorldSwapSubCommand sharedWorldSwap) {
        super(plugin, parent, "worldswap", 
              "Teleport between worlds while preserving locations", 
              "/rvnktools teleport worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);
        
        this.worldSwapImplementation = sharedWorldSwap;
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Directly delegate to the world swap implementation
        return worldSwapImplementation.execute(sender, args);
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        // Delegate tab completion to the world swap implementation
        return worldSwapImplementation.tabComplete(sender, args);
    }
}