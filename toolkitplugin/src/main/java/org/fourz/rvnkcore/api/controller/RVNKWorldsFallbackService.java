package org.fourz.rvnkcore.api.controller;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.server.jetty.LiveDataCache;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only fallback for {@link IRVNKWorldsApiService} used when the RVNKWorlds plugin is not loaded.
 * Derives world data from Bukkit's live world registry — no database access, no unloaded world history.
 *
 * <p><strong>Primary implementation:</strong>
 * {@code repos/RVNKWorlds/src/main/java/org/fourz/RVNKWorlds/api/WorldApiEndpointImpl.java}<br>
 * Keep {@link #listWorlds()} and {@link #getWorld(String)} response shapes in sync with
 * {@code WorldApiEndpointImpl.WorldSummaryDTO} and {@code WorldDataDTO} when updating either side.</p>
 *
 * <p>Write operations always return an error — they require RVNKWorlds' WorldManager and
 * Bukkit world lifecycle hooks unavailable here.</p>
 */
class RVNKWorldsFallbackService implements IRVNKWorldsApiService {

    // ==================== Read Operations ====================

    /**
     * Returns all worlds currently loaded by Bukkit.
     * Shape mirrors WorldApiEndpointImpl.WorldSummaryDTO — see primary impl.
     */
    @Override
    public CompletableFuture<ApiResponse<?>> listWorlds() {
        LiveDataCache cache = LiveDataCache.getInstance();
        List<LiveDataCache.WorldSnapshot> snapshot = cache != null
                ? cache.getSnapshot().worlds
                : List.of();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (LiveDataCache.WorldSnapshot w : snapshot) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", w.name());
            entry.put("displayName", w.name());
            entry.put("state", "ACTIVE");
            entry.put("environment", w.environment());
            entry.put("groupName", "");
            entry.put("playerCount", w.playerCount());
            entry.put("lastAccessed", System.currentTimeMillis());
            summaries.add(entry);
        }
        return CompletableFuture.completedFuture(ApiResponse.success(summaries));
    }

    /**
     * Returns a loaded world by name. Unloaded worlds are not visible — requires RVNKWorlds DB.
     * Shape mirrors WorldDataDTO — see primary impl.
     */
    @Override
    public CompletableFuture<ApiResponse<?>> getWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return (ApiResponse<?>) ApiResponse.error("NOT_FOUND",
                    "World not found or not loaded: " + worldName);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("worldName", world.getName());
            data.put("status", "ACTIVE");
            data.put("environment", world.getEnvironment().name());
            data.put("worldType", world.getWorldType() != null ? world.getWorldType().name() : null);
            data.put("createdAt", null);
            data.put("lastUsed", null);
            return (ApiResponse<?>) ApiResponse.success(data);
        });
    }

    /** Returns an empty list — template data requires RVNKWorlds' ConfigManager. */
    @Override
    public CompletableFuture<ApiResponse<?>> listTemplates() {
        return CompletableFuture.completedFuture(ApiResponse.success(Collections.emptyList()));
    }

    /** Returns an empty list — group data requires RVNKWorlds' ConfigManager. */
    @Override
    public CompletableFuture<ApiResponse<?>> listGroups() {
        return CompletableFuture.completedFuture(ApiResponse.success(Collections.emptyList()));
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getGroup(String groupName) {
        return CompletableFuture.completedFuture(
            ApiResponse.error("NOT_FOUND", "Group not found: " + groupName));
    }

    /**
     * Returns live Bukkit metrics. Shape mirrors WorldApiEndpointImpl.getMetrics() — see primary impl.
     */
    @Override
    public CompletableFuture<ApiResponse<?>> getMetrics() {
        LiveDataCache cache = LiveDataCache.getInstance();
        LiveDataCache.BukkitSnapshot snap = cache != null ? cache.getSnapshot() : null;
        int worldCount = snap != null ? snap.worlds.size() : 0;
        int onlineCount = snap != null ? snap.onlineCount : Bukkit.getOnlinePlayers().size();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalWorlds", worldCount);
        metrics.put("loadedWorlds", worldCount);
        metrics.put("totalPlayers", onlineCount);
        metrics.put("totalChunksLoaded", -1);
        metrics.put("uptimeMs", -1);
        metrics.put("worldsByState", Map.of("ACTIVE", worldCount));
        return CompletableFuture.completedFuture(ApiResponse.success(metrics));
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getHealthStatus() {
        LiveDataCache cache = LiveDataCache.getInstance();
        int worldCount = (cache != null) ? cache.getSnapshot().worlds.size() : 0;
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("healthy", false);
        health.put("version", "unavailable");
        health.put("uptimeMs", -1);
        health.put("managedWorlds", worldCount);
        health.put("databaseConnected", false);
        health.put("message", "RVNKWorlds plugin not loaded");
        return CompletableFuture.completedFuture(ApiResponse.success(health));
    }

    // ==================== Write Operations (unavailable without RVNKWorlds) ====================

    @Override
    public CompletableFuture<ApiResponse<?>> createWorld(String requestBody) {
        return writeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<?>> loadWorld(String worldName) {
        return writeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<?>> unloadWorld(String worldName) {
        return writeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<?>> deleteWorld(String worldName, boolean deleteFiles) {
        return writeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<?>> createTemplate(String requestBody) {
        return writeUnavailable();
    }

    private static CompletableFuture<ApiResponse<?>> writeUnavailable() {
        return CompletableFuture.completedFuture(
            ApiResponse.error("PLUGIN_NOT_LOADED",
                "RVNKWorlds plugin is not loaded; world management unavailable"));
    }
}
