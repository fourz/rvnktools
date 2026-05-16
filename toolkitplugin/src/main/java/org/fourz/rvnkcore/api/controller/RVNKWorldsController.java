package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API controller for RVNKWorlds endpoints.
 * Routes HTTP requests to {@link IRVNKWorldsApiService} provided by the RVNKWorlds plugin.
 *
 * <p>If the RVNKWorlds plugin is not loaded (service not registered), all endpoints return 501.</p>
 *
 * @since 1.4.0
 */
public class RVNKWorldsController extends HttpServlet {

    private final Gson gson;
    private final LogManager logger;

    private static final Pattern WORLDS_PATTERN = Pattern.compile("^/worlds/?$");
    private static final Pattern WORLD_NAME_PATTERN = Pattern.compile("^/worlds/([^/]+)/?$");
    private static final Pattern WORLD_LOAD_PATTERN = Pattern.compile("^/worlds/([^/]+)/load/?$");
    private static final Pattern WORLD_UNLOAD_PATTERN = Pattern.compile("^/worlds/([^/]+)/unload/?$");
    private static final Pattern TEMPLATES_PATTERN = Pattern.compile("^/templates/?$");
    private static final Pattern GROUPS_PATTERN = Pattern.compile("^/groups/?$");
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^/groups/([^/]+)/?$");
    private static final Pattern METRICS_PATTERN = Pattern.compile("^/metrics/?$");
    private static final Pattern HEALTH_PATTERN = Pattern.compile("^/health/?$");

    public RVNKWorldsController(IRVNKWorldsApiService ignored, Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Lazily resolves the API service from ServiceRegistry.
     * The service is registered by the RVNKWorlds plugin after RVNKCore starts.
     */
    private IRVNKWorldsApiService getApiService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(IRVNKWorldsApiService.class)) {
                return registry.getService(IRVNKWorldsApiService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Fall back to Bukkit-backed read-only service when RVNKWorlds is not loaded.
        // See RVNKWorldsFallbackService — keep response shapes in sync with WorldApiEndpointImpl.
        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            apiService = new RVNKWorldsFallbackService();
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<ApiResponse<?>> future;
            Matcher matcher;

            if (WORLDS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listWorlds();
            } else if ((matcher = WORLD_NAME_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getWorld(matcher.group(1));
            } else if (TEMPLATES_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listTemplates();
            } else if (GROUPS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listGroups();
            } else if ((matcher = GROUP_NAME_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getGroup(matcher.group(1));
            } else if (METRICS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.getMetrics();
            } else if (HEALTH_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.getHealthStatus();
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
            sendApiResponse(resp, response);

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API GET: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKWorlds plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<ApiResponse<?>> future;
            Matcher matcher;

            if (WORLDS_PATTERN.matcher(pathInfo).matches()) {
                String body = ApiUtils.readRequestBody(req);
                future = apiService.createWorld(body);
            } else if ((matcher = WORLD_LOAD_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.loadWorld(matcher.group(1));
            } else if ((matcher = WORLD_UNLOAD_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.unloadWorld(matcher.group(1));
            } else if (TEMPLATES_PATTERN.matcher(pathInfo).matches()) {
                String body = ApiUtils.readRequestBody(req);
                future = apiService.createTemplate(body);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            ApiResponse<?> response = future.get(30, TimeUnit.SECONDS);
            sendApiResponse(resp, response);

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API POST: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKWorlds plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            Matcher matcher = WORLD_NAME_PATTERN.matcher(pathInfo);
            if (matcher.matches()) {
                String worldName = matcher.group(1);
                boolean deleteFiles = "true".equalsIgnoreCase(req.getParameter("deleteFiles"));

                ApiResponse<?> response = apiService.deleteWorld(worldName, deleteFiles)
                    .get(30, TimeUnit.SECONDS);
                sendApiResponse(resp, response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API DELETE: " + pathInfo, e);
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
