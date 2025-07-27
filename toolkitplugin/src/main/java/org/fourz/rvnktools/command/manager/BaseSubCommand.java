package org.fourz.rvnktools.command.manager;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.log.RVNKLogger;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all RVNKTools subcommands.
 * Provides common functionality for subcommand execution, permission checking,
 * and validation.
 */
public abstract class BaseSubCommand implements SubCommand {
    
    protected final RVNKTools plugin;
    protected final RVNKCommand parent;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final boolean playerOnly;
    protected final RVNKLogger logger;
    
    /**
     * Constructor for BaseSubCommand.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param parent The parent command
     * @param name The subcommand name
     * @param description The subcommand description
     * @param usage The subcommand usage string
     * @param permission The permission required to use the subcommand (can be null)
     * @param playerOnly Whether this subcommand is restricted to players only
     */
    public BaseSubCommand(RVNKTools plugin, RVNKCommand parent, String name, String description, 
                         String usage, String permission, boolean playerOnly) {
        this.plugin = plugin;
        this.parent = parent;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Constructor for BaseSubCommand with default permission (inherits from parent).
     * 
     * @param plugin The RVNKTools plugin instance
     * @param parent The parent command
     * @param name The subcommand name
     * @param description The subcommand description
     * @param usage The subcommand usage string
     * @param playerOnly Whether this subcommand is restricted to players only
     */
    public BaseSubCommand(RVNKTools plugin, RVNKCommand parent, String name, String description, 
                         String usage, boolean playerOnly) {
        this(plugin, parent, name, description, usage, 
             parent.getPermission() != null ? parent.getPermission() + "." + name.toLowerCase() : null, 
             playerOnly);
    }
    
    /**
     * Constructor for BaseSubCommand with default permission and not player-only.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param parent The parent command
     * @param name The subcommand name
     * @param description The subcommand description
     * @param usage The subcommand usage string
     */
    public BaseSubCommand(RVNKTools plugin, RVNKCommand parent, String name, String description, String usage) {
        this(plugin, parent, name, description, usage, false);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getUsage() {
        return usage;
    }
    
    @Override
    public String getPermission() {
        return permission;
    }
    
    @Override
    public boolean isPlayerOnly() {
        return playerOnly;
    }
    
    @Override
    public RVNKCommand getParent() {
        return parent;
    }
    
    @Override
    public boolean hasPermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender.hasPermission(permission);
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        logger.debug("Subcommand executed: " + getName() + " with " + args.length + " arguments");
        
        // Check permission
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        
        // Check player-only restriction
        if (isPlayerOnly() && !validatePlayer(sender)) {
            return true;
        }
        
        return executeSubCommand(sender, args);
    }
    
    /**
     * Execute the subcommand logic. Subclasses must implement this method.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments
     * @return true if the subcommand was handled successfully
     */
    protected abstract boolean executeSubCommand(CommandSender sender, String[] args);
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (isPlayerOnly() && !(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        return getTabCompletions(sender, args);
    }
    
    /**
     * Get tab completions for this subcommand. Subclasses can override this method.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments
     * @return List of possible completions
     */
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    /**
     * Send a message when the sender doesn't have permission.
     * 
     * @param sender The command sender
     */
    protected void sendNoPermissionMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
        logger.warning("Permission denied for " + sender.getName() + " attempting to use subcommand: " + 
                      parent.getName() + " " + getName());
    }
    
    /**
     * Validate that the sender is a player.
     * 
     * @param sender The command sender
     * @return true if the sender is a player
     */
    protected boolean validatePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return false;
        }
        return true;
    }
    
    /**
     * Validate the number of arguments.
     * 
     * @param sender The command sender
     * @param args The arguments
     * @param minArgs Minimum number of arguments required
     * @return true if validation passes
     */
    protected boolean validateArgs(CommandSender sender, String[] args, int minArgs) {
        if (args.length < minArgs) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return false;
        }
        return true;
    }
    
    /**
     * Send a formatted message to the sender.
     * 
     * @param sender The command sender
     * @param message The message to send (supports color codes with &)
     */
    protected void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    /**
     * Send a success message to the sender.
     * 
     * @param sender The command sender
     * @param message The success message
     */
    protected void sendSuccessMessage(CommandSender sender, String message) {
        sendMessage(sender, "&a" + message);
    }
    
    /**
     * Send an error message to the sender.
     * 
     * @param sender The command sender
     * @param message The error message
     */
    protected void sendErrorMessage(CommandSender sender, String message) {
        sendMessage(sender, "&c" + message);
    }
    
    /**
     * Send an info message to the sender.
     * 
     * @param sender The command sender
     * @param message The info message
     */
    protected void sendInfoMessage(CommandSender sender, String message) {
        sendMessage(sender, "&e" + message);
    }
}
