package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    private static final String EVENTS_TABLE = "rvnk_events";
    private static final String SECTIONS_TABLE = "rvnk_event_sections";
    private static final Pattern ID_PATTERN = Pattern.compile("^/([^/]+)$");

    public EventController(Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    private ConnectionProvider getConnectionProvider() {
        RVNKCore core = RVNKCore.getInstance();
        if (core == null) return null;
        ServiceRegistry registry = core.getServiceRegistry();
        if (registry == null || !registry.hasService(ConnectionProvider.class)) return null;
        return registry.getService(ConnectionProvider.class);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ConnectionProvider cp = getConnectionProvider();
        if (cp == null) {
            sendError(response, 503, "Database service unavailable");
            return;
        }

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleListEvents(cp, response);
            } else {
                Matcher m = ID_PATTERN.matcher(pathInfo);
                if (m.matches()) {
                    handleGetEvent(cp, m.group(1), response);
                } else {
                    sendError(response, 404, "Endpoint not found");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling GET /v1/events" + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ConnectionProvider cp = getConnectionProvider();
        if (cp == null) {
            sendError(response, 503, "Database service unavailable");
            return;
        }

        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is empty");
                return;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String id = (json.has("id") && !json.get("id").isJsonNull())
                    ? json.get("id").getAsString()
                    : UUID.randomUUID().toString();
            Timestamp now = Timestamp.from(Instant.now());

            try (Connection conn = cp.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    insertEvent(conn, id, json, now);
                    if (json.has("sections") && json.get("sections").isJsonArray()) {
                        insertSections(conn, id, json.get("sections").getAsJsonArray(), now);
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

            ApiUtils.sendJson(response, gson, 201,
                    org.fourz.rvnkcore.api.model.response.ApiResponse.success(
                            Map.of("id", id, "created", true)));
        } catch (Exception e) {
            logger.error("Error creating event", e);
            sendError(response, 500, "Failed to create event: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Matcher m = pathInfo != null ? ID_PATTERN.matcher(pathInfo) : null;
        if (m == null || !m.matches()) {
            sendError(response, 400, "Event ID required: PUT /v1/events/{id}");
            return;
        }
        String id = m.group(1);

        ConnectionProvider cp = getConnectionProvider();
        if (cp == null) {
            sendError(response, 503, "Database service unavailable");
            return;
        }

        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is empty");
                return;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            Timestamp now = Timestamp.from(Instant.now());

            try (Connection conn = cp.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    updateEvent(conn, id, json, now);
                    softDeleteSections(conn, id, now);
                    if (json.has("sections") && json.get("sections").isJsonArray()) {
                        insertSections(conn, id, json.get("sections").getAsJsonArray(), now);
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

            ApiUtils.sendSuccess(response, gson, Map.of("id", id, "updated", true));
        } catch (Exception e) {
            logger.error("Error updating event: " + id, e);
            sendError(response, 500, "Failed to update event: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Matcher m = pathInfo != null ? ID_PATTERN.matcher(pathInfo) : null;
        if (m == null || !m.matches()) {
            sendError(response, 400, "Event ID required: DELETE /v1/events/{id}");
            return;
        }
        String id = m.group(1);

        ConnectionProvider cp = getConnectionProvider();
        if (cp == null) {
            sendError(response, 503, "Database service unavailable");
            return;
        }

        try (Connection conn = cp.getConnection()) {
            Timestamp now = Timestamp.from(Instant.now());
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + EVENTS_TABLE + " SET active = 0, updated_at = ? WHERE id = ?")) {
                ps.setTimestamp(1, now);
                ps.setString(2, id);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    sendError(response, 404, "Event not found: " + id);
                    return;
                }
            }
            ApiUtils.sendSuccess(response, gson, Map.of("id", id, "deleted", true));
        } catch (Exception e) {
            logger.error("Error deleting event: " + id, e);
            sendError(response, 500, "Failed to delete event: " + e.getMessage());
        }
    }

    // ====== DB helpers ======

    private void insertEvent(Connection conn, String id, JsonObject json, Timestamp now) throws SQLException {
        String sql = "INSERT INTO " + EVENTS_TABLE
                + " (id, title, emoji, category, status, intro, location, start_at, end_at,"
                + " active, metadata, owner_uuid, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, getString(json, "title"));
            ps.setString(3, getString(json, "emoji"));
            ps.setString(4, getString(json, "category"));
            ps.setString(5, getStringOrDefault(json, "status", "draft"));
            ps.setString(6, getString(json, "intro"));
            ps.setString(7, getString(json, "location"));
            ps.setTimestamp(8, parseTimestamp(getString(json, "startAt")));
            ps.setTimestamp(9, parseTimestamp(getString(json, "endAt")));
            ps.setInt(10, getBooleanInt(json, "active", true));
            ps.setString(11, getString(json, "metadata"));
            ps.setString(12, getString(json, "ownerUuid"));
            ps.setTimestamp(13, now);
            ps.setTimestamp(14, now);
            ps.executeUpdate();
        }
    }

    private void updateEvent(Connection conn, String id, JsonObject json, Timestamp now) throws SQLException {
        String sql = "UPDATE " + EVENTS_TABLE
                + " SET title = ?, emoji = ?, category = ?, status = ?, intro = ?,"
                + " location = ?, start_at = ?, end_at = ?, active = ?, updated_at = ?"
                + " WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, getString(json, "title"));
            ps.setString(2, getString(json, "emoji"));
            ps.setString(3, getString(json, "category"));
            ps.setString(4, getStringOrDefault(json, "status", "draft"));
            ps.setString(5, getString(json, "intro"));
            ps.setString(6, getString(json, "location"));
            ps.setTimestamp(7, parseTimestamp(getString(json, "startAt")));
            ps.setTimestamp(8, parseTimestamp(getString(json, "endAt")));
            ps.setInt(9, getBooleanInt(json, "active", true));
            ps.setTimestamp(10, now);
            ps.setString(11, id);
            ps.executeUpdate();
        }
    }

    private void softDeleteSections(Connection conn, String eventId, Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + SECTIONS_TABLE + " SET active = 0, updated_at = ? WHERE event_id = ?")) {
            ps.setTimestamp(1, now);
            ps.setString(2, eventId);
            ps.executeUpdate();
        }
    }

    private void insertSections(Connection conn, String eventId, JsonArray sections, Timestamp now) throws SQLException {
        String sql = "INSERT INTO " + SECTIONS_TABLE
                + " (id, event_id, position, heading, body_html, active, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < sections.size(); i++) {
                JsonObject s = sections.get(i).getAsJsonObject();
                String sId = (s.has("id") && !s.get("id").isJsonNull())
                        ? s.get("id").getAsString()
                        : UUID.randomUUID().toString();
                ps.setString(1, sId);
                ps.setString(2, eventId);
                ps.setInt(3, s.has("position") ? s.get("position").getAsInt() : i);
                ps.setString(4, getString(s, "heading"));
                ps.setString(5, getStringOrDefault(s, "bodyHtml", ""));
                ps.setTimestamp(6, now);
                ps.setTimestamp(7, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void handleListEvents(ConnectionProvider cp, HttpServletResponse response) throws Exception {
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM " + EVENTS_TABLE + " WHERE active = 1 ORDER BY start_at DESC")) {
            List<Map<String, Object>> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(rowToMap(rs));
                }
            }
            ApiUtils.sendSuccess(response, gson, events);
        }
    }

    private void handleGetEvent(ConnectionProvider cp, String id, HttpServletResponse response) throws Exception {
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM " + EVENTS_TABLE + " WHERE id = ? AND active = 1")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(response, 404, "Event not found: " + id);
                    return;
                }
                ApiUtils.sendSuccess(response, gson, rowToMap(rs));
            }
        }
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("title", rs.getString("title"));
        row.put("emoji", rs.getString("emoji"));
        row.put("category", rs.getString("category"));
        row.put("status", rs.getString("status"));
        row.put("intro", rs.getString("intro"));
        row.put("location", rs.getString("location"));
        Timestamp startAt = rs.getTimestamp("start_at");
        row.put("startAt", startAt != null ? startAt.toInstant().toString() : null);
        Timestamp endAt = rs.getTimestamp("end_at");
        row.put("endAt", endAt != null ? endAt.toInstant().toString() : null);
        row.put("active", rs.getBoolean("active"));
        row.put("ownerUuid", rs.getString("owner_uuid"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        row.put("createdAt", createdAt != null ? createdAt.toInstant().toString() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        row.put("updatedAt", updatedAt != null ? updatedAt.toInstant().toString() : null);
        row.put("sections", List.of());
        return row;
    }

    // ====== Parse helpers ======

    private String getString(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : null;
    }

    private String getStringOrDefault(JsonObject json, String key, String def) {
        String v = getString(json, key);
        return v != null ? v : def;
    }

    private int getBooleanInt(JsonObject json, String key, boolean def) {
        if (!json.has(key) || json.get(key).isJsonNull()) return def ? 1 : 0;
        return json.get(key).getAsBoolean() ? 1 : 0;
    }

    private Timestamp parseTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return Timestamp.from(Instant.parse(iso));
        } catch (Exception e) {
            return null;
        }
    }

    // ====== Response helpers ======

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "ERROR";
        };
        ApiUtils.sendError(response, gson, status, code, message);
    }
}
