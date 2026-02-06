# RVNK Plugin Structure Examples

> For async patterns, error handling, repository, and DTO examples, see parent [coding-standards.md](../../../docs/standard/coding-standards.md)

## Standard Plugin Directory Structure

```text
Plugin Root/
├── api/                    # Public plugin APIs
├── service/               # Business logic services
├── repository/            # Data access layer
├── command/              # Command implementations
├── listener/             # Event listeners
├── config/               # Configuration management
└── integration/          # Third-party integrations
```

## Plugin Dependencies (plugin.yml)

```yaml
depend: [RVNKCore]
softdepend: [RVNKTools, RVNKLore, PlaceholderAPI, Vault]
```

## Configuration Structure

```yaml
# Standard config.yml structure
plugin-name:
  database:
    enabled: true
    type: shared  # Use RVNKCore shared database

  features:
    feature-name:
      enabled: true
      options:
        option1: value1
        option2: value2

  integration:
    placeholder-api: true
    vault: true
```

## Performance Monitoring Pattern

```java
public class PerformanceMonitoredService {
    private final DebugLogger logger;

    public CompletableFuture<Data> getData(String key) {
        try (AutoCloseable timer = logger.timeSection("getData")) {
            return cache.get(key)
                .orElseGet(() -> repository.findByKey(key)
                    .thenApply(data -> {
                        cache.put(key, data);
                        return data;
                    }));
        }
    }
}
```
