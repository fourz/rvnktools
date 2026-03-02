package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.util.log.LogManager;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for world management and tracking operations.
 * Provides comprehensive world metadata access and correlation with player data.
 * 
 * @since 1.0.0
 */
public class WorldController extends HttpServlet {
    
    private final WorldService worldService;
    private final Gson gson;
    private final LogManager logger;

    public WorldController(WorldService worldService, Gson gson, LogManager logger) {
        this.worldService = worldService;
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
            } else if (pathInfo.startsWith("/") && !pathInfo.contains("/")) {
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
        worldService.getAllWorlds()
            .thenAccept(worlds -> {
                try {
                    String json = gson.toJson(worlds);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize worlds response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get all worlds", throwable);
                sendErrorResponse(response, 500, "Failed to retrieve worlds");
                return null;
            });
    }

    /**
     * Handles GET /api/v1/worlds/with-players - Get worlds that currently have players
     */
    private void handleGetWorldsWithPlayers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getWorldsWithPlayers()
            .thenAccept(worlds -> {
                try {
                    String json = gson.toJson(worlds);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize worlds with players response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get worlds with players", throwable);
                sendErrorResponse(response, 500, "Failed to retrieve worlds with players");
                return null;
            });
    }

    /**
     * Handles GET /api/v1/worlds/statistics - Get overall world statistics
     */
    private void handleGetWorldStatistics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getAllWorlds()
            .thenAccept(worlds -> {
                try {
                    // Calculate basic statistics from all worlds
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalWorlds", worlds.size());
                    stats.put("activeWorlds", worlds.stream().filter(w -> w.getPlayerCount() != null && w.getPlayerCount() > 0).count());
                    stats.put("totalPlayers", worlds.stream().mapToInt(w -> w.getPlayerCount() != null ? w.getPlayerCount() : 0).sum());
                    
                    String json = gson.toJson(stats);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize world statistics response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get world statistics", throwable);
                sendErrorResponse(response, 500, "Failed to retrieve world statistics");
                return null;
            });
    }

    /**
     * Handles GET /api/v1/worlds/environment/{environment} - Get worlds by environment type
     */
    private void handleGetWorldsByEnvironment(String environment, HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getWorldsByEnvironment(environment)
            .thenAccept(worlds -> {
                try {
                    String json = gson.toJson(worlds);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize worlds by environment response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get worlds by environment: " + environment, throwable);
                sendErrorResponse(response, 500, "Failed to retrieve worlds by environment");
                return null;
            });
    }

    /**
     * Handles GET /api/v1/worlds/player/{playerUuid} - Get worlds visited by specific player
     */
    private void handleGetWorldsForPlayer(String playerUuid, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // For now, return empty list since this requires PlayerWorld correlation
        try {
            String json = gson.toJson(java.util.Collections.emptyList());
            sendResponse(response, json);
        } catch (Exception e) {
            logger.error("Failed to serialize worlds for player response", e);
            sendErrorResponse(response, 500, "Failed to serialize response");
        }
    }

    /**
     * Handles GET /api/v1/worlds/correlation/{playerUuid} - Get world-player correlation data
     */
    private void handleGetWorldPlayerCorrelation(String playerUuid, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // For now, return empty correlation data
        try {
            Map<String, Object> correlationData = new HashMap<>();
            correlationData.put("playerUuid", playerUuid);
            correlationData.put("worldsVisited", 0);
            correlationData.put("totalPlaytime", 0);
            correlationData.put("worlds", java.util.Collections.emptyList());
            
            String json = gson.toJson(correlationData);
            sendResponse(response, json);
        } catch (Exception e) {
            logger.error("Failed to serialize correlation data response", e);
            sendErrorResponse(response, 500, "Failed to serialize response");
        }
    }

    /**
     * Handles GET /api/v1/worlds/recent - Get recently accessed worlds
     */
    private void handleGetRecentWorlds(HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getRecentlyAccessedWorlds(10)
            .thenAccept(worlds -> {
                try {
                    String json = gson.toJson(worlds);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize recent worlds response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get recent worlds", throwable);
                sendErrorResponse(response, 500, "Failed to retrieve recent worlds");
                return null;
            });
    }

    /**
     * Handles GET /api/worlds/name/{name} - Get world by name
     */
    private void handleGetWorldByName(String worldName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getWorld(worldName)
            .thenAccept(world -> {
                if (world.isPresent()) {
                    try {
                        String json = gson.toJson(world.get());
                        sendResponse(response, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize world response", e);
                        sendErrorResponse(response, 500, "Failed to serialize response");
                    }
                } else {
                    sendErrorResponse(response, 404, "World not found with name: " + worldName);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get world by name: " + worldName, throwable);
                sendErrorResponse(response, 500, "Failed to retrieve world");
                return null;
            });
    }

    /**
     * Handles GET /api/worlds/active - Get active worlds only
     */
    private void handleGetActiveWorlds(HttpServletRequest request, HttpServletResponse response) throws IOException {
        worldService.getActiveWorlds()
            .thenAccept(worlds -> {
                try {
                    String json = gson.toJson(worlds);
                    sendResponse(response, json);
                } catch (Exception e) {
                    logger.error("Failed to serialize active worlds response", e);
                    sendErrorResponse(response, 500, "Failed to serialize response");
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to get active worlds", throwable);
                sendErrorResponse(response, 500, "Failed to retrieve active worlds");
                return null;
            });
    }

    /**
     * Sends a successful JSON response
     */
    private void sendResponse(HttpServletResponse response, String json) {
        try {
            response.setStatus(200);
            PrintWriter writer = response.getWriter();
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to send response", e);
        }
    }

    /**
     * Sends an error response with specified status code and message
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
        try {
            response.setStatus(statusCode);
            PrintWriter writer = response.getWriter();
            writer.write("{\"error\":\"" + message + "\"}");
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to send error response", e);
        }
    }
}
