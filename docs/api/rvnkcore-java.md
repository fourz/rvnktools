# RVNKCore Java API Documentation

**Version**: 1.0.0  
**Status**: In Development  
**Package**: `org.fourz.rvnkcore.api`

## Overview

The RVNKCore Java API provides programmatic access to the RVNK plugin ecosystem's core services and data layer. This API enables plugin developers to integrate with RVNKCore's centralized database, service registry, and cross-plugin communication system.

## Getting Started

### Dependencies

Add RVNKCore as a dependency in your plugin:

**Maven (`pom.xml`):**
```xml
<dependencies>
    <dependency>
        <groupId>org.fourz</groupId>
        <artifactId>rvnkcore-api</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (`build.gradle`):**
```gradle
dependencies {
    compileOnly 'org.fourz:rvnkcore-api:1.0.0'
}
```

### Plugin Declaration

Declare RVNKCore as a dependency in your `plugin.yml`:

```yaml
name: YourPlugin
version: 1.0.0
main: com.yourname.yourplugin.YourPlugin
depend: [RVNKCore]
api-version: 1.21
```

### Basic Usage

```java
import org.fourz.rvnkcore.api.RVNKCoreAPI;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.model.PlayerDTO;

public class YourPlugin extends JavaPlugin {
    
    private PlayerService playerService;
    
    @Override
    public void onEnable() {
        // Get the RVNKCore API instance
        RVNKCoreAPI api = RVNKCoreAPI.getInstance();
        
        // Get service instances
        this.playerService = api.getService(PlayerService.class);
        
        getLogger().info("Successfully integrated with RVNKCore!");
    }
}
```

## Core API Classes

### RVNKCoreAPI

Main entry point for accessing RVNKCore services.

```java
public class RVNKCoreAPI {
    
    /**
     * Gets the singleton instance of RVNKCore API.
     * 
     * @return The RVNKCore API instance
     * @throws IllegalStateException if RVNKCore is not loaded
     */
    public static RVNKCoreAPI getInstance();
    
    /**
     * Gets a service instance by type.
     * 
     * @param <T> The service type
     * @param serviceClass The service class
     * @return The service instance
     * @throws ServiceException if service is not available
     */
    public <T> T getService(Class<T> serviceClass);
    
    /**
     * Checks if a service is available.
     * 
     * @param serviceClass The service class
     * @return true if the service is registered and available
     */
    public boolean isServiceAvailable(Class<?> serviceClass);
    
    /**
     * Gets the RVNKCore plugin version.
     * 
     * @return Version string
     */
    public String getVersion();
}
```

## Service Layer

### PlayerService

Comprehensive player data management service.

```java
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.model.PlayerDTO;

// Get player data
CompletableFuture<Optional<PlayerDTO>> playerFuture = 
    playerService.getPlayer(playerUUID);

playerFuture.thenAccept(playerOpt -> {
    if (playerOpt.isPresent()) {
        PlayerDTO player = playerOpt.get();
        getLogger().info("Player: " + player.getCurrentName());
        getLogger().info("Last seen: " + player.getLastSeen());
        getLogger().info("Location: " + player.getLastWorld() + 
                        " (" + player.getLastX() + ", " + 
                        player.getLastY() + ", " + player.getLastZ() + ")");
    }
});

// Create new player
CompletableFuture<PlayerDTO> newPlayer = playerService.createPlayer(
    playerUUID, 
    playerName, 
    worldName, 
    x, y, z
);

// Update player location
CompletableFuture<Void> locationUpdate = playerService.updatePlayerLocation(
    playerUUID, 
    newWorld, 
    newX, newY, newZ
);

// Search players
CompletableFuture<List<PlayerDTO>> searchResults = 
    playerService.searchPlayersByName("Player%");
```

### Complete PlayerService Interface

```java
public interface PlayerService {
    
    // Basic CRUD operations
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    CompletableFuture<Boolean> playerExists(UUID playerId);
    
    // Player lifecycle
    CompletableFuture<PlayerDTO> createPlayer(UUID playerId, String playerName, 
                                            String world, double x, double y, double z);
    
    // Data updates
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String world, 
                                               double x, double y, double z);
    CompletableFuture<Void> updatePlayerName(UUID playerId, String newName);
    CompletableFuture<Void> updatePlayerGroups(UUID playerId, String primaryGroup, 
                                             List<String> allGroups);
    
    // Queries
    CompletableFuture<List<PlayerDTO>> getRecentPlayers(int hoursAgo);
    CompletableFuture<List<PlayerDTO>> getPlayersByGroup(String groupName);
    CompletableFuture<List<PlayerDTO>> searchPlayersByName(String namePattern);
    CompletableFuture<Long> getPlayerCount();
}
```

## Data Models

### PlayerDTO

Data transfer object representing player information.

```java
public class PlayerDTO {
    
    // Getters
    public UUID getId();
    public String getCurrentName();
    public List<String> getNameHistory();
    public Timestamp getFirstJoin();
    public Timestamp getLastSeen();
    public String getLastWorld();
    public double getLastX();
    public double getLastY();
    public double getLastZ();
    public String getPrimaryGroup();
    public List<String> getGroups();
    public boolean isBanned();
    
    // Update methods
    public void updateName(String newName);
    public void updateLastLocation(String world, double x, double y, double z);
    public void updateGroups(String primaryGroup, List<String> allGroups);
    
    // Builder pattern
    public static class Builder {
        public Builder id(UUID id);
        public Builder currentName(String name);
        public Builder nameHistory(List<String> history);
        public Builder firstJoin(Timestamp timestamp);
        public Builder lastSeen(Timestamp timestamp);
        public Builder lastLocation(String world, double x, double y, double z);
        public Builder primaryGroup(String group);
        public Builder groups(List<String> groups);
        public Builder banned(boolean banned);
        public PlayerDTO build();
    }
}
```

### Creating PlayerDTO Instances

```java
// Using builder pattern
PlayerDTO player = new PlayerDTO.Builder()
    .id(playerUUID)
    .currentName("PlayerName")
    .firstJoin(Timestamp.valueOf(LocalDateTime.now()))
    .lastSeen(Timestamp.valueOf(LocalDateTime.now()))
    .lastLocation("world", 0, 64, 0)
    .primaryGroup("default")
    .banned(false)
    .build();

// Updating existing player
player.updateName("NewPlayerName");
player.updateLastLocation("nether", 100, 64, -50);
player.updateGroups("vip", Arrays.asList("default", "vip"));
```

## Event System

### RVNKCore Events

RVNKCore provides custom events for cross-plugin communication.

```java
import org.fourz.rvnkcore.api.event.PlayerDataUpdatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class YourListener implements Listener {
    
    @EventHandler
    public void onPlayerDataUpdated(PlayerDataUpdatedEvent event) {
        PlayerDTO player = event.getPlayer();
        String updateType = event.getUpdateType(); // "location", "name", "groups"
        
        getLogger().info("Player " + player.getCurrentName() + 
                        " data updated: " + updateType);
    }
}
```

### Available Events

```java
// Player data events
public class PlayerDataUpdatedEvent extends RVNKEvent {
    public PlayerDTO getPlayer();
    public String getUpdateType();
    public Object getOldValue();
    public Object getNewValue();
}

public class PlayerCreatedEvent extends RVNKEvent {
    public PlayerDTO getPlayer();
    public boolean isFirstTimeJoin();
}

// Service lifecycle events
public class ServiceRegisteredEvent extends RVNKEvent {
    public Class<?> getServiceType();
    public Object getServiceInstance();
}

public class ServiceUnregisteredEvent extends RVNKEvent {
    public Class<?> getServiceType();
}
```

## Database Integration

### Custom Repository Pattern

Create custom repositories for your plugin data:

```java
import org.fourz.rvnkcore.database.repository.BaseRepository;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;

public class CustomDataRepository extends BaseRepository<CustomDataDTO, Long> {
    
    public CustomDataRepository(ConnectionProvider connectionProvider, 
                               QueryBuilder queryBuilder, 
                               Plugin plugin) {
        super(connectionProvider, queryBuilder, "custom_data", 
              CustomDataDTO.class, plugin);
    }
    
    @Override
    protected CustomDataDTO mapResultSet(ResultSet rs) throws SQLException {
        return new CustomDataDTO.Builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .value(rs.getString("value"))
            .build();
    }
    
    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }
    
    @Override
    protected Long getId(CustomDataDTO entity) {
        return entity.getId();
    }
    
    // Custom query methods
    public CompletableFuture<List<CustomDataDTO>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("name = ?")
                .build();
                
            // Execute query and map results
            // ... implementation details
        });
    }
}
```

### Using Shared Database Connection

```java
import org.fourz.rvnkcore.api.RVNKCoreAPI;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;

public class YourPlugin extends JavaPlugin {
    
    private CustomDataRepository customRepository;
    
    @Override
    public void onEnable() {
        RVNKCoreAPI api = RVNKCoreAPI.getInstance();
        
        // Get shared database components
        ConnectionProvider connectionProvider = api.getConnectionProvider();
        QueryBuilder queryBuilder = api.getQueryBuilder();
        
        // Create custom repository
        this.customRepository = new CustomDataRepository(
            connectionProvider, queryBuilder, this);
        
        // Use repository
        customRepository.findByName("example").thenAccept(results -> {
            getLogger().info("Found " + results.size() + " records");
        });
    }
}
```

## Configuration Integration

### Accessing RVNKCore Configuration

```java
import org.fourz.rvnkcore.api.config.RVNKCoreConfig;

RVNKCoreAPI api = RVNKCoreAPI.getInstance();
RVNKCoreConfig config = api.getConfiguration();

// Database settings
boolean isDatabaseEnabled = config.isDatabaseEnabled();
String databaseType = config.getDatabaseType();
String databaseUrl = config.getDatabaseUrl();

// Performance settings
int connectionPoolSize = config.getConnectionPoolSize();
long queryTimeout = config.getQueryTimeout();

// Feature toggles
boolean isEventSystemEnabled = config.isEventSystemEnabled();
boolean isRestApiEnabled = config.isRestApiEnabled();
```

## Best Practices

### Async Operations

Always use CompletableFuture for database operations:

```java
// ✅ Good - Non-blocking
playerService.getPlayer(playerUUID)
    .thenAccept(playerOpt -> {
        // Handle result on async thread
        if (playerOpt.isPresent()) {
            PlayerDTO player = playerOpt.get();
            // Process player data
        }
    })
    .exceptionally(throwable -> {
        getLogger().severe("Failed to get player: " + throwable.getMessage());
        return null;
    });

// ❌ Bad - Blocking main thread
try {
    Optional<PlayerDTO> player = playerService.getPlayer(playerUUID).get();
    // This blocks the main thread!
} catch (Exception e) {
    getLogger().severe("Error: " + e.getMessage());
}
```

### Error Handling

```java
playerService.updatePlayerLocation(playerUUID, world, x, y, z)
    .whenComplete((result, throwable) -> {
        if (throwable != null) {
            if (throwable instanceof ServiceException) {
                getLogger().warning("Service error: " + throwable.getMessage());
            } else if (throwable instanceof DatabaseException) {
                getLogger().severe("Database error: " + throwable.getMessage());
            } else {
                getLogger().severe("Unexpected error", throwable);
            }
        } else {
            getLogger().info("Player location updated successfully");
        }
    });
```

### Resource Management

```java
public class YourPlugin extends JavaPlugin {
    
    private PlayerService playerService;
    private List<CompletableFuture<?>> pendingOperations = new ArrayList<>();
    
    @Override
    public void onEnable() {
        this.playerService = RVNKCoreAPI.getInstance().getService(PlayerService.class);
    }
    
    @Override
    public void onDisable() {
        // Cancel pending operations
        pendingOperations.forEach(future -> future.cancel(true));
        pendingOperations.clear();
        
        getLogger().info("Plugin disabled, cleanup completed");
    }
    
    private void trackOperation(CompletableFuture<?> operation) {
        pendingOperations.add(operation);
        operation.whenComplete((result, throwable) -> {
            pendingOperations.remove(operation);
        });
    }
}
```

### Service Availability Checking

```java
@Override
public void onEnable() {
    RVNKCoreAPI api = RVNKCoreAPI.getInstance();
    
    if (!api.isServiceAvailable(PlayerService.class)) {
        getLogger().warning("PlayerService not available, some features disabled");
        return;
    }
    
    this.playerService = api.getService(PlayerService.class);
    getLogger().info("Successfully connected to RVNKCore services");
}
```

## Examples

### Complete Plugin Example

```java
package com.example.integration;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.RVNKCoreAPI;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.model.PlayerDTO;

public class ExamplePlugin extends JavaPlugin implements Listener {
    
    private PlayerService playerService;
    
    @Override
    public void onEnable() {
        try {
            RVNKCoreAPI api = RVNKCoreAPI.getInstance();
            this.playerService = api.getService(PlayerService.class);
            
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("Successfully integrated with RVNKCore!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to integrate with RVNKCore: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        
        // Get player data from RVNKCore
        playerService.getPlayer(player.getUniqueId())
            .thenAccept(playerOpt -> {
                if (playerOpt.isPresent()) {
                    PlayerDTO playerData = playerOpt.get();
                    
                    // Send welcome back message
                    player.sendMessage("§aWelcome back, " + playerData.getCurrentName() + "!");
                    player.sendMessage("§7Last seen: " + playerData.getLastSeen());
                    
                    // Check if name changed
                    if (!player.getName().equals(playerData.getCurrentName())) {
                        getLogger().info("Player " + playerData.getCurrentName() + 
                                       " changed name to " + player.getName());
                    }
                } else {
                    // New player
                    player.sendMessage("§aWelcome to the server, " + player.getName() + "!");
                }
            })
            .exceptionally(throwable -> {
                getLogger().warning("Failed to load player data for " + player.getName());
                return null;
            });
    }
}
```

## Migration Guide

### From Legacy Systems

If migrating from a custom player data system:

```java
// Legacy approach
public class LegacyPlayerData {
    public void savePlayerData(Player player) {
        // Direct database access
        // Custom SQL queries
        // Blocking operations
    }
}

// RVNKCore approach
public class ModernPlayerData {
    private final PlayerService playerService;
    
    public ModernPlayerData() {
        this.playerService = RVNKCoreAPI.getInstance().getService(PlayerService.class);
    }
    
    public void savePlayerData(Player player) {
        playerService.updatePlayerLocation(
            player.getUniqueId(),
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        ).whenComplete((result, throwable) -> {
            if (throwable != null) {
                // Handle error
            } else {
                // Success
            }
        });
    }
}
```

---

**Note**: This API is part of the RVNKCore system currently under development. Some features may not be fully implemented yet. Check the [RVNKCore Roadmap](../ROADMAP.md) for current implementation status.
