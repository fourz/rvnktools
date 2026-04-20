package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.ApiVersion;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // Server info
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "healthy");
        data.put("apiVersion", ApiVersion.API_VERSION);
        data.put("mcVersion", parseMcVersion(Bukkit.getVersion()));
        data.put("version", Bukkit.getVersion());
        data.put("bukkitVersion", Bukkit.getBukkitVersion());
        data.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        data.put("maxPlayers", Bukkit.getMaxPlayers());
        data.put("memory", Map.of("used", usedMB, "max", maxMB));

        // Per-world live player counts
        List<Map<String, Object>> worlds = Bukkit.getWorlds().stream()
                .map(world -> {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("name", world.getName());
                    w.put("environment", world.getEnvironment().toString());
                    w.put("playerCount", world.getPlayers().size());
                    w.put("players", world.getPlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                    return w;
                })
                .collect(Collectors.toList());
        data.put("worlds", worlds);

        // TPS (Paper API via reflection — not in Spigot API)
        try {
            java.lang.reflect.Method getTPS = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(Bukkit.getServer());
            data.put("tps", Map.of(
                    "1m", Math.round(tps[0] * 100.0) / 100.0,
                    "5m", Math.round(tps[1] * 100.0) / 100.0,
                    "15m", Math.round(tps[2] * 100.0) / 100.0
            ));
        } catch (Exception e) {
            // TPS not available on non-Paper servers
        }

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
