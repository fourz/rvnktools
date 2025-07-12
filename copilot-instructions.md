# RVNKTools Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the codebase.

## General Directive

- **Focus on quality-of-life improvements for small-scale administrative tasks**
- **Consider modular design that would support feature extraction in the future**
- **Prioritize backward compatibility and data preservation**

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Explain the class's purpose and responsibility in the system
- Note important design patterns or architectural decisions
- Focus on "why" over implementation details

```java
/**
 * Manages scheduled announcements with configurable intervals and targeting.
 * Implements observer pattern to notify players of announcements.
 */
```

#### Method Documentation

- Describe purpose and behavior, not implementation
- Document parameters and return values
- Note exceptions that may be thrown
- Include examples for complex methods

```java
/**
 * Schedules an announcement to be displayed at specified intervals.
 *
 * @param announcement The announcement content to schedule
 * @param interval The interval in seconds between announcements
 * @param targetGroup Target player group, or null for all players
 * @return The unique identifier for the scheduled announcement
 * @throws IllegalArgumentException If interval is less than 5 seconds
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

## Logging Standard

- Use consistent logging approaches across the codebase
- Always declare the logger as a class variable
- Use appropriate log levels for different message types
- Include relevant context in log messages
- Consider implementing a centralized LogManager similar to the example

**Example Logger Implementation:**

```java
private final Logger logger = Logger.getLogger(getClass().getName());

public void doSomething() {
    logger.info("Operation started");
    try {
        // operation code
        logger.info("Operation completed successfully");
    } catch (Exception e) {
        logger.severe("Operation failed: " + e.getMessage());
    }
}
```

## Database Architecture Guidelines

When working with database functionality, follow these guidelines:

- Use prepared statements for all database queries
- Implement connection pooling for performance
- Close all resources in finally blocks or try-with-resources
- Use transactions for multi-step operations
- Consider implementing query builder and repository patterns for maintainability

## Code Structure Best Practices

### Command Implementation

For commands, follow these patterns:

- Separate command logic from execution
- Implement consistent permission checking
- Provide clear feedback for all command outcomes
- Use tab completers for better user experience
- Follow the command naming convention (verb-noun)

### Event Handling

- Register handlers properly in the plugin lifecycle
- Keep handlers focused and lightweight
- Consider performance implications for high-frequency events
- Clean up listeners when no longer needed

### Resource Management

- Properly initialize and clean up resources
- Use try-with-resources for closeable resources
- Unregister listeners and cancel tasks on plugin disable

## Performance Considerations

- Use asynchronous operations for I/O and database access
- Implement caching for frequently accessed data
- Batch operations when possible
- Monitor resource usage and performance metrics

## Modular Design Consideration

When implementing features, consider how they might be extracted into separate plugins in the future:

- Create clear boundaries between feature sets
- Design with API interfaces in mind
- Implement feature-specific event systems
- Avoid tight coupling between different functional areas

### Announcement System Modularization

The announcement system is a primary candidate for extraction into a standalone plugin:

1. **Current Approach**: Directly integrated into RVNKTools
2. **Transition Approach**: 
   - Implement an internal API for announcement management
   - Move announcement logic to separate packages
   - Create event-based communication for announcements
3. **Final Goal**: Standalone plugin with API for other plugins to leverage

## Development Workflow

### Building and Testing

To build and test the plugin, use one of the following methods:

- **Reload Server**
  - Use the `Reload Server` task to build, copy and reload the plugin without restarting the server. This is useful for quick testing of changes.

- **Restart the Server**:
  - Use the `Restart Server` task to build, copy and fully restart the server. This ensures a clean state and is recommended for testing major changes.

These tasks can be executed from the VS Code task runner or directly from the terminal using the provided PowerShell scripts at .vscode/*.ps1

### API Gateway Consideration

Consider designing RVNKTools to serve as an API gateway for other RVNK plugins:

- Implement a central registry for plugin services
- Create standardized communication channels between plugins
- Develop shared utility services (config, database, etc.)
- Design with cross-plugin dependencies in mind

## Documentation Best Practices

- Document configuration options thoroughly
- Create clear user guides for each feature
- Maintain changelogs for all versions
- Document API endpoints and expected behavior

Remember that RVNKTools is designed as a collection of small, focused QoL improvements. Prioritize usability and reliability over complex features. Consider the future possibility of splitting larger components (like the announcement system) into standalone plugins while maintaining RVNKTools as a central API gateway for other RVNK plugins.
