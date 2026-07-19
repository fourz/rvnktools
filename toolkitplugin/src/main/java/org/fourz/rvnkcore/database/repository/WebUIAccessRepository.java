package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.api.model.WebUIAccessLogDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for WebUI access-log storage.
 * Table: {@code rvnk_webui_access_log}
 *
 * <p>Append-and-query only: rows are inserted per WebUI page visit / login / admin action
 * and read back through {@link #query}. There are no update or delete paths.</p>
 *
 * <p>All operations are async via {@link CompletableFuture}.</p>
 *
 * @since 1.5.9
 */
public class WebUIAccessRepository {

    /** Hard cap on rows a single query may return. */
    private static final int MAX_LIMIT = 1000;
    /** Default row limit when the caller does not specify one. */
    private static final int DEFAULT_LIMIT = 100;

    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private final String tableName;

    public WebUIAccessRepository(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());

        // Apply the configured table prefix via DatabaseSetup's helper (matches sibling repos).
        DatabaseSetup dbSetup = new DatabaseSetup(connectionProvider, plugin);
        this.tableName = dbSetup.table(DatabaseSetup.TABLE_WEBUI_ACCESS_LOG);
    }

    /**
     * Inserts a new access-log row and returns its generated primary key.
     *
     * @param dto the log entry (created_at defaults to the DB clock)
     * @return CompletableFuture of the generated id, or {@code null} if no key was returned
     */
    public CompletableFuture<Long> insert(WebUIAccessLogDTO dto) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tableName
                    + " (ign, uuid, ip_address, country_code, page_path, action_type)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, dto.getIgn());
                stmt.setString(2, dto.getUuid());
                stmt.setString(3, dto.getIpAddress());
                stmt.setString(4, dto.getCountryCode());
                stmt.setString(5, dto.getPagePath());
                stmt.setString(6, dto.getActionType());
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                return null;
            } catch (SQLException e) {
                logger.error("Failed to insert webui access log", e);
                throw new DatabaseException("WebUI access log insert failed", e);
            }
        });
    }

    /**
     * Queries access-log rows with optional filters, ordered by {@code created_at DESC}.
     *
     * @param ign         optional exact IGN filter (null/blank = ignored)
     * @param countryCode optional exact country-code filter (null/blank = ignored)
     * @param actionType  optional exact action-type filter (null/blank = ignored)
     * @param from        optional inclusive lower bound on created_at (ISO datetime string)
     * @param to          optional inclusive upper bound on created_at (ISO datetime string)
     * @param limit       max rows (clamped to 1..{@value #MAX_LIMIT}; {@value #DEFAULT_LIMIT} if <= 0)
     * @return CompletableFuture of a {@link QueryResult} holding the page and the total match count
     */
    public CompletableFuture<QueryResult> query(String ign, String countryCode, String actionType,
                                                String from, String to, int limit) {
        final int safeLimit = clampLimit(limit);
        return CompletableFuture.supplyAsync(() -> {
            List<String> conditions = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            if (ign != null && !ign.isBlank()) { conditions.add("ign = ?"); params.add(ign); }
            if (countryCode != null && !countryCode.isBlank()) { conditions.add("country_code = ?"); params.add(countryCode); }
            if (actionType != null && !actionType.isBlank()) { conditions.add("action_type = ?"); params.add(actionType); }
            if (from != null && !from.isBlank()) { conditions.add("created_at >= ?"); params.add(from); }
            if (to != null && !to.isBlank()) { conditions.add("created_at <= ?"); params.add(to); }
            String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);

            // #1471: BasicSQLQueryBuilder is stateful and single-use — instantiate a FRESH one here
            // (per-lambda), never as a shared field. A second fresh instance is used for the count.
            BasicSQLQueryBuilder dataBuilder = new BasicSQLQueryBuilder();
            dataBuilder.select("*").from(tableName);
            if (whereClause != null) {
                dataBuilder.where(whereClause);
            }
            dataBuilder.orderBy("created_at", false).limit(safeLimit);
            String dataSql = dataBuilder.build();

            BasicSQLQueryBuilder countBuilder = new BasicSQLQueryBuilder();
            countBuilder.select("COUNT(*)").from(tableName);
            if (whereClause != null) {
                countBuilder.where(whereClause);
            }
            String countSql = countBuilder.build();

            List<WebUIAccessLogDTO> data = new ArrayList<>();
            long total = 0L;
            try (Connection conn = connectionProvider.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            data.add(mapResultSet(rs));
                        }
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            total = rs.getLong(1);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to query webui access logs", e);
                throw new DatabaseException("WebUI access log query failed", e);
            }
            return new QueryResult(data, total);
        });
    }

    private WebUIAccessLogDTO mapResultSet(ResultSet rs) throws SQLException {
        WebUIAccessLogDTO dto = new WebUIAccessLogDTO();
        dto.setId(rs.getLong("id"));
        dto.setIgn(rs.getString("ign"));
        dto.setUuid(rs.getString("uuid"));
        dto.setIpAddress(rs.getString("ip_address"));
        dto.setCountryCode(rs.getString("country_code"));
        dto.setPagePath(rs.getString("page_path"));
        dto.setActionType(rs.getString("action_type"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        return dto;
    }

    private static int clampLimit(int limit) {
        int effective = limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.max(1, Math.min(effective, MAX_LIMIT));
    }

    /**
     * Result of a {@link #query} call: the page of rows plus the total match count
     * (ignoring the limit).
     */
    public static final class QueryResult {
        private final List<WebUIAccessLogDTO> data;
        private final long total;

        public QueryResult(List<WebUIAccessLogDTO> data, long total) {
            this.data = data;
            this.total = total;
        }

        public List<WebUIAccessLogDTO> getData() { return data; }
        public long getTotal() { return total; }
    }
}
