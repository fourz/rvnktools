# RVNKTools Command Manager

## Overview

The Command Manager is a centralized system for handling all commands in the RVNKTools plugin. It provides a robust framework for command registration, execution, and tab completion while ensuring consistent behavior across the plugin.

## Features

- **Centralized Command Registration**: Single point of registration for all plugin commands
- **Permission Integration**: Built-in permission checking with detailed error feedback
- **Tab Completion**: Standardized tab completion for all commands
- **Subcommand Support**: Hierarchical command structure with nested subcommands
- **Alias Management**: Support for command aliases with transparent resolution
- **Error Handling**: Consistent error messages and logging
- **Command Lifecycle Management**: Proper initialization and cleanup of commands

## Core Components

### CommandManager

The `CommandManager` class serves as the central hub for all command operations:

```java
public class CommandManager {
    private static CommandManager instance;
    private final RVNKTools plugin;
    private final LogManager logger;
    private final Map<String, RVNKCommand> commands;
    private final Map<String, String> aliases;
    private final CycleCommands cycleCommands;
    
    private CommandManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.commands = new HashMap<>();
        this.aliases = new HashMap<>();
        this.cycleCommands = new CycleCommands(plugin);
    }
    
    public static CommandManager getInstance(RVNKTools plugin) {
        if (instance == null) {
            instance = new CommandManager(plugin);
        }
        return instance;
    }
    
    public void initializeCommands() {
        logger.info("Initializing RVNKTools commands...");
        
        // Register main plugin command first
        registerCommand(new RVNKToolsCommand(plugin));
        
        // Register core framework commands
        registerCommand(new PingCommand(plugin));
        registerAlias("tps", "ping");
        
        // Register utility framework commands
        registerCommand(new BroadcastCommand(plugin));
        registerCommand(new DiscordCommand(plugin));
        registerCommand(new EventsCommand(plugin));
        registerCommand(new TrainsCommand(plugin));
        registerCommand(new PutHatCommand(plugin));
        
        // Register cycle commands
        cycleCommands.registerCommands();
        
        logger.info("Command initialization complete!");
    }
}
```

### RVNKCommand Interface

All commands implement the `RVNKCommand` interface:

```java
public interface RVNKCommand {
    String getName();
    String getDescription();
    String getUsage();
    String getPermission();
    void registerSubCommand(String name, SubCommand subCommand);
    boolean executeCommand(CommandSender sender, String[] args);
}
```

### BaseCommand Class

The `BaseCommand` abstract class provides a foundation for all plugin commands:

```java
public abstract class BaseCommand implements CommandExecutor, TabCompleter, RVNKCommand {
    protected final RVNKTools plugin;
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final String permission;
    protected final Map<String, SubCommand> subCommands;
    
    public BaseCommand(RVNKTools plugin, String name, String description, 
                      String usage, String permission) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.subCommands = new HashMap<>();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                            String label, String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatFormat.colorize("&c✖ You don't have permission to use this command."));
            return true;
        }
        
        // Check for subcommands first
        if (args.length > 0) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                return subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        
        // Execute the main command logic
        return executeCommand(sender, args);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, 
                                     String alias, String[] args) {
        // If on the first argument, suggest available subcommands
        if (args.length <= 1) {
            return getMatchingSubCommands(sender, args.length == 0 ? "" : args[0]);
        }
        
        // If we have a valid subcommand, let it handle tab completion
        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
    }
    
    protected List<String> getMatchingSubCommands(CommandSender sender, String prefix) {
        List<String> matches = new ArrayList<>();
        
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getKey().startsWith(prefix.toLowerCase())) {
                SubCommand subCommand = entry.getValue();
                if (subCommand.getPermission() == null || 
                    sender.hasPermission(subCommand.getPermission())) {
                    matches.add(entry.getKey());
                }
            }
        }
        
        return matches;
    }
    
    protected abstract boolean executeCommand(CommandSender sender, String[] args);
}
```

### SubCommand Interface

Subcommands implement the `SubCommand` interface:

```java
public interface SubCommand {
    String getName();
    String getDescription();
    String getUsage();
    String getPermission();
    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);
}
```

## Command Registration Process

### 1. Define Command in plugin.yml

```yaml
commands:
  rvnktools:
    description: Main command for RVNKTools
    usage: /rvnktools [subcommand] [args]
    permission: rvnktools.command
```

### 2. Create Command Class

```java
public class RVNKToolsCommand extends BaseCommand {
    public RVNKToolsCommand(RVNKTools plugin) {
        super(plugin, "rvnktools", 
              "Main administrative command for RVNKTools plugin", 
              "/rvnktools <subcommand> [args]",
              "rvnktools.command");
        
        // Register subcommands
        registerSubCommand("reload", new ReloadSubCommand(plugin, this));
        registerSubCommand("debug", new DebugSubCommand(plugin, this));
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Handle command logic or show help
        sendHelp(sender);
        return true;
    }
}
```

### 3. Register Command with CommandManager

```java
// In CommandManager.initializeCommands()
registerCommand(new RVNKToolsCommand(plugin));
```

### 4. Command Lookup and Execution

1. Player inputs command
2. Bukkit routes command to plugin's CommandExecutor
3. CommandManager resolves the command (including alias resolution)
4. BaseCommand checks permissions
5. BaseCommand checks for subcommands
6. Command or subcommand is executed
7. Result is returned to player

## Alias Management

Aliases provide alternative names for commands:

```java
// Register an alias
commandManager.registerAlias("tps", "ping");

// Resolve command from name or alias
RVNKCommand command = commandManager.resolveCommand("tps"); // Returns PingCommand
```

## Integration with Other Systems

### Integration with Permission System

Commands use the permission system for access control:

```java
// In BaseCommand.onCommand()
if (permission != null && !sender.hasPermission(permission)) {
    sender.sendMessage(ChatFormat.colorize("&c✖ You don't have permission to use this command."));
    return true;
}
```

### Integration with Cycle Commands

The CommandManager initializes and works with CycleCommands:

```java
// In CommandManager constructor
this.cycleCommands = new CycleCommands(plugin);

// In initializeCommands()
cycleCommands.registerCommands();
```

### Integration with Logging System

Commands use the LogManager for consistent logging:

```java
// In CommandManager constructor
this.logger = LogManager.getInstance(plugin, getClass());

// Logging command registration
logger.info("Registered command: /" + name);
```

## Command Manager Lifecycle

### Initialization

```java
// In RVNKTools.onEnable()
private void initializeCommandFramework() {
    commandManager = CommandManager.getInstance(this);
    commandManager.initializeCommands();
}
```

### Shutdown

```java
// In CommandManager.shutdown()
public void shutdown() {
    logger.info("Shutting down CommandManager...");
    
    // Unregister all commands
    for (String commandName : commands.keySet()) {
        // Unregister with Bukkit
    }
    
    aliases.clear();
    logger.info("CommandManager shutdown complete");
}
```

## Best Practices

### 1. Command Implementation

- Extend `BaseCommand` for all main commands
- Implement `SubCommand` for all subcommands
- Keep command logic focused and specific
- Provide clear help and usage information
- Follow consistent command naming conventions

### 2. Permission Handling

- Always specify permissions for commands
- Use granular permissions for subcommands
- Check permissions before command execution
- Provide clear permission denied messages

### 3. Error Handling

- Validate command arguments before use
- Provide helpful error messages for invalid input
- Log command failures with context
- Catch and handle exceptions during command execution

### 4. Tab Completion

- Implement tab completion for all commands
- Filter suggestions based on permissions
- Support partial argument matching
- Provide context-aware suggestions

## Example Commands

### Main Plugin Command

```java
public class RVNKToolsCommand extends BaseCommand {
    public RVNKToolsCommand(RVNKTools plugin) {
        super(plugin, "rvnktools", 
              "Main administrative command for RVNKTools plugin", 
              "/rvnktools <subcommand> [args]",
              "rvnktools.command");
        
        // Register all administrative subcommands
        registerSubCommand("links", new LinksSubCommand(plugin, this));
        registerSubCommand("cycle", new CycleSubCommand(plugin, this));
        registerSubCommand("reload", new ReloadSubCommand(plugin, this));
        registerSubCommand("debug", new DebugSubCommand(plugin, this));
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Show help when no subcommand is provided
        sendHelp(sender);
        return true;
    }
    
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6RVNKTools Administrative Commands");
        sender.sendMessage("§7   Use /rvnktools <subcommand> for detailed help");
        sender.sendMessage("");
        sender.sendMessage("§f/rvnktools links reload §7- Reload links configuration");
        sender.sendMessage("§f/rvnktools cycle reload §7- Reload cycle commands configuration");
        sender.sendMessage("§f/rvnktools reload §7- Reload plugin configuration");
        sender.sendMessage("§f/rvnktools debug §7- Show debug information");
    }
}
```

### Utility Command

```java
public class PingCommand extends BaseCommand {
    public PingCommand(RVNKTools plugin) {
        super(plugin, "ping", 
              "Check server response time", 
              "/ping",
              "rvnktools.ping");
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        int ping = player.getPing();
        
        String message;
        if (ping < 50) {
            message = "§a✓ Your ping is §f" + ping + "ms §a(Excellent)";
        } else if (ping < 100) {
            message = "§2✓ Your ping is §f" + ping + "ms §2(Good)";
        } else if (ping < 200) {
            message = "§e⚠ Your ping is §f" + ping + "ms §e(Fair)";
        } else {
            message = "§c✖ Your ping is §f" + ping + "ms §c(Poor)";
        }
        
        player.sendMessage(message);
        return true;
    }
}
```

## Conclusion

The CommandManager provides a robust and extensible framework for handling all plugin commands. By centralizing command registration and standardizing command behavior, it ensures a consistent user experience while simplifying development and maintenance.

The command system integrates seamlessly with other core systems like permissions and logging, forming a cohesive foundation for the plugin's functionality. The hierarchical command structure with subcommand support allows for clean organization of complex command sets while maintaining ease of use.
