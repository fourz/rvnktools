package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.chatrelay.ChatMessageBuffer;
import org.fourz.rvnkcore.service.chatrelay.ChatRelayService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for inbound cross-server chat relay messages.
 * Mounted at {@code /v1/chat/*} and guarded by the existing AuthFilter (X-API-Key on {@code /v1/*}).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/chat/inbound} — Accept a relayed chat message from a peer server</li>
 *   <li>{@code POST /v1/chat/broadcast} — Inject a bot/persona line that mirrors to this server and
 *       every peer (used by the chatbot; #1769). Body: {@code {message, senderName?, label?}}</li>
 *   <li>{@code POST /v1/chat/server} — Post a bot line into the SERVER room: this server only,
 *       never relayed (#1777). Body: {@code {message, senderName?}}</li>
 *   <li>{@code POST /v1/chat/world} — Post a bot line into the WORLD room: scoped to one world,
 *       relayed to peers only when world-relay allows (#1777). Body: {@code {message, senderName?, world}}</li>
 *   <li>{@code GET /v1/chat/recent} — Cursor-polled tail of recent chat, so a client can read what was
 *       said without scraping the console log (#1870).
 *       Query: {@code since, boot, limit, room, world}</li>
 * </ul>
 *
 * <p>The {@link ChatRelayService} is resolved lazily from the ServiceRegistry at request time,
 * mirroring {@link NotificationController}.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    /**
     * Creates a new ChatRelayController.
     *
     * @param gson   JSON serializer
     * @param logger LogManager instance
     */
    public ChatRelayController(Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the ChatRelayService from ServiceRegistry.
     *
     * @return The service instance, or null if unavailable
     */
    private ChatRelayService getService() {
        return RVNKCore.getServiceSafe(ChatRelayService.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/recent")) {
                handleRecent(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown chat relay endpoint: GET " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling chat relay GET request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    /**
     * Serves the recent-chat tail for cursor polling (#1870).
     *
     * <p>{@code GET /v1/chat/recent?since=&boot=&limit=&room=&world=}. The client sends the
     * {@code seq} and {@code bootId} it last saw and receives everything after that {@code seq}.
     * A {@code bootId} that no longer matches — or a cursor the buffer has already evicted past —
     * comes back as {@code stale:true} with the buffer head, so the client resyncs instead of
     * silently waiting on a sequence position that will never arrive.</p>
     *
     * <p>Read-only: this serves what the relay already saw. It cannot expose private messages,
     * because {@code /msg}, {@code /tell} and {@code /r} never enter the chatroom path and so are
     * never recorded.</p>
     */
    private void handleRecent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ChatRelayService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Chat relay service not available");
            return;
        }

        long since;
        try {
            String raw = req.getParameter("since");
            since = (raw == null || raw.isBlank()) ? 0L : Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "since must be an integer");
            return;
        }

        String boot = trimToNull(req.getParameter("boot"));
        int limit = ApiUtils.getIntParam(req, "limit", ChatMessageBuffer.MAX_LIMIT);
        String room = trimToNull(req.getParameter("room"));
        String world = trimToNull(req.getParameter("world"));

        ChatMessageBuffer.Page page = service.getBuffer().since(boot, since, limit, room, world);

        List<Map<String, Object>> messages = new ArrayList<>(page.getMessages().size());
        for (ChatMessageBuffer.Entry entry : page.getMessages()) {
            messages.add(toWire(entry));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bootId", page.getBootId());
        data.put("seq", page.getSeq());
        data.put("stale", page.isStale());
        data.put("messages", messages);

        ApiUtils.sendSuccess(resp, gson, data);
    }

    /**
     * Flattens a buffer entry to the wire shape. {@code seq} is a buffer concern rather than part of
     * {@link ChatMessageDTO}, so the two are merged here rather than polluting the relay payload.
     */
    private Map<String, Object> toWire(ChatMessageBuffer.Entry entry) {
        ChatMessageDTO dto = entry.getDto();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", entry.getSeq());
        m.put("msgId", dto.getMsgId());
        m.put("timestamp", dto.getTimestamp());
        m.put("senderName", dto.getSenderName());
        m.put("senderUuid", dto.getSenderUuid());
        m.put("message", dto.getMessage());
        m.put("room", dto.getRoom());
        m.put("world", dto.getWorld());
        m.put("originServerId", dto.getOriginServerId());
        m.put("serverLabel", dto.getServerLabel());
        return m;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/inbound")) {
                handleInbound(req, resp);
            } else if (pathInfo != null && pathInfo.equals("/broadcast")) {
                handleBroadcast(req, resp);
            } else if (pathInfo != null && pathInfo.equals("/server")) {
                handleRoom(req, resp, "SERVER");
            } else if (pathInfo != null && pathInfo.equals("/world")) {
                handleRoom(req, resp, "WORLD");
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown chat relay endpoint: POST " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling chat relay POST request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    private void handleInbound(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ChatRelayService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Chat relay service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);

        ChatMessageDTO dto;
        try {
            dto = gson.fromJson(body, ChatMessageDTO.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        if (dto == null || dto.getMsgId() == null || dto.getMsgId().isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: msgId");
            return;
        }

        service.receiveInbound(dto);
        ApiUtils.sendSuccess(resp, gson, "Message accepted");
    }

    /** Upper bound on the serialized {@code components} JSON, guarding the console tellraw dispatch. */
    private static final int MAX_COMPONENTS_LEN = 8000;

    /**
     * Handles {@code POST /v1/chat/broadcast}: injects a bot/persona line that mirrors to this server
     * and every peer (#1769, #1773). The caller (chatbot) POSTs {@code {message?, senderName?, label?,
     * components?}} to any one tier; the mesh distributes. At least one of {@code message} or {@code
     * components} is required — {@code components} (a tellraw JSON array/object) renders styled via
     * {@code tellraw @a}; {@code message} is the flat-text fallback.
     */
    private void handleBroadcast(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ChatRelayService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Chat relay service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);

        JsonObject obj;
        try {
            obj = gson.fromJson(body, JsonObject.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        String message = optString(obj, "message");

        // Optional styled components: must be a tellraw JSON array/object. Re-serialize compact,
        // strip newlines (single-line console dispatch), and bound the length.
        String components = null;
        if (obj != null && obj.has("components") && !obj.get("components").isJsonNull()) {
            JsonElement compEl = obj.get("components");
            if (!compEl.isJsonArray() && !compEl.isJsonObject()) {
                ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                        "components must be a tellraw JSON array or object");
                return;
            }
            components = gson.toJson(compEl).replace("\r", "").replace("\n", "");
            if (components.length() > MAX_COMPONENTS_LEN) {
                ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                        "components too large (max " + MAX_COMPONENTS_LEN + " chars)");
                return;
            }
        }

        boolean hasMessage = message != null && !message.isBlank();
        boolean hasComponents = components != null && !components.isBlank();
        if (!hasMessage && !hasComponents) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                    "Provide 'message' and/or 'components'");
            return;
        }

        service.broadcast(message, optString(obj, "senderName"), optString(obj, "label"), components);
        ApiUtils.sendSuccess(resp, gson, "Broadcast accepted");
    }

    /**
     * Handles {@code POST /v1/chat/server} and {@code POST /v1/chat/world} (#1777): posts a bot/persona
     * line into a chat room, delivered by the RVNKEvents chatroom so it renders exactly like player room
     * chat (per-viewer display modes, world scoping, WORLD-relay allowlist).
     *
     * <p>Body: {@code {message, senderName?}} plus {@code world} for the WORLD route. Room posts are
     * plain text — {@code components} is rejected here and belongs on {@code /broadcast}.</p>
     *
     * @param room {@code SERVER} or {@code WORLD}
     */
    private void handleRoom(HttpServletRequest req, HttpServletResponse resp, String room)
            throws IOException {
        ChatRelayService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Chat relay service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);

        JsonObject obj;
        try {
            obj = gson.fromJson(body, JsonObject.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        if (obj != null && obj.has("components") && !obj.get("components").isJsonNull()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                    "Room posts are plain text — use 'message'. Styled 'components' is supported on "
                    + "POST /v1/chat/broadcast only.");
            return;
        }

        String message = optString(obj, "message");
        if (message == null || message.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: message");
            return;
        }

        String world = optString(obj, "world");
        if ("WORLD".equals(room) && (world == null || world.isBlank())) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                    "Missing required field for a WORLD post: world");
            return;
        }

        service.injectRoom(room, world, optString(obj, "senderName"), message);
        ApiUtils.sendSuccess(resp, gson, room + " post accepted");
    }

    /** Reads an optional, non-null string member from a JSON object, or null when absent/null. */
    private static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }
}
