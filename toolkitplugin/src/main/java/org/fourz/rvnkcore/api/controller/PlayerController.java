package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.model.request.GroupUpdateRequest;
import org.fourz.rvnkcore.api.model.request.LocationUpdateRequest;
import org.fourz.rvnkcore.api.model.response.CountResponse;
import org.fourz.rvnkcore.api.model.response.PagedResponse;
import org.fourz.rvnkcore.api.model.response.PlayerResponse;
import org.fourz.rvnkcore.api.model.response.StatusResponse;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
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
    private final Gson gson;
    private final LogManager logger;

    public PlayerController(PlayerService playerService, Gson gson, LogManager logger) {
        this.playerService = playerService;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = getClientIP(req);
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
                    case "online":
                        handleGetOnlinePlayers(resp);
                        break;
                    case "recent":
                        handleGetRecentPlayers(req, resp);
                        break;
                    case "name":
                        if (parts.length > 1) {
                            handleGetPlayerByName(parts[1], resp);
                        } else {
                            sendError(resp, 400, "Player name required");
                        }
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
                    default:
                        // Try to parse as UUID for single player lookup
                        try {
                            UUID uuid = UUID.fromString(endpoint);
                            handleGetPlayer(uuid, resp);
                        } catch (IllegalArgumentException e) {
                            sendError(resp, 404, "Unknown endpoint: " + endpoint);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Error handling GET request from IP: " + getClientIP(req) + ", Path: " + req.getPathInfo(), e);
            sendError(resp, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = getClientIP(req);
        
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
            logger.error("Error handling PUT request from IP: " + getClientIP(req) + ", Path: " + req.getPathInfo(), e);
            sendError(resp, 500, "Internal server error: " + e.getMessage());
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
        List<PlayerResponse> onlinePlayers = Bukkit.getOnlinePlayers().stream()
                .map(this::convertBukkitPlayerToResponse)
                .collect(Collectors.toList());
        
        sendResponse(resp, 200, onlinePlayers);
    }

    private void handleGetRecentPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int hours = getIntParam(req, "hours", 24);
        
        playerService.getRecentPlayers(hours)
                .thenAccept(players -> {
                    List<PlayerResponse> responses = players.stream()
                            .map(this::convertToResponse)
                            .collect(Collectors.toList());
                    sendResponse(resp, 200, responses);
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving recent players", ex);
                    sendError(resp, 500, "Failed to retrieve recent players");
                    return null;
                });
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
            List<PlayerDTO> players = playerService.searchPlayersByName("%" + name + "%")
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
            long count = playerService.getPlayerCount().get(15, TimeUnit.SECONDS);
            sendResponse(resp, 200, new CountResponse(count, "Total registered players"));
        } catch (Exception ex) {
            logger.error("Error retrieving player count", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to retrieve player count");
        }
    }

    private void handleUpdateLocation(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LocationUpdateRequest request = gson.fromJson(readRequestBody(req), LocationUpdateRequest.class);
        if (!request.isValid()) { sendError(resp, 400, "Invalid location update request"); return; }
        try {
            playerService.updatePlayerLocation(uuid, request.getWorld(), request.getX(), request.getY(), request.getZ())
                     .get(15, TimeUnit.SECONDS);
            sendResponse(resp, 200, StatusResponse.success("Location updated successfully"));
        } catch (Exception ex) {
            logger.error("Error updating player location", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to update location");
        }
    }

    private void handleUpdateGroups(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        GroupUpdateRequest request = gson.fromJson(readRequestBody(req), GroupUpdateRequest.class);
        if (!request.isValid()) { sendError(resp, 400, "Invalid group update request"); return; }
        String primary = request.getGroups().isEmpty() ? "" : request.getGroups().get(0);
        try {
            playerService.updatePlayerGroups(uuid, primary, request.getGroups())
                     .get(15, TimeUnit.SECONDS);
            sendResponse(resp, 200, StatusResponse.success("Groups updated successfully"));
        } catch (Exception ex) {
            logger.error("Error updating player groups", ex instanceof CompletionException ? ex.getCause() : ex);
            sendError(resp, 500, "Failed to update groups");
        }
    }

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
                .totalPlaytimeMinutes(player.getTotalPlaytimeSeconds() / 60)
                .groups(player.getGroups())
                .nameHistory(player.getNameHistory())
                .build();
    }

    private PlayerResponse convertBukkitPlayerToResponse(Player player) {
        return PlayerResponse.builder()
                .uuid(player.getUniqueId())
                .name(player.getName())
                .online(true)
                .currentWorld(player.getWorld().getName())
                .timesJoined(1) // Online players have at least joined once
                .totalPlaytimeMinutes(0L) // Real-time calculation would require session tracking
                .build();
    }

    private void sendResponse(HttpServletResponse resp, int status, Object data) {
        try {
            resp.setStatus(status);
            resp.getWriter().write(gson.toJson(data));
        } catch (IOException e) {
            logger.error("Error sending response", e);
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) {
        try {
            resp.setStatus(status);
            StatusResponse error = StatusResponse.error(message, status);
            resp.getWriter().write(gson.toJson(error));
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
    }

    private int getIntParam(HttpServletRequest req, String name, int defaultValue) {
        String value = req.getParameter(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Extracts client IP address, handling forwarded headers.
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = req.getReader().readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }
}
