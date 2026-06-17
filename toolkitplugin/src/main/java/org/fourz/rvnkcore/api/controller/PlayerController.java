package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.model.request.GroupUpdateRequest;
import org.fourz.rvnkcore.api.model.request.LocationUpdateRequest;
import org.fourz.rvnkcore.api.model.response.PagedResponse;
import org.fourz.rvnkcore.api.model.response.PlayerNameHistoryResponse;
import org.fourz.rvnkcore.api.model.response.PlayerResponse;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.model.response.PlayerWorldDataResponse;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST API controller for player operations.
 * Implements comprehensive CRUD operations for player data.
 */
public class PlayerController extends HttpServlet {
    private final PlayerService playerService;
    private final PlayerWorldService playerWorldService;
    private final Gson gson;
    private final LogManager logger;
    private final org.fourz.rvnkcore.api.server.jetty.LiveDataCache liveDataCache;

    public PlayerController(PlayerService playerService, PlayerWorldService playerWorldService,
                            Gson gson, LogManager logger,
                            org.fourz.rvnkcore.api.server.jetty.LiveDataCache liveDataCache) {
        this.playerService = playerService;
        this.playerWorldService = playerWorldService;
        this.gson = gson;
        this.logger = logger;
        this.liveDataCache = liveDataCache;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = ApiUtils.getClientIP(req);
        String queryString = req.getQueryString();
        
        // Move request logging to debug level to reduce verbosity
        logger.debug("PlayerController GET request: " + pathInfo + 
                   (queryString != null ? "?" + queryString : "") + " from IP: " + clientIP);
        
        resp.setContentType("application/json");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleGetAllPlayers(req, resp);
            } else {
                String[] parts = pathInfo.substring(1).split("/");
                String endpoint = parts[0].toLowerCase();

                switch (endpoint) {
                    case "name":
                        // Handle direct /player/name/{name} or /player/name/{name}/history endpoints
                        handleDirectPlayerNameEndpoint(parts, resp);
                        break;
                    case "online":
                        handleGetOnlinePlayers(resp);
                        break;
                    case "recent":
                        handleGetRecentPlayers(req, resp);
                        break;
                    case "group":
                        if (parts.length > 1) {
                            handleGetPlayersByGroup(parts[1], resp);
                        } else {
                            sendError(resp, 400, "Group name required");
                        }
                        break;
                    case "search":
                        handleSearchPlayers(req, resp);
                        break;
                    case "count":
                        handleGetPlayerCount(resp);
                        break;
                    case "player":
                        if (parts.length < 2) {
                            sendError(resp, 400, "Player operation required");
                            return;
                        }
                        handleSinglePlayerEndpoints(parts, resp);
                        break;
                    default:
                        // Try to parse as UUID for single player lookup or world endpoints
                        try {
                            UUID uuid = UUID.fromString(endpoint);
                            // Check if this is a UUID-based player world query
                            if (parts.length > 1 && "worlds".equals(parts[1].toLowerCase())) {
                                handlePlayerWorldEndpoints(uuid, parts, req, resp);
                            } else {
                                handleGetPlayer(uuid, resp);
                            }
                        } catch (IllegalArgumentException e) {
                            sendError(resp, 404, "Unknown endpoint: " + endpoint);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Error handling GET request from IP: " + ApiUtils.getClientIP(req) + ", Path: " + req.getPathInfo(), e);
            sendError(resp, 500, "An unexpected error occurred.");
        }
    }

    private void handleSinglePlayerEndpoints(String[] parts, HttpServletResponse resp) throws IOException {
        if (parts.length < 3) {
            sendError(resp, 400, "Player identifier required");
            return;
        }
        
        String operation = parts[1].toLowerCase();
        String identifier = parts[2];
        
        switch (operation) {
            case "name":
                if (parts.length > 3 && "history".equals(parts[3].toLowerCase())) {
                    // Handle /player/name/{name}/history
                    handleGetPlayerNameHistory(identifier, resp);
                } else {
                    // Handle /player/name/{name}
                    handleGetPlayerByName(identifier, resp);
                }
                break;
            case "history":
                // Keep backward compatibility for /player/history/{name}
                handleGetPlayerNameHistory(identifier, resp);
                break;
            default:
                sendError(resp, 404, "Unknown player operation: " + operation);
                break;
        }
    }

    private void handleDirectPlayerNameEndpoint(String[] parts, HttpServletResponse resp) throws IOException {
        if (parts.length < 2) {
            sendError(resp, 400, "Player name required");
            return;
        }
        
        String playerName = parts[1];
        
        if (parts.length > 2 && "history".equals(parts[2].toLowerCase())) {
            // Handle /player/name/{name}/history
            handleGetPlayerNameHistory(playerName, resp);
        } else {
            // Handle /player/name/{name}
            handleGetPlayerByName(playerName, resp);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = ApiUtils.getClientIP(req);
        
        // Move request logging to debug level to reduce verbosity
        logger.debug("PlayerController PUT request: " + pathInfo + " from IP: " + clientIP);
        
        resp.setContentType("application/json");

        try {
            if (pathInfo == null) {
                logger.warning("PUT request missing path info from IP: " + clientIP);
                sendError(resp, 400, "Player UUID required");
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            if (parts.length < 2) {
                logger.warning("PUT request with invalid path format: " + pathInfo + " from IP: " + clientIP);
                sendError(resp, 400, "Invalid path format");
                return;
            }

            UUID uuid = UUID.fromString(parts[0]);
            String operation = parts[1].toLowerCase();

            switch (operation) {
                case "location":
                    handleUpdateLocation(uuid, req, resp);
                    break;
                case "groups":
                    handleUpdateGroups(uuid, req, resp);
                    break;
                default:
                    sendError(resp, 404, "Unknown operation: " + operation);
                    break;
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, "Invalid UUID format");
        } catch (Exception e) {
            logger.error("Error handling PUT request from IP: " + ApiUtils.getClientIP(req) + ", Path: " + req.getPathInfo(), e);
            sendError(resp, 500, "An unexpected error occurred.");
        }
    }

    private void handleGetAllPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int offset = getIntParam(req, "offset", 0);
        int limit  = getIntParam(req, "limit", 50);

        try {
            List<PlayerDTO> players = playerService.getAllPlayers().get(15, TimeUnit.SECONDS);
            List<PlayerResponse> data = players.stream()
                                           .skip(offset).limit(limit)
                                           .map(this::convertToResponse)
                                           .collect(Collectors.toList());
            sendResponse(resp, 200, new PagedResponse<>(data, offset, limit, players.size()));
        } catch (Exception ex) {
            logger.error("Error retrieving all players", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve players");
        }
    }

    private void handleGetOnlinePlayers(HttpServletResponse resp) throws IOException {
        sendResponse(resp, 200, liveDataCache.getSnapshot().onlinePlayers);
    }

    private void handleGetRecentPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int hours = getIntParam(req, "hours", 24);

        try {
            List<PlayerDTO> players = playerService.getRecentPlayers(hours).get(15, TimeUnit.SECONDS);
            List<PlayerResponse> responses = players.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
            sendResponse(resp, 200, responses);
        } catch (Exception ex) {
            logger.error("Error retrieving recent players", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve recent players");
        }
    }

    private void handleGetPlayer(UUID uuid, HttpServletResponse resp) throws IOException {
        try {
            Optional<PlayerDTO> opt = playerService.getPlayer(uuid).get(15, TimeUnit.SECONDS);
            if (opt.isPresent()) {
                sendResponse(resp, 200, convertToResponse(opt.get()));
            } else {
                sendError(resp, 404, "Player not found");
            }
        } catch (Exception ex) {
            logger.error("Error retrieving player", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player");
        }
    }

    private void handleGetPlayerByName(String name, HttpServletResponse resp) throws IOException {
        try {
            Optional<PlayerDTO> opt = playerService.getPlayerByName(name).get(15, TimeUnit.SECONDS);
            if (opt.isPresent()) {
                sendResponse(resp, 200, convertToResponse(opt.get()));
            } else {
                sendError(resp, 404, "Player not found");
            }
        } catch (Exception ex) {
            logger.error("Error retrieving player by name", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player");
        }
    }

    private void handleGetPlayersByGroup(String group, HttpServletResponse resp) throws IOException {
        try {
            List<PlayerDTO> players = playerService.getPlayersByGroup(group).get(15, TimeUnit.SECONDS);
            List<PlayerResponse> responses = players.stream()
                                                .map(this::convertToResponse)
                                                .collect(Collectors.toList());
            sendResponse(resp, 200, responses);
        } catch (Exception ex) {
            logger.error("Error retrieving players by group", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve players by group");
        }
    }

    private void handleSearchPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        if (name == null || name.trim().isEmpty()) {
            sendError(resp, 400, "Name parameter required");
            return;
        }
        try {
            String escaped = name.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            List<PlayerDTO> players = playerService.searchPlayersByName("%" + escaped + "%")
                                               .get(15, TimeUnit.SECONDS);
            List<PlayerResponse> responses = players.stream()
                                                .map(this::convertToResponse)
                                                .collect(Collectors.toList());
            sendResponse(resp, 200, responses);
        } catch (Exception ex) {
            logger.error("Error searching players", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to search players");
        }
    }

    private void handleGetPlayerCount(HttpServletResponse resp) throws IOException {
        try {
            long registeredCount = playerService.getPlayerCount().get(15, TimeUnit.SECONDS);
            org.fourz.rvnkcore.api.server.jetty.LiveDataCache.BukkitSnapshot snap = liveDataCache.getSnapshot();

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("registered", registeredCount);
            data.put("online", snap.onlineCount);
            data.put("max", snap.maxPlayers);
            data.put("worlds", snap.worldGroups);

            sendResponse(resp, 200, data);
        } catch (Exception ex) {
            logger.error("Error retrieving player count", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player count");
        }
    }

    private void handleGetPlayerNameHistory(String name, HttpServletResponse resp) throws IOException {
        try {
            Optional<PlayerDTO> opt = playerService.getPlayerByName(name).get(15, TimeUnit.SECONDS);
            if (opt.isPresent()) {
                PlayerDTO player = opt.get();
                PlayerNameHistoryResponse response = PlayerNameHistoryResponse.builder()
                        .uuid(player.getId())
                        .currentName(player.getCurrentName())
                        .nameHistory(player.getNameHistory())
                        .build();
                sendResponse(resp, 200, response);
            } else {
                sendError(resp, 404, "Player not found");
            }
        } catch (Exception ex) {
            logger.error("Error retrieving player name history", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player name history");
        }
    }

    private void handleUpdateLocation(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LocationUpdateRequest request = gson.fromJson(ApiUtils.readRequestBody(req), LocationUpdateRequest.class);
        if (!request.isValid()) { sendError(resp, 400, "Invalid location update request"); return; }
        try {
            playerService.updatePlayerLocation(uuid, request.getWorld(), request.getX(), request.getY(), request.getZ())
                     .get(15, TimeUnit.SECONDS);
            sendResponse(resp, 200, java.util.Map.of("message", "Location updated successfully"));
        } catch (Exception ex) {
            logger.error("Error updating player location", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to update location");
        }
    }

    private void handleUpdateGroups(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        GroupUpdateRequest request = gson.fromJson(ApiUtils.readRequestBody(req), GroupUpdateRequest.class);
        if (!request.isValid()) { sendError(resp, 400, "Invalid group update request"); return; }
        String primary = request.getGroups().isEmpty() ? "" : request.getGroups().get(0);
        try {
            playerService.updatePlayerGroups(uuid, primary, request.getGroups())
                     .get(15, TimeUnit.SECONDS);
            sendResponse(resp, 200, java.util.Map.of("message", "Groups updated successfully"));
        } catch (Exception ex) {
            logger.error("Error updating player groups", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to update groups");
        }
    }

    // ====== PlayerWorld API Endpoints ======

    /**
     * Handles PlayerWorld-specific endpoints:
     * - GET /api/v1/players/{uuid}/worlds - Get all world data for player
     * - GET /api/v1/players/{uuid}/worlds/{world} - Get specific world data
     * - GET /api/v1/players/{uuid}/worlds/{world}/location - Get last known location in world
     * - GET /api/v1/players/{uuid}/worlds/visited - Get list of visited worlds
     * - GET /api/v1/players/{uuid}/worlds/stats - Get world statistics
     */
    private void handlePlayerWorldEndpoints(UUID playerId, String[] parts, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (parts.length < 2) {
            sendError(resp, 400, "Invalid world endpoint format");
            return;
        }
        
        // parts[0] = UUID, parts[1] = "worlds", parts[2+] = specific operations
        if (parts.length == 2) {
            // GET /{uuid}/worlds - All world data for player
            handleGetPlayerAllWorldData(playerId, resp);
        } else if (parts.length == 3) {
            String operation = parts[2].toLowerCase();
            switch (operation) {
                case "visited":
                    // GET /{uuid}/worlds/visited - List of visited worlds
                    handleGetPlayerVisitedWorlds(playerId, resp);
                    break;
                case "stats":
                    // GET /{uuid}/worlds/stats - World statistics
                    handleGetPlayerWorldStats(playerId, resp);
                    break;
                default:
                    // GET /{uuid}/worlds/{world} - Specific world data
                    handleGetPlayerWorldData(playerId, parts[2], resp);
                    break;
            }
        } else if (parts.length == 4) {
            String worldName = parts[2];
            String operation = parts[3].toLowerCase();
            switch (operation) {
                case "location":
                    // GET /{uuid}/worlds/{world}/location - Last known location
                    handleGetPlayerLastLocation(playerId, worldName, resp);
                    break;
                default:
                    sendError(resp, 404, "Unknown world operation: " + operation);
                    break;
            }
        } else {
            sendError(resp, 400, "Invalid world endpoint format");
        }
    }

    private void handleGetPlayerAllWorldData(UUID playerId, HttpServletResponse resp) throws IOException {
        try {
            List<PlayerWorldDataDTO> worldData = playerWorldService.getAllPlayerWorldData(playerId).get(15, TimeUnit.SECONDS);
            // All records share the same playerId — fetch the name once to avoid N+1 queries.
            String playerName = playerService.getPlayer(playerId)
                    .get(5, TimeUnit.SECONDS)
                    .map(PlayerDTO::getCurrentName)
                    .orElse(null);
            List<PlayerWorldDataResponse> responses = worldData.stream()
                    .map(wd -> convertWorldDataToResponse(wd, playerName))
                    .collect(Collectors.toList());
            sendResponse(resp, 200, responses);
        } catch (Exception ex) {
            logger.error("Error retrieving player world data", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player world data");
        }
    }

    private void handleGetPlayerWorldData(UUID playerId, String worldName, HttpServletResponse resp) throws IOException {
        try {
            Optional<PlayerWorldDataDTO> worldData = playerWorldService.getPlayerWorldData(playerId, worldName).get(15, TimeUnit.SECONDS);
            if (worldData.isPresent()) {
                PlayerWorldDataResponse response = convertWorldDataToResponse(worldData.get(), null);
                sendResponse(resp, 200, response);
            } else {
                sendError(resp, 404, "Player has not visited world: " + worldName);
            }
        } catch (Exception ex) {
            logger.error("Error retrieving player world data", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player world data");
        }
    }

    private void handleGetPlayerLastLocation(UUID playerId, String worldName, HttpServletResponse resp) throws IOException {
        try {
            Optional<PlayerWorldDataDTO> worldData = playerWorldService.getLastKnownLocation(playerId, worldName).get(15, TimeUnit.SECONDS);
            if (worldData.isPresent()) {
                PlayerWorldDataDTO data = worldData.get();
                Map<String, Object> location = new java.util.HashMap<>();
                location.put("worldName", data.getWorldName());
                location.put("x", data.getLastX());
                location.put("y", data.getLastY());
                location.put("z", data.getLastZ());
                location.put("yaw", data.getLastYaw());
                location.put("pitch", data.getLastPitch());
                location.put("biome", data.getLastBiome());
                location.put("lastVisit", data.getLastVisit());
                sendResponse(resp, 200, location);
            } else {
                sendError(resp, 404, "Player has not visited world: " + worldName);
            }
        } catch (Exception ex) {
            logger.error("Error retrieving player last location", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player location");
        }
    }

    private void handleGetPlayerVisitedWorlds(UUID playerId, HttpServletResponse resp) throws IOException {
        try {
            List<String> visitedWorlds = playerWorldService.getPlayerVisitedWorlds(playerId).get(15, TimeUnit.SECONDS);
            Map<String, Object> response = Map.of(
                    "playerId", playerId,
                    "worldCount", visitedWorlds.size(),
                    "worlds", visitedWorlds
            );
            sendResponse(resp, 200, response);
        } catch (Exception ex) {
            logger.error("Error retrieving player visited worlds", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve visited worlds");
        }
    }

    private void handleGetPlayerWorldStats(UUID playerId, HttpServletResponse resp) throws IOException {
        try {
            List<PlayerWorldDataDTO> allWorldData = playerWorldService.getAllPlayerWorldData(playerId).get(15, TimeUnit.SECONDS);
            List<PlayerWorldDataDTO> mostVisited = playerWorldService.getPlayerMostVisitedWorlds(playerId, 5).get(15, TimeUnit.SECONDS);
            
            long totalPlaytimeSeconds = allWorldData.stream().mapToLong(PlayerWorldDataDTO::getPlaytimeSeconds).sum();
            int totalDeaths = allWorldData.stream().mapToInt(PlayerWorldDataDTO::getDeathCount).sum();
            int totalVisits = allWorldData.stream().mapToInt(PlayerWorldDataDTO::getVisitCount).sum();
            
            String favoriteWorld = mostVisited.isEmpty() ? null : mostVisited.get(0).getWorldName();
            
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("playerId", playerId);
            stats.put("totalWorlds", allWorldData.size());
            stats.put("totalPlaytimeMinutes", totalPlaytimeSeconds / 60);
            stats.put("totalDeaths", totalDeaths);
            stats.put("totalVisits", totalVisits);
            stats.put("favoriteWorld", favoriteWorld);
            stats.put("mostVisitedWorlds", mostVisited.stream()
                    .map(data -> Map.of(
                            "worldName", data.getWorldName(),
                            "visitCount", data.getVisitCount(),
                            "playtimeMinutes", data.getPlaytimeSeconds() / 60,
                            "deathCount", data.getDeathCount()
                    ))
                    .collect(Collectors.toList()));
            sendResponse(resp, 200, stats);
        } catch (Exception ex) {
            logger.error("Error retrieving player world statistics", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve world statistics");
        }
    }

    // ====== End PlayerWorld API Endpoints ======

    private PlayerResponse convertToResponse(PlayerDTO player) {
        // Convert Timestamp to LocalDateTime
        LocalDateTime firstSeen = player.getFirstJoin() != null ? 
                player.getFirstJoin().toLocalDateTime() : null;
        LocalDateTime lastSeen = player.getLastSeen() != null ? 
                player.getLastSeen().toLocalDateTime() : null;
        
        return PlayerResponse.builder()
                .uuid(player.getId())
                .name(player.getCurrentName())
                .online(Bukkit.getPlayer(player.getId()) != null)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .timesJoined(player.getTimesJoined())
                .currentWorld(player.getCurrentWorld())
                .totalPlaytimeHours(player.getTotalPlaytimeHours())
                .groups(player.getGroups())
                .build();
    }

    /** Converts world data using a pre-fetched player name. Pass null to trigger a single DB lookup. */
    private PlayerWorldDataResponse convertWorldDataToResponse(PlayerWorldDataDTO worldData, String playerName) {
        if (playerName == null) {
            try {
                playerName = playerService.getPlayer(worldData.getPlayerId())
                        .get(5, TimeUnit.SECONDS)
                        .map(PlayerDTO::getCurrentName)
                        .orElse(null);
            } catch (Exception ex) {
                logger.debug("Could not retrieve player name for world data response", ex);
            }
        }

        return PlayerWorldDataResponse.builder()
                .playerId(worldData.getPlayerId())
                .playerName(playerName)
                .worldName(worldData.getWorldName())
                .firstVisit(worldData.getFirstVisit() != null ? worldData.getFirstVisit().toLocalDateTime() : null)
                .lastVisit(worldData.getLastVisit() != null ? worldData.getLastVisit().toLocalDateTime() : null)
                .visitCount(worldData.getVisitCount())
                .playtimeSeconds(worldData.getPlaytimeSeconds())
                .location(worldData.getLastX(), worldData.getLastY(), worldData.getLastZ(), 
                         worldData.getLastYaw(), worldData.getLastPitch())
                .lastBiome(worldData.getLastBiome())
                .deathCount(worldData.getDeathCount())
                .worldSpecificData(worldData.getWorldSpecificData())
                .build();
    }

    private void sendResponse(HttpServletResponse resp, int status, Object data) {
        ApiUtils.sendSuccess(resp, gson, data);
    }

    private void sendError(HttpServletResponse resp, int status, String message) {
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        ApiUtils.sendError(resp, gson, status, code, message);
    }

    private int getIntParam(HttpServletRequest req, String name, int defaultValue) {
        return ApiUtils.getIntParam(req, name, defaultValue);
    }
}
