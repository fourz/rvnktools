package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for Announcement data operations.
 * 
 * Provides database access methods for announcement information including
 * content management, scheduling, targeting, and metadata operations.
 * 
 * @since 1.0.0
 */
public class AnnouncementRepository extends BaseRepository<AnnouncementDTO, String> {
    
    private static final String TABLE_NAME = "rvnk_announcements";
    
    /**
     * Constructor for AnnouncementRepository.
     * 
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param plugin The plugin instance for logging
     */
    public AnnouncementRepository(ConnectionProvider connectionProvider, 
                                QueryBuilder queryBuilder, 
                                Plugin plugin) {
        super(connectionProvider, queryBuilder, TABLE_NAME, AnnouncementDTO.class, plugin);
    }
    
    /**
     * Finds all active announcements that are currently valid for display.
     * 
     * @return CompletableFuture containing list of active announcements
     */
    public CompletableFuture<List<AnnouncementDTO>> findActiveAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("active = ? AND (expires_at IS NULL OR expires_at > ?) AND (scheduled_for IS NULL OR scheduled_for <= ?)")
                .orderBy("created_at", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                stmt.setBoolean(1, true);
                stmt.setTimestamp(2, now);
                stmt.setTimestamp(3, now);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find active announcements", e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Active announcements lookup failed", e);
            }
        });
    }
    
    /**
     * Finds announcements by their type.
     * 
     * @param type The announcement type
     * @return CompletableFuture containing list of announcements of the specified type
     */
    public CompletableFuture<List<AnnouncementDTO>> findByType(String type) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("type = ?")
                .orderBy("created_at", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, type);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find announcements by type: " + type, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Announcements by type lookup failed", e);
            }
        });
    }
    
    /**
     * Searches for announcements whose titles or messages match the provided pattern.
     * 
     * @param searchPattern The pattern to match (supports SQL LIKE syntax)
     * @return CompletableFuture containing list of matching announcements
     */
    public CompletableFuture<List<AnnouncementDTO>> searchByContent(String searchPattern) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("title LIKE ? OR message LIKE ?")
                .orderBy("created_at", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                String pattern = "%" + searchPattern + "%";
                stmt.setString(1, pattern);
                stmt.setString(2, pattern);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to search announcements by content: " + searchPattern, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Announcement content search failed", e);
            }
        });
    }
    
    /**
     * Finds announcements that target a specific world.
     * 
     * @param worldName The name of the world
     * @return CompletableFuture containing list of announcements targeting the world
     */
    public CompletableFuture<List<AnnouncementDTO>> findByTargetWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("target_worlds = '' OR target_worlds IS NULL OR target_worlds LIKE ?")
                .orderBy("created_at", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                String pattern = "%" + worldName + "%";
                stmt.setString(1, pattern);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        AnnouncementDTO announcement = mapResultSet(rs);
                        // Double-check targeting logic
                        if (announcement.targetsWorld(worldName)) {
                            results.add(announcement);
                        }
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find announcements for world: " + worldName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Announcements by world lookup failed", e);
            }
        });
    }
    
    /**
     * Finds announcements that target a specific permission group.
     * 
     * @param groupName The name of the permission group
     * @return CompletableFuture containing list of announcements targeting the group
     */
    public CompletableFuture<List<AnnouncementDTO>> findByTargetGroup(String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("*")
                .from(tableName)
                .where("target_groups = '' OR target_groups IS NULL OR target_groups LIKE ?")
                .orderBy("created_at", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                String pattern = "%" + groupName + "%";
                stmt.setString(1, pattern);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AnnouncementDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        AnnouncementDTO announcement = mapResultSet(rs);
                        // Double-check targeting logic
                        if (announcement.targetsGroup(groupName)) {
                            results.add(announcement);
                        }
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find announcements for group: " + groupName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Announcements by group lookup failed", e);
            }
        });
    }
    
    /**
     * Gets the count of active announcements.
     * 
     * @return CompletableFuture containing the active announcement count
     */
    public CompletableFuture<Long> countActiveAnnouncements() {
        return CompletableFuture.supplyAsync(() -> {
            String query = queryBuilder.select("COUNT(*)")
                .from(tableName)
                .where("active = ? AND (expires_at IS NULL OR expires_at > ?)")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setBoolean(1, true);
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            } catch (SQLException e) {
                logger.error("Failed to count active announcements", e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Active announcement count failed", e);
            }
        });
    }
    
    /**
     * Updates the active status of an announcement.
     * 
     * @param id The announcement ID
     * @param active The new active status
     * @return CompletableFuture that completes when the update is finished
     */
    public CompletableFuture<Void> updateActiveStatus(String id, boolean active) {
        return CompletableFuture.runAsync(() -> {
            String query = queryBuilder.update(tableName)
                .set("active", "?")
                .set("updated_at", "?")
                .where("id = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setBoolean(1, active);
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(3, id);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new org.fourz.rvnkcore.api.exception.DatabaseException("No announcement found with ID: " + id);
                }
            } catch (SQLException e) {
                logger.error("Failed to update active status for announcement: " + id, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Announcement active status update failed", e);
            }
        });
    }
    
    @Override
    protected AnnouncementDTO mapResultSet(ResultSet rs) throws SQLException {
        AnnouncementDTO.Builder builder = new AnnouncementDTO.Builder()
            .id(rs.getString("id"))
            .title(rs.getString("title"))
            .message(rs.getString("message"))
            .type(rs.getString("type"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at"))
            .updatedAt(rs.getTimestamp("updated_at"))
            .scheduledFor(rs.getTimestamp("scheduled_for"))
            .expiresAt(rs.getTimestamp("expires_at"))
            .intervalSeconds(rs.getInt("interval_seconds"));
        
        // Parse target worlds from comma-separated string
        String targetWorldsStr = rs.getString("target_worlds");
        if (targetWorldsStr != null && !targetWorldsStr.trim().isEmpty()) {
            List<String> targetWorlds = Arrays.asList(targetWorldsStr.split(","));
            builder.targetWorlds(targetWorlds);
        }
        
        // Parse target groups from comma-separated string
        String targetGroupsStr = rs.getString("target_groups");
        if (targetGroupsStr != null && !targetGroupsStr.trim().isEmpty()) {
            List<String> targetGroups = Arrays.asList(targetGroupsStr.split(","));
            builder.targetGroups(targetGroups);
        }
        
        // Parse metadata from JSON-like string (simplified implementation)
        String metadataStr = rs.getString("metadata");
        if (metadataStr != null && !metadataStr.trim().isEmpty()) {
            // For now, store as a simple key-value pair
            // In a full implementation, this would be proper JSON deserialization
            builder.metadata("raw_metadata", metadataStr);
        }
        
        return builder.build();
    }
    
    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }
    
    @Override
    protected String getId(AnnouncementDTO entity) {
        return entity.getId();
    }
    
    @Override
    protected void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, String id) throws SQLException {
        stmt.setString(parameterIndex, id);
    }
    
    @Override
    protected String buildInsertQuery() {
        return queryBuilder.insert(tableName)
            .columns("id", "title", "message", "type", "active", "created_at", "updated_at", 
                    "scheduled_for", "expires_at", "interval_seconds", "target_worlds", "target_groups", "metadata")
            .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
            .build();
    }
    
    @Override
    protected String buildUpdateQuery() {
        return queryBuilder.update(tableName)
            .set("title", "?")
            .set("message", "?")
            .set("type", "?")
            .set("active", "?")
            .set("updated_at", "?")
            .set("scheduled_for", "?")
            .set("expires_at", "?")
            .set("interval_seconds", "?")
            .set("target_worlds", "?")
            .set("target_groups", "?")
            .set("metadata", "?")
            .where("id = ?")
            .build();
    }
    
    @Override
    protected void setInsertParameters(PreparedStatement stmt, AnnouncementDTO entity) throws SQLException {
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getTitle());
        stmt.setString(3, entity.getMessage());
        stmt.setString(4, entity.getType());
        stmt.setBoolean(5, entity.isActive());
        stmt.setTimestamp(6, entity.getCreatedAt());
        stmt.setTimestamp(7, entity.getUpdatedAt());
        stmt.setTimestamp(8, entity.getScheduledFor());
        stmt.setTimestamp(9, entity.getExpiresAt());
        stmt.setInt(10, entity.getIntervalSeconds());
        stmt.setString(11, String.join(",", entity.getTargetWorlds()));
        stmt.setString(12, String.join(",", entity.getTargetGroups()));
        
        // Serialize metadata (simplified implementation)
        StringBuilder metadataStr = new StringBuilder();
        for (Map.Entry<String, Object> entry : entity.getMetadata().entrySet()) {
            if (metadataStr.length() > 0) metadataStr.append(",");
            metadataStr.append(entry.getKey()).append("=").append(entry.getValue());
        }
        stmt.setString(13, metadataStr.toString());
    }
    
    @Override
    protected void setUpdateParameters(PreparedStatement stmt, AnnouncementDTO entity) throws SQLException {
        stmt.setString(1, entity.getTitle());
        stmt.setString(2, entity.getMessage());
        stmt.setString(3, entity.getType());
        stmt.setBoolean(4, entity.isActive());
        stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now())); // Always update timestamp
        stmt.setTimestamp(6, entity.getScheduledFor());
        stmt.setTimestamp(7, entity.getExpiresAt());
        stmt.setInt(8, entity.getIntervalSeconds());
        stmt.setString(9, String.join(",", entity.getTargetWorlds()));
        stmt.setString(10, String.join(",", entity.getTargetGroups()));
        
        // Serialize metadata (simplified implementation)
        StringBuilder metadataStr = new StringBuilder();
        for (Map.Entry<String, Object> entry : entity.getMetadata().entrySet()) {
            if (metadataStr.length() > 0) metadataStr.append(",");
            metadataStr.append(entry.getKey()).append("=").append(entry.getValue());
        }
        stmt.setString(11, metadataStr.toString());
        stmt.setString(12, entity.getId()); // WHERE clause
    }
}
