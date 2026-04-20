package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.api.model.EventDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for Event data operations.
 *
 * Authoritative store for admin-editable events on the WebUI hub. Sections are
 * persisted via {@link EventSectionRepository}; this repo is scoped to the
 * parent event row. Soft-deletes are performed by toggling {@code active = 0}.
 *
 * @since 1.5.0
 */
public class EventRepository extends BaseRepository<EventDTO, String> {

    private static final String TABLE_NAME = "rvnk_events";

    public EventRepository(ConnectionProvider connectionProvider,
                           QueryBuilder queryBuilder,
                           Plugin plugin) {
        super(connectionProvider, queryBuilder, TABLE_NAME, EventDTO.class, plugin);
    }

    /**
     * Finds currently "live" or "scheduled" events (active flag + status filter).
     */
    public CompletableFuture<List<EventDTO>> findActive() {
        return CompletableFuture.supplyAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("active = ? AND status IN ('live', 'scheduled')")
                .orderBy("start_at", true)
                .build();

            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {

                stmt.setBoolean(1, true);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<EventDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find active events", e);
                throw new DatabaseException("Active events lookup failed", e);
            }
        });
    }

    /**
     * Finds events by status.
     */
    public CompletableFuture<List<EventDTO>> findByStatus(String status) {
        return CompletableFuture.supplyAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("active = ? AND status = ?")
                .orderBy("start_at", false)
                .build();

            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {

                stmt.setBoolean(1, true);
                stmt.setString(2, status);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<EventDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find events by status: " + status, e);
                throw new DatabaseException("Events by status lookup failed", e);
            }
        });
    }

    /**
     * Soft-delete: set active = 0.
     */
    public CompletableFuture<Void> softDelete(String id) {
        return CompletableFuture.runAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.update(tableName)
                .set("active", "?")
                .set("updated_at", "?")
                .where("id = ?")
                .build();

            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {

                stmt.setBoolean(1, false);
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(3, id);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new DatabaseException("No event found with ID: " + id);
                }
            } catch (SQLException e) {
                logger.error("Failed to soft-delete event: " + id, e);
                throw new DatabaseException("Event soft-delete failed", e);
            }
        });
    }

    @Override
    protected EventDTO mapResultSet(ResultSet rs) throws SQLException {
        EventDTO.Builder builder = new EventDTO.Builder()
            .id(rs.getString("id"))
            .title(rs.getString("title"))
            .emoji(rs.getString("emoji"))
            .category(rs.getString("category"))
            .status(rs.getString("status"))
            .intro(rs.getString("intro"))
            .location(rs.getString("location"))
            .startAt(rs.getTimestamp("start_at"))
            .endAt(rs.getTimestamp("end_at"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at"))
            .updatedAt(rs.getTimestamp("updated_at"))
            .metadata(rs.getString("metadata"))
            .ownerUuid(rs.getString("owner_uuid"));

        return builder.build();
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getId(EventDTO entity) {
        return entity.getId();
    }

    @Override
    protected void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, String id) throws SQLException {
        stmt.setString(parameterIndex, id);
    }

    @Override
    protected String buildInsertQuery() {
        QueryBuilder builder = createQueryBuilder();
        return builder.insert(tableName)
            .columns("id", "title", "emoji", "category", "status", "intro", "location",
                     "start_at", "end_at", "active", "created_at", "updated_at", "metadata", "owner_uuid")
            .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
            .build();
    }

    @Override
    protected String buildUpdateQuery() {
        QueryBuilder builder = createQueryBuilder();
        return builder.update(tableName)
            .set("title", "?")
            .set("emoji", "?")
            .set("category", "?")
            .set("status", "?")
            .set("intro", "?")
            .set("location", "?")
            .set("start_at", "?")
            .set("end_at", "?")
            .set("active", "?")
            .set("updated_at", "?")
            .set("metadata", "?")
            .set("owner_uuid", "?")
            .where("id = ?")
            .build();
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, EventDTO e) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        stmt.setString(1, e.getId());
        stmt.setString(2, e.getTitle());
        stmt.setString(3, e.getEmoji());
        stmt.setString(4, e.getCategory() != null ? e.getCategory() : "general");
        stmt.setString(5, e.getStatus() != null ? e.getStatus() : "draft");
        stmt.setString(6, e.getIntro());
        stmt.setString(7, e.getLocation());
        stmt.setTimestamp(8, e.getStartAt());
        stmt.setTimestamp(9, e.getEndAt());
        stmt.setBoolean(10, e.isActive());
        stmt.setTimestamp(11, e.getCreatedAt() != null ? e.getCreatedAt() : now);
        stmt.setTimestamp(12, e.getUpdatedAt() != null ? e.getUpdatedAt() : now);
        stmt.setString(13, e.getMetadata());
        stmt.setString(14, e.getOwnerUuid());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, EventDTO e) throws SQLException {
        stmt.setString(1, e.getTitle());
        stmt.setString(2, e.getEmoji());
        stmt.setString(3, e.getCategory());
        stmt.setString(4, e.getStatus());
        stmt.setString(5, e.getIntro());
        stmt.setString(6, e.getLocation());
        stmt.setTimestamp(7, e.getStartAt());
        stmt.setTimestamp(8, e.getEndAt());
        stmt.setBoolean(9, e.isActive());
        stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(11, e.getMetadata());
        stmt.setString(12, e.getOwnerUuid());
        stmt.setString(13, e.getId());
    }
}
