package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REST controller for server whitelist management.
 * POST /v1/whitelist/add       — add a player to the whitelist
 * DELETE /v1/whitelist/{ign}   — remove a player from the whitelist
 * GET /v1/whitelist/{ign}      — check whether a player is whitelisted
 * Protected by AuthFilter (X-API-Key header). Called by fourzorg-api on application acceptance.
 */
public class WhitelistController extends HttpServlet {
    private final Gson gson;
    private final LogManager logger;
    private final Plugin plugin;

    public WhitelistController(Gson gson, LogManager logger, Plugin plugin) {
        this.gson = gson;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        String ign = extractIgn(req.getPathInfo());
        if (ign == null) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_IGN", "Path must be /v1/whitelist/{ign}");
            return;
        }
        boolean whitelisted = Bukkit.getWhitelistedPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(ign));
        ApiUtils.sendJson(resp, gson, 200, Map.of("ign", ign, "whitelisted", whitelisted));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        try {
            if ("/add".equals(pathInfo)) {
                handleAdd(req, resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND", "Unknown whitelist endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("[whitelist] Unhandled error", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Whitelist operation failed");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        String ign = extractIgn(req.getPathInfo());
        if (ign == null) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_IGN", "Path must be /v1/whitelist/{ign}");
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getOfflinePlayer(ign).setWhitelisted(false);
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
            logger.info("[whitelist] Removed: " + ign);
            ApiUtils.sendJson(resp, gson, 200, Map.of("ign", ign, "removed", true));
        } catch (TimeoutException e) {
            ApiUtils.sendError(resp, gson, 504, "TIMEOUT", "Whitelist operation timed out");
        } catch (Exception e) {
            logger.error("[whitelist] Error removing: " + ign, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to remove from whitelist");
        }
    }

    private void handleAdd(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<?, ?> body;
        try {
            body = gson.fromJson(req.getReader(), Map.class);
        } catch (Exception e) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_JSON", "Request body must be valid JSON");
            return;
        }

        if (body == null || !body.containsKey("ign")) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_FIELD", "Body must contain 'ign'");
            return;
        }

        String ign = body.get("ign").toString().trim();
        if (!ign.matches("[a-zA-Z0-9_]{3,16}")) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_IGN",
                    "IGN must be 3–16 characters (letters, numbers, underscores)");
            return;
        }

        // Must run on the main thread
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getOfflinePlayer(ign).setWhitelisted(true);
            future.complete(null);
        });

        try {
            future.get(5, TimeUnit.SECONDS);
            logger.info("[whitelist] Added: " + ign);
            ApiUtils.sendSuccess(resp, gson, Map.of("ign", ign));
        } catch (TimeoutException e) {
            logger.error("[whitelist] Timed out adding: " + ign);
            ApiUtils.sendError(resp, gson, 504, "TIMEOUT", "Whitelist operation timed out");
        } catch (Exception e) {
            logger.error("[whitelist] Error adding: " + ign, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to add to whitelist");
        }
    }

    private String extractIgn(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) return null;
        String ign = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        return ign.matches("[a-zA-Z0-9_]{3,16}") ? ign : null;
    }
}
