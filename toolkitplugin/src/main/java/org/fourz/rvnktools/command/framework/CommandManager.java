package org.fourz.rvnktools.command.framework;

import org.bukkit.command.PluginCommand;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.logging.LogManager;
import org.fourz.rvnktools.util.logging.RVNKLogger;
import org.fourz.rvnktools.command.TPSCommand;
import org.fourz.rvnktools.command.framework.commands.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Centralized command management system for RVNKTools.
 * Handles registration, lookup, and management of all plugin commands.
 */
public class CommandManager {
    
    private static CommandManager instance;
    private final RVNKTools plugin;
    private final RVNKLogger logger;
    private final Map<String, RVNKCommand> commands;
    private final Map<String, String> aliases;
    
    private CommandManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.commands = new HashMap<>();
        this.aliases = new HashMap<>();
    }
    
    /**
     * Initialize all plugin commands.
     * This should be called during plugin initialization to set up all commands.
     */
    public void initializeCommands() {
        logger.info("Initializing RVNKTools commands...");

        // Register core framework commands
        registerCommand(new TPSCommand(plugin));
        registerAlias("ping", "tps");  // Register ping as alias for TPS command
        
        // Register utility framework commands
        registerCommand(new EventsFrameworkCommand(plugin));
        
        logger.info("Command initialization complete!");
    }
    
    /**
     * Get the CommandManager instance (singleton pattern).
     * 
     * @param plugin The RVNKTools plugin instance
     * @return The CommandManager instance
     */
    public static CommandManager getInstance(RVNKTools plugin) {
        if (instance == null) {
            instance = new CommandManager(plugin);
        }
        return instance;
    }
    
    /**
     * Get the current CommandManager instance.
     * 
     * @return The CommandManager instance, or null if not initialized
     */
    public static CommandManager getInstance() {
        return instance;
    }
    
    /**
     * Register a command with the command manager.
     * This will also register the command with Bukkit.
     * 
     * @param command The command to register
     * @return true if the command was registered successfully
     */
    public boolean registerCommand(RVNKCommand command) {
        String name = command.getName().toLowerCase();
        
        // Check if command already exists
        if (commands.containsKey(name)) {
            logger.warning("Command " + name + " is already registered");
            return false;
        }
        
        // Get the PluginCommand from Bukkit
        PluginCommand pluginCommand = plugin.getCommand(name);
        if (pluginCommand == null) {
            logger.error("Failed to register command: " + name + " - not found in plugin.yml");
            return false;
        }
        
        // Register with Bukkit
        if (command instanceof BaseCommand) {
            BaseCommand baseCommand = (BaseCommand) command;
            pluginCommand.setExecutor(baseCommand);
            pluginCommand.setTabCompleter(baseCommand);
        } else {
            logger.warning("Command " + name + " does not extend BaseCommand - manual registration required");
            return false;
        }
        
        // Store in our registry
        commands.put(name, command);
        logger.info("Registered command: /" + name);
        
        return true;
    }
    
    /**
     * Register a subcommand with an existing parent command.
     * 
     * @param parentCommandName The name of the parent command
     * @param subCommandName The name of the subcommand
     * @param subCommand The subcommand implementation
     * @return true if the subcommand was registered successfully
     */
    public boolean registerSubCommand(String parentCommandName, String subCommandName, SubCommand subCommand) {
        RVNKCommand parentCommand = commands.get(parentCommandName.toLowerCase());
        if (parentCommand == null) {
            logger.error("Failed to register subcommand: " + subCommandName + 
                        " - parent command " + parentCommandName + " not found");
            return false;
        }
        
        parentCommand.registerSubCommand(subCommandName, subCommand);
        logger.info("Registered subcommand: " + parentCommandName + " -> " + subCommandName);
        return true;
    }
    
    /**
     * Unregister a command from the command manager.
     * 
     * @param commandName The name of the command to unregister
     * @return true if the command was unregistered successfully
     */
    public boolean unregisterCommand(String commandName) {
        String name = commandName.toLowerCase();
        RVNKCommand command = commands.remove(name);
        
        if (command == null) {
            logger.warning("Cannot unregister command " + name + " - not found");
            return false;
        }
        
        // Remove from Bukkit (set to null)
        PluginCommand pluginCommand = plugin.getCommand(name);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(null);
            pluginCommand.setTabCompleter(null);
        }
        
        logger.info("Unregistered command: /" + name);
        return true;
    }
    
    /**
     * Get a registered command by name.
     * 
     * @param name The command name
     * @return The command, or null if not found
     */
    public RVNKCommand getCommand(String name) {
        return commands.get(name.toLowerCase());
    }
    
    /**
     * Check if a command is registered.
     * 
     * @param name The command name
     * @return true if the command is registered
     */
    public boolean isCommandRegistered(String name) {
        return commands.containsKey(name.toLowerCase());
    }
    
    /**
     * Get all registered command names.
     * 
     * @return Set of command names
     */
    public Set<String> getRegisteredCommands() {
        return commands.keySet();
    }
    
    /**
     * Get the number of registered commands.
     * 
     * @return The number of registered commands
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * Register an alias for a command.
     * 
     * @param alias The alias name
     * @param commandName The target command name
     * @return true if the alias was registered successfully
     */
    public boolean registerAlias(String alias, String commandName) {
        String aliasLower = alias.toLowerCase();
        String commandLower = commandName.toLowerCase();
        
        if (!commands.containsKey(commandLower)) {
            logger.error("Cannot register alias " + alias + " - target command " + commandName + " not found");
            return false;
        }
        
        if (aliases.containsKey(aliasLower)) {
            logger.warning("Alias " + alias + " is already registered");
            return false;
        }
        
        aliases.put(aliasLower, commandLower);
        logger.debug("Registered alias: " + alias + " -> " + commandName);
        return true;
    }
    
    /**
     * Resolve a command name or alias to the actual command.
     * 
     * @param nameOrAlias The command name or alias
     * @return The command, or null if not found
     */
    public RVNKCommand resolveCommand(String nameOrAlias) {
        String lower = nameOrAlias.toLowerCase();
        
        // Check direct command name first
        RVNKCommand command = commands.get(lower);
        if (command != null) {
            return command;
        }
        
        // Check aliases
        String resolvedName = aliases.get(lower);
        if (resolvedName != null) {
            return commands.get(resolvedName);
        }
        
        return null;
    }
    
    /**
     * Get information about all registered commands for debugging.
     * 
     * @return A formatted string with command information
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("CommandManager Debug Info:\n");
        info.append("Registered Commands: ").append(commands.size()).append("\n");
        
        for (Map.Entry<String, RVNKCommand> entry : commands.entrySet()) {
            RVNKCommand command = entry.getValue();
            info.append("  - ").append(entry.getKey())
                .append(" (").append(command.getClass().getSimpleName()).append(")");
            
            if (command.getPermission() != null) {
                info.append(" [").append(command.getPermission()).append("]");
            }
            
            info.append("\n");
        }
        
        if (!aliases.isEmpty()) {
            info.append("Registered Aliases: ").append(aliases.size()).append("\n");
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                info.append("  - ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
            }
        }
        
        return info.toString();
    }
    
    /**
     * Initialize the command manager and register all default commands.
     * This should be called during plugin startup.
     */
    public void initialize() {
        logger.info("Initializing CommandManager...");
        
        // TODO: Register default commands here
        // This will be implemented in later phases
        
        logger.info("CommandManager initialized with " + commands.size() + " commands");
    }
    
    /**
     * Shutdown the command manager and clean up resources.
     * This should be called during plugin shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down CommandManager...");
        
        // Unregister all commands
        for (String commandName : commands.keySet()) {
            unregisterCommand(commandName);
        }
        
        aliases.clear();
        logger.info("CommandManager shutdown complete");
    }
}
