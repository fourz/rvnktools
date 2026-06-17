package org.fourz.rvnktools.command.manager;

import org.bukkit.command.PluginCommand;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.command.manager.commands.DiscordCommand;
import org.fourz.rvnktools.command.manager.commands.EventCommand;
import org.fourz.rvnktools.command.manager.commands.EventsCommand;
import org.fourz.rvnktools.command.manager.commands.PingCommand;
import org.fourz.rvnktools.command.manager.commands.PlayerServiceTestCommand;
import org.fourz.rvnktools.command.manager.commands.PutHatCommand;
import org.fourz.rvnktools.command.manager.commands.RVNKCoreCommand;
import org.fourz.rvnktools.command.manager.commands.BackCommand;
import org.fourz.rvnktools.command.manager.commands.TeleportCommand;
import org.fourz.rvnktools.command.manager.commands.TpaCommand;
import org.fourz.rvnktools.command.manager.commands.TpAcceptCommand;
import org.fourz.rvnktools.command.manager.commands.TpDenyCommand;
import org.fourz.rvnktools.command.manager.commands.TrainsCommand;
import org.fourz.rvnktools.command.manager.commands.WorldSwapCommand;
import org.fourz.rvnktools.command.manager.commands.WorldSwapSubCommand;
import org.fourz.rvnkcore.service.teleport.BackLocationService;
import org.fourz.rvnkcore.service.teleport.DefaultBackLocationService;
import org.fourz.rvnkcore.service.teleport.TpaRequest;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;
import org.fourz.rvnktools.listener.TpaListener;
import org.fourz.rvnktools.link.LinkCommand;
import org.fourz.rvnktools.logfilter.LogFilterCommand;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnktools.command.manager.commands.RVNKToolsCommand;
import org.fourz.rvnktools.command.cycle.CycleCommands;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Centralized command management system for RVNKTools.
 * Handles registration, lookup, and management of all plugin commands.
 *
 * <p>Thread-safe singleton using double-checked locking pattern.</p>
 *
 * @since 1.0.0
 * @since 1.4.0 (thread safety improvements)
 */
public class CommandManager {

    private static volatile CommandManager instance;
    private static final Object lock = new Object();

    private final RVNKCore plugin;
    private final LogManager logger;
    private final Map<String, RVNKCommand> commands;
    private final Map<String, String> aliases;
    private final CycleCommands cycleCommands;

    private CommandManager(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.commands = new HashMap<>();
        this.aliases = new HashMap<>();
        this.cycleCommands = new CycleCommands(plugin);
    }
    
    /**
     * Initialize all plugin commands.
     * This should be called during plugin initialization to set up all commands.
     */
    public void initializeCommands() {
        logger.info("Initializing RVNKTools commands...");

        // Register main plugin command first
        registerCommand(new RVNKToolsCommand(plugin));

        // Register core framework commands
        registerCommand(new PingCommand(plugin));
        registerAlias("tps", "ping");
        
        // Register utility framework commands
        registerCommand(new DiscordCommand(plugin));
        registerCommand(new EventsCommand(plugin));
        registerCommand(new TrainsCommand(plugin));
        
        // Create a single shared WorldSwapSubCommand instance to prevent duplicate initialization
        WorldSwapSubCommand sharedWorldSwap = new WorldSwapSubCommand(plugin, null);

        // Register teleportation commands with shared instance
        TeleportCommand teleportCommand = new TeleportCommand(plugin, sharedWorldSwap);
        registerCommand(teleportCommand);

        // Conditionally register /tp alias based on config
        registerTpAlias(plugin, teleportCommand);

        // Register standalone worldswap and event commands that directly use the shared instance
        registerCommand(new WorldSwapCommand(plugin, sharedWorldSwap));
        registerCommand(new EventCommand(plugin, sharedWorldSwap));
        
        // Register debugging and testing commands
        registerCommand(new PlayerServiceTestCommand(plugin));
        registerCommand(new RVNKCoreCommand(plugin));
        
        // Register puthat command with CommandManager
        registerCommand(new PutHatCommand(plugin));
        
        // Register DH log filter command
        registerCommand(new LogFilterCommand(plugin));

        // Register link command (magic link auth)
        registerLinkCommand();

        // Register TPA commands (if enabled)
        registerTpaCommands();

        // Register cycle commands
        cycleCommands.registerCommands();

        logger.info("Command initialization complete!");
    }
    
    /**
     * Registers the /link command if AuthTokenStore is available in ServiceRegistry.
     * AuthTokenStore is created by ApiServerInitializer — if the API server is disabled,
     * the /link command will not be registered.
     */
    private void registerLinkCommand() {
        try {
            ServiceRegistry registry = plugin.getServiceRegistry();
            if (registry.hasService(AuthTokenStore.class)) {
                AuthTokenStore authTokenStore = registry.getService(AuthTokenStore.class);
                registerCommand(new LinkCommand(plugin, authTokenStore));
            } else {
                logger.info("AuthTokenStore not available — /link command not registered (API server may be disabled)");
            }
        } catch (Exception e) {
            logger.warning("Failed to register /link command: " + e.getMessage());
        }
    }

    /**
     * Registers TPA commands if the feature is enabled in config.
     * Creates TpaRequestService and BackLocationService, registers the listener,
     * and registers all 5 commands: /tpa, /tpahere, /tpaccept, /tpdeny, /back.
     */
    private void registerTpaCommands() {
        boolean enabled = plugin.getConfig().getBoolean("features.tpa-commands", true);
        if (!enabled) {
            logger.info("TPA commands disabled in config");
            return;
        }

        // Create services
        TpaRequestService tpaService = new TpaRequestService(plugin);
        tpaService.setRequestExpireSeconds(plugin.getConfig().getInt("teleport.request-expire-seconds", 60));
        tpaService.setWarmupSeconds(plugin.getConfig().getInt("teleport.warmup-seconds", 3));
        tpaService.setCooldownSeconds(plugin.getConfig().getInt("teleport.cooldown-seconds", 10));

        BackLocationService backService = new DefaultBackLocationService();

        // Register services with ServiceRegistry
        ServiceRegistry registry = plugin.getServiceRegistry();
        registry.registerService(TpaRequestService.class, tpaService);
        registry.registerService(BackLocationService.class, backService);

        // Register listener for warmup movement cancellation + player quit cleanup
        plugin.getServer().getPluginManager().registerEvents(
            new TpaListener(tpaService, backService), plugin);

        // Register commands
        registerCommand(new TpaCommand(plugin, tpaService,
            "tpa", "Request to teleport to another player", "/tpa <player>",
            TpaRequest.Type.TPA));
        registerCommand(new TpaCommand(plugin, tpaService,
            "tpahere", "Request another player teleport to you", "/tpahere <player>",
            TpaRequest.Type.TPAHERE));
        registerCommand(new TpAcceptCommand(plugin, tpaService, backService));
        registerCommand(new TpDenyCommand(plugin, tpaService));
        registerCommand(new BackCommand(plugin, tpaService, backService));

        logger.info("TPA commands registered: /tpa, /tpahere, /tpaccept, /tpdeny, /back");
    }

    /**
     * Get the CommandManager instance (singleton pattern).
     *
     * <p>Thread-safe using double-checked locking with volatile.</p>
     *
     * @param plugin The RVNKCore plugin instance
     * @return The CommandManager instance
     */
    public static CommandManager getInstance(RVNKCore plugin) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CommandManager(plugin);
                }
            }
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
     * Conditionally registers the /tp alias based on config.
     * If override is enabled in config, routes /tp to TeleportCommand.
     * If disabled, /tp remains as vanilla Minecraft behavior.
     *
     * @param plugin The RVNKCore plugin instance
     * @param teleportCommand The TeleportCommand instance to route /tp to
     */
    private void registerTpAlias(RVNKCore plugin, TeleportCommand teleportCommand) {
        boolean shouldOverrideTp = plugin.getConfig().getBoolean("commands.override-vanilla-tp", false);

        if (!shouldOverrideTp) {
            logger.info("Vanilla /tp override is DISABLED - using vanilla Minecraft teleport");
            return;
        }

        // Get the /tp command from plugin.yml
        PluginCommand tpCommand = plugin.getCommand("tp");
        if (tpCommand == null) {
            logger.warning("Failed to register /tp alias - command not found in plugin.yml");
            return;
        }

        // Route /tp to TeleportCommand
        tpCommand.setExecutor(teleportCommand);
        tpCommand.setTabCompleter(teleportCommand);

        logger.info("Registered /tp command override (vanilla behavior overridden)");
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
