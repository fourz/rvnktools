package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Repository implementation for AnnouncementType data operations.
 *
 * Provides CRUD access for announcement type definitions stored in
 * the rvnk_announcement_types table.
 *
 * @since 1.4.0
 */
public class AnnouncementTypeRepository extends BaseRepository<AnnouncementTypeDTO, String> {

    private static final String TABLE_NAME = "rvnk_announcement_types";

    public AnnouncementTypeRepository(ConnectionProvider connectionProvider,
                                      QueryBuilder queryBuilder,
                                      Plugin plugin) {
        super(connectionProvider, queryBuilder, TABLE_NAME, AnnouncementTypeDTO.class, plugin);
    }

    @Override
    protected AnnouncementTypeDTO mapResultSet(ResultSet rs) throws SQLException {
        AnnouncementTypeDTO.Builder builder = new AnnouncementTypeDTO.Builder()
            .id(rs.getString("id"))
            .name(rs.getString("name"))
            .prefix(rs.getString("prefix"))
            .suffix(rs.getString("suffix"))
            .permission(rs.getString("permission"))
            .listFee(rs.getInt("list_fee"))
            .weeklyFee(rs.getInt("weekly_fee"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at"))
            .updatedAt(rs.getTimestamp("updated_at"))
            .displayContext(rs.getString("display_context"));

        String metadataStr = rs.getString("metadata");
        if (metadataStr != null && !metadataStr.trim().isEmpty()) {
            builder.metadata("raw_metadata", metadataStr);
        }

        return builder.build();
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getId(AnnouncementTypeDTO entity) {
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
            .columns("id", "name", "prefix", "suffix", "permission",
                     "list_fee", "weekly_fee", "active", "created_at", "updated_at", "metadata",
                     "display_context")
            .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
            .build();
    }

    @Override
    protected String buildUpdateQuery() {
        QueryBuilder builder = createQueryBuilder();
        return builder.update(tableName)
            .set("name", "?")
            .set("prefix", "?")
            .set("suffix", "?")
            .set("permission", "?")
            .set("list_fee", "?")
            .set("weekly_fee", "?")
            .set("active", "?")
            .set("updated_at", "?")
            .set("metadata", "?")
            .set("display_context", "?")
            .where("id = ?")
            .build();
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, AnnouncementTypeDTO entity) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getName());
        stmt.setString(3, entity.getPrefix());
        stmt.setString(4, entity.getSuffix());
        stmt.setString(5, entity.getPermission());
        stmt.setInt(6, entity.getListFee() != null ? entity.getListFee() : 0);
        stmt.setInt(7, entity.getWeeklyFee() != null ? entity.getWeeklyFee() : 0);
        stmt.setBoolean(8, entity.isActive());
        stmt.setTimestamp(9, entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        stmt.setTimestamp(10, entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);
        stmt.setString(11, serializeMetadata(entity));
        stmt.setString(12, entity.getDisplayContext());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, AnnouncementTypeDTO entity) throws SQLException {
        stmt.setString(1, entity.getName());
        stmt.setString(2, entity.getPrefix());
        stmt.setString(3, entity.getSuffix());
        stmt.setString(4, entity.getPermission());
        stmt.setInt(5, entity.getListFee() != null ? entity.getListFee() : 0);
        stmt.setInt(6, entity.getWeeklyFee() != null ? entity.getWeeklyFee() : 0);
        stmt.setBoolean(7, entity.isActive());
        stmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(9, serializeMetadata(entity));
        stmt.setString(10, entity.getDisplayContext());
        stmt.setString(11, entity.getId()); // WHERE clause
    }

    private String serializeMetadata(AnnouncementTypeDTO entity) {
        if (entity.getMetadata() == null || entity.getMetadata().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : entity.getMetadata().entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}
