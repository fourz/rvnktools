# RVNKTools Refactor: CommandManager Implementation Plan

This document outlines a step-by-step plan to implement a centralized CommandManager for the RVNKTools plugin. This refactoring will improve code organization, maintainability, and consistency across command implementations.

## Current State

Currently, commands in RVNKTools are implemented in various ways:
- Direct CommandExecutor implementations
- Ad-hoc subcommand patterns
- Inconsistent permission handling
- Duplicated validation logic
- Varying approaches to tab completion

## Goals

1. Centralize command registration and management
2. Standardize subcommand pattern implementation
3. Implement consistent permission checking
4. Provide uniform error handling and user feedback
5. Simplify tab completion through shared utilities
6. Integrate with the LogManager for consistent logging

## Implementation Plan

### Phase 1: Core Command Framework (Week 1)

#### Step 1: Create Base Interfaces and Classes
1. Create `CommandManager` class
   - Singleton pattern for global access
   - Methods for registering commands and subcommands
   - Integration with LogManager

2. Create `RVNKCommand` interface
   - Methods for execution, tab completion, and metadata
   - Permission specification
   - Usage and help text

3. Create `BaseCommand` abstract class
   - Implementation of common methods
   - Standard validation logic
   - Default error messages

4. Create `SubCommand` interface and `BaseSubCommand` abstract class
   - Similar to command interfaces but for subcommands
   - Parent command reference
   - Path-based permission structure

#### Step 2: Create Utility Classes
1. Create `CommandValidator` for input validation
2. Create `ArgumentParser` for command argument handling
3. Create `TabCompletionUtil` for common completion patterns

### Phase 2: Command Management System (Week 2)

#### Step 1: Implement CommandManager
```java
public class CommandManager {
    private static CommandManager instance;
    private final RVNKTools plugin;
    private final LogManager logger;
    private final Map<String, RVNKCommand> commands = new HashMap<>();
    
    private CommandManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    public static CommandManager getInstance(RVNKTools plugin) {
        if (instance == null) {
            instance = new CommandManager(plugin);
        }
        return instance;
    }
    
    public void registerCommand(String name, RVNKCommand command) {
        PluginCommand pluginCommand = plugin.getCommand(name);
        if (pluginCommand == null) {
            logger.error("Failed to register command: " + name + " - not found in plugin.yml");
            return;
        }
        
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        commands.put(name, command);
        logger.info("Registered command: " + name);
    }
    
    public void registerSubCommand(String commandName, String subCommandName, SubCommand subCommand) {
        RVNKCommand command = commands.get(commandName);
        if (command == null) {
            logger.error("Failed to register subcommand: " + subCommandName + " - parent command not found");
            return;
        }
        
        command.registerSubCommand(subCommandName, subCommand);
        logger.info("Registered subcommand: " + commandName + " -> " + subCommandName);
    }
    
    // Additional utility methods
}
```

#### Step 2: Implement BaseCommand
```java
public abstract class BaseCommand implements RVNKCommand {
    protected final RVNKTools plugin;
    protected final LogManager logger;
    protected final Map<String, SubCommand> subCommands = new HashMap<>();
    
    public BaseCommand(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!validatePermission(sender, getPermission())) {
            return true;
        }
        
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sendUnknownCommandMessage(sender, subCommandName);
            return true;
        }
        
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(sender, subArgs);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return getMatchingSubCommands(sender, args[0]);
        }
        
        if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete(sender, subArgs);
            }
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
    }
    
    // Helper methods for validation, permissions, etc.
}
```

### Phase 3: Migration Strategy (Week 3)

#### Step 1: Create Migration Priority List
1. Simple commands with no subcommands
2. Commands with basic subcommand structure
3. Complex commands with extensive validation/permissions
4. Special-case commands with unique behavior

#### Step 2: Refactor One Command Group at a Time
1. Start with the AnnounceCommand system
   - Create AnnounceCommand extending BaseCommand
   - Convert existing subcommands to new SubCommand interface
   - Implement tab completion properly
   - Update permission handling

2. Refactor cycle commands
3. Refactor link maker commands
4. Refactor utility commands

#### Step 3: Testing and Validation
1. Create unit tests for command execution
2. Test permission handling
3. Verify tab completion works correctly
4. Test error handling and user feedback

### Phase 4: Documentation and Integration (Week 4)

#### Step 1: Update Documentation
1. Create detailed JavaDoc for all classes
2. Update command usage in plugin documentation
3. Create examples for new command implementations

#### Step 2: Integration with Other Systems
1. Connect with LogManager for consistent logging
2. Integrate with permission system
3. Add performance monitoring where needed

## Implementation Example: AnnounceCommand

Here's a concrete example of how the AnnounceCommand would be refactored:

```java
// New implementation
public class AnnounceCommand extends BaseCommand {
    private final AnnounceManager announceManager;
    
    public AnnounceCommand(RVNKTools plugin, AnnounceManager announceManager) {
        super(plugin);
        this.announceManager = announceManager;
        registerSubCommands();
    }
    
    private void registerSubCommands() {
        registerSubCommand("help", new AnnounceHelpCommand(plugin, announceManager));
        registerSubCommand("list", new AnnounceListCommand(plugin, announceManager));
        registerSubCommand("add", new AnnounceAddCommand(plugin, announceManager));
        registerSubCommand("delete", new AnnounceDeleteCommand(plugin, announceManager));
        // Register other subcommands
    }
    
    @Override
    public String getPermission() {
        return "rvnktools.command.announce";
    }
    
    @Override
    public String getUsage() {
        return "/announce <subcommand> [args...]";
    }
    
    @Override
    public String getDescription() {
        return "Manage server announcements";
    }
}

// Subcommand implementation
public class AnnounceListCommand extends BaseSubCommand {
    private final AnnounceManager announceManager;
    
    public AnnounceListCommand(RVNKTools plugin, AnnounceManager announceManager) {
        super(plugin);
        this.announceManager = announceManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!validatePermission(sender)) {
            return true;
        }
        
        List<Announcement> announcements = announceManager.getAnnouncements();
        if (announcements.isEmpty()) {
            MessageUtil.sendMessage(sender, "No announcements configured");
            return true;
        }
        
        MessageUtil.sendMessage(sender, "§6Announcements:");
        for (Announcement announcement : announcements) {
            MessageUtil.sendMessage(sender, String.format(
                "§7- §e%s §7(§f%s§7)",
                announcement.getId(),
                announcement.getType()
            ));
        }
        
        return true;
    }
    
    @Override
    public String getPermission() {
        return "rvnktools.command.announce.list";
    }
    
    @Override
    public String getUsage() {
        return "/announce list";
    }
    
    @Override
    public String getDescription() {
        return "List all announcements";
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
```

## Migration Schedule

| Week | Task | Commands to Refactor |
|------|------|----------------------|
| 1    | Framework Creation | Core interfaces and classes |
| 2    | Initial Refactoring | AnnounceCommand, HelpCommand |
| 3    | Main Command Migration | CycleCommands, LinkMaker, HatManager |
| 4    | Complex Commands | Admin commands, RVNKToolsCommand |
| 5    | Testing & Polishing | All commands |
| 6    | Documentation | Update all documentation |

## Conclusion

This refactoring will create a more maintainable and consistent command system for RVNKTools. By standardizing how commands are implemented, we'll reduce code duplication, improve error handling, and make future command additions simpler. The CommandManager will also provide better integration with other plugin systems like logging and permissions.
