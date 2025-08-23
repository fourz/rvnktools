package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.service.WorldService;
import org.fourz.rvnkcore.service.WorldService.WorldPlayerCorrelation;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for world management and tracking operations.
 * Provides comprehensive world metadata access and correlation with player data.
 * 
 * @since 1.0.0
 */
public class WorldController {
    
    private final WorldService worldService;
    private final Gson gson;
    private final LogManager logger;

    public WorldController(WorldService worldService, Gson gson, LogManager logger) {
        this.worldService = worldService;
        this.gson = gson;
        this.logger = logger;
    }

    /**
     * Handles GET /api/worlds - Get all worlds with metadata
     */
    public HttpHandler handleGetAllWorlds() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            worldService.getAllWorlds()
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize worlds response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get all worlds", throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve worlds\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/active - Get only active worlds
     */
    public HttpHandler handleGetActiveWorlds() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            worldService.getActiveWorlds()
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize active worlds response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get active worlds", throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve active worlds\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/with-players - Get worlds with active players
     */
    public HttpHandler handleGetWorldsWithPlayers() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            worldService.getWorldsWithPlayers()
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize worlds with players response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get worlds with players", throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve worlds with players\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/{worldName} - Get specific world by name
     */
    public HttpHandler handleGetWorld() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            
            if (segments.length < 4) {
                sendResponse(exchange, 400, "{\"error\":\"World name is required\"}");
                return;
            }

            String worldName = segments[3]; // /api/worlds/{worldName}

            worldService.getWorld(worldName)
                .thenAccept(worldOpt -> {
                    if (worldOpt.isPresent()) {
                        try {
                            String json = gson.toJson(worldOpt.get());
                            sendResponse(exchange, 200, json);
                        } catch (Exception e) {
                            logger.error("Failed to serialize world response", e);
                            sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                        }
                    } else {
                        sendResponse(exchange, 404, "{\"error\":\"World not found\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get world: " + worldName, throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve world\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/environment/{environment} - Get worlds by environment
     */
    public HttpHandler handleGetWorldsByEnvironment() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            
            if (segments.length < 5) {
                sendResponse(exchange, 400, "{\"error\":\"Environment type is required\"}");
                return;
            }

            String environment = segments[4]; // /api/worlds/environment/{environment}

            worldService.getWorldsByEnvironment(environment)
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize worlds by environment response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get worlds by environment: " + environment, throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve worlds by environment\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/player/{playerUuid} - Get worlds for specific player
     */
    public HttpHandler handleGetWorldsForPlayer() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            
            if (segments.length < 5) {
                sendResponse(exchange, 400, "{\"error\":\"Player UUID is required\"}");
                return;
            }

            String playerUuid = segments[4]; // /api/worlds/player/{playerUuid}

            worldService.getWorldsForPlayer(playerUuid)
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize worlds for player response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get worlds for player: " + playerUuid, throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve worlds for player\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/correlation/{playerUuid} - Get world-player correlation data
     */
    public HttpHandler handleGetWorldPlayerCorrelation() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            
            if (segments.length < 5) {
                sendResponse(exchange, 400, "{\"error\":\"Player UUID is required\"}");
                return;
            }

            String playerUuid = segments[4]; // /api/worlds/correlation/{playerUuid}

            worldService.getWorldPlayerCorrelation(playerUuid)
                .thenAccept(correlations -> {
                    try {
                        String json = gson.toJson(correlations);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize world-player correlation response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get world-player correlation for: " + playerUuid, throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve world-player correlation\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/statistics - Get world statistics
     */
    public HttpHandler handleGetWorldStatistics() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            worldService.getWorldStatistics()
                .thenAccept(statistics -> {
                    try {
                        String json = gson.toJson(statistics);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize world statistics response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get world statistics", throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve world statistics\"}");
                    return null;
                });
        };
    }

    /**
     * Handles GET /api/worlds/recent?limit={limit} - Get recently accessed worlds
     */
    public HttpHandler handleGetRecentWorlds() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            // Parse limit from query parameters
            String query = exchange.getRequestURI().getQuery();
            int limit = 10; // default
            
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "limit".equals(keyValue[0])) {
                        try {
                            limit = Integer.parseInt(keyValue[1]);
                            if (limit < 1 || limit > 100) {
                                limit = 10; // Reset to default if invalid
                            }
                        } catch (NumberFormatException e) {
                            // Use default
                        }
                    }
                }
            }

            final int finalLimit = limit;
            worldService.getRecentlyAccessedWorlds(finalLimit)
                .thenAccept(worlds -> {
                    try {
                        String json = gson.toJson(worlds);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        logger.error("Failed to serialize recent worlds response", e);
                        sendResponse(exchange, 500, "{\"error\":\"Failed to serialize response\"}");
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to get recent worlds", throwable);
                    sendResponse(exchange, 500, "{\"error\":\"Failed to retrieve recent worlds\"}");
                    return null;
                });
        };
    }

    /**
     * Sends an HTTP response with the specified status code and body.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) {
        try {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (IOException e) {
            logger.error("Failed to send HTTP response", e);
        }
    }
}
