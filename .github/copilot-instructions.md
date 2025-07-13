# RVNKTools Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the codebase using documentation files as references.

## General Directive

- **Use the CommandManager framework for all commands. Do not create standalone command executors.**
- **Follow SOLID principles when adding new features or refactoring existing code.**
- **Ensure proper resource cleanup in all managers and services.**

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Explain the class's purpose and responsibility in the system
- Note important design patterns or architectural decisions
- Focus on "why" over implementation details

```java
/**
 * Manages lore item creation and distribution with configurable properties.
 * Acts as the central registry for all custom items within the lore system.
 */
```

#### Method Documentation

- Describe purpose and behavior, not implementation
- Document parameters and return values
- Note exceptions that may be thrown
- Include examples for complex methods

```java
/**
 * Retrieves lore content based on provided entity type and identifier.
 * Handles fallback behavior when specific lore isn't available.
 *
 * @param entityType The type of entity to retrieve lore for
 * @param identifier Unique identifier within the entity type
 * @return The lore content or default text if none found
 * @throws IllegalArgumentException If entityType is null
 */
```

### Code Comments

- Comment on "why" less "what" - explain reasoning behind code
- Place comments above the code they describe
- Keep comments concise and meaningful
- Use TODO and FIXME sparingly and with clear descriptions
- Explain complex logic, business rules, or non-obvious decisions

## Message Formatting Standards

### Player-Facing Messages

Use these standardized message prefixes:

- `&c▶` for usage instructions and command help
- `&6⚙` for operations in progress
- `&a✓` for success messages
- `&c✖` for error messages
- `&e⚠` for warnings
- `&7␣␣␣` for additional information or tips (three spaces after)

### Console and Debug Messages

- Use the designated logging system for all console output
- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

## Logging Manager Standard

- Use the persistent `LogManager` class for all info, warning, and error logging in plugin code.
- Always declare the property as `private final LogManager logger;` (or `private LogManager logger;` if not final).
- Initialize with `this.logger = LogManager.getInstance(plugin);` in constructors.
- Use `logger.info(message)`, `logger.warning(message)`, and `logger.error(message, exception)` for all logging.
- Do not use `System.out.println()`, direct logger calls, or custom logger fields for these log levels.
- Use the property name `logger` for all `LogManager` usages to ensure consistency across the codebase.
- Reserve the `Debug` class for debug-level or trace logging only.

**Example:**

```java
private final LogManager logger;

public MyClass(RVNKLore plugin) {
    this.logger = LogManager.getInstance(plugin);
}

public void doSomething() {
    logger.info("Something happened");
    logger.warning("A warning");
    logger.error("An error occurred", exception);
}
```

## Command Framework Guidelines

Follow the CommandManager framework for all commands:

1. Extend `BaseCommand` for new commands:
```java
public class MyCommand extends BaseCommand {
    public MyCommand(RVNKTools plugin) {
        super(plugin, "commandname", 
              "Command description", 
              "/commandname <arg>",
              "rvnktools.command.permission");
    }
}
```

2. Register commands through CommandManager:
```java
commandManager.registerCommand(new MyCommand(plugin));
```

3. Use subcommands where appropriate:
```java
registerSubCommand("subcommand", new MySubCommand(plugin));
```

4. Implement proper tab completion:
```java
@Override
public List<String> tabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
        return getMatchingSubCommands(sender, args[0]);
    }
    return Collections.emptyList();
}
```

## Resource Management

- Initialize resources in proper order during onEnable
- Clean up resources in reverse order during onDisable
- Use try-with-resources for closeable resources
- Cancel tasks and unregister listeners properly
- Implement shutdown methods in manager classes

## Plugin Architecture

### Core Components

1. **CommandManager**
   - Central command registration and handling
   - Permission management
   - Tab completion support

2. **AnnounceManager**
   - Announcement scheduling and delivery
   - YAML configuration integration
   - PlaceholderAPI support

3. **LinkMaker**
   - Link creation and management
   - Click handling
   - Permission integration

4. **Integration Support**
   - Economy (Vault)
   - Permissions (LuckPerms)
   - PlaceholderAPI
   - Multiverse

### Best Practices

1. **Command Implementation**
   - Use CommandManager framework
   - Follow consistent error handling
   - Implement proper permissions
   - Support tab completion

2. **Configuration Management**
   - Use typed configuration objects
   - Validate configuration on load
   - Support live reloading
   - Handle missing/invalid values

3. **Event Handling**
   - Register listeners properly
   - Keep handlers focused
   - Consider performance impact
   - Clean up on disable

## Performance Considerations

- Use async tasks for I/O operations
- Implement caching where appropriate
- Batch operations when possible
- Monitor resource usage
- Clean up resources promptly

## Development Workflow

Use VS Code tasks for development:

- **Build Plugin**: `mvn clean package`
- **Copy to Server**: Copy JAR to dev server
- **Restart Server**: Full server restart
- **Reload Server**: Plugin reload only

## Testing Guidelines

- Test commands with various input combinations
- Verify permission handling
- Check resource cleanup
- Test integration points
- Validate configuration handling

## Documentation Reference

For detailed information, refer to:

- [README.md](../README.md) - Project overview and features
- [ROADMAP.md](../ROADMAP.md) - Development roadmap and priorities