package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.WebUIAccessLogDTO;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.database.repository.WebUIAccessRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for WebUI access logging.
 * Mounted at {@code /v1/webui/*}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/webui/access} — Record a WebUI access-log entry. Returns 201 {@code {"id": <id>}}.</li>
 *   <li>{@code GET  /v1/webui/access} — Query access-log entries. Returns {@code {"data":[...],"total":N}}.</li>
 * </ul>
 *
 * <p>The country code is resolved upstream by fourzorg-api (ip-api lookup) and passed in
 * pre-resolved; this controller performs no geo lookup of its own.</p>
 *
 * @since 1.5.9
 */
public class WebUIAccessController extends HttpServlet {

    /** How long to wait on the async DB operation before degrading to a 500/503. */
    private static final int DB_TIMEOUT_SECONDS = 10;

    /** Valid {@code action_type} values; anything else is rejected with 400. */
    private static final Set<String> VALID_ACTION_TYPES = Set.of("LOGIN", "PAGE_VISIT", "ADMIN_ACTION");
    private static final String DEFAULT_ACTION_TYPE = "PAGE_VISIT";

    private final WebUIAccessRepository repository;
    private final Gson gson;
    private final LogManager logger;

    public WebUIAccessController(WebUIAccessRepository repository, Gson gson, LogManager logger) {
        this.repository = repository;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/access")) {
                handleRecordAccess(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown webui endpoint: POST " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling webui POST request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/access")) {
                handleQueryAccess(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown webui endpoint: GET " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling webui GET request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }

    /**
     * POST /v1/webui/access — Record an access-log entry.
     * Body: {@code {"ign":?, "uuid":?, "ip_address":"...", "country_code":?, "page_path":"...", "action_type":?}}
     * {@code ip_address} and {@code page_path} are required; {@code action_type} defaults to PAGE_VISIT.
     */
    private void handleRecordAccess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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

        String ipAddress = getJsonString(json, "ip_address");
        String pagePath = getJsonString(json, "page_path");
        if (ipAddress.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: ip_address");
            return;
        }
        if (pagePath.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: page_path");
            return;
        }

        String actionType = getJsonString(json, "action_type");
        if (actionType.isBlank()) {
            actionType = DEFAULT_ACTION_TYPE;
        }
        if (!VALID_ACTION_TYPES.contains(actionType)) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST",
                    "Invalid action_type: must be one of LOGIN, PAGE_VISIT, ADMIN_ACTION");
            return;
        }

        WebUIAccessLogDTO dto = WebUIAccessLogDTO.builder()
                .ign(nullIfBlank(getJsonString(json, "ign")))
                .uuid(nullIfBlank(getJsonString(json, "uuid")))
                .ipAddress(ipAddress)
                .countryCode(nullIfBlank(getJsonString(json, "country_code")))
                .pagePath(pagePath)
                .actionType(actionType)
                .build();

        try {
            Long id = repository.insert(dto).get(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            logger.debug("Recorded webui access log id=" + id + " (" + dto.getActionType() + ")");
            ApiUtils.sendJson(resp, gson, 201, ApiResponse.success(result));
        } catch (Exception e) {
            logger.error("Failed to record webui access log", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to record access log");
        }
    }

    /**
     * GET /v1/webui/access — Query access-log entries.
     * Query params: {@code ign, country, from, to, limit, action_type}.
     * Response: {@code {"data":[...],"total":N}}.
     */
    private void handleQueryAccess(HttpServletRequest req, HttpServletResponse resp) {
        String ign = req.getParameter("ign");
        String country = req.getParameter("country");
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        String actionType = req.getParameter("action_type");
        int limit = ApiUtils.getIntParam(req, "limit", 100);

        try {
            WebUIAccessRepository.QueryResult qr =
                    repository.query(ign, country, actionType, from, to, limit)
                            .get(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", qr.getData());
            result.put("total", qr.getTotal());
            ApiUtils.sendJson(resp, gson, 200, ApiResponse.success(result));
        } catch (Exception e) {
            logger.error("Failed to query webui access logs", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to query access logs");
        }
    }

    private static String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return "";
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
