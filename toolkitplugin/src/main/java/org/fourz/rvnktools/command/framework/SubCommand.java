package org.fourz.rvnktools.command.framework;

import org.bukkit.command.CommandSender;
import java.util.List;

/**
 * Interface for subcommands within the RVNKTools command framework.
 * Subcommands are commands that are executed as part of a parent command.
 */
public interface SubCommand {
    
    /**
     * Execute the subcommand logic.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments (excluding the parent command and subcommand name)
     * @return true if the subcommand was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Provide tab completion for the subcommand.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments being completed
     * @return List of possible completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
    
    /**
     * Get the subcommand name.
     * 
     * @return The subcommand name
     */
    String getName();
    
    /**
     * Get the subcommand description.
     * 
     * @return The subcommand description
     */
    String getDescription();
    
    /**
     * Get the subcommand usage string.
     * 
     * @return The usage string
     */
    String getUsage();
    
    /**
     * Get the permission required to use this subcommand.
     * 
     * @return The permission string, or null if no permission is required
     */
    String getPermission();
    
    /**
     * Check if the sender has permission to use this subcommand.
     * 
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Check if this subcommand is restricted to players only.
     * 
     * @return true if only players can use this subcommand
     */
    boolean isPlayerOnly();
    
    /**
     * Get the parent command of this subcommand.
     * 
     * @return The parent command
     */
    RVNKCommand getParent();
}
