package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IBarterShopsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * REST API controller for BarterShops endpoints.
 * Routes HTTP requests to {@link IBarterShopsApiService} provided by the BarterShops plugin.
 *
 * <p>If the BarterShops plugin is not loaded (service not registered), all endpoints return 503.</p>
 *
 * @since 1.4.0
 */
public class BarterShopsController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    private static final Pattern SHOP_BY_ID_PATTERN       = Pattern.compile("^/shops/([^/]+)$");
    private static final Pattern TRADE_BY_ID_PATTERN       = Pattern.compile("^/trades/([^/]+)$");
    private static final Pattern STATS_SHOPS_PATTERN       = Pattern.compile("^/stats/shops/?(.*)$");
    private static final Pattern GROUP_BY_ID_PATTERN       = Pattern.compile("^/groups/(\\d+)$");
    private static final Pattern GROUP_COOWNERS_PATTERN    = Pattern.compile("^/groups/(\\d+)/coowners$");
    private static final Pattern GROUP_COOWNER_UUID_PATTERN = Pattern.compile("^/groups/(\\d+)/coowners/([^/]+)$");

    public BarterShopsController(IBarterShopsApiService ignored, Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the API service from ServiceRegistry.
     * The service is registered by the BarterShops plugin after RVNKCore starts.
     */
    private IBarterShopsApiService getApiService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(IBarterShopsApiService.class)) {
                return registry.getService(IBarterShopsApiService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IBarterShopsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 503, "SERVICE_UNAVAILABLE", "BarterShops plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<ApiResponse<?>> future;

            if (pathInfo.equals("/shops")) {
                future = apiService.getShops(ApiUtils.extractQueryParams(req));
            } else if (pathInfo.equals("/shops/nearby")) {
                future = handleShopsNearby(req, resp);
                if (future == null) return; // error already sent
            } else if (SHOP_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = SHOP_BY_ID_PATTERN.matcher(pathInfo);
                m.matches();
                future = apiService.getShopById(m.group(1));
            } else if (pathInfo.equals("/trades/recent")) {
                future = handleRecentTrades(req, resp);
                if (future == null) return;
            } else if (TRADE_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = TRADE_BY_ID_PATTERN.matcher(pathInfo);
                m.matches();
                future = apiService.getTradeById(m.group(1));
            } else if (pathInfo.equals("/stats")) {
                future = apiService.getServerStats();
            } else if (STATS_SHOPS_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = STATS_SHOPS_PATTERN.matcher(pathInfo);
                m.matches();
                String shopId = m.group(1);
                if (shopId != null && !shopId.isEmpty()) {
                    shopId = shopId.replaceAll("^/|/$", "");
                }
                future = apiService.getShopStats(shopId);
            } else if (pathInfo.equals("/health")) {
                future = apiService.getHealthStatus();
            } else if (pathInfo.equals("/groups")) {
                future = apiService.getGroups(ApiUtils.extractQueryParams(req));
            } else if (GROUP_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = GROUP_BY_ID_PATTERN.matcher(pathInfo);
                m.matches();
                future = apiService.getGroupById(m.group(1));
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
            sendApiResponse(resp, response);

        } catch (Exception e) {
            logger.error("Error handling BarterShops API request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IBarterShopsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 503, "SERVICE_UNAVAILABLE", "BarterShops plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            if (GROUP_COOWNERS_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = GROUP_COOWNERS_PATTERN.matcher(pathInfo);
                m.matches();
                String groupId = m.group(1);

                String body = req.getReader().lines().collect(Collectors.joining());
                @SuppressWarnings("unchecked")
                Map<String, String> bodyMap = gson.fromJson(body, Map.class);
                if (bodyMap == null) {
                    sendError(resp, 400, "INVALID_REQUEST", "Request body is required");
                    return;
                }
                String requesterUuid = bodyMap.get("requesterUuid");
                String coOwnerUuid = bodyMap.get("coOwnerUuid");
                if (requesterUuid == null || coOwnerUuid == null) {
                    sendError(resp, 400, "INVALID_REQUEST", "requesterUuid and coOwnerUuid are required");
                    return;
                }

                ApiResponse<?> response = apiService.addGroupCoOwner(groupId, requesterUuid, coOwnerUuid)
                        .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling BarterShops POST request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws jakarta.servlet.ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handlePatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    private void handlePatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IBarterShopsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 503, "SERVICE_UNAVAILABLE", "BarterShops plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            if (GROUP_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = GROUP_BY_ID_PATTERN.matcher(pathInfo);
                m.matches();
                String groupId = m.group(1);

                String body = req.getReader().lines().collect(Collectors.joining());
                @SuppressWarnings("unchecked")
                Map<String, String> bodyMap = gson.fromJson(body, Map.class);
                if (bodyMap == null) {
                    sendError(resp, 400, "INVALID_REQUEST", "Request body is required");
                    return;
                }
                String requesterUuid = bodyMap.get("requesterUuid");
                String groupName = bodyMap.get("groupName");
                if (requesterUuid == null || groupName == null || groupName.isBlank()) {
                    sendError(resp, 400, "INVALID_REQUEST", "requesterUuid and groupName are required");
                    return;
                }

                ApiResponse<?> response = apiService.renameGroup(groupId, requesterUuid, groupName.trim())
                        .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling BarterShops PATCH request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IBarterShopsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 503, "SERVICE_UNAVAILABLE", "BarterShops plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            if (GROUP_COOWNER_UUID_PATTERN.matcher(pathInfo).matches()) {
                Matcher m = GROUP_COOWNER_UUID_PATTERN.matcher(pathInfo);
                m.matches();
                String groupId = m.group(1);
                String coOwnerUuid = m.group(2);
                String requesterUuid = req.getParameter("requesterUuid");
                if (requesterUuid == null || requesterUuid.isEmpty()) {
                    sendError(resp, 400, "INVALID_REQUEST", "requesterUuid query parameter is required");
                    return;
                }

                ApiResponse<?> response = apiService.removeGroupCoOwner(groupId, coOwnerUuid, requesterUuid)
                        .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling BarterShops DELETE request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private CompletableFuture<ApiResponse<?>> handleShopsNearby(HttpServletRequest req, HttpServletResponse resp) {
        String world = req.getParameter("world");
        String xStr = req.getParameter("x");
        String yStr = req.getParameter("y");
        String zStr = req.getParameter("z");
        String radiusStr = req.getParameter("radius");

        if (world == null || xStr == null || yStr == null || zStr == null) {
            sendError(resp, 400, "INVALID_REQUEST", "Missing required parameters: world, x, y, z");
            return null;
        }

        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            double z = Double.parseDouble(zStr);
            double radius = radiusStr != null ? Double.parseDouble(radiusStr) : 50.0;
            return getApiService().getShopsNearby(world, x, y, z, radius);
        } catch (NumberFormatException e) {
            sendError(resp, 400, "INVALID_REQUEST", "Invalid coordinate format: " + e.getMessage());
            return null;
        }
    }

    private CompletableFuture<ApiResponse<?>> handleRecentTrades(HttpServletRequest req, HttpServletResponse resp) {
        String limitStr = req.getParameter("limit");
        String shopId = req.getParameter("shop");
        String playerUuid = req.getParameter("player");

        int limit = 20;
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                // keep default
            }
        }

        return getApiService().getRecentTrades(limit, shopId, playerUuid);
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
