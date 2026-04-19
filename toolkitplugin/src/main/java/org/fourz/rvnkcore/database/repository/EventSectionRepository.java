package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.api.model.EventSectionDTO;
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
 * Repository for Event Section data operations.
 *
 * Sections are ordered child rows under an Event (FK event_id, cascade delete).
 * Each section stores jsoup-sanitized TipTap HTML in body_html.
 *
 * @since 1.5.0
 */
public class EventSectionRepository extends BaseRepository<EventSectionDTO, String> {

    private static final String TABLE_NAME = "rvnk_event_sections";

    public EventSectionRepository(ConnectionProvider connectionProvider,
                                  QueryBuilder queryBuilder,
                                  Plugin plugin) {
        super(connectionProvider, queryBuilder, TABLE_NAME, EventSectionDTO.class, plugin);
    }

    /**
     * Loads all sections belonging to an event, ordered by position.
     */
    public CompletableFuture<List<EventSectionDTO>> findByEventId(String eventId) {
        return CompletableFuture.supplyAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("event_id = ?")
                .orderBy("position", true)
                .build();

            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {

                stmt.setString(1, eventId);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<EventSectionDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to load sections for event: " + eventId, e);
                throw new DatabaseException("Event section lookup failed", e);
            }
        });
    }

    /**
     * Deletes all sections belonging to an event (used when replacing the full list).
     */
    public CompletableFuture<Void> deleteByEventId(String eventId) {
        return CompletableFuture.runAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.delete()
                .from(tableName)
                .where("event_id = ?")
                .build();

            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {

                stmt.setString(1, eventId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Failed to delete sections for event: " + eventId, e);
                throw new DatabaseException("Event section bulk delete failed", e);
            }
        });
    }

    @Override
    protected EventSectionDTO mapResultSet(ResultSet rs) throws SQLException {
        return new EventSectionDTO.Builder()
            .id(rs.getString("id"))
            .eventId(rs.getString("event_id"))
            .position(rs.getInt("position"))
            .heading(rs.getString("heading"))
            .bodyHtml(rs.getString("body_html"))
            .createdAt(rs.getTimestamp("created_at"))
            .updatedAt(rs.getTimestamp("updated_at"))
            .build();
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getId(EventSectionDTO entity) {
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
            .columns("id", "event_id", "position", "heading", "body_html", "created_at", "updated_at")
            .values("?", "?", "?", "?", "?", "?", "?")
            .build();
    }

    @Override
    protected String buildUpdateQuery() {
        QueryBuilder builder = createQueryBuilder();
        return builder.update(tableName)
            .set("event_id", "?")
            .set("position", "?")
            .set("heading", "?")
            .set("body_html", "?")
            .set("updated_at", "?")
            .where("id = ?")
            .build();
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, EventSectionDTO s) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        stmt.setString(1, s.getId());
        stmt.setString(2, s.getEventId());
        stmt.setInt(3, s.getPosition());
        stmt.setString(4, s.getHeading());
        stmt.setString(5, s.getBodyHtml() != null ? s.getBodyHtml() : "");
        stmt.setTimestamp(6, s.getCreatedAt() != null ? s.getCreatedAt() : now);
        stmt.setTimestamp(7, s.getUpdatedAt() != null ? s.getUpdatedAt() : now);
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, EventSectionDTO s) throws SQLException {
        stmt.setString(1, s.getEventId());
        stmt.setInt(2, s.getPosition());
        stmt.setString(3, s.getHeading());
        stmt.setString(4, s.getBodyHtml() != null ? s.getBodyHtml() : "");
        stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(6, s.getId());
    }
}
