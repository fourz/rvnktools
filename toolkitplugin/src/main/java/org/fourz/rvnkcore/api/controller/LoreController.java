package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.ILoreApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for RVNKLore endpoints.
 * Routes HTTP requests to {@link ILoreApiService} provided by the RVNKLore plugin.
 *
 * <p>If the RVNKLore plugin is not loaded (service not registered), all endpoints return 501.</p>
 *
 * @since 1.4.0
 */
public class LoreController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    public LoreController(ILoreApiService ignored, Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the API service from ServiceRegistry.
     * The service is registered by the RVNKLore plugin after RVNKCore starts.
     */
    private ILoreApiService getApiService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(ILoreApiService.class)) {
                return registry.getService(ILoreApiService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        ILoreApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKLore plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<ApiResponse<?>> future;

            if (pathInfo.equals("/") || pathInfo.equals("/entries")) {
                future = apiService.getEntries(ApiUtils.extractQueryParams(req));
            } else if (pathInfo.equals("/entries/search")) {
                String query = req.getParameter("q");
                if (query == null || query.trim().isEmpty()) {
                    sendError(resp, 400, "INVALID_REQUEST", "Search query 'q' is required");
                    return;
                }
                future = apiService.searchEntries(query, ApiUtils.extractQueryParams(req));
            } else if (pathInfo.matches("^/entries/type/[^/]+$")) {
                String type = pathInfo.substring("/entries/type/".length());
                future = apiService.getEntriesByType(type, ApiUtils.extractQueryParams(req));
            } else if (pathInfo.matches("^/entries/[^/]+$")) {
                String id = pathInfo.substring("/entries/".length());
                future = apiService.getEntryById(id);
            } else if (pathInfo.matches("^/player/[^/]+(/collection)?$")) {
                String[] parts = pathInfo.substring(1).split("/");
                future = apiService.getPlayerCollection(parts[1]);
            } else if (pathInfo.equals("/collections")) {
                future = apiService.getCollections();
            } else if (pathInfo.equals("/types")) {
                future = apiService.getTypes();
            } else if (pathInfo.equals("/categories")) {
                future = apiService.getCategories();
            } else if (pathInfo.equals("/stats")) {
                future = apiService.getStats();
            } else if (pathInfo.equals("/health")) {
                future = apiService.getHealthStatus();
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
            sendApiResponse(resp, response);

        } catch (Exception e) {
            logger.error("Error handling Lore API request: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        ILoreApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKLore plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            if (pathInfo.equals("/") || pathInfo.equals("/submit")) {
                String body = ApiUtils.readRequestBody(req);
                ApiResponse<?> response = apiService.submitEntry(body)
                    .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Unknown POST endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling Lore API POST: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_OK);
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
