# RVNKCore PlayerService API Documentation

**Version**: 1.0.0  
**Status**: ✅ Implemented  
**Package**: `org.fourz.rvnkcore.api.service`

## Overview

The PlayerService provides centralized player data management across the RVNK plugin ecosystem. It offers comprehensive async operations for player tracking, location management, name history, and permission group handling.

## Interface Definition

```java
package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerService {
    // Player retrieval methods
    CompletableFuture<Optional<PlayerDTO>> getPlayer(UUID playerId);
    CompletableFuture<Optional<PlayerDTO>> getPlayerByName(String playerName);
    CompletableFuture<Boolean> playerExists(UUID playerId);
    
    // Player lifecycle methods
    CompletableFuture<PlayerDTO> createPlayer(UUID playerId, String playerName, 
                                            String world, double x, double y, double z);
    CompletableFuture<PlayerDTO> savePlayer(PlayerDTO player);
    
    // Player data update methods
    CompletableFuture<Void> updatePlayerLocation(UUID playerId, String world, 
                                               double x, double y, double z);
    CompletableFuture<Void> updatePlayerName(UUID playerId, String newName);
    CompletableFuture<Void> updatePlayerGroups(UUID playerId, String primaryGroup, 
                                             List<String> allGroups);
    
    // Query methods
    CompletableFuture<List<PlayerDTO>> getRecentPlayers(int hoursAgo);
    CompletableFuture<List<PlayerDTO>> getPlayersByGroup(String groupName);
    CompletableFuture<List<PlayerDTO>> searchPlayersByName(String namePattern);
    CompletableFuture<Long> getPlayerCount();
}
```

## Method Reference

### Player Retrieval

#### getPlayer(UUID playerId)

Retrieves a player by their unique identifier.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier

**Returns:**

- `CompletableFuture<Optional<PlayerDTO>>` - Future containing player data if found

**Example:**

```java
playerService.getPlayer(playerUUID)
    .thenAccept(playerOpt -> {
        if (playerOpt.isPresent()) {
            PlayerDTO player = playerOpt.get();
            logger.info("Found player: " + player.getCurrentName());
            logger.info("Last seen: " + player.getLastSeen());
            logger.info("Location: " + player.getLastWorld() + 
                       " (" + player.getLastX() + ", " + 
                       player.getLastY() + ", " + player.getLastZ() + ")");
        } else {
            logger.info("Player not found");
        }
    })
    .exceptionally(throwable -> {
        logger.error("Failed to retrieve player", throwable);
        return null;
    });
```

#### getPlayerByName(String playerName)

Retrieves a player by their current name.

**Parameters:**

- `playerName` (String) - The player's current name

**Returns:**

- `CompletableFuture<Optional<PlayerDTO>>` - Future containing player data if found

**Example:**

```java
playerService.getPlayerByName("PlayerName")
    .thenAccept(playerOpt -> {
        if (playerOpt.isPresent()) {
            PlayerDTO player = playerOpt.get();
            logger.info("Player UUID: " + player.getId());
            logger.info("Name history: " + player.getNameHistory());
        }
    });
```

#### playerExists(UUID playerId)

Checks if a player exists in the database.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier

**Returns:**

- `CompletableFuture<Boolean>` - Future containing true if player exists

**Example:**

```java
playerService.playerExists(playerUUID)
    .thenAccept(exists -> {
        if (exists) {
            logger.info("Player exists in database");
        } else {
            logger.info("Player not found, creating new record");
            // Create new player logic
        }
    });
```

### Player Lifecycle

#### createPlayer(UUID, String, String, double, double, double)

Creates a new player record when they first join the server.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier
- `playerName` (String) - The player's name
- `world` (String) - The world they joined in
- `x` (double) - X coordinate
- `y` (double) - Y coordinate  
- `z` (double) - Z coordinate

**Returns:**

- `CompletableFuture<PlayerDTO>` - Future containing the created player data

**Example:**

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    if (!player.hasPlayedBefore()) {
        playerService.createPlayer(
            player.getUniqueId(),
            player.getName(),
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        ).thenAccept(playerDTO -> {
            logger.info("Created new player record: " + playerDTO.getCurrentName());
            player.sendMessage("§aWelcome to the server!");
        }).exceptionally(throwable -> {
            logger.error("Failed to create player record", throwable);
            return null;
        });
    }
}
```

#### savePlayer(PlayerDTO player)

Saves or updates player information.

**Parameters:**

- `player` (PlayerDTO) - The player data to save

**Returns:**

- `CompletableFuture<PlayerDTO>` - Future containing the saved player data

**Example:**

```java
// Get player, modify data, then save
playerService.getPlayer(playerUUID)
    .thenCompose(playerOpt -> {
        if (playerOpt.isPresent()) {
            PlayerDTO player = playerOpt.get();
            
            // Update player data
            player.updateName("NewName");
            player.updateLastLocation("nether", 100, 64, -50);
            
            // Save changes
            return playerService.savePlayer(player);
        } else {
            throw new IllegalArgumentException("Player not found");
        }
    })
    .thenAccept(savedPlayer -> {
        logger.info("Successfully updated player: " + savedPlayer.getCurrentName());
    });
```

### Player Data Updates

#### updatePlayerLocation(UUID, String, double, double, double)

Updates the player's location data.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier
- `world` (String) - The world name
- `x` (double) - X coordinate
- `y` (double) - Y coordinate
- `z` (double) - Z coordinate

**Returns:**

- `CompletableFuture<Void>` - Future that completes when update is finished

**Example:**

```java
@EventHandler
public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    Location loc = player.getLocation();
    
    playerService.updatePlayerLocation(
        player.getUniqueId(),
        player.getWorld().getName(),
        loc.getX(),
        loc.getY(),
        loc.getZ()
    ).thenRun(() -> {
        logger.debug("Updated location for " + player.getName() + 
                    " to " + player.getWorld().getName());
    }).exceptionally(throwable -> {
        logger.error("Failed to update player location", throwable);
        return null;
    });
}
```

#### updatePlayerName(UUID, String)

Updates the player's name and maintains name history.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier
- `newName` (String) - The new player name

**Returns:**

- `CompletableFuture<Void>` - Future that completes when update is finished

**Example:**

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    // Update name in case player changed it
    playerService.updatePlayerName(
        player.getUniqueId(),
        player.getName()
    ).thenRun(() -> {
        logger.debug("Updated name for " + player.getUniqueId() + 
                    " to " + player.getName());
    });
}
```

#### updatePlayerGroups(UUID, String, List<String>)

Updates the player's permission group information.

**Parameters:**

- `playerId` (UUID) - The player's unique identifier
- `primaryGroup` (String) - The primary permission group
- `allGroups` (List<String>) - List of all groups the player belongs to

**Returns:**

- `CompletableFuture<Void>` - Future that completes when update is finished

**Example:**

```java
// Example: Update player groups when permissions change
public void updatePlayerPermissions(Player player) {
    // Get groups from LuckPerms or other permission plugin
    String primaryGroup = "vip";
    List<String> allGroups = Arrays.asList("default", "vip", "builder");
    
    playerService.updatePlayerGroups(
        player.getUniqueId(),
        primaryGroup,
        allGroups
    ).thenRun(() -> {
        logger.info("Updated groups for " + player.getName() + 
                   ": primary=" + primaryGroup + ", all=" + allGroups);
    });
}
```

### Query Methods

#### getRecentPlayers(int hoursAgo)

Retrieves all players who have been seen within the specified hours.

**Parameters:**

- `hoursAgo` (int) - The number of hours to look back

**Returns:**

- `CompletableFuture<List<PlayerDTO>>` - Future containing list of recent players

**Example:**

```java
// Get players active in the last 24 hours
playerService.getRecentPlayers(24)
    .thenAccept(recentPlayers -> {
        logger.info("Found " + recentPlayers.size() + " recent players");
        
        for (PlayerDTO player : recentPlayers) {
            logger.info("- " + player.getCurrentName() + 
                       " (last seen: " + player.getLastSeen() + ")");
        }
    });

// Get players active in the last hour
playerService.getRecentPlayers(1)
    .thenAccept(players -> {
        if (players.isEmpty()) {
            logger.info("No players have been active in the last hour");
        } else {
            logger.info("Players active in last hour: " + 
                       players.stream()
                               .map(PlayerDTO::getCurrentName)
                               .collect(Collectors.joining(", ")));
        }
    });
```

#### getPlayersByGroup(String groupName)

Retrieves players by their permission group.

**Parameters:**

- `groupName` (String) - The name of the permission group

**Returns:**

- `CompletableFuture<List<PlayerDTO>>` - Future containing list of players in the group

**Example:**

```java
// Get all VIP players
playerService.getPlayersByGroup("vip")
    .thenAccept(vipPlayers -> {
        logger.info("VIP players (" + vipPlayers.size() + "):");
        
        for (PlayerDTO player : vipPlayers) {
            logger.info("- " + player.getCurrentName() + 
                       " (joined: " + player.getFirstJoin() + ")");
        }
    });

// Send message to all staff members
playerService.getPlayersByGroup("staff")
    .thenAccept(staffPlayers -> {
        for (PlayerDTO staffPlayer : staffPlayers) {
            Player onlineStaff = Bukkit.getPlayer(staffPlayer.getId());
            if (onlineStaff != null) {
                onlineStaff.sendMessage("§c[Staff] Server maintenance in 5 minutes!");
            }
        }
    });
```

#### searchPlayersByName(String namePattern)

Searches for players whose names match the provided pattern.

**Parameters:**

- `namePattern` (String) - The pattern to match (supports SQL LIKE syntax with % wildcards)

**Returns:**

- `CompletableFuture<List<PlayerDTO>>` - Future containing list of matching players

**Example:**

```java
// Search for players whose names start with "Admin"
playerService.searchPlayersByName("Admin%")
    .thenAccept(adminPlayers -> {
        logger.info("Found " + adminPlayers.size() + " admin players");
        
        for (PlayerDTO player : adminPlayers) {
            logger.info("- " + player.getCurrentName());
        }
    });

// Search for players with "test" anywhere in their name
playerService.searchPlayersByName("%test%")
    .thenAccept(testPlayers -> {
        if (!testPlayers.isEmpty()) {
            logger.info("Test players found: " + 
                       testPlayers.stream()
                                 .map(PlayerDTO::getCurrentName)
                                 .collect(Collectors.joining(", ")));
        }
    });

// Command example: /findplayer <partial-name>
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 1) {
        sender.sendMessage("§cUsage: /findplayer <partial-name>");
        return true;
    }
    
    String searchPattern = "%" + args[0] + "%";
    
    playerService.searchPlayersByName(searchPattern)
        .thenAccept(players -> {
            if (players.isEmpty()) {
                sender.sendMessage("§cNo players found matching: " + args[0]);
            } else {
                sender.sendMessage("§aFound " + players.size() + " player(s):");
                for (PlayerDTO player : players) {
                    sender.sendMessage("§f- " + player.getCurrentName() + 
                                     " (last seen: " + player.getLastSeen() + ")");
                }
            }
        })
        .exceptionally(throwable -> {
            sender.sendMessage("§cError searching for players: " + throwable.getMessage());
            return null;
        });
    
    return true;
}
```

#### getPlayerCount()

Gets the total count of registered players.

**Parameters:** None

**Returns:**

- `CompletableFuture<Long>` - Future containing the total player count

**Example:**

```java
// Get server statistics
playerService.getPlayerCount()
    .thenAccept(totalPlayers -> {
        logger.info("Total registered players: " + totalPlayers);
        
        // Also get recent players for comparison
        playerService.getRecentPlayers(24).thenAccept(recentPlayers -> {
            double activityRate = (double) recentPlayers.size() / totalPlayers * 100;
            logger.info("24h activity rate: " + String.format("%.1f%%", activityRate) + 
                       " (" + recentPlayers.size() + "/" + totalPlayers + ")");
        });
    });

// Command example: /serverstats
public void showServerStats(CommandSender sender) {
    CompletableFuture<Long> totalFuture = playerService.getPlayerCount();
    CompletableFuture<List<PlayerDTO>> recentFuture = playerService.getRecentPlayers(24);
    
    CompletableFuture.allOf(totalFuture, recentFuture)
        .thenRun(() -> {
            long total = totalFuture.join();
            int recent = recentFuture.join().size();
            int online = Bukkit.getOnlinePlayers().size();
            
            sender.sendMessage("§6═══ Server Statistics ═══");
            sender.sendMessage("§fOnline players: §a" + online);
            sender.sendMessage("§fActive (24h): §a" + recent);
            sender.sendMessage("§fTotal registered: §a" + total);
            sender.sendMessage("§fActivity rate: §a" + 
                              String.format("%.1f%%", (double) recent / total * 100));
        })
        .exceptionally(throwable -> {
            sender.sendMessage("§cFailed to retrieve server statistics");
            return null;
        });
}
```

## Advanced Usage Patterns

### Batch Operations

```java
// Update multiple players efficiently
public CompletableFuture<Void> updateMultiplePlayerLocations(
        Map<UUID, Location> playerLocations) {
    
    List<CompletableFuture<Void>> updateFutures = playerLocations.entrySet()
        .stream()
        .map(entry -> {
            UUID playerId = entry.getKey();
            Location loc = entry.getValue();
            
            return playerService.updatePlayerLocation(
                playerId,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ()
            );
        })
        .collect(Collectors.toList());
    
    return CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0]));
}
```

### Caching Pattern

```java
public class CachedPlayerService {
    
    private final PlayerService playerService;
    private final Map<UUID, PlayerDTO> cache = new ConcurrentHashMap<>();
    private final long cacheExpiryMs = 300000; // 5 minutes
    
    public CompletableFuture<Optional<PlayerDTO>> getCachedPlayer(UUID playerId) {
        PlayerDTO cached = cache.get(playerId);
        
        if (cached != null && !isCacheExpired(cached)) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        
        return playerService.getPlayer(playerId)
            .thenApply(playerOpt -> {
                playerOpt.ifPresent(player -> cache.put(playerId, player));
                return playerOpt;
            });
    }
    
    private boolean isCacheExpired(PlayerDTO player) {
        long ageMs = System.currentTimeMillis() - player.getLastSeen().getTime();
        return ageMs > cacheExpiryMs;
    }
}
```

### Error Handling Patterns

```java
public void robustPlayerOperation(UUID playerId) {
    playerService.getPlayer(playerId)
        .thenCompose(playerOpt -> {
            if (playerOpt.isEmpty()) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            
            PlayerDTO player = playerOpt.get();
            player.updateLastLocation("world", 0, 64, 0);
            
            return playerService.savePlayer(player);
        })
        .thenAccept(savedPlayer -> {
            logger.info("Successfully updated player: " + savedPlayer.getCurrentName());
        })
        .exceptionally(throwable -> {
            if (throwable.getCause() instanceof IllegalArgumentException) {
                logger.warning("Player not found: " + playerId);
            } else if (throwable.getCause() instanceof DatabaseException) {
                logger.severe("Database error updating player", throwable);
            } else {
                logger.severe("Unexpected error", throwable);
            }
            return null;
        });
}
```

## Performance Considerations

### Avoid Blocking Operations

```java
// ❌ Bad - Blocks main thread
try {
    Optional<PlayerDTO> player = playerService.getPlayer(playerId).get();
    // Main thread is blocked!
} catch (Exception e) {
    // Handle error
}

// ✅ Good - Non-blocking
playerService.getPlayer(playerId)
    .thenAccept(playerOpt -> {
        // Handle result asynchronously
    })
    .exceptionally(throwable -> {
        // Handle error asynchronously
        return null;
    });
```

### Minimize Database Calls

```java
// ❌ Bad - Multiple separate calls
playerService.updatePlayerName(playerId, newName);
playerService.updatePlayerLocation(playerId, world, x, y, z);
playerService.updatePlayerGroups(playerId, primaryGroup, allGroups);

// ✅ Good - Single operation
playerService.getPlayer(playerId)
    .thenCompose(playerOpt -> {
        if (playerOpt.isPresent()) {
            PlayerDTO player = playerOpt.get();
            
            // Update all data in memory
            player.updateName(newName);
            player.updateLastLocation(world, x, y, z);
            player.updateGroups(primaryGroup, allGroups);
            
            // Single save operation
            return playerService.savePlayer(player);
        } else {
            throw new IllegalArgumentException("Player not found");
        }
    });
```

## Integration Examples

### LuckPerms Integration

```java
@EventHandler
public void onUserPromote(UserPromoteEvent event) {
    UUID playerId = event.getUser().getUniqueId();
    
    // Get updated groups from LuckPerms
    User user = event.getUser();
    String primaryGroup = user.getPrimaryGroup();
    List<String> allGroups = user.getInheritedGroups(user.getQueryOptions())
        .stream()
        .map(Group::getName)
        .collect(Collectors.toList());
    
    // Update in RVNKCore
    playerService.updatePlayerGroups(playerId, primaryGroup, allGroups)
        .thenRun(() -> {
            logger.info("Updated groups for " + user.getFriendlyName());
        });
}
```

## Complete Implementation Example

Here's a complete working example that demonstrates both `playerService.updatePlayerGroups()` and `playerService.getRecentPlayers()`:

```java
package org.fourz.rvnktools.listener;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.core.RVNKCoreBootstrap;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Complete LuckPerms integration with RVNKCore PlayerService.
 * Demonstrates both updatePlayerGroups() and getRecentPlayers() functionality.
 */
public class LuckPermsIntegrationListener {
    
    private final RVNKCoreBootstrap coreBootstrap;
    private final LogManager logger;
    private final LuckPerms luckPerms;
    private final EventBus eventBus;
    
    public LuckPermsIntegrationListener(RVNKCoreBootstrap coreBootstrap, Plugin plugin) {
        this.coreBootstrap = coreBootstrap;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        try {
            this.luckPerms = LuckPermsManager.getLuckPerms();
            this.eventBus = luckPerms.getEventBus();
            registerEventListeners();
            logger.info("LuckPerms integration listener initialized successfully");
        } catch (IllegalStateException e) {
            logger.warning("LuckPerms not available: " + e.getMessage());
            throw e;
        }
    }
    
    private void registerEventListeners() {
        // Listen for user data recalculation events (includes group changes)
        eventBus.subscribe(UserDataRecalculateEvent.class, this::onUserDataRecalculate);
        logger.debug("Registered LuckPerms event listeners");
    }
    
    /**
     * IMPLEMENTATION: playerService.updatePlayerGroups() on LuckPerms.onUserPromote()
     * 
     * This method handles LuckPerms group changes and updates RVNKCore player data.
     */
    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        User user = event.getUser();
        UUID playerId = user.getUniqueId();
        
        logger.debug("Processing LuckPerms user data recalculation for: " + user.getFriendlyName());
        
        try {
            PlayerService playerService = coreBootstrap.getService(PlayerService.class);
            
            // Check if player exists in RVNKCore first
            playerService.playerExists(playerId)
                .thenCompose(exists -> {
                    if (!exists) {
                        logger.debug("Player not found in RVNKCore, skipping group update");
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    // Get updated groups from LuckPerms
                    String primaryGroup = user.getPrimaryGroup();
                    List<String> allGroups = user.getInheritedGroups(user.getQueryOptions())
                        .stream()
                        .map(Group::getName)
                        .collect(Collectors.toList());
                    
                    logger.debug("Updating groups for " + user.getFriendlyName() + 
                               ": primary=" + primaryGroup + ", all=" + allGroups);
                    
                    // ✅ IMPLEMENTATION: playerService.updatePlayerGroups()
                    return playerService.updatePlayerGroups(playerId, primaryGroup, allGroups);
                })
                .thenRun(() -> {
                    logger.debug("Successfully updated groups for " + user.getFriendlyName());
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to update groups for " + user.getFriendlyName(), throwable);
                    return null;
                });
                
        } catch (ServiceException e) {
            logger.error("Failed to get PlayerService for LuckPerms group update", e);
        }
    }
    
    /**
     * IMPLEMENTATION: playerService.getRecentPlayers()
     * 
     * This method demonstrates how to retrieve recent players from RVNKCore.
     */
    public void showRecentPlayerActivity(int hours) {
        try {
            PlayerService playerService = coreBootstrap.getService(PlayerService.class);
            
            logger.info("Retrieving players active in the last " + hours + " hours...");
            
            // ✅ IMPLEMENTATION: playerService.getRecentPlayers()
            playerService.getRecentPlayers(hours)
                .thenAccept(recentPlayers -> {
                    if (recentPlayers.isEmpty()) {
                        logger.info("No players found active in the last " + hours + " hours");
                        return;
                    }
                    
                    logger.info("Found " + recentPlayers.size() + " recent player(s):");
                    
                    for (PlayerDTO player : recentPlayers) {
                        logger.info("- " + player.getCurrentName() + 
                                   " (groups: " + player.getAllGroups() + 
                                   ", last seen: " + player.getLastSeen() + ")");
                    }
                    
                    // Example: Find VIP players who were recently active
                    List<PlayerDTO> recentVips = recentPlayers.stream()
                        .filter(p -> p.getPrimaryGroup() != null && p.getPrimaryGroup().equals("vip"))
                        .collect(Collectors.toList());
                    
                    if (!recentVips.isEmpty()) {
                        logger.info("Recent VIP players: " + 
                                   recentVips.stream()
                                           .map(PlayerDTO::getCurrentName)
                                           .collect(Collectors.joining(", ")));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error retrieving recent players", throwable);
                    return null;
                });
                
        } catch (ServiceException e) {
            logger.error("Failed to get PlayerService for recent players lookup", e);
        }
    }
    
    /**
     * Manual group synchronization method.
     */
    public CompletableFuture<Void> updatePlayerGroups(UUID playerId) {
        return luckPerms.getUserManager().loadUser(playerId)
            .thenCompose(user -> {
                if (user == null) {
                    logger.warning("User not found in LuckPerms: " + playerId);
                    return CompletableFuture.completedFuture(null);
                }
                
                try {
                    PlayerService playerService = coreBootstrap.getService(PlayerService.class);
                    
                    String primaryGroup = user.getPrimaryGroup();
                    List<String> allGroups = user.getInheritedGroups(user.getQueryOptions())
                        .stream()
                        .map(Group::getName)
                        .collect(Collectors.toList());
                    
                    return playerService.updatePlayerGroups(playerId, primaryGroup, allGroups);
                } catch (ServiceException e) {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            });
    }
    
    public void shutdown() {
        // LuckPerms EventBus automatically cleans up subscriptions when plugin unloads
        logger.info("LuckPerms event listeners will be cleaned up automatically");
    }
}
```

## Usage Summary

### 1. LuckPerms Integration (`updatePlayerGroups`)
The `LuckPermsIntegrationListener` automatically synchronizes permission group changes:

- **Listens to**: `UserDataRecalculateEvent` from LuckPerms
- **Updates**: Player group information in RVNKCore when permissions change
- **Method**: `playerService.updatePlayerGroups(playerId, primaryGroup, allGroups)`
- **Triggers**: Promotions, demotions, group changes, permission modifications

### 2. Recent Players Lookup (`getRecentPlayers`)
The `showRecentPlayerActivity()` method demonstrates querying recent player activity:

- **Retrieves**: Players active within specified hours
- **Method**: `playerService.getRecentPlayers(hours)`
- **Data includes**: Player name, groups, last seen time, activity status
- **Use cases**: Activity reports, finding recent VIPs, server statistics

### WorldGuard Integration

```java
@EventHandler
public void onRegionEnter(RegionEnterEvent event) {
    Player player = event.getPlayer();
    ProtectedRegion region = event.getRegion();
    
    // Log region entry in player data
    playerService.updatePlayerLocation(
        player.getUniqueId(),
        player.getWorld().getName() + ":" + region.getId(),
        player.getLocation().getX(),
        player.getLocation().getY(),
        player.getLocation().getZ()
    ).thenRun(() -> {
        logger.debug(player.getName() + " entered region: " + region.getId());
    });
}
```

---

**Implementation Status**: ✅ **Complete** - All methods are implemented and tested in the current RVNKCore development branch.
