# RVNKTools Feature: Cycle Commands

## Overview

The Cycle Commands system provides a powerful framework for creating commands that cycle through different behaviors each time they are used. This enables server administrators to create complex, multi-stage command sequences with minimal configuration.

## Features

- **Sequential Command Execution**: Define a sequence of commands that rotate with each use
- **Player-Specific State Tracking**: Each player's position in the cycle is tracked independently
- **Persistent State**: Command positions persist across server restarts
- **Configurable Actions**: Support for various action types (commands, messages, permissions)
- **Delayed Execution**: Support for timed delays between actions
- **Dynamic Variables**: Variable substitution in commands and messages
- **Permission Integration**: Fine-grained permission control

## Configuration

### Basic Structure

```yaml
commands:
  cyclegamemode:
    permission: rvnktools.cycle.gamemode
    instructions:
      set_survival:
        - run_command_as_server: gamemode survival $player
        - send_message_to_player: "&a✓ Gamemode set to &fSurvival"
      set_creative:
        - run_command_as_server: gamemode creative $player
        - send_message_to_player: "&a✓ Gamemode set to &fCreative"
      set_adventure:
        - run_command_as_server: gamemode adventure $player
        - send_message_to_player: "&a✓ Gamemode set to &fAdventure"
      set_spectator:
        - run_command_as_server: gamemode spectator $player
        - send_message_to_player: "&a✓ Gamemode set to &fSpectator"
```

### Advanced Configuration Example

```yaml
commands:
  eventmode:
    permission: rvnktools.cycle.eventmode
    instructions:
      event_start:
        - send_message_to_all_players: "&6⚙ $player is starting an event!"
        - set_permission: rvnktools.event.participant
        - run_command_as_server: tp $player world_event 100 64 100
        - send_message_to_player: "&a✓ Event mode activated"
        - wait: 5s
        - send_message_to_player: "&7   The event will begin shortly..."
      event_end:
        - send_message_to_all_players: "&6⚙ $player is ending the event"
        - unset_permission: rvnktools.event.participant
        - run_command_as_server: tp $player world 0 64 0
        - send_message_to_player: "&a✓ Event mode deactivated"
        - wait: 3s
        - run_command_as_server: clear $player
```

## Core Components

### CycleCommands

The `CycleCommands` class serves as the main manager for cycle commands:

```java
public class CycleCommands {
    private final RVNKTools plugin;
    private final LogManager logger;
    private FileConfiguration config;
    private CycleState state;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleCommands(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.playerCommandPositions = new HashMap<>();
        loadConfig();
        registerCommands();
        logger.info("CycleCommands initialized successfully");
    }
    
    public void loadConfig() {
        // Load configuration from file
    }
    
    public void saveState() {
        // Save command state to persistent storage
    }
    
    public void registerCommands() {
        // Register commands defined in configuration
    }
    
    public String getNextInstructionKey(String commandKey, UUID playerId) {
        // Get the next instruction set for this player and command
    }
}
```

### CycleState

The `CycleState` class manages the persistent state of cycle commands:

```java
public class CycleState {
    private final File stateFile;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleState(File pluginFolder, String filename) {
        this.stateFile = new File(pluginFolder, filename);
        this.playerCommandPositions = new HashMap<>();
    }
    
    public void load() {
        // Load state from file
    }
    
    public void save() {
        // Save state to file
    }
    
    public Map<String, Map<UUID, Integer>> getPlayerCommandPositions() {
        // Get current command positions
    }
    
    public void setPlayerCommandPosition(String command, UUID player, int position) {
        // Set a player's position for a command
    }
}
```

### CycleCommandExecutor

The `CycleCommandExecutor` class handles the execution of cycle commands:

```java
public class CycleCommandExecutor implements CommandExecutor {
    private final String commandKey;
    private final ConfigurationSection commandConfig;
    private final CycleCommands cycleCommands;

    public CycleCommandExecutor(String commandKey, ConfigurationSection commandConfig, CycleCommands cycleCommands) {
        this.commandKey = commandKey;
        this.commandConfig = commandConfig;
        this.cycleCommands = cycleCommands;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Process and execute the cycle command
    }
    
    private void executeInstructions(Player player, Iterator<Map<?, ?>> iterator) {
        // Execute instruction set actions
    }
}
```

## Instruction Types

### Available Actions

| Action Type | Description | Example |
|-------------|-------------|---------|
| `run_command_as_player` | Executes a command as the player | `run_command_as_player: kit tools` |
| `run_command_as_server` | Executes a command as the server | `run_command_as_server: give $player diamond 1` |
| `send_message_to_player` | Sends a message to the player | `send_message_to_player: "&aYou received a diamond!"` |
| `send_message_to_all_players` | Broadcasts a message to all players | `send_message_to_all_players: "&e$player received a reward!"` |
| `set_permission` | Grants a permission to the player | `set_permission: rvnktools.vip.fly` |
| `unset_permission` | Removes a permission from the player | `unset_permission: rvnktools.vip.fly` |
| `wait` | Pauses execution for a specified time | `wait: 5s` |

### Variable Substitution

Variables can be used in command and message actions:

| Variable | Description | Example |
|----------|-------------|---------|
| `$player` | The player's name | `tp $player 0 64 0` |
| `$uuid` | The player's UUID | `data modify entity @e[type=armor_stand,limit=1] ArmorItems[3].tag.SkullOwner set value "$uuid"` |
| `$world` | The player's current world | `broadcast $player is in $world` |

## Integration with CommandManager

The CycleCommands system integrates with the CommandManager framework:

```java
// In CommandManager constructor
private final CycleCommands cycleCommands;

private CommandManager(RVNKTools plugin) {
    this.plugin = plugin;
    this.logger = LogManager.getInstance(plugin, getClass());
    this.commands = new HashMap<>();
    this.aliases = new HashMap<>();
    this.cycleCommands = new CycleCommands(plugin);
}

// In initializeCommands()
public void initializeCommands() {
    // Register standard commands...
    
    // Register cycle commands
    cycleCommands.registerCommands();
    
    logger.info("Command initialization complete!");
}
```

## Error Handling and Logging

The CycleCommands system uses the LogManager for comprehensive logging:

```java
// In CycleCommands constructor
this.logger = LogManager.getInstance(plugin, getClass());

// Debug logging
logger.debug("Loading cycle commands configuration");

// Info logging
logger.info("Successfully registered " + registeredCount + " cycle commands");

// Warning logging
logger.warning("No commands section found in configuration");

// Error logging
logger.error("Failed to register command: " + commandKey, e);
```

## Use Cases

### 1. Gamemode Cycling

Create a single command that cycles through different game modes:

```yaml
commands:
  cyclegamemode:
    permission: rvnktools.cycle.gamemode
    instructions:
      set_survival:
        - run_command_as_server: gamemode survival $player
      set_creative:
        - run_command_as_server: gamemode creative $player
      set_adventure:
        - run_command_as_server: gamemode adventure $player
      set_spectator:
        - run_command_as_server: gamemode spectator $player
```

### 2. Multi-Stage Event Management

Manage complex event sequences:

```yaml
commands:
  eventphase:
    permission: rvnktools.admin.event
    instructions:
      phase1:
        - send_message_to_all_players: "&6⚙ Event Phase 1 starting!"
        - run_command_as_server: worldborder set 1000
      phase2:
        - send_message_to_all_players: "&6⚙ Event Phase 2 starting!"
        - run_command_as_server: worldborder set 500 300
      phase3:
        - send_message_to_all_players: "&6⚙ Final Phase starting!"
        - run_command_as_server: worldborder set 100 120
      reset:
        - send_message_to_all_players: "&6⚙ Event concluded!"
        - run_command_as_server: worldborder set 1000 5
```

### 3. Role-Based Access Control

Toggle permissions for specific roles:

```yaml
commands:
  togglebuilder:
    permission: rvnktools.admin.roles
    instructions:
      grant_builder:
        - set_permission: rvnktools.builder
        - send_message_to_player: "&a✓ Builder permissions granted"
        - run_command_as_server: give $player builder_tool
      revoke_builder:
        - unset_permission: rvnktools.builder
        - send_message_to_player: "&c✖ Builder permissions revoked"
        - run_command_as_server: clear $player
```

## Command Registration in plugin.yml

Cycle commands must be registered in plugin.yml:

```yaml
commands:
  cyclegamemode:
    description: Cycle through different game modes
    usage: /cyclegamemode
    permission: rvnktools.cycle.gamemode
  eventphase:
    description: Control event phases
    usage: /eventphase
    permission: rvnktools.admin.event
  togglebuilder:
    description: Toggle builder permissions
    usage: /togglebuilder
    permission: rvnktools.admin.roles
```

## Permissions

### Permission Structure

Cycle commands use the standard permission structure:

```yaml
permissions:
  rvnktools.cycle.*:
    description: Access to all cycle commands
    default: op
    children:
      rvnktools.cycle.gamemode: true
      rvnktools.cycle.eventmode: true
  
  rvnktools.cycle.gamemode:
    description: Allows cycling through game modes
    default: op
  
  rvnktools.cycle.eventmode:
    description: Allows toggling event mode
    default: op
```

## Performance Considerations

- **State Management**: Player state is stored in memory with periodic saves to disk
- **Command Registration**: Commands are registered at startup to minimize runtime overhead
- **Asynchronous Operations**: Time-consuming operations are performed asynchronously
- **Caching**: Configuration and state are cached for optimal performance

## Best Practices

### 1. Configuration

- Use descriptive instruction set names
- Keep instruction sets focused on related actions
- Use comments to document complex sequences
- Validate permissions in configuration

### 2. Command Design

- Create intuitive command sequences
- Provide clear feedback messages
- Include confirmation for destructive actions
- Use appropriate wait times for user experience

### 3. Error Handling

- Validate all configuration values
- Handle missing or invalid configuration
- Provide fallback options for missing state
- Log errors with sufficient context

## Troubleshooting

### Common Issues

1. **NullPointerException in CycleCommandExecutor**
   - Cause: Missing cycleCommands reference
   - Solution: Ensure proper initialization in CommandManager

2. **Command Not Found**
   - Cause: Command not registered in plugin.yml
   - Solution: Add command definition to plugin.yml

3. **State Not Persisting**
   - Cause: State file corruption or saving issues
   - Solution: Check file permissions and error handling in save methods

4. **Permission Issues**
   - Cause: Incorrect permission node or missing permission check
   - Solution: Verify permission nodes and permission checking logic

## Future Enhancements

1. **GUI-Based Configuration**: Visual editor for cycle command configuration
2. **Conditional Instructions**: Support for condition-based action execution
3. **Extended Variables**: More context variables for dynamic command generation
4. **Cycle Presets**: Pre-configured cycle command templates
5. **Command Analytics**: Usage tracking and performance metrics

## Conclusion

The Cycle Commands system provides a powerful and flexible way to create complex command sequences with minimal configuration. By leveraging this feature, server administrators can create intuitive, multi-stage commands that enhance the user experience and simplify administrative tasks.

The integration with the CommandManager framework ensures consistent behavior, proper error handling, and seamless permission checking, making Cycle Commands a robust and reliable tool for server management.
