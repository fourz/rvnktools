# World Tracking Implementation Guide

**Implementation Guide ID**: 05-world-tracking-implementation-guide  
**Phase**: World Tracking Implementation  
**Prerequisites**: Database schema created, Core services implemented

## Overview

This implementation guide provides step-by-step instructions for implementing comprehensive world tracking capabilities within the RVNKCore PlayerWorld system, including automated world discovery, metadata management, and analytics.

## Implementation Steps

### Step 1: World Repository Implementation

**File**: `src/main/java/org/fourz/rvnkcore/repository/WorldRepository.java`

```java
package org.fourz.rvnkcore.repository;

import org.fourz.rvnkcore.data.WorldData;
import org.fourz.rvnkcore.database.ConnectionProvider;
import org.bukkit.World;
import org.bukkit.Difficulty;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DefaultWorldRepository implements WorldRepository {
    
    private final ConnectionProvider connectionProvider;
    
    public DefaultWorldRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }
    
    @Override
    public CompletableFuture<Optional<WorldData>> findByName(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT w.*, 
                       COALESCE(SUM(pwd.visit_count), 0) as total_visits,
                       COALESCE(SUM(pwd.playtime_seconds), 0) as total_playtime,
                       COUNT(DISTINCT pwd.player_id) as unique_players
                FROM rvnk_worlds w
                LEFT JOIN rvnk_player_world_data pwd ON w.world_name = pwd.world_name
                WHERE w.world_name = ?
                GROUP BY w.world_name
                """;
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, worldName);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return Optional.of(mapResultSetToWorldData(rs));
                }
                return Optional.empty();
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find world: " + worldName, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<WorldData>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT w.*, 
                       COALESCE(SUM(pwd.visit_count), 0) as total_visits,
                       COALESCE(SUM(pwd.playtime_seconds), 0) as total_playtime,
                       COUNT(DISTINCT pwd.player_id) as unique_players
                FROM rvnk_worlds w
                LEFT JOIN rvnk_player_world_data pwd ON w.world_name = pwd.world_name
                GROUP BY w.world_name
                ORDER BY w.created_date
                """;
                
            List<WorldData> worlds = new ArrayList<>();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    worlds.add(mapResultSetToWorldData(rs));
                }
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find all worlds", e);
            }
            
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldData>> findByType(String worldType) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT w.*, 
                       COALESCE(SUM(pwd.visit_count), 0) as total_visits,
                       COALESCE(SUM(pwd.playtime_seconds), 0) as total_playtime,
                       COUNT(DISTINCT pwd.player_id) as unique_players
                FROM rvnk_worlds w
                LEFT JOIN rvnk_player_world_data pwd ON w.world_name = pwd.world_name
                WHERE w.world_type = ?
                GROUP BY w.world_name
                ORDER BY w.created_date
                """;
                
            List<WorldData> worlds = new ArrayList<>();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, worldType);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    worlds.add(mapResultSetToWorldData(rs));
                }
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find worlds by type: " + worldType, e);
            }
            
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<WorldData> save(WorldData worldData) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO rvnk_worlds (
                    world_name, display_name, world_type, environment, world_path,
                    difficulty, game_mode, seed, spawn_x, spawn_y, spawn_z,
                    border_size, border_center_x, border_center_z,
                    allow_animals, allow_monsters, allow_pvp, keep_spawn_in_memory, auto_save,
                    is_loaded, is_enabled, player_count, total_visits, total_playtime,
                    metadata, tags, created_date, last_loaded
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name = VALUES(display_name),
                    world_type = VALUES(world_type),
                    environment = VALUES(environment),
                    world_path = VALUES(world_path),
                    difficulty = VALUES(difficulty),
                    game_mode = VALUES(game_mode),
                    seed = VALUES(seed),
                    spawn_x = VALUES(spawn_x),
                    spawn_y = VALUES(spawn_y),
                    spawn_z = VALUES(spawn_z),
                    border_size = VALUES(border_size),
                    border_center_x = VALUES(border_center_x),
                    border_center_z = VALUES(border_center_z),
                    allow_animals = VALUES(allow_animals),
                    allow_monsters = VALUES(allow_monsters),
                    allow_pvp = VALUES(allow_pvp),
                    keep_spawn_in_memory = VALUES(keep_spawn_in_memory),
                    auto_save = VALUES(auto_save),
                    is_loaded = VALUES(is_loaded),
                    is_enabled = VALUES(is_enabled),
                    metadata = VALUES(metadata),
                    tags = VALUES(tags),
                    last_loaded = VALUES(last_loaded)
                """;
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                setWorldDataParameters(stmt, worldData);
                stmt.executeUpdate();
                
                return worldData;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save world: " + worldData.getWorldName(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> delete(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM rvnk_worlds WHERE world_name = ?";
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, worldName);
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete world: " + worldName, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> updatePlayerCounts(Map<String, Integer> worldPlayerCounts) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE rvnk_worlds SET player_count = ?, last_loaded = CURRENT_TIMESTAMP WHERE world_name = ?";
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                int updatedCount = 0;
                for (Map.Entry<String, Integer> entry : worldPlayerCounts.entrySet()) {
                    stmt.setInt(1, entry.getValue());
                    stmt.setString(2, entry.getKey());
                    stmt.addBatch();
                }
                
                int[] results = stmt.executeBatch();
                for (int result : results) {
                    if (result > 0) updatedCount++;
                }
                
                return updatedCount;
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update world player counts", e);
            }
        });
    }
    
    private WorldData mapResultSetToWorldData(ResultSet rs) throws SQLException {
        WorldData worldData = new WorldData();
        
        // Core identification
        worldData.setWorldName(rs.getString("world_name"));
        worldData.setDisplayName(rs.getString("display_name"));
        worldData.setWorldType(rs.getString("world_type"));
        worldData.setEnvironment(rs.getString("environment"));
        worldData.setWorldPath(rs.getString("world_path"));
        
        // World configuration
        worldData.setDifficulty(rs.getString("difficulty"));
        worldData.setGameMode(rs.getString("game_mode"));
        
        Long seed = rs.getLong("seed");
        worldData.setSeed(rs.wasNull() ? null : seed);
        
        worldData.setSpawnX(rs.getInt("spawn_x"));
        worldData.setSpawnY(rs.getInt("spawn_y"));
        worldData.setSpawnZ(rs.getInt("spawn_z"));
        
        // World border
        Double borderSize = rs.getDouble("border_size");
        worldData.setBorderSize(rs.wasNull() ? null : borderSize);
        worldData.setBorderCenterX(rs.getDouble("border_center_x"));
        worldData.setBorderCenterZ(rs.getDouble("border_center_z"));
        
        // World settings
        worldData.setAllowAnimals(rs.getBoolean("allow_animals"));
        worldData.setAllowMonsters(rs.getBoolean("allow_monsters"));
        worldData.setAllowPvp(rs.getBoolean("allow_pvp"));
        worldData.setKeepSpawnInMemory(rs.getBoolean("keep_spawn_in_memory"));
        worldData.setAutoSave(rs.getBoolean("auto_save"));
        
        // Runtime status
        worldData.setLoaded(rs.getBoolean("is_loaded"));
        worldData.setEnabled(rs.getBoolean("is_enabled"));
        worldData.setCurrentPlayerCount(rs.getInt("player_count"));
        
        Timestamp lastLoaded = rs.getTimestamp("last_loaded");
        worldData.setLastLoaded(lastLoaded != null ? lastLoaded.toInstant() : null);
        
        // Statistics (from joined query)
        worldData.setTotalVisits(rs.getLong("total_visits"));
        worldData.setTotalPlaytime(rs.getLong("total_playtime"));
        worldData.setUniquePlayerCount(rs.getInt("unique_players"));
        
        // Created date
        Timestamp createdDate = rs.getTimestamp("created_date");
        worldData.setCreatedDate(createdDate != null ? createdDate.toInstant() : Instant.now());
        
        // Metadata and tags
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            // Parse JSON metadata - implementation depends on JSON library
            worldData.setMetadata(parseJsonToMap(metadataJson));
        }
        
        String tagsString = rs.getString("tags");
        if (tagsString != null && !tagsString.isEmpty()) {
            worldData.setTags(Arrays.asList(tagsString.split(",")));
        }
        
        return worldData;
    }
    
    private void setWorldDataParameters(PreparedStatement stmt, WorldData worldData) throws SQLException {
        int paramIndex = 1;
        
        stmt.setString(paramIndex++, worldData.getWorldName());
        stmt.setString(paramIndex++, worldData.getDisplayName());
        stmt.setString(paramIndex++, worldData.getWorldType());
        stmt.setString(paramIndex++, worldData.getEnvironment());
        stmt.setString(paramIndex++, worldData.getWorldPath());
        stmt.setString(paramIndex++, worldData.getDifficulty());
        stmt.setString(paramIndex++, worldData.getGameMode());
        
        if (worldData.getSeed() != null) {
            stmt.setLong(paramIndex++, worldData.getSeed());
        } else {
            stmt.setNull(paramIndex++, Types.BIGINT);
        }
        
        stmt.setInt(paramIndex++, worldData.getSpawnX());
        stmt.setInt(paramIndex++, worldData.getSpawnY());
        stmt.setInt(paramIndex++, worldData.getSpawnZ());
        
        if (worldData.getBorderSize() != null) {
            stmt.setDouble(paramIndex++, worldData.getBorderSize());
        } else {
            stmt.setNull(paramIndex++, Types.DOUBLE);
        }
        
        stmt.setDouble(paramIndex++, worldData.getBorderCenterX());
        stmt.setDouble(paramIndex++, worldData.getBorderCenterZ());
        
        stmt.setBoolean(paramIndex++, worldData.isAllowAnimals());
        stmt.setBoolean(paramIndex++, worldData.isAllowMonsters());
        stmt.setBoolean(paramIndex++, worldData.isAllowPvp());
        stmt.setBoolean(paramIndex++, worldData.isKeepSpawnInMemory());
        stmt.setBoolean(paramIndex++, worldData.isAutoSave());
        
        stmt.setBoolean(paramIndex++, worldData.isLoaded());
        stmt.setBoolean(paramIndex++, worldData.isEnabled());
        stmt.setInt(paramIndex++, worldData.getCurrentPlayerCount());
        stmt.setLong(paramIndex++, worldData.getTotalVisits());
        stmt.setLong(paramIndex++, worldData.getTotalPlaytime());
        
        // Metadata as JSON
        if (worldData.getMetadata() != null && !worldData.getMetadata().isEmpty()) {
            stmt.setString(paramIndex++, mapToJsonString(worldData.getMetadata()));
        } else {
            stmt.setNull(paramIndex++, Types.LONGVARCHAR);
        }
        
        // Tags as comma-separated string
        if (worldData.getTags() != null && !worldData.getTags().isEmpty()) {
            stmt.setString(paramIndex++, String.join(",", worldData.getTags()));
        } else {
            stmt.setNull(paramIndex++, Types.VARCHAR);
        }
        
        // Timestamps
        if (worldData.getCreatedDate() != null) {
            stmt.setTimestamp(paramIndex++, Timestamp.from(worldData.getCreatedDate()));
        } else {
            stmt.setTimestamp(paramIndex++, Timestamp.from(Instant.now()));
        }
        
        if (worldData.getLastLoaded() != null) {
            stmt.setTimestamp(paramIndex++, Timestamp.from(worldData.getLastLoaded()));
        } else {
            stmt.setNull(paramIndex++, Types.TIMESTAMP);
        }
    }
    
    private Map<String, Object> parseJsonToMap(String json) {
        // Implementation depends on chosen JSON library (e.g., Gson, Jackson)
        // For now, return empty map
        return new HashMap<>();
    }
    
    private String mapToJsonString(Map<String, Object> map) {
        // Implementation depends on chosen JSON library (e.g., Gson, Jackson)
        // For now, return empty JSON object
        return "{}";
    }
}
```

### Step 2: World Service Implementation

**File**: `src/main/java/org/fourz/rvnkcore/service/WorldService.java`

```java
package org.fourz.rvnkcore.service;

import org.fourz.rvnkcore.data.WorldData;
import org.fourz.rvnkcore.data.WorldStatistics;
import org.fourz.rvnkcore.repository.WorldRepository;
import org.fourz.rvnkcore.repository.PlayerWorldDataRepository;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DefaultWorldService implements WorldService {
    
    private final WorldRepository worldRepository;
    private final PlayerWorldDataRepository playerWorldDataRepository;
    
    public DefaultWorldService(WorldRepository worldRepository, 
                             PlayerWorldDataRepository playerWorldDataRepository) {
        this.worldRepository = worldRepository;
        this.playerWorldDataRepository = playerWorldDataRepository;
    }
    
    @Override
    public CompletableFuture<Optional<WorldData>> getWorld(String worldName) {
        return worldRepository.findByName(worldName);
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getAllWorlds() {
        return worldRepository.findAll();
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getWorldsByType(String worldType) {
        return worldRepository.findByType(worldType);
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getWorldsByEnvironment(World.Environment environment) {
        return worldRepository.findByEnvironment(environment.name());
    }
    
    @Override
    public CompletableFuture<WorldData> registerWorld(World bukkitWorld) {
        return CompletableFuture.supplyAsync(() -> {
            WorldData worldData = createWorldDataFromBukkit(bukkitWorld);
            return worldRepository.save(worldData).join();
        });
    }
    
    @Override
    public CompletableFuture<WorldData> synchronizeWorldData(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) {
                throw new IllegalArgumentException("World not found: " + worldName);
            }
            
            return worldRepository.findByName(worldName)
                .thenCompose(existingWorld -> {
                    WorldData worldData;
                    if (existingWorld.isPresent()) {
                        // Update existing world data
                        worldData = updateWorldDataFromBukkit(existingWorld.get(), bukkitWorld);
                    } else {
                        // Create new world data
                        worldData = createWorldDataFromBukkit(bukkitWorld);
                    }
                    
                    return worldRepository.save(worldData);
                }).join();
        });
    }
    
    @Override
    public CompletableFuture<List<WorldData>> synchronizeAllWorlds() {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletableFuture<WorldData>> syncTasks = Bukkit.getWorlds().stream()
                .map(world -> synchronizeWorldData(world.getName()))
                .collect(Collectors.toList());
                
            return CompletableFuture.allOf(syncTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> syncTasks.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList())
                ).join();
        });
    }
    
    @Override
    public CompletableFuture<WorldData> updateWorldStatus(String worldName, boolean isLoaded, int playerCount) {
        return worldRepository.findByName(worldName)
            .thenCompose(worldData -> {
                if (worldData.isPresent()) {
                    WorldData data = worldData.get();
                    data.setLoaded(isLoaded);
                    data.setCurrentPlayerCount(playerCount);
                    data.setLastLoaded(isLoaded ? Instant.now() : data.getLastLoaded());
                    return worldRepository.save(data);
                } else {
                    throw new IllegalArgumentException("World not found: " + worldName);
                }
            });
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getLoadedWorlds() {
        return worldRepository.findAll()
            .thenApply(worlds -> worlds.stream()
                .filter(WorldData::isLoaded)
                .collect(Collectors.toList())
            );
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getEnabledWorlds() {
        return worldRepository.findByEnabled(true);
    }
    
    @Override
    public CompletableFuture<WorldStatistics> getWorldStatistics(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            // Collect comprehensive world statistics
            WorldStatistics stats = new WorldStatistics();
            stats.setWorldName(worldName);
            
            // Get basic world data
            WorldData worldData = worldRepository.findByName(worldName).join().orElse(null);
            if (worldData == null) {
                throw new IllegalArgumentException("World not found: " + worldName);
            }
            
            // Calculate player statistics
            List<PlayerWorldData> playerData = playerWorldDataRepository.findByWorldName(worldName).join();
            stats.setPlayerStatistics(calculatePlayerStatistics(playerData));
            
            // Calculate activity statistics
            stats.setActivityStatistics(calculateActivityStatistics(playerData));
            
            // Calculate block statistics
            stats.setBlockStatistics(calculateBlockStatistics(playerData));
            
            // Calculate location statistics
            stats.setLocationStatistics(calculateLocationStatistics(playerData));
            
            // Calculate time statistics
            stats.setTimeStatistics(calculateTimeStatistics(worldData, playerData));
            
            return stats;
        });
    }
    
    private WorldData createWorldDataFromBukkit(World bukkitWorld) {
        WorldData worldData = new WorldData();
        
        // Core identification
        worldData.setWorldName(bukkitWorld.getName());
        worldData.setDisplayName(bukkitWorld.getName());
        worldData.setWorldType(bukkitWorld.getWorldType().name());
        worldData.setEnvironment(bukkitWorld.getEnvironment().name());
        worldData.setWorldPath(bukkitWorld.getWorldFolder().getAbsolutePath());
        
        // World configuration
        worldData.setDifficulty(bukkitWorld.getDifficulty().name());
        worldData.setSeed(bukkitWorld.getSeed());
        
        // Spawn location
        Location spawn = bukkitWorld.getSpawnLocation();
        worldData.setSpawnX(spawn.getBlockX());
        worldData.setSpawnY(spawn.getBlockY());
        worldData.setSpawnZ(spawn.getBlockZ());
        
        // World border
        org.bukkit.WorldBorder border = bukkitWorld.getWorldBorder();
        worldData.setBorderSize(border.getSize());
        worldData.setBorderCenterX(border.getCenter().getX());
        worldData.setBorderCenterZ(border.getCenter().getZ());
        
        // World settings
        worldData.setAllowAnimals(bukkitWorld.getAllowAnimals());
        worldData.setAllowMonsters(bukkitWorld.getAllowMonsters());
        worldData.setAllowPvp(bukkitWorld.getPVP());
        worldData.setKeepSpawnInMemory(bukkitWorld.getKeepSpawnInMemory());
        worldData.setAutoSave(bukkitWorld.isAutoSave());
        
        // Runtime status
        worldData.setLoaded(true);
        worldData.setEnabled(true);
        worldData.setCurrentPlayerCount(bukkitWorld.getPlayers().size());
        worldData.setLastLoaded(Instant.now());
        
        // Initialize statistics
        worldData.setTotalVisits(0);
        worldData.setTotalPlaytime(0);
        worldData.setUniquePlayerCount(0);
        worldData.setCreatedDate(Instant.now());
        
        // Default metadata and tags
        worldData.setMetadata(new HashMap<>());
        worldData.setTags(new ArrayList<>());
        
        return worldData;
    }
    
    private WorldData updateWorldDataFromBukkit(WorldData existingData, World bukkitWorld) {
        // Update dynamic data while preserving static/configured data
        existingData.setDifficulty(bukkitWorld.getDifficulty().name());
        
        // Update spawn location
        Location spawn = bukkitWorld.getSpawnLocation();
        existingData.setSpawnX(spawn.getBlockX());
        existingData.setSpawnY(spawn.getBlockY());
        existingData.setSpawnZ(spawn.getBlockZ());
        
        // Update world border
        org.bukkit.WorldBorder border = bukkitWorld.getWorldBorder();
        existingData.setBorderSize(border.getSize());
        existingData.setBorderCenterX(border.getCenter().getX());
        existingData.setBorderCenterZ(border.getCenter().getZ());
        
        // Update settings
        existingData.setAllowAnimals(bukkitWorld.getAllowAnimals());
        existingData.setAllowMonsters(bukkitWorld.getAllowMonsters());
        existingData.setAllowPvp(bukkitWorld.getPVP());
        existingData.setKeepSpawnInMemory(bukkitWorld.getKeepSpawnInMemory());
        existingData.setAutoSave(bukkitWorld.isAutoSave());
        
        // Update runtime status
        existingData.setLoaded(true);
        existingData.setCurrentPlayerCount(bukkitWorld.getPlayers().size());
        existingData.setLastLoaded(Instant.now());
        
        return existingData;
    }
    
    // Statistics calculation methods would be implemented here
    private PlayerStatistics calculatePlayerStatistics(List<PlayerWorldData> playerData) {
        // Implementation for player statistics calculation
        return new PlayerStatistics();
    }
    
    private ActivityStatistics calculateActivityStatistics(List<PlayerWorldData> playerData) {
        // Implementation for activity statistics calculation
        return new ActivityStatistics();
    }
    
    private BlockStatistics calculateBlockStatistics(List<PlayerWorldData> playerData) {
        // Implementation for block statistics calculation
        return new BlockStatistics();
    }
    
    private LocationStatistics calculateLocationStatistics(List<PlayerWorldData> playerData) {
        // Implementation for location statistics calculation
        return new LocationStatistics();
    }
    
    private TimeStatistics calculateTimeStatistics(WorldData worldData, List<PlayerWorldData> playerData) {
        // Implementation for time statistics calculation
        return new TimeStatistics();
    }
}
```

### Step 3: World Discovery Service Implementation

**File**: `src/main/java/org/fourz/rvnkcore/service/WorldDiscoveryService.java`

```java
package org.fourz.rvnkcore.service;

import org.fourz.rvnkcore.data.WorldData;
import org.fourz.rvnkcore.repository.WorldRepository;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DefaultWorldDiscoveryService implements WorldDiscoveryService, Listener {
    
    private final WorldService worldService;
    private final WorldRepository worldRepository;
    
    public DefaultWorldDiscoveryService(WorldService worldService, WorldRepository worldRepository) {
        this.worldService = worldService;
        this.worldRepository = worldRepository;
    }
    
    @Override
    public CompletableFuture<List<World>> discoverWorlds() {
        return CompletableFuture.supplyAsync(() -> Bukkit.getWorlds());
    }
    
    @Override
    public CompletableFuture<WorldData> analyzeWorld(World bukkitWorld) {
        return worldService.registerWorld(bukkitWorld);
    }
    
    @Override
    public CompletableFuture<Boolean> registerDiscoveredWorld(World bukkitWorld) {
        return worldService.registerWorld(bukkitWorld)
            .thenApply(worldData -> worldData != null);
    }
    
    @Override
    public CompletableFuture<SyncResult> synchronizeWithServer() {
        return CompletableFuture.supplyAsync(() -> {
            SyncResult result = new SyncResult();
            
            // Get all current server worlds
            List<World> serverWorlds = Bukkit.getWorlds();
            
            // Get all tracked worlds
            List<WorldData> trackedWorlds = worldRepository.findAll().join();
            
            // Find unregistered worlds
            List<String> trackedWorldNames = trackedWorlds.stream()
                .map(WorldData::getWorldName)
                .collect(Collectors.toList());
                
            List<World> unregisteredWorlds = serverWorlds.stream()
                .filter(world -> !trackedWorldNames.contains(world.getName()))
                .collect(Collectors.toList());
            
            // Register unregistered worlds
            for (World world : unregisteredWorlds) {
                try {
                    worldService.registerWorld(world).join();
                    result.addRegistered(world.getName());
                } catch (Exception e) {
                    result.addError(world.getName(), e.getMessage());
                }
            }
            
            // Find orphaned world records (worlds that no longer exist)
            List<String> serverWorldNames = serverWorlds.stream()
                .map(World::getName)
                .collect(Collectors.toList());
                
            List<String> orphanedWorlds = trackedWorlds.stream()
                .map(WorldData::getWorldName)
                .filter(worldName -> !serverWorldNames.contains(worldName))
                .collect(Collectors.toList());
                
            result.setOrphanedWorlds(orphanedWorlds);
            
            // Synchronize existing worlds
            for (World world : serverWorlds) {
                if (trackedWorldNames.contains(world.getName())) {
                    try {
                        worldService.synchronizeWorldData(world.getName()).join();
                        result.addSynchronized(world.getName());
                    } catch (Exception e) {
                        result.addError(world.getName(), e.getMessage());
                    }
                }
            }
            
            return result;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldData>> getUnregisteredWorlds() {
        return discoverWorlds()
            .thenCompose(worlds -> {
                return worldRepository.findAll()
                    .thenApply(trackedWorlds -> {
                        List<String> trackedNames = trackedWorlds.stream()
                            .map(WorldData::getWorldName)
                            .collect(Collectors.toList());
                            
                        return worlds.stream()
                            .filter(world -> !trackedNames.contains(world.getName()))
                            .map(world -> createWorldDataFromBukkit(world))
                            .collect(Collectors.toList());
                    });
            });
    }
    
    @Override
    public CompletableFuture<List<String>> getOrphanedWorldRecords() {
        return worldRepository.findAll()
            .thenApply(trackedWorlds -> {
                List<String> serverWorldNames = Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .collect(Collectors.toList());
                    
                return trackedWorlds.stream()
                    .map(WorldData::getWorldName)
                    .filter(worldName -> !serverWorldNames.contains(worldName))
                    .collect(Collectors.toList());
            });
    }
    
    // Event Handlers for Real-Time Discovery
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        
        // Register newly initialized world
        worldService.isWorldTracked(world.getName())
            .thenCompose(isTracked -> {
                if (!isTracked) {
                    return worldService.registerWorld(world)
                        .thenAccept(worldData -> {
                            // Log successful registration
                            System.out.println("Registered new world: " + world.getName());
                        });
                }
                return CompletableFuture.completedFuture(null);
            })
            .exceptionally(throwable -> {
                System.err.println("Failed to register world on init: " + world.getName() + " - " + throwable.getMessage());
                return null;
            });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        
        // Update world status to loaded
        worldService.updateWorldStatus(
            world.getName(), 
            true, 
            world.getPlayers().size()
        ).thenAccept(worldData -> {
            System.out.println("World loaded: " + world.getName());
        }).exceptionally(throwable -> {
            System.err.println("Failed to update world status on load: " + world.getName() + " - " + throwable.getMessage());
            return null;
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();
        
        // Update world status to unloaded
        worldService.updateWorldStatus(worldName, false, 0)
            .thenAccept(worldData -> {
                System.out.println("World unloaded: " + worldName);
            })
            .exceptionally(throwable -> {
                System.err.println("Failed to update world status on unload: " + worldName + " - " + throwable.getMessage());
                return null;
            });
    }
    
    private WorldData createWorldDataFromBukkit(World bukkitWorld) {
        // This would use the same logic as in WorldService
        // For brevity, not duplicated here
        return new WorldData();
    }
}

// Supporting classes
public class SyncResult {
    private List<String> registeredWorlds = new ArrayList<>();
    private List<String> synchronizedWorlds = new ArrayList<>();
    private List<String> orphanedWorlds = new ArrayList<>();
    private Map<String, String> errors = new HashMap<>();
    
    // Getters, setters, and utility methods
    public void addRegistered(String worldName) { registeredWorlds.add(worldName); }
    public void addSynchronized(String worldName) { synchronizedWorlds.add(worldName); }
    public void addError(String worldName, String error) { errors.put(worldName, error); }
    
    // Additional getters and setters...
}
```

### Step 4: World REST Controller Implementation

**File**: `src/main/java/org/fourz/rvnkcore/rest/controller/WorldController.java`

```java
package org.fourz.rvnkcore.rest.controller;

import org.fourz.rvnkcore.data.WorldData;
import org.fourz.rvnkcore.data.WorldStatistics;
import org.fourz.rvnkcore.service.WorldService;
import org.fourz.rvnkcore.service.WorldDiscoveryService;
import org.fourz.rvnkcore.rest.dto.*;
import org.fourz.rvnkcore.rest.response.ApiResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class WorldController extends BaseController {
    
    private final WorldService worldService;
    private final WorldDiscoveryService worldDiscoveryService;
    
    public WorldController(WorldService worldService, WorldDiscoveryService worldDiscoveryService) {
        this.worldService = worldService;
        this.worldDiscoveryService = worldDiscoveryService;
    }
    
    // GET /worlds
    public void getAllWorlds(HttpServletRequest request, HttpServletResponse response) {
        // Parse query parameters
        String type = request.getParameter("type");
        String environment = request.getParameter("environment");
        String enabled = request.getParameter("enabled");
        
        CompletableFuture<List<WorldData>> worldsFuture;
        
        if (type != null) {
            worldsFuture = worldService.getWorldsByType(type);
        } else if (environment != null) {
            worldsFuture = worldService.getWorldsByEnvironment(World.Environment.valueOf(environment.toUpperCase()));
        } else if ("true".equals(enabled)) {
            worldsFuture = worldService.getEnabledWorlds();
        } else {
            worldsFuture = worldService.getAllWorlds();
        }
        
        worldsFuture
            .thenApply(worlds -> worlds.stream()
                .map(this::convertToWorldDTO)
                .collect(Collectors.toList())
            )
            .thenAccept(worldDTOs -> {
                sendJsonResponse(response, ApiResponse.success(worldDTOs));
            })
            .exceptionally(throwable -> {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to retrieve worlds: " + throwable.getMessage());
                return null;
            });
    }
    
    // GET /worlds/{world}
    public void getWorld(HttpServletRequest request, HttpServletResponse response, String worldName) {
        worldService.getWorld(worldName)
            .thenAccept(worldData -> {
                if (worldData.isPresent()) {
                    WorldDTO worldDTO = convertToWorldDTO(worldData.get());
                    sendJsonResponse(response, ApiResponse.success(worldDTO));
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, 
                        "World not found: " + worldName);
                }
            })
            .exceptionally(throwable -> {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to retrieve world: " + throwable.getMessage());
                return null;
            });
    }
    
    // PUT /worlds/{world}
    public void updateWorld(HttpServletRequest request, HttpServletResponse response, String worldName) {
        try {
            WorldUpdateDTO updateData = parseJsonRequest(request, WorldUpdateDTO.class);
            
            worldService.getWorld(worldName)
                .thenCompose(existingWorld -> {
                    if (existingWorld.isEmpty()) {
                        throw new IllegalArgumentException("World not found: " + worldName);
                    }
                    
                    WorldData worldData = existingWorld.get();
                    applyUpdateToWorldData(worldData, updateData);
                    
                    return worldService.updateWorld(worldName, worldData);
                })
                .thenAccept(updatedWorld -> {
                    WorldDTO worldDTO = convertToWorldDTO(updatedWorld);
                    sendJsonResponse(response, ApiResponse.success(worldDTO));
                })
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof IllegalArgumentException) {
                        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, 
                            throwable.getCause().getMessage());
                    } else {
                        sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Failed to update world: " + throwable.getMessage());
                    }
                    return null;
                });
                
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "Invalid request body: " + e.getMessage());
        }
    }
    
    // POST /worlds/{world}/sync
    public void synchronizeWorld(HttpServletRequest request, HttpServletResponse response, String worldName) {
        worldService.synchronizeWorldData(worldName)
            .thenAccept(worldData -> {
                WorldDTO worldDTO = convertToWorldDTO(worldData);
                sendJsonResponse(response, ApiResponse.success(worldDTO, "World synchronized successfully"));
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, 
                        throwable.getCause().getMessage());
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to synchronize world: " + throwable.getMessage());
                }
                return null;
            });
    }
    
    // GET /worlds/{world}/statistics
    public void getWorldStatistics(HttpServletRequest request, HttpServletResponse response, String worldName) {
        worldService.getWorldStatistics(worldName)
            .thenAccept(statistics -> {
                WorldStatisticsDTO statisticsDTO = convertToWorldStatisticsDTO(statistics);
                sendJsonResponse(response, ApiResponse.success(statisticsDTO));
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, 
                        throwable.getCause().getMessage());
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to retrieve world statistics: " + throwable.getMessage());
                }
                return null;
            });
    }
    
    // GET /worlds/{world}/players
    public void getWorldPlayers(HttpServletRequest request, HttpServletResponse response, String worldName) {
        // Parse query parameters
        boolean activeOnly = "true".equals(request.getParameter("active"));
        String minPlaytimeStr = request.getParameter("minPlaytime");
        String sortBy = Optional.ofNullable(request.getParameter("sortBy")).orElse("playtime");
        String order = Optional.ofNullable(request.getParameter("order")).orElse("desc");
        
        CompletableFuture<List<PlayerWorldData>> playersFuture;
        
        if (activeOnly) {
            playersFuture = playerWorldService.getActiveWorldPlayers(worldName);
        } else {
            playersFuture = playerWorldService.getWorldPlayers(worldName);
        }
        
        playersFuture
            .thenApply(players -> {
                // Apply filtering
                if (minPlaytimeStr != null) {
                    long minPlaytime = Long.parseLong(minPlaytimeStr);
                    players = players.stream()
                        .filter(p -> p.getPlaytimeSeconds() >= minPlaytime)
                        .collect(Collectors.toList());
                }
                
                // Apply sorting
                Comparator<PlayerWorldData> comparator = createPlayerSortComparator(sortBy, order);
                players.sort(comparator);
                
                return players.stream()
                    .map(this::convertToPlayerWorldDataDTO)
                    .collect(Collectors.toList());
            })
            .thenAccept(playerDTOs -> {
                sendJsonResponse(response, ApiResponse.success(playerDTOs));
            })
            .exceptionally(throwable -> {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to retrieve world players: " + throwable.getMessage());
                return null;
            });
    }
    
    private WorldDTO convertToWorldDTO(WorldData worldData) {
        // Implementation of WorldData to WorldDTO conversion
        WorldDTO dto = new WorldDTO();
        dto.setWorldName(worldData.getWorldName());
        dto.setDisplayName(worldData.getDisplayName());
        dto.setWorldType(worldData.getWorldType());
        dto.setEnvironment(worldData.getEnvironment());
        dto.setWorldPath(worldData.getWorldPath());
        // ... set all other fields
        return dto;
    }
    
    private WorldStatisticsDTO convertToWorldStatisticsDTO(WorldStatistics statistics) {
        // Implementation of WorldStatistics to WorldStatisticsDTO conversion
        return new WorldStatisticsDTO(statistics);
    }
    
    private PlayerWorldDataDTO convertToPlayerWorldDataDTO(PlayerWorldData data) {
        // Implementation of PlayerWorldData to PlayerWorldDataDTO conversion
        return new PlayerWorldDataDTO(data);
    }
    
    private void applyUpdateToWorldData(WorldData worldData, WorldUpdateDTO updateData) {
        // Apply updates from DTO to entity
        if (updateData.getDisplayName() != null) {
            worldData.setDisplayName(updateData.getDisplayName());
        }
        if (updateData.getDifficulty() != null) {
            worldData.setDifficulty(updateData.getDifficulty());
        }
        if (updateData.getGameMode() != null) {
            worldData.setGameMode(updateData.getGameMode());
        }
        if (updateData.getSettings() != null) {
            WorldSettingsDTO settings = updateData.getSettings();
            worldData.setAllowAnimals(settings.isAllowAnimals());
            worldData.setAllowMonsters(settings.isAllowMonsters());
            worldData.setAllowPvp(settings.isAllowPvp());
            worldData.setKeepSpawnInMemory(settings.isKeepSpawnInMemory());
            worldData.setAutoSave(settings.isAutoSave());
        }
        if (updateData.isEnabled() != null) {
            worldData.setEnabled(updateData.isEnabled());
        }
        if (updateData.getTags() != null) {
            worldData.setTags(updateData.getTags());
        }
        if (updateData.getMetadata() != null) {
            worldData.setMetadata(updateData.getMetadata());
        }
    }
    
    private Comparator<PlayerWorldData> createPlayerSortComparator(String sortBy, String order) {
        Comparator<PlayerWorldData> comparator;
        
        switch (sortBy.toLowerCase()) {
            case "playtime":
                comparator = Comparator.comparingLong(PlayerWorldData::getPlaytimeSeconds);
                break;
            case "visitcount":
                comparator = Comparator.comparingInt(PlayerWorldData::getVisitCount);
                break;
            case "lastvisit":
                comparator = Comparator.comparing(PlayerWorldData::getLastVisit);
                break;
            default:
                comparator = Comparator.comparingLong(PlayerWorldData::getPlaytimeSeconds);
        }
        
        return "asc".equalsIgnoreCase(order) ? comparator : comparator.reversed();
    }
}
```

## Integration with Existing System

### Step 5: Update Plugin Main Class

**File**: `src/main/java/org/fourz/rvnktools/RVNKTools.java`

```java
// Add world tracking initialization in onEnable()
private void initializeWorldTracking() {
    try {
        // Initialize world repository
        WorldRepository worldRepository = new DefaultWorldRepository(connectionProvider);
        
        // Initialize world service
        WorldService worldService = new DefaultWorldService(worldRepository, playerWorldDataRepository);
        
        // Initialize world discovery service
        WorldDiscoveryService worldDiscoveryService = new DefaultWorldDiscoveryService(worldService, worldRepository);
        
        // Register world discovery event listener
        getServer().getPluginManager().registerEvents(worldDiscoveryService, this);
        
        // Perform initial world synchronization
        worldDiscoveryService.synchronizeWithServer()
            .thenAccept(result -> {
                logger.info("World synchronization completed - Registered: {}, Synchronized: {}, Orphaned: {}", 
                    result.getRegisteredWorlds().size(),
                    result.getSynchronizedWorlds().size(),
                    result.getOrphanedWorlds().size());
            })
            .exceptionally(throwable -> {
                logger.error("Failed to synchronize worlds on startup", throwable);
                return null;
            });
        
        // Register world tracking with service registry
        ServiceRegistry.getInstance().registerService(WorldService.class, worldService);
        ServiceRegistry.getInstance().registerService(WorldDiscoveryService.class, worldDiscoveryService);
        
        logger.info("World tracking system initialized successfully");
        
    } catch (Exception e) {
        logger.error("Failed to initialize world tracking system", e);
        throw new RuntimeException("World tracking initialization failed", e);
    }
}
```

### Step 6: REST API Registration

**File**: `src/main/java/org/fourz/rvnkcore/rest/RVNKCoreRestServer.java`

```java
// Add world endpoints to REST server
private void registerWorldEndpoints() {
    WorldService worldService = ServiceRegistry.getInstance().getService(WorldService.class);
    WorldDiscoveryService worldDiscoveryService = ServiceRegistry.getInstance().getService(WorldDiscoveryService.class);
    WorldController worldController = new WorldController(worldService, worldDiscoveryService);
    
    // World management endpoints
    registerEndpoint("GET", "/api/v1/worlds", worldController::getAllWorlds);
    registerEndpoint("GET", "/api/v1/worlds/{world}", worldController::getWorld);
    registerEndpoint("PUT", "/api/v1/worlds/{world}", worldController::updateWorld);
    registerEndpoint("POST", "/api/v1/worlds/{world}/sync", worldController::synchronizeWorld);
    
    // World analytics endpoints
    registerEndpoint("GET", "/api/v1/worlds/{world}/statistics", worldController::getWorldStatistics);
    registerEndpoint("GET", "/api/v1/worlds/{world}/players", worldController::getWorldPlayers);
    registerEndpoint("GET", "/api/v1/worlds/{world}/players/active", worldController::getActiveWorldPlayers);
}
```

## Testing and Validation

### Step 7: Test Implementation

**File**: `src/test/java/org/fourz/rvnkcore/service/WorldServiceTest.java`

```java
package org.fourz.rvnkcore.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class WorldServiceTest {
    
    @Mock private WorldRepository worldRepository;
    @Mock private PlayerWorldDataRepository playerWorldDataRepository;
    
    private WorldService worldService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        worldService = new DefaultWorldService(worldRepository, playerWorldDataRepository);
    }
    
    @Test
    void testGetWorld_ExistingWorld_ReturnsWorldData() {
        // Arrange
        String worldName = "world";
        WorldData expectedWorld = createTestWorldData(worldName);
        when(worldRepository.findByName(worldName))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(expectedWorld)));
        
        // Act
        Optional<WorldData> result = worldService.getWorld(worldName).join();
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(worldName, result.get().getWorldName());
        verify(worldRepository).findByName(worldName);
    }
    
    @Test
    void testGetWorld_NonExistentWorld_ReturnsEmpty() {
        // Arrange
        String worldName = "nonexistent";
        when(worldRepository.findByName(worldName))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        
        // Act
        Optional<WorldData> result = worldService.getWorld(worldName).join();
        
        // Assert
        assertFalse(result.isPresent());
        verify(worldRepository).findByName(worldName);
    }
    
    // Additional test methods...
    
    private WorldData createTestWorldData(String worldName) {
        WorldData worldData = new WorldData();
        worldData.setWorldName(worldName);
        worldData.setDisplayName(worldName);
        worldData.setWorldType("NORMAL");
        worldData.setEnvironment("NORMAL");
        worldData.setEnabled(true);
        worldData.setLoaded(true);
        return worldData;
    }
}
```

## Configuration Updates

### Step 8: Add World Tracking Configuration

**File**: `src/main/resources/config.yml`

```yaml
# World Tracking Configuration
worldTracking:
  enabled: true
  
  # World discovery settings
  discovery:
    autoRegisterNewWorlds: true
    syncOnStartup: true
    syncIntervalMinutes: 60
    trackUnloadedWorlds: false
  
  # Statistics collection
  statistics:
    enabled: true
    collectionIntervalMinutes: 5
    retainHistoryDays: 90
    calculatePopularLocations: true
    popularLocationRadius: 50
    maxPopularLocations: 20
  
  # Analytics and caching
  analytics:
    cacheStatisticsDuration: 300  # 5 minutes
    enableRealTimeUpdates: true
    
  # World defaults for new worlds
  worldDefaults:
    enableNewWorlds: true
    trackPlayerActivity: true
    collectBlockStatistics: true
    collectLocationStatistics: true

# World-specific configuration
worlds:
  world:
    displayName: "Main Survival World"
    enabled: true
    trackingEnabled: true
    tags: ["survival", "main", "overworld"]
    metadata:
      description: "Primary survival world for all players"
      rules: ["No griefing", "Respect others"]
      features: ["Towns", "Economy", "Shops"]
  
  world_nether:
    displayName: "The Nether"
    enabled: true
    trackingEnabled: true
    tags: ["nether", "dangerous", "resources"]
    metadata:
      description: "Nether dimension for resource gathering"
      rules: ["Extreme caution advised"]
      features: ["Nether fortresses", "Resource nodes"]
  
  world_the_end:
    displayName: "The End"
    enabled: true
    trackingEnabled: true
    tags: ["end", "enderdragon", "endgame"]
    metadata:
      description: "End dimension for advanced players"
      rules: ["Dragon fight area - coordinate with others"]
      features: ["End cities", "Chorus farming"]
```

This implementation provides comprehensive world tracking capabilities that seamlessly integrate with the existing PlayerWorld system while maintaining high performance and providing rich analytics and management features.
