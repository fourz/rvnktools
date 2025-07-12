package org.fourz.rvnktools.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Standalone command executor for /event and /worldswap that delegates to the WorldSwap subcommand
 */
public class EventCommand implements CommandExecutor {
    
    private final TeleportWorldSwapSubCommand worldSwapCommand;
    
    public EventCommand(TeleportWorldSwapSubCommand worldSwapCommand) {
        this.worldSwapCommand = worldSwapCommand;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return worldSwapCommand.execute(sender, args);
    }
}
