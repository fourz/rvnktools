package org.fourz.rvnktools.api.server.jetty;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class JettyServerPlayers extends HttpServlet {
    private final JavaPlugin plugin;
    private final Gson gson;

    /**
     * Initializes the player servlet with plugin instance and Gson serializer
     * @param plugin JavaPlugin instance for Bukkit server access
     * @param gson Gson instance for JSON serialization
     */
    public JettyServerPlayers(JavaPlugin plugin, Gson gson) {
        this.plugin = plugin;
        this.gson = gson;
    }

    /**
     * Handles all GET requests to the player endpoints
     * Routes requests to appropriate handler methods based on path
     * Supports: online players, today's players, recent players, and player lookups
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        // Default to online players if no path
        if (pathInfo == null || pathInfo.equals("/")) {
            handleCurrentPlayers(resp);
            return;
        }

        // Remove leading slash and sanitize path
        pathInfo = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = pathInfo.split("/");
        String endpoint = parts[0].toLowerCase();

        try {
            switch (endpoint) {
                case "online":
                    handleCurrentPlayers(resp);
                    break;
                case "today":
                    handlePlayersToday(resp);
                    break;
                case "recent":
                case "last":
                    handleRecentPlayers(req, resp);
                    break;
                case "name":
                    if (parts.length > 1) {
                        handlePlayerLookup(parts[1], "name", resp);
                    } else {
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing player name");
                    }
                    break;
                case "uuid":
                    if (parts.length > 1) {
                        handlePlayerLookup(parts[1], "uuid", resp);
                    } else {
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing player UUID");
                    }
                    break;
                default:
                    // Try to handle as a player name
                    handlePlayerLookup(endpoint, "name", resp);
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    /**
     * Retrieves list of currently online players
     * Returns player names and UUIDs in JSON format
     * Used for real-time server population monitoring
     */
    private void handleCurrentPlayers(HttpServletResponse resp) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> players = Bukkit.getOnlinePlayers().stream()
            .map(player -> Map.of(
                "name", player.getName(),
                "uuid", player.getUniqueId().toString()
            ))
            .collect(Collectors.toList());

        result.put("count", players.size());
        result.put("players", players);
        
        sendSuccess(resp, result);
    }

    /**
     * Gets all players who have been online in the last 24 hours
     * Includes both currently online and recently offline players
     * Useful for daily activity monitoring and statistics
     */
    private void handlePlayersToday(HttpServletResponse resp) throws IOException {
        // Use start of day in server's timezone
        long todayStart = Instant.now().toEpochMilli() - (24 * 60 * 60 * 1000);
        
        List<Map<String, String>> players = new ArrayList<>();
        
        // Add currently online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(Map.of(
                "name", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "online", "true",
                "lastSeen", String.valueOf(System.currentTimeMillis())
            ));
        }
        
        // Add offline players who played today
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getLastPlayed() > todayStart && !player.isOnline()) {
                players.add(Map.of(
                    "name", player.getName() != null ? player.getName() : "unknown",
                    "uuid", player.getUniqueId().toString(),
                    "online", "false",
                    "lastSeen", String.valueOf(player.getLastPlayed())
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("count", players.size());
        result.put("players", players);
        
        sendSuccess(resp, result);
    }

    /**
     * Retrieves players active within a specified number of days
     * Accepts days parameter via query string or path parameter
     * Defaults to 7 days if no parameter provided
     * Used for tracking player activity over time
     */
    private void handleRecentPlayers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int days = 7; // default to 7 days
        
        // Try to get days parameter from either query param or path
        String daysParam = req.getParameter("days");
        if (daysParam == null) {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null) {
                String[] parts = pathInfo.split("/");
                if (parts.length > 1) {
                    daysParam = parts[1];
                }
            }
        }
        
        try {
            if (daysParam != null) {
                days = Integer.parseInt(daysParam);
            }
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid days parameter");
            return;
        }

        long cutoff = Instant.now().toEpochMilli() - (days * 24 * 60 * 60 * 1000L);
        List<Map<String, String>> players = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(player -> player.getLastPlayed() > cutoff)
            .map(player -> Map.of(
                "name", player.getName() != null ? player.getName() : "unknown",
                "uuid", player.getUniqueId().toString(),
                "lastSeen", String.valueOf(player.getLastPlayed())
            ))
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("count", players.size());
        result.put("days", days);
        result.put("players", players);
        
        sendSuccess(resp, result);
    }

    /**
     * Looks up player information by name or UUID
     * Supports both online and offline players
     * Auto-detects lookup type based on input format
     * Returns detailed player info including online status and last seen time
     * @param value The player name or UUID to look up
     * @param type The lookup type ("name" or "uuid")
     */
    private void handlePlayerLookup(String value, String type, HttpServletResponse resp) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing player identifier");
            return;
        }

        // Auto-detect type if not specified
        if (type == null) {
            type = value.length() > 16 ? "uuid" : "name";
        }

        Map<String, String> result = new HashMap<>();
        try {
            if (type.equalsIgnoreCase("uuid")) {
                UUID uuid = UUID.fromString(value);
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                if (player.hasPlayedBefore() || player.isOnline()) {
                    result.put("name", player.getName());
                    result.put("uuid", uuid.toString());
                    result.put("online", String.valueOf(player.isOnline()));
                    result.put("lastSeen", String.valueOf(player.getLastPlayed()));
                    sendSuccess(resp, result);
                } else {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Player not found");
                }
            } else {
                // Exact match for online players first
                Player player = Bukkit.getPlayerExact(value);
                if (player != null) {
                    result.put("name", player.getName());
                    result.put("uuid", player.getUniqueId().toString());
                    result.put("online", "true");
                    result.put("lastSeen", String.valueOf(System.currentTimeMillis()));
                    sendSuccess(resp, result);
                    return;
                }

                // Case-insensitive search for offline players
                OfflinePlayer offlinePlayer = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(value))
                    .findFirst()
                    .orElse(null);

                if (offlinePlayer != null) {
                    result.put("name", offlinePlayer.getName());
                    result.put("uuid", offlinePlayer.getUniqueId().toString());
                    result.put("online", "false");
                    result.put("lastSeen", String.valueOf(offlinePlayer.getLastPlayed()));
                    sendSuccess(resp, result);
                } else {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Player not found: " + value);
                }
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid player identifier format");
        }
    }

    /**
     * Helper method to send successful JSON responses
     * Sets HTTP 200 status and serializes data to JSON
     * @param data Object to be converted to JSON response
     */
    private void sendSuccess(HttpServletResponse resp, Object data) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(gson.toJson(data));
    }

    /**
     * Helper method to send error JSON responses
     * Sets appropriate HTTP status code and error message
     * @param status HTTP status code to return
     * @param message Error message to include in response
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().println(gson.toJson(Map.of(
            "status", "error",
            "message", message
        )));
    }
}