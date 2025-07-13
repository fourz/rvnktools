package org.fourz.rvnktools.command.manager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.logging.LogManager;
import org.fourz.rvnktools.util.logging.RVNKLogger;

import java.util.*;

/**
 * Abstract base class for all RVNKTools commands.
 * Provides common functionality for command execution, permission checking,
 * subcommand management, and help text generation.
 */
public abstract class BaseCommand implements RVNKCommand, CommandExecutor, TabCompleter {
    
    protected final RVNKTools plugin;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final RVNKLogger logger;
    protected final Map<String, SubCommand> subCommands;
    
    /**
     * Constructor for BaseCommand.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param name The command name
     * @param description The command description
     * @param usage The command usage string
     * @param permission The permission required to use the command (can be null)
     */
    public BaseCommand(RVNKTools plugin, String name, String description, String usage, String permission) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.subCommands = new HashMap<>();
    }
    
    /**
     * Constructor for BaseCommand with default permission.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param name The command name
     * @param description The command description
     * @param usage The command usage string
     */
    public BaseCommand(RVNKTools plugin, String name, String description, String usage) {
        this(plugin, name, description, usage, "rvnktools.command." + name.toLowerCase());
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
    public boolean hasPermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender.hasPermission(permission);
    }
    
    @Override
    public void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
        logger.debug("Registered subcommand: " + this.name + " -> " + name);
    }
    
    @Override
    public SubCommand getSubCommand(String name) {
        return subCommands.get(name.toLowerCase());
    }
    
    /**
     * Get all registered subcommand names.
     * 
     * @return Set of subcommand names
     */
    protected Set<String> getSubCommandNames() {
        return subCommands.keySet();
    }
    
    /**
     * Get matching subcommands for tab completion.
     * 
     * @param sender The command sender
     * @param partial The partial subcommand name
     * @return List of matching subcommand names
     */
    protected List<String> getMatchingSubCommands(CommandSender sender, String partial) {
        List<String> matches = new ArrayList<>();
        String lowerPartial = partial.toLowerCase();
        
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            String subCommandName = entry.getKey();
            SubCommand subCommand = entry.getValue();
            
            if (subCommandName.startsWith(lowerPartial) && subCommand.hasPermission(sender)) {
                // Check if subcommand is player-only and sender is not a player
                if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
                    continue;
                }
                matches.add(subCommandName);
            }
        }
        
        return matches;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        logger.debug("Command executed: " + label + " with " + args.length + " arguments");
        
        // Check permission
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        
        return execute(sender, args);
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // If no arguments or help is requested, show help
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        
        // Check for subcommands
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = getSubCommand(subCommandName);
        
        if (subCommand != null) {
            // Check if subcommand is player-only
            if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }
            
            // Execute subcommand with remaining arguments
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return subCommand.execute(sender, subArgs);
        }
        
        // If no subcommand found, try to execute the base command
        return executeCommand(sender, args);
    }
    
    /**
     * Execute the base command logic. Subclasses should override this method
     * to provide command-specific functionality.
     * 
     * @param sender The command sender
     * @param args Command arguments
     * @return true if the command was handled successfully
     */
    protected boolean executeCommand(CommandSender sender, String[] args) {
        sendUnknownSubCommandMessage(sender, args[0]);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(sender, args);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            // Return matching subcommands
            return getMatchingSubCommands(sender, args[0]);
        } else if (args.length > 1) {
            // Delegate to subcommand tab completion
            SubCommand subCommand = getSubCommand(args[0]);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete(sender, subArgs);
            }
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + getName() + " Command ===");
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + getUsage());
        
        if (!subCommands.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Subcommands:");
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                SubCommand subCommand = entry.getValue();
                if (subCommand.hasPermission(sender)) {
                    sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + 
                        ChatColor.WHITE + " - " + subCommand.getDescription());
                }
            }
        }
    }
    
    /**
     * Send a message when the sender doesn't have permission.
     * 
     * @param sender The command sender
     */
    protected void sendNoPermissionMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
        logger.warning("Permission denied for " + sender.getName() + " attempting to use command: " + getName());
    }
    
    /**
     * Send a message when an unknown subcommand is used.
     * 
     * @param sender The command sender
     * @param subCommandName The unknown subcommand name
     */
    protected void sendUnknownSubCommandMessage(CommandSender sender, String subCommandName) {
        sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
        sender.sendMessage(ChatColor.GRAY + "Use '/" + getName() + " help' for available commands.");
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
     * @param usage Usage string to display if validation fails
     * @return true if validation passes
     */
    protected boolean validateArgs(CommandSender sender, String[] args, int minArgs, String usage) {
        if (args.length < minArgs) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usage);
            return false;
        }
        return true;
    }
}
