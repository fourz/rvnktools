package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API controller for authentication endpoints.
 * Mounted at {@code /v1/auth/*}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/auth/verify} — Exchange a one-time magic link token for player data</li>
 *   <li>{@code GET  /v1/auth/session} — Validate an existing session (stub for Phase 2.5)</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class AuthController extends HttpServlet {

    private final AuthTokenStore authTokenStore;
    private final Gson gson;
    private final LogManager logger;

    public AuthController(AuthTokenStore authTokenStore, Gson gson, LogManager logger) {
        this.authTokenStore = authTokenStore;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/verify")) {
                handleVerify(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown auth endpoint: POST " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling auth POST request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Authentication request failed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/session")) {
                handleSession(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND",
                        "Unknown auth endpoint: GET " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling auth GET request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Session validation failed");
        }
    }

    /**
     * POST /v1/auth/verify — Exchange a one-time token for player data.
     * Body: {"token": "uuid-string"}
     * Returns: {uuid, name, groups} or 401
     */
    private void handleVerify(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = ApiUtils.readRequestBody(req);

        // Parse token from JSON body
        String token;
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("token") || json.get("token").isJsonNull()) {
                ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: token");
                return;
            }
            token = json.get("token").getAsString();
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        if (token.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Token must not be empty");
            return;
        }

        // Consume the token (one-time use)
        AuthTokenStore.ConsumeOutcome outcome = authTokenStore.consumeToken(token);

        if (!outcome.isSuccess()) {
            String code;
            String message;
            switch (outcome.result()) {
                case ALREADY_USED -> { code = "TOKEN_ALREADY_USED"; message = "This link has already been used. Run /link login to get a new one."; }
                case EXPIRED -> { code = "TOKEN_EXPIRED"; message = "This link has expired. Run /link login to get a new one."; }
                default -> { code = "INVALID_TOKEN"; message = "Token is invalid or expired"; }
            }
            logger.debug("Token verification failed (" + code + ") from " + ApiUtils.getClientIP(req));
            ApiUtils.sendError(resp, gson, 401, code, message);
            return;
        }

        AuthTokenStore.AuthToken data = outcome.token();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", data.playerUuid().toString());
        result.put("name", data.playerName());
        result.put("groups", data.groups());

        logger.info("Token verified for " + data.playerName() + " from " + ApiUtils.getClientIP(req));
        ApiUtils.sendSuccess(resp, gson, result);
    }

    /**
     * GET /v1/auth/session — Validate an existing JWT session.
     * Stub for Phase 2.5 — returns 501 Not Implemented.
     */
    private void handleSession(HttpServletRequest req, HttpServletResponse resp) {
        ApiUtils.sendError(resp, gson, 501, "NOT_IMPLEMENTED",
                "Session validation is not yet implemented");
    }
}
