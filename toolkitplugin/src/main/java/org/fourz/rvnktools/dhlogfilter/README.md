# DH Log Filter

The DH Log Filter system reduces console spam from repetitive Distant Horizons server plugin messages while maintaining visibility of important warnings and errors.

## Features

- **Hierarchical Log Level Filtering**: DEBUG (verbose) → INFO (default) → WARN → ERROR (restrictive)
- **Keyword-based Message Filtering**: Configurable patterns with optional regex support
- **Rate Limiting**: Prevents repetitive messages using a 30-second cache window
- **Asynchronous Operations**: All I/O operations use CompletableFuture to prevent server lag
- **Performance Monitoring**: Built-in statistics tracking and processing time metrics
- **Live Configuration Reloading**: Update settings without server restart

## Configuration

The filter is configured through `dhlogfilter.yml` in your plugin's data folder:

```yaml
logging:
  # Log level: DEBUG, INFO, WARN, ERROR
  level: INFO
  
  # Enable/disable the filtering system
  enabled: true
  
  # Keywords to filter (supports regex if enabled)
  keywords:
    - "[DHS] Received"
    - "[DHS] Player config" 
    - "[DHS] Chunk data"
  
  # Rate limiting window in seconds
  rateLimitSeconds: 30
  
  # Enable regex pattern matching
  useRegexPatterns: false
```

## Commands

All commands require the `rvnktools.command.dhfilter` permission (default: op).

### `/dhfilter reload`
Reloads the configuration file and applies changes immediately.

### `/dhfilter status` 
Shows current filter status and performance statistics:
- Filter active/inactive status
- Current log level and keyword rules
- Messages filtered vs. allowed
- Processing performance metrics
- Cache statistics

### `/dhfilter level <debug|info|warn|error>`
Temporarily changes the log level without modifying the configuration file. Use `reload` to restore the configured level.

## Usage Examples

### Basic Setup
1. The filter initializes automatically when the plugin starts
2. Default configuration filters `[DHS] Received` messages with INFO level
3. Use `/dhfilter status` to verify operation

### Custom Configuration
```yaml
# Filter everything except errors
logging:
  level: ERROR
  enabled: true
  keywords: []

# Verbose debugging (disable filtering)
logging:
  level: DEBUG
  enabled: false

# Advanced regex patterns
logging:
  level: INFO
  keywords:
    - "\[DHS\] Received.*config.*"
    - "\[DHS\] Player \w+ in \w+"
  useRegexPatterns: true
```

### Performance Tuning
- **Rate Limiting**: Lower `rateLimitSeconds` for more aggressive filtering
- **Regex Patterns**: Use sparingly as they impact performance
- **Log Levels**: Use WARN or ERROR for maximum spam reduction

## Architecture

The system follows RVNK Plugin Ecosystem standards:

- **Service Layer**: `DHLogFilterService` with async CompletableFuture operations
- **Configuration**: `DHLogFilterConfigRepository` with YAML persistence  
- **Commands**: `DHLogFilterCommand` extending BaseCommand with CommandManager
- **Management**: `DHLogFilterManager` for lifecycle and resource cleanup
- **Statistics**: `DHLogFilterStats` for performance monitoring

## Integration

The filter integrates seamlessly with:
- **RVNKCore**: Service registry and dependency injection
- **CommandManager**: Standardized command framework
- **LogManager**: Consistent logging patterns
- **Plugin Lifecycle**: Proper initialization and cleanup

## Performance Impact

The filter is designed for minimal performance impact:
- **Thread-safe**: Uses concurrent collections and atomic operations
- **Memory efficient**: Automatic cache cleanup and size limits
- **Asynchronous**: Non-blocking I/O operations
- **Optimized**: Fast string matching with optional regex fallback

Typical processing time is < 0.001ms per message with minimal memory usage.