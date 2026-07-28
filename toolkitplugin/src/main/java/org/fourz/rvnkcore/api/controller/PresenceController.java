package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.presence.PresenceService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;

/**
 * REST API controller for inbound cross-server presence snapshots (#1728).
 * Mounted at {@code /v1/presence/*} and guarded by the existing AuthFilter (X-API-Key on {@code /v1/*}).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/presence/inbound} — Accept a peer server's online-roster snapshot</li>
 * </ul>
 *
 * <p>Mirrors {@link ChatRelayController}; the {@link PresenceService} is resolved lazily from the
 * ServiceRegistry at request time.</p>
 *
 * @since 1.5.37
 */
public class PresenceController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    /**
     * Creates a new PresenceController.
     *
     * @param gson   JSON serializer
     * @param logger LogManager instance
     */
    public PresenceController(Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    private PresenceService getService() {
        return RVNKCore.getServiceSafe(PresenceService.class);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo != null && pathInfo.equals("/inbound")) {
                handleInbound(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown presence endpoint: POST " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling presence POST request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    private void handleInbound(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PresenceService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Presence service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);
        PresenceDTO dto;
        try {
            dto = gson.fromJson(body, PresenceDTO.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        if (dto == null || dto.getOriginServerId() == null || dto.getOriginServerId().isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: originServerId");
            return;
        }

        service.receiveInbound(dto);
        ApiUtils.sendSuccess(resp, gson, "Roster accepted");
    }
}
