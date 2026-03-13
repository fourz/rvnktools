package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST API controller for world management and tracking operations.
 * Provides comprehensive world metadata access and correlation with player data.
 * 
 * @since 1.0.0
 */
public class WorldController extends HttpServlet {
    
    private final WorldService worldService;
    private final PlayerWorldService playerWorldService;
    private final Gson gson;
    private final LogManager logger;

    public WorldController(WorldService worldService, PlayerWorldService playerWorldService, Gson gson, LogManager logger) {
        this.worldService = worldService;
        this.playerWorldService = playerWorldService;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // GET /api/v1/worlds - Get all worlds with metadata
                handleGetAllWorlds(request, response);
            } else if (pathInfo.equals("/active")) {
                // GET /api/v1/worlds/active - Get active worlds only
                handleGetActiveWorlds(request, response);
            } else if (pathInfo.equals("/with-players")) {
                // GET /api/v1/worlds/with-players - Get worlds that have players
                handleGetWorldsWithPlayers(request, response);
            } else if (pathInfo.equals("/statistics")) {
                // GET /api/v1/worlds/statistics - Get world statistics
                handleGetWorldStatistics(request, response);
            } else if (pathInfo.startsWith("/environment/")) {
                // GET /api/v1/worlds/environment/{environment} - Get worlds by environment
                String environment = pathInfo.substring(13); // Remove "/environment/"
                handleGetWorldsByEnvironment(environment, request, response);
            } else if (pathInfo.startsWith("/player/")) {
                // GET /api/v1/worlds/player/{playerUuid} - Get worlds for player
                String playerUuid = pathInfo.substring(8); // Remove "/player/"
                handleGetWorldsForPlayer(playerUuid, request, response);
            } else if (pathInfo.startsWith("/correlation/")) {
                // GET /api/v1/worlds/correlation/{playerUuid} - Get world-player correlation
                String playerUuid = pathInfo.substring(13); // Remove "/correlation/"
                handleGetWorldPlayerCorrelation(playerUuid, request, response);
            } else if (pathInfo.startsWith("/recent")) {
                // GET /api/v1/worlds/recent - Get recently accessed worlds
                handleGetRecentWorlds(request, response);
            } else if (!pathInfo.substring(1).contains("/")) {
                // GET /api/v1/worlds/{worldName} - Get world by name (direct world name)
                String worldName = pathInfo.substring(1); // Remove leading "/"
                handleGetWorldByName(worldName, request, response);
            } else {
                sendErrorResponse(response, 404, "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error processing world API request: " + pathInfo, e);
            sendErrorResponse(response, 500, "Internal server error");
        }
    }

    /**
     * Handles GET /api/worlds - Get all worlds with metadata
     */
    private void handleGetAllWorlds(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var worlds = worldService.getAllWorlds().get(30, TimeUnit.SECONDS);
            sendResponse(response, worlds);
        } catch (Exception e) {
            logger.error("Failed to get all worlds", e);
            sendErrorResponse(response, 500, "Failed to retrieve worlds");
        }
    }

    /**
     * Handles GET /api/v1/worlds/with-players - Get worlds that currently have players
     */
    private void handleGetWorldsWithPlayers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var worlds = worldService.getWorldsWithPlayers().get(30, TimeUnit.SECONDS);
            sendResponse(response, worlds);
        } catch (Exception e) {
            logger.error("Failed to get worlds with players", e);
            sendErrorResponse(response, 500, "Failed to retrieve worlds with players");
        }
    }

    /**
     * Handles GET /api/v1/worlds/statistics - Get overall world statistics
     * Uses live Bukkit data for player counts, not stale DB values.
     */
    private void handleGetWorldStatistics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<World> loadedWorlds = Bukkit.getWorlds();
            int totalOnline = Bukkit.getOnlinePlayers().size();

            List<Map<String, Object>> worldStats = loadedWorlds.stream()
                    .map(w -> {
                        Map<String, Object> ws = new HashMap<>();
                        ws.put("name", w.getName());
                        ws.put("environment", w.getEnvironment().toString());
                        ws.put("playerCount", w.getPlayers().size());
                        return ws;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalWorlds", loadedWorlds.size());
            stats.put("activeWorlds", loadedWorlds.stream().filter(w -> !w.getPlayers().isEmpty()).count());
            stats.put("totalPlayers", totalOnline);
            stats.put("maxPlayers", Bukkit.getMaxPlayers());
            stats.put("worlds", worldStats);
            sendResponse(response, stats);
        } catch (Exception e) {
            logger.error("Failed to get world statistics", e);
            sendErrorResponse(response, 500, "Failed to retrieve world statistics");
        }
    }

    /**
     * Handles GET /api/v1/worlds/environment/{environment} - Get worlds by environment type
     */
    private void handleGetWorldsByEnvironment(String environment, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var worlds = worldService.getWorldsByEnvironment(environment).get(30, TimeUnit.SECONDS);
            sendResponse(response, worlds);
        } catch (Exception e) {
            logger.error("Failed to get worlds by environment: " + environment, e);
            sendErrorResponse(response, 500, "Failed to retrieve worlds by environment");
        }
    }

    /**
     * Handles GET /api/v1/worlds/player/{playerUuid} - Get worlds visited by specific player
     */
    private void handleGetWorldsForPlayer(String playerUuid, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            List<String> worlds = playerWorldService.getPlayerVisitedWorlds(uuid).get(15, TimeUnit.SECONDS);
            Map<String, Object> result = new HashMap<>();
            result.put("playerUuid", playerUuid);
            result.put("worldCount", worlds.size());
            result.put("worlds", worlds);
            sendResponse(response, result);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(response, 400, "Invalid UUID format: " + playerUuid);
        } catch (Exception e) {
            logger.error("Failed to get worlds for player: " + playerUuid, e);
            sendErrorResponse(response, 500, "Failed to retrieve worlds for player");
        }
    }

    /**
     * Handles GET /api/v1/worlds/correlation/{playerUuid} - Get world-player correlation data
     */
    private void handleGetWorldPlayerCorrelation(String playerUuid, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            List<PlayerWorldDataDTO> allData = playerWorldService.getAllPlayerWorldData(uuid).get(15, TimeUnit.SECONDS);
            List<PlayerWorldDataDTO> mostVisited = playerWorldService.getPlayerMostVisitedWorlds(uuid, 5).get(15, TimeUnit.SECONDS);

            long totalPlaytime = allData.stream().mapToLong(PlayerWorldDataDTO::getPlaytimeSeconds).sum();
            int totalDeaths = allData.stream().mapToInt(PlayerWorldDataDTO::getDeathCount).sum();
            int totalVisits = allData.stream().mapToInt(PlayerWorldDataDTO::getVisitCount).sum();
            String favoriteWorld = mostVisited.isEmpty() ? null : mostVisited.get(0).getWorldName();

            Map<String, Object> correlationData = new HashMap<>();
            correlationData.put("playerUuid", playerUuid);
            correlationData.put("worldsVisited", allData.size());
            correlationData.put("totalPlaytimeSeconds", totalPlaytime);
            correlationData.put("totalDeaths", totalDeaths);
            correlationData.put("totalVisits", totalVisits);
            correlationData.put("favoriteWorld", favoriteWorld);
            correlationData.put("worlds", allData.stream().map(d -> {
                Map<String, Object> world = new HashMap<>();
                world.put("worldName", d.getWorldName());
                world.put("visitCount", d.getVisitCount());
                world.put("playtimeSeconds", d.getPlaytimeSeconds());
                world.put("deathCount", d.getDeathCount());
                world.put("lastVisit", d.getLastVisit());
                return world;
            }).collect(Collectors.toList()));
            sendResponse(response, correlationData);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(response, 400, "Invalid UUID format: " + playerUuid);
        } catch (Exception e) {
            logger.error("Failed to get world-player correlation: " + playerUuid, e);
            sendErrorResponse(response, 500, "Failed to retrieve world-player correlation");
        }
    }

    /**
     * Handles GET /api/v1/worlds/recent - Get recently accessed worlds
     */
    private void handleGetRecentWorlds(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var worlds = worldService.getRecentlyAccessedWorlds(10).get(30, TimeUnit.SECONDS);
            sendResponse(response, worlds);
        } catch (Exception e) {
            logger.error("Failed to get recent worlds", e);
            sendErrorResponse(response, 500, "Failed to retrieve recent worlds");
        }
    }

    /**
     * Handles GET /api/worlds/name/{name} - Get world by name
     */
    private void handleGetWorldByName(String worldName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var world = worldService.getWorld(worldName).get(30, TimeUnit.SECONDS);
            if (world.isPresent()) {
                sendResponse(response, world.get());
            } else {
                sendErrorResponse(response, 404, "World not found with name: " + worldName);
            }
        } catch (Exception e) {
            logger.error("Failed to get world by name: " + worldName, e);
            sendErrorResponse(response, 500, "Failed to retrieve world");
        }
    }

    /**
     * Handles GET /api/worlds/active - Get active worlds only
     */
    private void handleGetActiveWorlds(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var worlds = worldService.getActiveWorlds().get(30, TimeUnit.SECONDS);
            sendResponse(response, worlds);
        } catch (Exception e) {
            logger.error("Failed to get active worlds", e);
            sendErrorResponse(response, 500, "Failed to retrieve active worlds");
        }
    }

    private void sendResponse(HttpServletResponse response, Object data) {
        ApiUtils.sendSuccess(response, gson, data);
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
        String code = switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        ApiUtils.sendError(response, gson, statusCode, code, message);
    }
}
