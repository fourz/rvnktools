package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IQuestsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for RVNKQuests endpoints (#2042).
 * Routes HTTP requests to {@link IQuestsApiService} provided by the RVNKQuests plugin.
 *
 * <p>Quests is not deployed on every tier (it is Event/Dev-only today), so a missing
 * service is the NORMAL state on some servers, not a fault: all endpoints return a
 * clean 503 with a message naming the situation — never a stack trace.</p>
 *
 * @since 1.5.82
 */
public class QuestsController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    public QuestsController(IQuestsApiService ignored, Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the API service from ServiceRegistry.
     * The service is registered by the RVNKQuests plugin after RVNKCore starts.
     */
    private IQuestsApiService getApiService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(IQuestsApiService.class)) {
                return registry.getService(IQuestsApiService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IQuestsApiService apiService = getApiService();
        if (apiService == null) {
            sendServiceUnavailable(resp);
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<ApiResponse<?>> future;

            if (pathInfo.equals("/")) {
                future = apiService.getQuests(ApiUtils.extractQueryParams(req));
            } else if (pathInfo.matches("^/player/[^/]+$")) {
                String uuid = pathInfo.substring("/player/".length());
                future = apiService.getPlayerProgress(uuid);
            } else if (pathInfo.matches("^/[^/]+$")) {
                String questId = pathInfo.substring(1);
                future = apiService.getQuestById(questId);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
            sendApiResponse(resp, response);

        } catch (Exception e) {
            logger.error("Error handling Quests API request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IQuestsApiService apiService = getApiService();
        if (apiService == null) {
            sendServiceUnavailable(resp);
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            if (pathInfo.matches("^/[^/]+/assign$")) {
                // POST /quests/{id}/assign  body: {"playerUuid": "..."}
                String questId = pathInfo.substring(1, pathInfo.length() - "/assign".length());
                String body = ApiUtils.readRequestBody(req);
                ApiResponse<?> response = apiService.assignQuest(questId, body)
                    .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Unknown POST endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling Quests API POST: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * 503, not 501: Quests is absent by design on some tiers (prod today), and 503
     * "service unavailable on this server" states that plainly to a cross-tier caller.
     */
    private void sendServiceUnavailable(HttpServletResponse resp) {
        sendError(resp, 503, "SERVICE_UNAVAILABLE",
            "RVNKQuests is not loaded on this server; the quest API is unavailable here.");
    }

    private void sendApiResponse(HttpServletResponse resp, ApiResponse<?> response) {
        int httpStatus = response.success() ? 200
            : (response.error() != null ? response.error().suggestedHttpStatus() : 400);
        ApiUtils.sendJson(resp, gson, httpStatus, response);
    }

    private void sendError(HttpServletResponse resp, int status, String code, String message) {
        ApiUtils.sendError(resp, gson, status, code, message);
    }
}
