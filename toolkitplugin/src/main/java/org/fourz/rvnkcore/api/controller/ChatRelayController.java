package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.chatrelay.ChatRelayService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;

/**
 * REST API controller for inbound cross-server chat relay messages.
 * Mounted at {@code /v1/chat/*} and guarded by the existing AuthFilter (X-API-Key on {@code /v1/*}).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/chat/inbound} — Accept a relayed chat message from a peer server</li>
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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/inbound")) {
                handleInbound(req, resp);
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
}
