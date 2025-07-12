# RVNKTools LogManager

## Overview

The LogManager is a centralized logging system for the RVNKTools plugin that provides consistent logging capabilities across all components. It simplifies logging management, improves code readability, and adds features like debug levels and performance tracking.

## Features

- **Centralized logging** - Single point of configuration for all plugin logging
- **Context-aware logs** - Class-specific logging with automatic prefixing
- **Multiple log levels** - Support for info, debug, warning, and error levels
- **Performance tracking** - Built-in timing for performance-sensitive operations
- **Exception handling** - Simplified error logging with stack traces
- **Singleton pattern** - Efficient instance management and reuse

## Usage

### Basic Usage

```java
// Get an instance for your class
private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());

// Log at different levels
logger.info("Plugin initialized");
logger.debug("Processing data structure");
logger.warning("Configuration incomplete");
logger.error("Failed to connect to database");

// Log with exceptions
try {
    // Some operation
} catch (Exception e) {
    logger.error("Operation failed", e);
}
```

### Performance Monitoring

```java
// Start timing
long start = System.nanoTime();

// Perform operation
doSomethingExpensive();

// Log performance
long elapsed = System.nanoTime() - start;
logger.performance("ExpensiveOperation", elapsed);
```

### Conditional Debug Logging

```java
// Check if debug is enabled before performing expensive debug operations
if (logger.isDebugEnabled()) {
    String complexDebugInfo = generateComplexDebugInfo();
    logger.debug(complexDebugInfo);
}
```

## Implementation

### RVNKLogger Interface

The interface defines the contract for all loggers in the system:

```java
public interface RVNKLogger {
    void debug(String message);
    void debug(String message, Throwable throwable);
    void info(String message);
    void info(String message, Throwable throwable);
    void warning(String message);
    void warning(String message, Throwable throwable);
    void error(String message);
    void error(String message, Throwable throwable);
    void performance(String section, long timeInNanos);
    boolean isDebugEnabled();
    void setDebugEnabled(boolean enabled);
}
```

### LogManager Class

The LogManager implements the RVNKLogger interface and provides instance management:

```java
public class LogManager implements RVNKLogger {
    private static final Map<String, LogManager> instances = new ConcurrentHashMap<>();
    private final Logger logger;
    private boolean debugEnabled = false;
    private final String prefix;

    private LogManager(Plugin plugin, String prefix) {
        this.logger = plugin.getLogger();
        this.prefix = prefix;
    }

    public static LogManager getInstance(Plugin plugin, Class<?> clazz) {
        String key = plugin.getName() + ":" + clazz.getSimpleName();
        return instances.computeIfAbsent(key, k -> 
            new LogManager(plugin, "[" + clazz.getSimpleName() + "] "));
    }

    public static LogManager getInstance(Plugin plugin) {
        return getInstance(plugin, plugin.getClass());
    }

    // Implementation of logger methods
}
```

## Configuration

Debug logging can be enabled globally or for specific classes via the config.yml:

```yaml
logging:
  debug: false                  # Global debug setting
  performance-tracking: true    # Track performance metrics
  classes:
    DataManager: true           # Enable debug for specific classes
    CommandManager: true
    EventManager: false
```

## Best Practices

### 1. **Use Appropriate Log Levels**

- **DEBUG**: Detailed information useful for diagnosing problems
- **INFO**: Confirmation that things are working as expected
- **WARNING**: Indication that something unexpected happened, but the application can continue
- **ERROR**: A serious problem that prevents normal operation

### 2. **Meaningful Messages**

- Include relevant context in log messages
- Use consistent formatting for similar events
- Include identifiers (e.g., player names, item IDs) in messages

### 3. **Performance Considerations**

- Use `isDebugEnabled()` before constructing complex debug messages
- Keep logging concise in performance-critical code paths
- Use the performance tracker for critical operations

### 4. **Exception Handling**

- Always log the full exception with stack trace
- Include context about what caused the exception
- Don't catch and log the same exception multiple times

### 5. **Integration with Other Systems**

- Use LogManager with CommandManager for command logging
- Integrate with the DataStoreManager for data operation logging
- Use in event listeners for tracking important server events

## Migration from Debug Class

The LogManager replaces the older Debug class. Here's how to migrate:

```java
// Old debug pattern
private final Debug debug = new Debug(plugin, CLASS_NAME, AnnounceConfig.getLogLevel()) {};

// Migrate to LogManager pattern
private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());

// Update method calls
debug.info("Message");  // Old
logger.info("Message"); // New

debug.debug("Message");  // Old
logger.debug("Message"); // New

debug.error("Message", e);  // Old
logger.error("Message", e); // New
```

## Future Enhancements

- **Log file rotation** - Automatically manage log file sizes
- **Category-based logging** - Enable/disable logging for specific categories
- **Remote logging** - Send logs to a remote server for monitoring
- **Statistical aggregation** - Collect and analyze performance metrics over time
- **Log search and filtering** - Tools to analyze and search through logs
