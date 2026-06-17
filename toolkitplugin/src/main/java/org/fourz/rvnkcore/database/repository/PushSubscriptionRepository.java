package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.model.PushSubscriptionDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for web push subscription storage.
 * Table: {@code rvnk_push_subscriptions}
 *
 * @since 1.6.0
 */
public class PushSubscriptionRepository extends BaseRepository<PushSubscriptionDTO, Integer> {

    public PushSubscriptionRepository(ConnectionProvider connectionProvider, Plugin plugin) {
        super(connectionProvider, new BasicSQLQueryBuilder(), "rvnk_push_subscriptions",
                PushSubscriptionDTO.class, plugin);
    }

    @Override
    protected PushSubscriptionDTO mapResultSet(ResultSet rs) throws SQLException {
        PushSubscriptionDTO dto = new PushSubscriptionDTO();
        dto.setId(rs.getInt("id"));
        dto.setPlayerId(rs.getString("player_id"));
        dto.setEndpoint(rs.getString("endpoint"));
        dto.setP256dh(rs.getString("p256dh"));
        dto.setAuthKey(rs.getString("auth_key"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        return dto;
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected Integer getId(PushSubscriptionDTO entity) {
        return entity.getId();
    }

    @Override
    protected void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, Integer id) throws SQLException {
        stmt.setInt(parameterIndex, id);
    }

    @Override
    protected String buildInsertQuery() {
        return "INSERT INTO " + tableName
                + " (player_id, endpoint, p256dh, auth_key) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE player_id = VALUES(player_id), p256dh = VALUES(p256dh), auth_key = VALUES(auth_key)";
    }

    @Override
    protected String buildUpdateQuery() {
        return "UPDATE " + tableName
                + " SET player_id = ?, endpoint = ?, p256dh = ?, auth_key = ? WHERE id = ?";
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, PushSubscriptionDTO dto) throws SQLException {
        stmt.setString(1, dto.getPlayerId());
        stmt.setString(2, dto.getEndpoint());
        stmt.setString(3, dto.getP256dh());
        stmt.setString(4, dto.getAuthKey());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, PushSubscriptionDTO dto) throws SQLException {
        stmt.setString(1, dto.getPlayerId());
        stmt.setString(2, dto.getEndpoint());
        stmt.setString(3, dto.getP256dh());
        stmt.setString(4, dto.getAuthKey());
        stmt.setInt(5, dto.getId());
    }

    public CompletableFuture<List<PushSubscriptionDTO>> findByPlayerId(String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<PushSubscriptionDTO> results = new ArrayList<>();
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE player_id = ?")) {
                stmt.setString(1, playerId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to query subscriptions by player " + playerId, e);
            }
            return results;
        });
    }

    public CompletableFuture<Void> deleteByEndpoint(String endpoint) {
        return CompletableFuture.runAsync(() -> {
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement("DELETE FROM " + tableName + " WHERE endpoint = ?")) {
                stmt.setString(1, endpoint);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Failed to delete subscription by endpoint", e);
            }
        });
    }

    /**
     * Creates the push subscriptions table if it doesn't exist.
     */
    public CompletableFuture<Void> createTable() {
        return CompletableFuture.runAsync(() -> {
            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "player_id VARCHAR(36) NOT NULL, "
                    + "endpoint TEXT NOT NULL, "
                    + "p256dh TEXT NOT NULL, "
                    + "auth_key VARCHAR(128) NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "INDEX idx_push_player (player_id), "
                    + "UNIQUE INDEX idx_push_endpoint (endpoint(255))"
                    + ")";
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                logger.info("Push subscriptions table ensured");
            } catch (SQLException e) {
                logger.error("Failed to create push subscriptions table", e);
            }
        });
    }
}
