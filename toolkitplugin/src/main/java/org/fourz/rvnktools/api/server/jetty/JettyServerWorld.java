package org.fourz.rvnktools.api.server.jetty;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Chunk;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class JettyServerWorld extends HttpServlet {
    private final JavaPlugin plugin;
    private final Gson gson;

    /**
     * Initializes the world servlet with plugin instance and Gson serializer
     */
    public JettyServerWorld(JavaPlugin plugin, Gson gson) {
        this.plugin = plugin;
        this.gson = gson;
    }

    /**
     * Handles all GET requests to the world endpoints
     * Supports: list worlds, world details, world boundaries
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        if (pathInfo == null || pathInfo.equals("/")) {
            handleListWorlds(resp);
            return;
        }

        // Remove leading slash and sanitize path
        pathInfo = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = pathInfo.split("/");
        String endpoint = parts[0].toLowerCase();

        try {
            switch (endpoint) {
                case "list":
                    handleListWorlds(resp);
                    break;
                case "details":
                    if (parts.length > 1) {
                        handleWorldDetails(parts[1], resp);
                    } else {
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing world name");
                    }
                    break;
                default:
                    // Try to handle as a world name
                    handleWorldDetails(endpoint, resp);
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    /**
     * Lists all available worlds with basic information
     */
    private void handleListWorlds(HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> worlds = Bukkit.getWorlds().stream()
            .map(this::getBasicWorldInfo)
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("count", worlds.size());
        result.put("worlds", worlds);
        
        sendSuccess(resp, result);
    }

    /**
     * Gets detailed information about a specific world
     */
    private void handleWorldDetails(String worldName, HttpServletResponse resp) throws IOException {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "World not found: " + worldName);
            return;
        }

        Map<String, Object> details = getDetailedWorldInfo(world);
        sendSuccess(resp, details);
    }

    /**
     * Gets basic world information
     */
    private Map<String, Object> getBasicWorldInfo(World world) {
        return Map.of(
            "name", world.getName(),
            "environment", world.getEnvironment().name(),
            "loaded", world.isChunkLoaded(0, 0),
            "players", world.getPlayers().size()
        );
    }

    /**
     * Gets detailed world information including boundaries
     */
    private Map<String, Object> getDetailedWorldInfo(World world) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", world.getName());
        info.put("environment", world.getEnvironment().name());
        info.put("difficulty", world.getDifficulty().name());
        info.put("seed", world.getSeed());
        info.put("time", world.getTime());
        info.put("loaded_chunks", world.getLoadedChunks().length);
        info.put("players", world.getPlayers().size());
        
        // Get world boundaries
        Map<String, Integer> bounds = getWorldBoundaries(world);
        info.put("boundaries", bounds);
        
        // Get player activity
        List<Map<String, Object>> recentPlayers = world.getPlayers().stream()
            .map(p -> Map.of(
                "name", p.getName(),
                "location", Map.of(
                    "x", p.getLocation().getBlockX(),
                    "z", p.getLocation().getBlockZ()
                )
            ))
            .collect(Collectors.toList());
        info.put("active_players", recentPlayers);

        return info;
    }

    /**
     * Calculates world boundaries from loaded chunks
     */
    private Map<String, Integer> getWorldBoundaries(World world) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) {
            return Map.of(
                "north", 0,
                "south", 0,
                "east", 0,
                "west", 0
            );
        }

        int north = Integer.MIN_VALUE;
        int south = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int west = Integer.MAX_VALUE;

        for (Chunk chunk : loadedChunks) {
            int x = chunk.getX() * 16;
            int z = chunk.getZ() * 16;
            
            west = Math.min(west, x);
            east = Math.max(east, x + 15);
            south = Math.min(south, z);
            north = Math.max(north, z + 15);
        }

        return Map.of(
            "north", north,
            "south", south,
            "east", east,
            "west", west
        );
    }

    private void sendSuccess(HttpServletResponse resp, Object data) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(gson.toJson(data));
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().println(gson.toJson(Map.of(
            "status", "error",
            "message", message
        )));
    }
}