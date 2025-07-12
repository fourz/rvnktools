package org.fourz.rvnktools.command.framework;

import org.bukkit.command.CommandSender;
import java.util.List;

/**
 * Base interface for all RVNKTools commands.
 * Provides a standardized contract for command execution, permissions, and metadata.
 */
public interface RVNKCommand {
    
    /**
     * Execute the command logic.
     * 
     * @param sender The command sender
     * @param args Command arguments (excluding the base command name)
     * @return true if the command was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Provide tab completion for the command.
     * 
     * @param sender The command sender
     * @param args Command arguments being completed
     * @return List of possible completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
    
    /**
     * Get the command name.
     * 
     * @return The command name
     */
    String getName();
    
    /**
     * Get the command description.
     * 
     * @return The command description
     */
    String getDescription();
    
    /**
     * Get the command usage string.
     * 
     * @return The usage string
     */
    String getUsage();
    
    /**
     * Get the permission required to use this command.
     * 
     * @return The permission string, or null if no permission is required
     */
    String getPermission();
    
    /**
     * Check if the sender has permission to use this command.
     * 
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Send help information to the command sender.
     * 
     * @param sender The command sender
     */
    void sendHelp(CommandSender sender);
    
    /**
     * Register a subcommand with this command.
     * 
     * @param name The subcommand name
     * @param subCommand The subcommand implementation
     */
    void registerSubCommand(String name, SubCommand subCommand);
    
    /**
     * Get a subcommand by name.
     * 
     * @param name The subcommand name
     * @return The subcommand, or null if not found
     */
    SubCommand getSubCommand(String name);
}
