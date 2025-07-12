package org.fourz.rvnktools.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;

/**
 * Legacy wrapper for TeleportWorldSwapSubCommand to maintain backward compatibility
 * Delegates all functionality to the new implementation
 */
public class WorldSwapSubCommand extends RVNKToolsSubCommand {

    private final TeleportWorldSwapSubCommand delegate;

    public WorldSwapSubCommand(RVNKTools plugin) {
        super(plugin);
        this.delegate = new TeleportWorldSwapSubCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return delegate.execute(sender, args);
    }
}
