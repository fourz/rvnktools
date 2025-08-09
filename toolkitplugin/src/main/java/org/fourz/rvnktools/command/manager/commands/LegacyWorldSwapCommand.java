package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Legacy WorldSwap command that provides backward compatibility.
 * This command delegates to the new WorldSwap subcommand functionality
 * while maintaining the original /worldswap interface.
 * 
 * @deprecated This command exists for backward compatibility and will be removed in 2.0
 */
@Deprecated(since = "1.1", forRemoval = true)
public class LegacyWorldSwapCommand extends BaseCommand {
    
    private final WorldSwapSubCommand worldSwapImplementation;
    
    public LegacyWorldSwapCommand(RVNKTools plugin) {
        super(plugin, "worldswap", 
              "Teleport to your last known location in a specific world", 
              "/worldswap <world_name>",
              "rvnktools.command.worldswap");
        
        // Create the actual implementation using the subcommand framework
        this.worldSwapImplementation = new WorldSwapSubCommand(plugin, this);
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Show deprecation warning to console/ops only to avoid spamming players
        if (sender.isOp()) {
            sender.sendMessage("§e⚠ WorldSwap command will be reorganized in a future version.");
            sender.sendMessage("§7Consider using '/teleport worldswap' or '/rvnktools teleport worldswap' for new features.");
        }
        
        // Delegate to the WorldSwap subcommand implementation
        return worldSwapImplementation.execute(sender, args);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Delegate tab completion to the WorldSwap subcommand
        return worldSwapImplementation.tabComplete(sender, args);
    }
}
