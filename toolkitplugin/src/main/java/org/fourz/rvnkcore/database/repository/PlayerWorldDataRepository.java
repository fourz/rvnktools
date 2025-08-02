package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for PlayerWorldData operations.
 * 
 * Provides database access methods for world-specific player data including
 * location tracking, playtime, visit history, and world-specific statistics.
 * 
 * @since 1.0.0
 */
public class PlayerWorldDataRepository {
    
    private static final String TABLE_NAME = "rvnk_player_world_data";
    
    private final ConnectionProvider connectionProvider;
    private final QueryBuilder queryBuilder;
    private final LogManager logger;
    
    /**
     * Constructor for PlayerWorldDataRepository.
     * 
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param plugin The plugin instance for logging
     */
    public PlayerWorldDataRepository(ConnectionProvider connectionProvider, 
                                   QueryBuilder queryBuilder, 
                                   Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.queryBuilder = queryBuilder;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Finds player world data for a specific player and world.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @return CompletableFuture containing the player world data if found
     */
    public CompletableFuture<Optional<PlayerWorldDataDTO>> findByPlayerAndWorld(UUID playerId, String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(TABLE_NAME)
                .where("player_id = ? AND world_name = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerId.toString());
                stmt.setString(2, worldName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Failed to find player world data for player: " + playerId + ", world: " + worldName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player world data lookup failed", e);
            }
        });
    }
    
    /**
     * Finds all world data for a specific player.
     * 
     * @param playerId The player's UUID
     * @return CompletableFuture containing list of world data for the player
     */
    public CompletableFuture<List<PlayerWorldDataDTO>> findAllByPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(TABLE_NAME)
                .where("player_id = ?")
                .orderBy("last_visit", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerWorldDataDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find all world data for player: " + playerId, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player world data lookup failed", e);
            }
        });
    }
    
    /**
     * Finds all players who have visited a specific world.
     * 
     * @param worldName The world name
     * @return CompletableFuture containing list of player data for the world
     */
    public CompletableFuture<List<PlayerWorldDataDTO>> findAllByWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(TABLE_NAME)
                .where("world_name = ?")
                .orderBy("last_visit", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, worldName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerWorldDataDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find all player data for world: " + worldName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("World player data lookup failed", e);
            }
        });
    }
    
    /**
     * Finds players who visited a world recently.
     * 
     * @param worldName The world name
     * @param hoursAgo Number of hours to look back
     * @return CompletableFuture containing list of recent visitors
     */
    public CompletableFuture<List<PlayerWorldDataDTO>> findRecentVisitors(String worldName, int hoursAgo) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(TABLE_NAME)
                .where("world_name = ? AND last_visit > ?")
                .orderBy("last_visit", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, worldName);
                Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo));
                stmt.setTimestamp(2, cutoff);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerWorldDataDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find recent visitors for world: " + worldName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Recent visitors lookup failed", e);
            }
        });
    }
    
    /**
     * Saves or updates player world data.
     * 
     * @param worldData The player world data to save
     * @return CompletableFuture containing the saved data
     */
    public CompletableFuture<PlayerWorldDataDTO> save(PlayerWorldDataDTO worldData) {
        return CompletableFuture.supplyAsync(() -> {
            // Check if record exists
            String checkQuery = queryBuilder.select("COUNT(*)")
                .from(TABLE_NAME)
                .where("player_id = ? AND world_name = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection()) {
                
                boolean exists;
                try (var checkStmt = conn.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, worldData.getPlayerId().toString());
                    checkStmt.setString(2, worldData.getWorldName());
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next() && rs.getInt(1) > 0;
                    }
                }
                
                String query;
                if (exists) {
                    // Update existing record
                    query = queryBuilder.update(TABLE_NAME)
                        .set("first_visit", worldData.getFirstVisit())
                        .set("last_visit", worldData.getLastVisit())
                        .set("visit_count", worldData.getVisitCount())
                        .set("playtime_seconds", worldData.getPlaytimeSeconds())
                        .set("last_x", worldData.getLastX())
                        .set("last_y", worldData.getLastY())
                        .set("last_z", worldData.getLastZ())
                        .set("last_yaw", worldData.getLastYaw())
                        .set("last_pitch", worldData.getLastPitch())
                        .set("last_biome", worldData.getLastBiome())
                        .set("death_count", worldData.getDeathCount())
                        .set("updated_at", Timestamp.valueOf(LocalDateTime.now()))
                        .where("player_id = ? AND world_name = ?")
                        .build();
                } else {
                    // Insert new record
                    query = queryBuilder.insert(TABLE_NAME)
                        .columns("player_id", "world_name", "first_visit", "last_visit", "visit_count",
                                "playtime_seconds", "last_x", "last_y", "last_z", "last_yaw", "last_pitch",
                                "last_biome", "death_count", "created_at", "updated_at")
                        .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
                        .build();
                }
                
                try (var stmt = conn.prepareStatement(query)) {
                    if (exists) {
                        // Update parameters
                        stmt.setTimestamp(1, worldData.getFirstVisit());
                        stmt.setTimestamp(2, worldData.getLastVisit());
                        stmt.setInt(3, worldData.getVisitCount());
                        stmt.setLong(4, worldData.getPlaytimeSeconds());
                        stmt.setDouble(5, worldData.getLastX());
                        stmt.setDouble(6, worldData.getLastY());
                        stmt.setDouble(7, worldData.getLastZ());
                        stmt.setFloat(8, worldData.getLastYaw());
                        stmt.setFloat(9, worldData.getLastPitch());
                        stmt.setString(10, worldData.getLastBiome());
                        stmt.setInt(11, worldData.getDeathCount());
                        stmt.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
                        stmt.setString(13, worldData.getPlayerId().toString());
                        stmt.setString(14, worldData.getWorldName());
                    } else {
                        // Insert parameters
                        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                        stmt.setString(1, worldData.getPlayerId().toString());
                        stmt.setString(2, worldData.getWorldName());
                        stmt.setTimestamp(3, worldData.getFirstVisit());
                        stmt.setTimestamp(4, worldData.getLastVisit());
                        stmt.setInt(5, worldData.getVisitCount());
                        stmt.setLong(6, worldData.getPlaytimeSeconds());
                        stmt.setDouble(7, worldData.getLastX());
                        stmt.setDouble(8, worldData.getLastY());
                        stmt.setDouble(9, worldData.getLastZ());
                        stmt.setFloat(10, worldData.getLastYaw());
                        stmt.setFloat(11, worldData.getLastPitch());
                        stmt.setString(12, worldData.getLastBiome());
                        stmt.setInt(13, worldData.getDeathCount());
                        stmt.setTimestamp(14, now);
                        stmt.setTimestamp(15, now);
                    }
                    
                    stmt.executeUpdate();
                    return worldData;
                }
                
            } catch (SQLException e) {
                logger.error("Failed to save player world data: " + worldData.getPlayerId() + " in " + worldData.getWorldName(), e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player world data save failed", e);
            }
        });
    }
    
    /**
     * Deletes player world data for a specific player and world.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     * @return CompletableFuture indicating completion
     */
    public CompletableFuture<Void> deleteByPlayerAndWorld(UUID playerId, String worldName) {
        return CompletableFuture.runAsync(() -> {
            String query = queryBuilder.delete()
                .from(TABLE_NAME)
                .where("player_id = ? AND world_name = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerId.toString());
                stmt.setString(2, worldName);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                logger.error("Failed to delete player world data for player: " + playerId + ", world: " + worldName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player world data deletion failed", e);
            }
        });
    }
    
    /**
     * Maps a ResultSet row to a PlayerWorldDataDTO.
     * 
     * @param rs The ResultSet to map
     * @return The mapped PlayerWorldDataDTO
     * @throws SQLException if database access fails
     */
    protected PlayerWorldDataDTO mapResultSet(ResultSet rs) throws SQLException {
        return new PlayerWorldDataDTO.Builder()
            .playerId(UUID.fromString(rs.getString("player_id")))
            .worldName(rs.getString("world_name"))
            .firstVisit(rs.getTimestamp("first_visit"))
            .lastVisit(rs.getTimestamp("last_visit"))
            .visitCount(rs.getInt("visit_count"))
            .playtimeSeconds(rs.getLong("playtime_seconds"))
            .location(
                rs.getDouble("last_x"),
                rs.getDouble("last_y"),
                rs.getDouble("last_z"),
                rs.getFloat("last_yaw"),
                rs.getFloat("last_pitch")
            )
            .lastBiome(rs.getString("last_biome"))
            .deathCount(rs.getInt("death_count"))
            .build();
    }
}
