package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.fourz.rvnkcore.ApiVersion;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API controller for server health and status.
 * Provides live server metrics from Bukkit — no stale DB data.
 */
public class HealthController extends HttpServlet {
    private final Gson gson;
    private final LogManager logger;

    public HealthController(Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleHealth(resp);
            } else {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND", "Unknown health endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling health request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Health check failed");
        }
    }

    private void handleHealth(HttpServletResponse resp) {
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMB = runtime.maxMemory() / (1024 * 1024);
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "healthy");
        data.put("apiVersion", ApiVersion.API_VERSION);
        data.put("mcVersion", parseMcVersion(Bukkit.getVersion()));
        data.put("version", Bukkit.getVersion());
        data.put("bukkitVersion", Bukkit.getBukkitVersion());
        data.put("maxPlayers", Bukkit.getMaxPlayers());
        data.put("memory", Map.of("used", usedMB, "max", maxMB));
        data.put("uptimeMs", uptimeMs);

        ApiUtils.sendSuccess(resp, gson, data);
    }

    /** "git-Paper-xxx (MC: 1.21.1)" → "1.21.1" */
    private static String parseMcVersion(String raw) {
        int start = raw.indexOf("MC: ");
        if (start >= 0) {
            int end = raw.indexOf(')', start);
            if (end >= 0) return raw.substring(start + 4, end).trim();
        }
        return raw;
    }
}
