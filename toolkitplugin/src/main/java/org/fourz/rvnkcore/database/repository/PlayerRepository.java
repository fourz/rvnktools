package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for Player data operations.
 * 
 * Provides database access methods for player information including
 * activity tracking, location data, name history, and permission groups.
 * 
 * @since 1.0.0
 */
public class PlayerRepository extends BaseRepository<PlayerDTO, UUID> {
    
    private static final String TABLE_NAME = "rvnk_players";
    
    /**
     * Constructor for PlayerRepository.
     * 
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param plugin The plugin instance for logging
     */
    public PlayerRepository(ConnectionProvider connectionProvider, 
                          QueryBuilder queryBuilder, 
                          Plugin plugin) {
        super(connectionProvider, queryBuilder, TABLE_NAME, PlayerDTO.class, plugin);
    }
    
    /**
     * Finds a player by their current name.
     * 
     * @param playerName The current name of the player
     * @return CompletableFuture containing the player if found
     */
    public CompletableFuture<Optional<PlayerDTO>> findByCurrentName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("current_name = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Failed to find player by name: " + playerName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player lookup by name failed", e);
            }
        });
    }
    
    /**
     * Finds players who were last seen within the specified hours.
     * 
     * @param hoursAgo The number of hours to look back
     * @return CompletableFuture containing list of recent players
     */
    public CompletableFuture<List<PlayerDTO>> findRecentPlayers(int hoursAgo) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("last_seen > ?")
                .orderBy("last_seen", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo));
                stmt.setTimestamp(1, cutoff);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find recent players", e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Recent players lookup failed", e);
            }
        });
    }
    
    /**
     * Finds players by their primary permission group.
     * 
     * @param groupName The name of the permission group
     * @return CompletableFuture containing list of players in the group
     */
    public CompletableFuture<List<PlayerDTO>> findByPrimaryGroup(String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("primary_group = ?")
                .orderBy("current_name", true)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, groupName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find players by group: " + groupName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Players by group lookup failed", e);
            }
        });
    }
    
    /**
     * Searches for players whose names match the provided pattern.
     * 
     * @param namePattern The pattern to match (supports SQL LIKE syntax)
     * @return CompletableFuture containing list of matching players
     */
    public CompletableFuture<List<PlayerDTO>> searchByNamePattern(String namePattern) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("current_name LIKE ? OR name_history LIKE ?")
                .orderBy("current_name", true)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                String pattern = "%" + namePattern + "%";
                stmt.setString(1, pattern);
                stmt.setString(2, pattern);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to search players by name pattern: " + namePattern, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player name search failed", e);
            }
        });
    }
    
    @Override
    protected PlayerDTO mapResultSet(ResultSet rs) throws SQLException {
        PlayerDTO.Builder builder = new PlayerDTO.Builder()
            .id(UUID.fromString(rs.getString("id")))
            .currentName(rs.getString("current_name"))
            .firstJoin(rs.getTimestamp("first_join"))
            .lastSeen(rs.getTimestamp("last_seen"))
            .currentWorld(rs.getString("current_world"))
            .timesJoined(rs.getInt("times_joined"))
            .totalPlaytimeSeconds(rs.getLong("total_playtime_seconds"))
            .primaryGroup(rs.getString("primary_group"))
            .banned(rs.getBoolean("banned"));
        
        // Parse name history from comma-separated string
        String nameHistoryStr = rs.getString("name_history");
        if (nameHistoryStr != null && !nameHistoryStr.trim().isEmpty()) {
            List<String> nameHistory = Arrays.asList(nameHistoryStr.split(","));
            builder.nameHistory(nameHistory);
        }
        
        // Parse groups from comma-separated string
        String groupsStr = rs.getString("groups");
        if (groupsStr != null && !groupsStr.trim().isEmpty()) {
            List<String> groups = Arrays.asList(groupsStr.split(","));
            builder.groups(groups);
        }
        
        return builder.build();
    }
    
    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }
    
    @Override
    protected UUID getId(PlayerDTO entity) {
        return entity.getId();
    }
    
    @Override
    protected void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, UUID id) throws SQLException {
        stmt.setString(parameterIndex, id.toString());
    }
    
    @Override
    protected String buildInsertQuery() {
        return queryBuilder.insert(tableName)
            .columns("id", "current_name", "name_history", "first_join", "last_seen", 
                    "current_world", "times_joined", "total_playtime_seconds", "primary_group", "groups", "banned")
            .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
            .build();
    }
    
    @Override
    protected String buildUpdateQuery() {
        QueryBuilder builder = queryBuilder.update(tableName);
        builder.set("current_name", "?")
               .set("name_history", "?")
               .set("last_seen", "?")
               .set("current_world", "?")
               .set("times_joined", "?")
               .set("total_playtime_seconds", "?")
               .set("primary_group", "?")
               .set("groups", "?")
               .set("banned", "?")
               .where("id = ?");
        return builder.build();
    }
    
    @Override
    protected void setInsertParameters(PreparedStatement stmt, PlayerDTO entity) throws SQLException {
        stmt.setString(1, entity.getId().toString());
        stmt.setString(2, entity.getCurrentName());
        stmt.setString(3, String.join(",", entity.getNameHistory()));
        stmt.setTimestamp(4, entity.getFirstJoin());
        stmt.setTimestamp(5, entity.getLastSeen());
        stmt.setString(6, entity.getCurrentWorld());
        stmt.setInt(7, entity.getTimesJoined());
        stmt.setLong(8, entity.getTotalPlaytimeSeconds());
        stmt.setString(9, entity.getPrimaryGroup());
        stmt.setString(10, String.join(",", entity.getGroups()));
        stmt.setBoolean(11, entity.isBanned());
    }
    
    @Override
    protected void setUpdateParameters(PreparedStatement stmt, PlayerDTO entity) throws SQLException {
        stmt.setString(1, entity.getCurrentName());
        stmt.setString(2, String.join(",", entity.getNameHistory()));
        stmt.setTimestamp(3, entity.getLastSeen());
        stmt.setString(4, entity.getCurrentWorld());
        stmt.setInt(5, entity.getTimesJoined());
        stmt.setLong(6, entity.getTotalPlaytimeSeconds());
        stmt.setString(7, entity.getPrimaryGroup());
        stmt.setString(8, String.join(",", entity.getGroups()));
        stmt.setBoolean(9, entity.isBanned());
        stmt.setString(10, entity.getId().toString()); // WHERE clause
    }
}
