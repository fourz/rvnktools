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
 * POST /v1/whitelist/add — adds a player to the server whitelist via Bukkit console dispatch.
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

        // Bukkit.dispatchCommand must run on the main thread
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () ->
                future.complete(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + ign))
        );

        try {
            boolean ok = future.get(5, TimeUnit.SECONDS);
            if (ok) {
                logger.info("[whitelist] Added: " + ign);
                ApiUtils.sendSuccess(resp, gson, Map.of("ign", ign));
            } else {
                logger.warning("[whitelist] Command returned false for: " + ign);
                ApiUtils.sendError(resp, gson, 500, "COMMAND_FAILED",
                        "Whitelist command returned failure for: " + ign);
            }
        } catch (TimeoutException e) {
            logger.error("[whitelist] Timed out dispatching whitelist add for: " + ign);
            ApiUtils.sendError(resp, gson, 504, "TIMEOUT", "Whitelist command timed out");
        } catch (Exception e) {
            logger.error("[whitelist] Error dispatching command for: " + ign, e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Failed to execute whitelist command");
        }
    }
}
