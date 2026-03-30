package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PushSubscriptionDTO;
import org.fourz.rvnkcore.api.service.PushSubscriptionService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for push notification subscription management.
 * Mounted at {@code /v1/notifications/*}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/notifications/subscribe} — Store a push subscription</li>
 *   <li>{@code DELETE /v1/notifications/subscribe} — Remove a push subscription by endpoint</li>
 *   <li>{@code GET /v1/notifications/subscriptions} — List all subscriptions (broadcast)</li>
 *   <li>{@code GET /v1/notifications/subscriptions/{uuid}} — List subscriptions for a player</li>
 * </ul>
 *
 * @since 1.6.0
 */
public class NotificationController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    public NotificationController(PushSubscriptionService ignored, Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the PushSubscriptionService from ServiceRegistry.
     */
    private PushSubscriptionService getService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(PushSubscriptionService.class)) {
                return registry.getService(PushSubscriptionService.class);
            }
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/subscribe")) {
                handleSubscribe(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown notification endpoint: POST " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling notification POST request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/subscribe")) {
                handleUnsubscribe(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown notification endpoint: DELETE " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling notification DELETE request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/subscriptions") || pathInfo.equals("/subscriptions/")) {
                handleGetAllSubscriptions(resp);
            } else if (pathInfo.startsWith("/subscriptions/")) {
                String uuid = pathInfo.substring("/subscriptions/".length());
                handleGetPlayerSubscriptions(uuid, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown notification endpoint: GET " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling notification GET request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    private void handleSubscribe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PushSubscriptionService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Push notification service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);

        JsonObject json;
        try {
            json = gson.fromJson(body, JsonObject.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        if (json == null) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Empty request body");
            return;
        }

        String uuid = getJsonString(json, "uuid");
        String endpoint = getJsonString(json, "endpoint");
        String p256dh = getJsonString(json, "p256dh");
        String authKey = getJsonString(json, "authKey");

        if (uuid.isBlank() || endpoint.isBlank() || p256dh.isBlank() || authKey.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                    "Missing required fields: uuid, endpoint, p256dh, authKey");
            return;
        }

        try {
            PushSubscriptionDTO dto = new PushSubscriptionDTO(uuid, endpoint, p256dh, authKey);
            service.saveSubscription(dto).get(10, TimeUnit.SECONDS);
            logger.info("Push subscription stored for " + uuid);
            ApiUtils.sendSuccess(resp, gson, "Subscription stored");
        } catch (Exception e) {
            logger.error("Failed to store push subscription", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to store subscription");
        }
    }

    private void handleUnsubscribe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PushSubscriptionService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Push notification service not available");
            return;
        }

        String body = ApiUtils.readRequestBody(req);

        JsonObject json;
        try {
            json = gson.fromJson(body, JsonObject.class);
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        String endpoint = json != null ? getJsonString(json, "endpoint") : "";
        if (endpoint.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: endpoint");
            return;
        }

        try {
            service.deleteByEndpoint(endpoint).get(10, TimeUnit.SECONDS);
            logger.info("Push subscription removed for endpoint");
            ApiUtils.sendSuccess(resp, gson, "Subscription removed");
        } catch (Exception e) {
            logger.error("Failed to remove push subscription", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to remove subscription");
        }
    }

    private void handleGetAllSubscriptions(HttpServletResponse resp) {
        PushSubscriptionService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Push notification service not available");
            return;
        }

        try {
            List<PushSubscriptionDTO> subs = service.getAllSubscriptions()
                    .get(10, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(resp, gson, subs);
        } catch (Exception e) {
            logger.error("Failed to fetch subscriptions", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to fetch subscriptions");
        }
    }

    private void handleGetPlayerSubscriptions(String uuid, HttpServletResponse resp) {
        PushSubscriptionService service = getService();
        if (service == null) {
            ApiUtils.sendError(resp, gson, 503, "SERVICE_UNAVAILABLE", "Push notification service not available");
            return;
        }

        try {
            List<PushSubscriptionDTO> subs = service.getSubscriptionsByPlayer(uuid)
                    .get(10, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(resp, gson, subs);
        } catch (Exception e) {
            logger.error("Failed to fetch subscriptions for " + uuid, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to fetch subscriptions");
        }
    }

    private static String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return "";
    }
}
