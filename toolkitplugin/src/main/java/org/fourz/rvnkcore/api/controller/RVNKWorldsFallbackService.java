package org.fourz.rvnkcore.api.controller;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.worlds.CreateWorldV2Request;
import org.fourz.rvnkcore.api.model.worlds.GeneratorInfoDTO;
import org.fourz.rvnkcore.api.model.worlds.JobDTO;
import org.fourz.rvnkcore.api.model.worlds.PresetDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewRequest;
import org.fourz.rvnkcore.api.model.worlds.SkyStackDTO;
import org.fourz.rvnkcore.api.model.worlds.SkyStackTemplateDTO;
import org.fourz.rvnkcore.api.model.worlds.WorldGenSettingsDTO;
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
     * Resolves a world's human-readable display name from RVNKCore config
     * ({@code world-display-names.<world>}), falling back to the raw world name.
     *
     * <p>This is the RVNKWorlds-free equivalent of RVNKWorlds' {@code display_names} in
     * worlds.yml: on servers where the RVNKWorlds plugin is not loaded (e.g. nations),
     * this fallback serves {@code /rvnkworlds/worlds}, so friendly names must come from
     * core config instead. Read live per request so a {@code /rvnkcore reload} takes effect
     * without a restart. Key match is case-insensitive; blank values fall back to the raw name.</p>
     */
    private static String displayNameFor(String worldName) {
        if (worldName == null) return "";
        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core != null) {
                ConfigurationSection section = core.getConfig().getConfigurationSection("world-display-names");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        if (key.equalsIgnoreCase(worldName)) {
                            String display = section.getString(key, "");
                            if (display != null && !display.trim().isEmpty()) {
                                return display.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Config unavailable or malformed — fall through to the raw name.
        }
        return worldName;
    }

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
            entry.put("displayName", displayNameFor(w.name()));
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

    @Override
    public CompletableFuture<ApiResponse<?>> restoreWorldSnapshot(String worldName, String requestBody) {
        return writeUnavailable();
    }

    // ==================== World Forge (#2200) — unavailable without RVNKWorlds ====================
    //
    // Every World Forge route, GETs included, answers 501 PLUGIN_NOT_LOADED. GET /generators does
    // NOT return an empty list: "no generators exist" and "generators cannot be listed here" lead a
    // client to different decisions (hide the create form vs. say RVNKWorlds is down), and an empty
    // list reads as the former while meaning the latter — the same rule surveySite follows with
    // available:false. Only GETs reach this class; the controller answers other methods with 501
    // before resolving a service, so the write overrides below are belt-and-braces.

    @Override
    public CompletableFuture<ApiResponse<List<GeneratorInfoDTO>>> listGenerators() {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<GeneratorInfoDTO>> getGenerator(String generatorId) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<List<PresetDTO>>> listPresets(String generatorFilter) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<PresetDTO>> getPreset(String name) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<PresetDTO>> savePreset(PresetDTO preset) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<PresetDTO>> deletePreset(String name) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<JobDTO>> createWorldV2(CreateWorldV2Request request) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<JobDTO>> getJob(String jobId) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<PreviewDTO>> previewTerrain(PreviewRequest request) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<WorldGenSettingsDTO>> getWorldGenSettings(String worldName) {
        return forgeUnavailable();
    }

    // Control plane (#2218): the read routes answer 501 PLUGIN_NOT_LOADED for the same reason as
    // World Forge - an empty stack list would read as "no stacks" while meaning "cannot list them".
    // The writes never reach this class (the controller answers 501 first).

    @Override
    public CompletableFuture<ApiResponse<List<SkyStackDTO>>> listSkyStacks() {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<SkyStackDTO>> getSkyStack(String groupName) {
        return forgeUnavailable();
    }

    @Override
    public CompletableFuture<ApiResponse<List<SkyStackTemplateDTO>>> listSkyStackTemplates() {
        return forgeUnavailable();
    }

    private static <T> CompletableFuture<ApiResponse<T>> forgeUnavailable() {
        return CompletableFuture.completedFuture(
            ApiResponse.error("PLUGIN_NOT_LOADED",
                "RVNKWorlds plugin is not loaded; World Forge unavailable"));
    }

    private static CompletableFuture<ApiResponse<?>> writeUnavailable() {
        return CompletableFuture.completedFuture(
            ApiResponse.error("PLUGIN_NOT_LOADED",
                "RVNKWorlds plugin is not loaded; world management unavailable"));
    }
}
