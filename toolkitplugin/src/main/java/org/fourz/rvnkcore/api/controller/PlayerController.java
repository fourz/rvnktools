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
import java.util.UUID;
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
            logger.error("Error handling GET request", e);
            sendError(resp, 500, "Internal server error");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        try {
            if (pathInfo == null) {
                sendError(resp, 400, "Player UUID required");
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            if (parts.length < 2) {
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
            logger.error("Error handling PUT request", e);
            sendError(resp, 500, "Internal server error");
        }
    }

    private void handleGetAllPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int offset = getIntParam(req, "offset", 0);
        int limit = getIntParam(req, "limit", 50);

        // For now, return recent players as a substitute for paginated all players
        // This can be enhanced with proper pagination in the repository layer
        playerService.getRecentPlayers(24 * 30) // Last 30 days
                .thenAccept(players -> {
                    List<PlayerResponse> responses = players.stream()
                            .skip(offset)
                            .limit(limit)
                            .map(this::convertToResponse)
                            .collect(Collectors.toList());
                    
                    PagedResponse<PlayerResponse> pagedResponse = 
                            new PagedResponse<>(responses, offset, limit, players.size());
                    
                    sendResponse(resp, 200, pagedResponse);
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving all players", ex);
                    sendError(resp, 500, "Failed to retrieve players");
                    return null;
                });
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
        playerService.getPlayer(uuid)
                .thenAccept(optionalPlayer -> {
                    if (optionalPlayer.isPresent()) {
                        PlayerResponse response = convertToResponse(optionalPlayer.get());
                        sendResponse(resp, 200, response);
                    } else {
                        sendError(resp, 404, "Player not found");
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving player", ex);
                    sendError(resp, 500, "Failed to retrieve player");
                    return null;
                });
    }

    private void handleGetPlayerByName(String name, HttpServletResponse resp) throws IOException {
        playerService.getPlayerByName(name)
                .thenAccept(optionalPlayer -> {
                    if (optionalPlayer.isPresent()) {
                        PlayerResponse response = convertToResponse(optionalPlayer.get());
                        sendResponse(resp, 200, response);
                    } else {
                        sendError(resp, 404, "Player not found");
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving player by name", ex);
                    sendError(resp, 500, "Failed to retrieve player");
                    return null;
                });
    }

    private void handleGetPlayersByGroup(String group, HttpServletResponse resp) throws IOException {
        playerService.getPlayersByGroup(group)
                .thenAccept(players -> {
                    List<PlayerResponse> responses = players.stream()
                            .map(this::convertToResponse)
                            .collect(Collectors.toList());
                    sendResponse(resp, 200, responses);
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving players by group", ex);
                    sendError(resp, 500, "Failed to retrieve players by group");
                    return null;
                });
    }

    private void handleSearchPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        if (name == null || name.trim().isEmpty()) {
            sendError(resp, 400, "Name parameter required");
            return;
        }

        playerService.searchPlayersByName("%" + name + "%")
                .thenAccept(players -> {
                    List<PlayerResponse> responses = players.stream()
                            .map(this::convertToResponse)
                            .collect(Collectors.toList());
                    sendResponse(resp, 200, responses);
                })
                .exceptionally(ex -> {
                    logger.error("Error searching players", ex);
                    sendError(resp, 500, "Failed to search players");
                    return null;
                });
    }

    private void handleGetPlayerCount(HttpServletResponse resp) throws IOException {
        playerService.getPlayerCount()
                .thenAccept(count -> {
                    CountResponse response = new CountResponse(count, "Total registered players");
                    sendResponse(resp, 200, response);
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving player count", ex);
                    sendError(resp, 500, "Failed to retrieve player count");
                    return null;
                });
    }

    private void handleUpdateLocation(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readRequestBody(req);
            LocationUpdateRequest request = gson.fromJson(body, LocationUpdateRequest.class);
            
            if (!request.isValid()) {
                sendError(resp, 400, "Invalid location update request");
                return;
            }

            playerService.updatePlayerLocation(uuid, request.getWorld(), 
                    request.getX(), request.getY(), request.getZ())
                    .thenAccept(v -> {
                        StatusResponse response = StatusResponse.success("Location updated successfully");
                        sendResponse(resp, 200, response);
                    })
                    .exceptionally(ex -> {
                        logger.error("Error updating player location", ex);
                        sendError(resp, 500, "Failed to update location");
                        return null;
                    });
        } catch (Exception e) {
            sendError(resp, 400, "Invalid request body");
        }
    }

    private void handleUpdateGroups(UUID uuid, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readRequestBody(req);
            GroupUpdateRequest request = gson.fromJson(body, GroupUpdateRequest.class);
            
            if (!request.isValid()) {
                sendError(resp, 400, "Invalid group update request");
                return;
            }

            // For simplicity, treating all actions as "set" for now
            // This can be enhanced to support add/remove operations
            String primaryGroup = request.getGroups().isEmpty() ? "" : request.getGroups().get(0);
            
            playerService.updatePlayerGroups(uuid, primaryGroup, request.getGroups())
                    .thenAccept(v -> {
                        StatusResponse response = StatusResponse.success("Groups updated successfully");
                        sendResponse(resp, 200, response);
                    })
                    .exceptionally(ex -> {
                        logger.error("Error updating player groups", ex);
                        sendError(resp, 500, "Failed to update groups");
                        return null;
                    });
        } catch (Exception e) {
            sendError(resp, 400, "Invalid request body");
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
                .timesJoined(0) // TODO: Add timesJoined to PlayerDTO
                .lastWorld(player.getLastWorld())
                .lastX(player.getLastX())
                .lastY(player.getLastY())
                .lastZ(player.getLastZ())
                .groups(player.getGroups())
                .nameHistory(player.getNameHistory())
                .playtimeMinutes(0L) // TODO: Implement playtime tracking
                .build();
    }

    private PlayerResponse convertBukkitPlayerToResponse(Player player) {
        return PlayerResponse.builder()
                .uuid(player.getUniqueId())
                .name(player.getName())
                .online(true)
                .lastWorld(player.getWorld().getName())
                .lastX(player.getLocation().getX())
                .lastY(player.getLocation().getY())
                .lastZ(player.getLocation().getZ())
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

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = req.getReader().readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }
}
