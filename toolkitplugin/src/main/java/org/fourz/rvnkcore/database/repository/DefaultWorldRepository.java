package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of WorldRepository for world data access operations.
 */
public class DefaultWorldRepository implements WorldRepository {
    
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private final boolean useSQLite;

    public DefaultWorldRepository(ConnectionProvider connectionProvider, JavaPlugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.useSQLite = "sqlite".equalsIgnoreCase(connectionProvider.getDatabaseType());
    }
    
    @Override
    public CompletableFuture<Optional<WorldDTO>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds WHERE name = ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToDTO(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Error finding world by name: " + name, e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findByEnvironment(String environment) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds WHERE environment = ?";
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, environment);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding worlds by environment: " + environment, e);
            }
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findActiveWorlds() {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds WHERE is_active = 1";
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding active worlds", e);
            }
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findRecentlyAccessed(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds ORDER BY last_accessed DESC LIMIT ?";
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding recently accessed worlds", e);
            }
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findWorldsWithPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds WHERE player_count > 0";
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding worlds with players", e);
            }
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM rvnk_worlds ORDER BY name";
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding all worlds", e);
            }
            return worlds;
        });
    }
    
    @Override
    public CompletableFuture<Void> save(WorldDTO worldDTO) {
        return CompletableFuture.runAsync(() -> {
            String query;
            if (useSQLite) {
                query = "INSERT OR REPLACE INTO rvnk_worlds (name, display_name, environment, world_type, difficulty, " +
                        "world_folder, seed, spawn_x, spawn_y, spawn_z, is_active, " +
                        "player_count, max_players_seen, total_playtime_seconds, created_at, last_accessed) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                query = "INSERT INTO rvnk_worlds (name, display_name, environment, world_type, difficulty, " +
                        "world_folder, seed, spawn_x, spawn_y, spawn_z, is_active, " +
                        "player_count, max_players_seen, total_playtime_seconds, created_at, last_accessed) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "display_name = VALUES(display_name), environment = VALUES(environment), " +
                        "world_type = VALUES(world_type), difficulty = VALUES(difficulty), " +
                        "is_active = VALUES(is_active), player_count = VALUES(player_count), " +
                        "max_players_seen = VALUES(max_players_seen), " +
                        "total_playtime_seconds = VALUES(total_playtime_seconds), last_accessed = VALUES(last_accessed)";
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, worldDTO.getName());
                stmt.setString(2, worldDTO.getDisplayName());
                stmt.setString(3, worldDTO.getEnvironment());
                stmt.setString(4, worldDTO.getWorldType());
                stmt.setString(5, worldDTO.getDifficulty());
                stmt.setString(6, worldDTO.getWorldFolder());
                stmt.setLong(7, worldDTO.getSeed() != null ? worldDTO.getSeed() : 0L);
                stmt.setDouble(8, worldDTO.getSpawnX() != null ? worldDTO.getSpawnX() : 0.0);
                stmt.setDouble(9, worldDTO.getSpawnY() != null ? worldDTO.getSpawnY() : 0.0);
                stmt.setDouble(10, worldDTO.getSpawnZ() != null ? worldDTO.getSpawnZ() : 0.0);
                stmt.setBoolean(11, worldDTO.getIsActive() != null ? worldDTO.getIsActive() : false);
                stmt.setInt(12, worldDTO.getPlayerCount() != null ? worldDTO.getPlayerCount() : 0);
                stmt.setInt(13, worldDTO.getMaxPlayersSeen() != null ? worldDTO.getMaxPlayersSeen() : 0);
                stmt.setLong(14, worldDTO.getTotalPlaytimeSeconds() != null ? worldDTO.getTotalPlaytimeSeconds() : 0L);
                stmt.setTimestamp(15, worldDTO.getCreatedAt() != null ? Timestamp.valueOf(worldDTO.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
                stmt.setTimestamp(16, worldDTO.getLastAccessed() != null ? Timestamp.valueOf(worldDTO.getLastAccessed()) : new Timestamp(System.currentTimeMillis()));
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error saving world: " + worldDTO.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updateTrackingInfo(String worldName, long totalPlaytimeSeconds,
                                                     int playerCount, int maxPlayersSeen) {
        return CompletableFuture.runAsync(() -> {
            String query;
            if (useSQLite) {
                query = "UPDATE rvnk_worlds SET total_playtime_seconds = ?, player_count = ?, " +
                        "max_players_seen = MAX(max_players_seen, ?), last_accessed = CURRENT_TIMESTAMP " +
                        "WHERE name = ?";
            } else {
                query = "UPDATE rvnk_worlds SET total_playtime_seconds = ?, player_count = ?, " +
                        "max_players_seen = GREATEST(max_players_seen, ?), last_accessed = CURRENT_TIMESTAMP " +
                        "WHERE name = ?";
            }
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setLong(1, totalPlaytimeSeconds);
                stmt.setInt(2, playerCount);
                stmt.setInt(3, maxPlayersSeen);
                stmt.setString(4, worldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating tracking info for world: " + worldName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerCount(String worldName, int playerCount) {
        return CompletableFuture.runAsync(() -> {
            String query;
            if (useSQLite) {
                query = "UPDATE rvnk_worlds SET player_count = ?, max_players_seen = MAX(max_players_seen, ?), last_accessed = CURRENT_TIMESTAMP WHERE name = ?";
            } else {
                query = "UPDATE rvnk_worlds SET player_count = ?, max_players_seen = GREATEST(max_players_seen, ?), last_accessed = CURRENT_TIMESTAMP WHERE name = ?";
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setInt(1, playerCount);
                stmt.setInt(2, playerCount);
                stmt.setString(3, worldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating player count for world: " + worldName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updateActiveStatus(String worldName, boolean isActive) {
        return CompletableFuture.runAsync(() -> {
            String query = "UPDATE rvnk_worlds SET is_active = ? WHERE name = ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setBoolean(1, isActive);
                stmt.setString(2, worldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating active status for world: " + worldName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> delete(String worldName) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM rvnk_worlds WHERE name = ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, worldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error deleting world: " + worldName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    public CompletableFuture<Void> updateDisplayName(String worldName, String displayName) {
        return CompletableFuture.runAsync(() -> {
            String query = "UPDATE rvnk_worlds SET display_name = ? WHERE name = ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, displayName);
                stmt.setString(2, worldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating display name for world: " + worldName, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT 1 FROM rvnk_worlds WHERE name = ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, worldName);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.error("Error checking if world exists: " + worldName, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getWorldStatistics() {
        return findAll(); // For now, return all worlds as statistics
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> findWorldsForPlayer(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = """
                SELECT DISTINCT w.* FROM rvnk_worlds w 
                JOIN rvnk_player_world pw ON w.name = pw.world_name 
                WHERE pw.player_uuid = ?
                """;
            
            List<WorldDTO> worlds = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.add(mapResultSetToDTO(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding worlds for player: " + playerUuid, e);
            }
            return worlds;
        });
    }
    
    private WorldDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        WorldDTO dto = new WorldDTO();
        dto.setName(rs.getString("name"));
        dto.setDisplayName(rs.getString("display_name"));
        dto.setEnvironment(rs.getString("environment"));
        dto.setWorldType(rs.getString("world_type"));
        dto.setDifficulty(rs.getString("difficulty"));
        dto.setWorldFolder(rs.getString("world_folder"));
        dto.setSeed(rs.getLong("seed"));
        dto.setSpawnX(rs.getDouble("spawn_x"));
        dto.setSpawnY(rs.getDouble("spawn_y"));
        dto.setSpawnZ(rs.getDouble("spawn_z"));
        dto.setIsActive(rs.getBoolean("is_active"));
        dto.setPlayerCount(rs.getInt("player_count"));
        dto.setMaxPlayersSeen(rs.getInt("max_players_seen"));
        dto.setTotalPlaytimeSeconds(rs.getLong("total_playtime_seconds"));
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            dto.setCreatedAt(created.toLocalDateTime());
        }
        
        Timestamp accessed = rs.getTimestamp("last_accessed");
        if (accessed != null) {
            dto.setLastAccessed(accessed.toLocalDateTime());
        }
        
        return dto;
    }
}
