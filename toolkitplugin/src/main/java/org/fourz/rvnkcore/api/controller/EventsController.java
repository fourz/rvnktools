package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.EventDTO;
import org.fourz.rvnkcore.api.model.EventSectionDTO;
import org.fourz.rvnkcore.api.service.EventService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for Events.
 *
 * <p>Routes:</p>
 * <ul>
 *   <li>GET    /v1/events              — list all (optional ?status=)</li>
 *   <li>GET    /v1/events/active       — list live/scheduled</li>
 *   <li>GET    /v1/events/{id}         — single event + sections</li>
 *   <li>POST   /v1/events              — create event (with sections)</li>
 *   <li>PUT    /v1/events/{id}         — update event (replaces sections)</li>
 *   <li>DELETE /v1/events/{id}         — soft-delete (active=0)</li>
 * </ul>
 *
 * <p>Auth: X-API-Key required (enforced by {@code AuthFilter}). Writes additionally
 * require a valid X-User-UUID header whose LuckPerms groups include an editor role;
 * that gate is enforced by the WebUI layer at present (jsoup sanitization here protects
 * the DB against XSS regardless).</p>
 *
 * @since 1.5.0
 */
public class EventsController extends HttpServlet {

    private final EventService service;
    private final LogManager logger;
    private final Gson gson;

    public EventsController(EventService service, LogManager logger, Gson gson) {
        this.service = service;
        this.logger = logger;
        this.gson = gson;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleList(request, response);
            } else if (pathInfo.equals("/active")) {
                handleActive(response);
            } else if (pathInfo.startsWith("/")) {
                handleGetById(pathInfo.substring(1), response);
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling GET request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleCreate(request, response);
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling POST request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo != null && pathInfo.startsWith("/")) {
                String id = pathInfo.substring(1);
                if (id.isEmpty() || id.contains("/")) {
                    sendError(response, 404, "Endpoint not found");
                    return;
                }
                handleUpdate(id, request, response);
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling PUT request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo != null && pathInfo.startsWith("/")) {
                String id = pathInfo.substring(1);
                if (id.isEmpty() || id.contains("/")) {
                    sendError(response, 404, "Endpoint not found");
                    return;
                }
                service.deleteEvent(id).get(15, TimeUnit.SECONDS);
                ApiUtils.sendSuccess(response, gson, java.util.Map.of("id", id, "deleted", true));
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling DELETE request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    // ===== handlers =====

    private void handleList(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String status = request.getParameter("status");
        List<EventDTO> events = (status != null && !status.isBlank())
            ? service.getEventsByStatus(status).get(15, TimeUnit.SECONDS)
            : service.getAllEvents().get(15, TimeUnit.SECONDS);
        ApiUtils.sendSuccess(response, gson, events);
    }

    private void handleActive(HttpServletResponse response) throws Exception {
        List<EventDTO> events = service.getActiveEvents().get(15, TimeUnit.SECONDS);
        ApiUtils.sendSuccess(response, gson, events);
    }

    private void handleGetById(String id, HttpServletResponse response) throws Exception {
        Optional<EventDTO> opt = service.getEvent(id).get(15, TimeUnit.SECONDS);
        if (opt.isEmpty()) {
            sendError(response, 404, "Event not found: " + id);
            return;
        }
        ApiUtils.sendSuccess(response, gson, opt.get());
    }

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = ApiUtils.readRequestBody(request);
        if (body == null || body.isEmpty()) {
            sendError(response, 400, "Request body is empty");
            return;
        }
        EventDTO event = parseEvent(JsonParser.parseString(body).getAsJsonObject(), null, request);
        EventDTO saved = service.createEvent(event).get(15, TimeUnit.SECONDS);
        ApiUtils.sendJson(response, gson, 201, java.util.Map.of(
            "success", true,
            "data", saved
        ));
    }

    private void handleUpdate(String id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = ApiUtils.readRequestBody(request);
        if (body == null || body.isEmpty()) {
            sendError(response, 400, "Request body is empty");
            return;
        }
        EventDTO event = parseEvent(JsonParser.parseString(body).getAsJsonObject(), id, request);
        EventDTO saved = service.updateEvent(event).get(15, TimeUnit.SECONDS);
        ApiUtils.sendSuccess(response, gson, saved);
    }

    // ===== parsing =====

    private EventDTO parseEvent(JsonObject json, String fallbackId, HttpServletRequest request) {
        EventDTO.Builder b = new EventDTO.Builder();
        if (fallbackId != null) b.id(fallbackId);
        else if (json.has("id")) b.id(json.get("id").getAsString());

        if (json.has("title")) b.title(json.get("title").getAsString());
        if (json.has("emoji") && !json.get("emoji").isJsonNull()) b.emoji(json.get("emoji").getAsString());
        if (json.has("category") && !json.get("category").isJsonNull()) b.category(json.get("category").getAsString());
        if (json.has("status") && !json.get("status").isJsonNull()) b.status(json.get("status").getAsString());
        if (json.has("intro") && !json.get("intro").isJsonNull()) b.intro(json.get("intro").getAsString());
        if (json.has("location") && !json.get("location").isJsonNull()) b.location(json.get("location").getAsString());
        if (json.has("startAt") && !json.get("startAt").isJsonNull()) b.startAt(parseTimestamp(json.get("startAt").getAsString()));
        if (json.has("endAt") && !json.get("endAt").isJsonNull()) b.endAt(parseTimestamp(json.get("endAt").getAsString()));
        if (json.has("active")) b.active(json.get("active").getAsBoolean());
        else b.active(true);
        if (json.has("metadata") && !json.get("metadata").isJsonNull()) b.metadata(json.get("metadata").toString());

        // owner_uuid from body or header
        String ownerUuid = null;
        if (json.has("ownerUuid") && !json.get("ownerUuid").isJsonNull()) {
            ownerUuid = json.get("ownerUuid").getAsString();
        } else if (request != null) {
            String header = request.getHeader("X-User-UUID");
            if (header != null && !header.isEmpty()) ownerUuid = header;
        }
        if (ownerUuid != null) b.ownerUuid(ownerUuid);

        // Sections
        List<EventSectionDTO> sections = new ArrayList<>();
        if (json.has("sections") && json.get("sections").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("sections");
            int pos = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject so = el.getAsJsonObject();
                EventSectionDTO.Builder sb = new EventSectionDTO.Builder();
                if (so.has("id") && !so.get("id").isJsonNull()) sb.id(so.get("id").getAsString());
                if (so.has("heading") && !so.get("heading").isJsonNull()) sb.heading(so.get("heading").getAsString());
                if (so.has("bodyHtml") && !so.get("bodyHtml").isJsonNull()) sb.bodyHtml(so.get("bodyHtml").getAsString());
                else if (so.has("body_html") && !so.get("body_html").isJsonNull()) sb.bodyHtml(so.get("body_html").getAsString());
                sb.position(so.has("position") ? so.get("position").getAsInt() : pos);
                sections.add(sb.build());
                pos++;
            }
        }
        b.sections(sections);

        return b.build();
    }

    private Timestamp parseTimestamp(String isoOrLocal) {
        if (isoOrLocal == null || isoOrLocal.isEmpty()) return null;
        try {
            return Timestamp.from(Instant.parse(isoOrLocal));
        } catch (Exception e) {
            try {
                return Timestamp.valueOf(isoOrLocal.replace('T', ' '));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) {
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        ApiUtils.sendError(response, gson, status, code, message);
    }
}
