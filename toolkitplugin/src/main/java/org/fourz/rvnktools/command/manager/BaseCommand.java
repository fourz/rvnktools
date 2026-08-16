package org.fourz.rvnktools.command.manager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Abstract base class for all RVNKTools commands.
 * Provides common functionality for command execution, permission checking,
 * subcommand management, and help text generation.
 */
public abstract class BaseCommand implements RVNKCommand, CommandExecutor, TabCompleter {

    protected final RVNKCore plugin;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final LogManager logger;
    protected final Map<String, SubCommand> subCommands;
    
    /**
     * Constructor for BaseCommand.
     *
     * @param plugin The RVNKCore plugin instance
     * @param name The command name
     * @param description The command description
     * @param usage The command usage string
     * @param permission The permission required to use the command (can be null)
     */
    public BaseCommand(RVNKCore plugin, String name, String description, String usage, String permission) {
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
     * @param plugin The RVNKCore plugin instance
     * @param name The command name
     * @param description The command description
     * @param usage The command usage string
     */
    public BaseCommand(RVNKCore plugin, String name, String description, String usage) {
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
        // An explicit help request always wins, for leaf and parent commands alike. With a verb
        // argument it serves that verb's usage and worked examples (#1981).
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            if (args.length >= 2) {
                sendVerbHelp(sender, args[1].toLowerCase());
            } else {
                sendHelp(sender);
            }
            return true;
        }

        // A bare invocation only means "show help" for a command that dispatches to subcommands.
        // A leaf command must reach executeCommand(), which is the only reason to override it.
        // This previously returned help unconditionally on zero args, which made every no-argument
        // command in the plugin unreachable — /ping and /discord both answered with their own usage
        // text instead of running (#1600).
        if (args.length == 0) {
            if (!subCommands.isEmpty()) {
                sendHelp(sender);
                return true;
            }
            return executeCommand(sender, args);
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
        // Reachable with zero args now that a bare leaf invocation dispatches here (#1600).
        // A subclass that does not override this has no bare-invocation behaviour to offer.
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
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
            sendSubCommandList(sender);
        }
    }

    /**
     * The subcommand list, generated from the registry.
     *
     * <p>Split out so a command that wants extra lines of its own can print them around the
     * generated list rather than hand-maintaining a duplicate of it. A hand-kept list drifts: the
     * {@code /rvnktools} one advertised a {@code createtestdata} command that has no handler
     * anywhere in the plugin, and RVNKWorlds' curated list had silently lost six verbs. (#1981)</p>
     */
    protected void sendSubCommandList(CommandSender sender) {
        List<String> names = new ArrayList<>(subCommands.keySet());
        Collections.sort(names);

        List<String> lines = new ArrayList<>();
        boolean anyExamples = false;
        for (String name : names) {
            SubCommand subCommand = subCommands.get(name);
            if (subCommand == null || !subCommand.hasPermission(sender)) {
                continue;
            }
            boolean hasExamples = !subCommand.getExamples().isEmpty();
            anyExamples |= hasExamples;
            lines.add(ChatColor.GRAY + "  " + name
                    + (hasExamples ? ChatColor.AQUA + "*" : " ")
                    + ChatColor.WHITE + " - " + subCommand.getDescription());
        }

        sender.sendMessage(ChatColor.YELLOW + "Subcommands (" + lines.size() + "):");
        for (String line : lines) {
            sender.sendMessage(line);
        }
        if (anyExamples) {
            sender.sendMessage(ChatColor.AQUA + "*" + ChatColor.GRAY + " has worked examples - "
                    + ChatColor.WHITE + "/" + getName() + " help <subcommand>");
        }
    }

    /**
     * {@code /<command> help <verb>} &mdash; one subcommand's usage and worked examples.
     *
     * <p>Usage always prints; examples print only when the subcommand overrides
     * {@link SubCommand#getExamples()}. A verb with none says so rather than showing an empty
     * section, so the reader knows the usage line is the whole grammar.</p>
     */
    protected void sendVerbHelp(CommandSender sender, String verb) {
        SubCommand subCommand = getSubCommand(verb);
        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + verb);
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/" + getName() + " help"
                    + ChatColor.GRAY + " for the list.");
            return;
        }
        if (!subCommand.hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + getName() + " " + verb + " ===");
        sender.sendMessage(ChatColor.WHITE + subCommand.getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + subCommand.getUsage());
        if (subCommand.isPlayerOnly()) {
            sender.sendMessage(ChatColor.GRAY + "Players only - not available from console.");
        }
        if (subCommand.getPermission() != null) {
            sender.sendMessage(ChatColor.GRAY + "Permission: " + subCommand.getPermission());
        }

        List<String> examples = subCommand.getExamples();
        if (examples.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY
                    + "No further examples - the usage line above is the whole grammar.");
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        for (String example : examples) {
            if (example.startsWith("  ")) {
                sender.sendMessage(ChatColor.DARK_GRAY + "     " + example.trim());
            } else {
                sender.sendMessage(ChatColor.WHITE + "  " + example);
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
