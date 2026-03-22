package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for authentication endpoints.
 * Mounted at {@code /v1/auth/*}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /v1/auth/verify} — Exchange a one-time magic link token for player data</li>
 *   <li>{@code POST /v1/auth/session-token} — Generate a short-lived token for QR code session sharing</li>
 *   <li>{@code GET  /v1/auth/session} — Validate an existing session</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class AuthController extends HttpServlet {

    private final AuthTokenStore authTokenStore;
    private final PlayerService playerService;
    private final Gson gson;
    private final LogManager logger;

    public AuthController(AuthTokenStore authTokenStore, PlayerService playerService, Gson gson, LogManager logger) {
        this.authTokenStore = authTokenStore;
        this.playerService = playerService;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/verify")) {
                handleVerify(req, resp);
            } else if (pathInfo != null && pathInfo.equals("/session-token")) {
                handleSessionToken(req, resp);
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
     * POST /v1/auth/session-token — Generate a short-lived auth token for QR code session sharing.
     * Body: {"uuid": "player-uuid"}
     *
     * <p>Allows an already-authenticated user to generate a token that can be
     * encoded in a QR code and scanned on another device (e.g., mobile) to
     * create a new session without requiring the in-game /link command.</p>
     *
     * <p>Uses the same AuthTokenStore as magic-link tokens. Rate limited to
     * prevent token flooding.</p>
     *
     * Returns: { "token": "...", "expiresIn": 300 }
     */
    private void handleSessionToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = ApiUtils.readRequestBody(req);

        String uuidStr;
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("uuid") || json.get("uuid").isJsonNull()) {
                ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required field: uuid");
                return;
            }
            uuidStr = json.get("uuid").getAsString();
        } catch (JsonSyntaxException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid JSON body");
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid UUID format");
            return;
        }

        // Rate limit: reuse AuthTokenStore's per-player rate limiting
        if (authTokenStore.isRateLimited(uuid)) {
            ApiUtils.sendError(resp, gson, 429, "RATE_LIMITED",
                    "Please wait before generating another session token");
            return;
        }

        try {
            // Look up the player to get current name and groups
            Optional<PlayerDTO> optPlayer = playerService.getPlayer(uuid).get(10, TimeUnit.SECONDS);
            if (optPlayer.isEmpty()) {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND", "Player not found");
                return;
            }

            PlayerDTO player = optPlayer.get();

            // Check ban status — don't generate tokens for banned players
            boolean banned = player.isBanned()
                    || Bukkit.getBanList(BanList.Type.NAME).isBanned(player.getCurrentName());
            if (banned) {
                ApiUtils.sendError(resp, gson, 403, "FORBIDDEN", "Cannot generate token for banned player");
                return;
            }

            List<String> groups = player.getGroups() != null ? player.getGroups() : List.of();
            String token = authTokenStore.generateToken(uuid, player.getCurrentName(), groups);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("expiresIn", 300); // 5 min (tokens are 15 min TTL but we advertise 5 for UX)

            logger.info("Session token generated for " + player.getCurrentName()
                    + " (QR login) from " + ApiUtils.getClientIP(req));
            ApiUtils.sendSuccess(resp, gson, result);
        } catch (Exception e) {
            logger.error("Failed to generate session token for " + uuid, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to generate session token");
        }
    }

    /**
     * GET /v1/auth/session — Validate an existing JWT session.
     * Checks player existence, ban status, and resolves current LuckPerms groups.
     *
     * <p>Query params:</p>
     * <ul>
     *   <li>{@code uuid} — Player UUID (required)</li>
     * </ul>
     *
     * <p>Response: {@code { valid: bool, banned: bool, groups: [...], name: "..." }}</p>
     */
    private void handleSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String uuidParam = req.getParameter("uuid");
        if (uuidParam == null || uuidParam.isBlank()) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Missing required query parameter: uuid");
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidParam);
        } catch (IllegalArgumentException e) {
            ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "Invalid UUID format");
            return;
        }

        try {
            Optional<PlayerDTO> optPlayer = playerService.getPlayer(uuid).get(10, TimeUnit.SECONDS);

            if (optPlayer.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("valid", false);
                result.put("banned", false);
                result.put("groups", List.of());
                result.put("name", "");
                ApiUtils.sendSuccess(resp, gson, result);
                return;
            }

            PlayerDTO player = optPlayer.get();

            // Check ban status via Bukkit ban list
            boolean banned = player.isBanned();
            if (!banned) {
                // Also check server ban list by name (covers console bans)
                banned = Bukkit.getBanList(BanList.Type.NAME).isBanned(player.getCurrentName());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", !banned);
            result.put("banned", banned);
            result.put("groups", player.getGroups() != null ? player.getGroups() : List.of());
            result.put("name", player.getCurrentName());
            ApiUtils.sendSuccess(resp, gson, result);

            logger.debug("Session validated for " + player.getCurrentName() + " (banned=" + banned + ")");
        } catch (Exception e) {
            logger.error("Failed to validate session for UUID " + uuid, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Session validation failed");
        }
    }
}
