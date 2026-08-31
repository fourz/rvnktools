package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.response.ApiResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API service interface for RVNKLore REST endpoints.
 * Implemented by the RVNKLore plugin and registered with ServiceRegistry.
 * The LoreController in RVNKCore routes HTTP requests to this service.
 *
 * @since 1.4.0
 */
public interface ILoreApiService {

    CompletableFuture<ApiResponse<?>> getEntries(Map<String, String> params);

    CompletableFuture<ApiResponse<?>> getEntryById(String id);

    CompletableFuture<ApiResponse<?>> getEntriesByType(String type, Map<String, String> params);

    CompletableFuture<ApiResponse<?>> searchEntries(String query, Map<String, String> params);

    CompletableFuture<ApiResponse<?>> submitEntry(String requestBody);

    CompletableFuture<ApiResponse<?>> getPlayerCollection(String playerUuid);

    CompletableFuture<ApiResponse<?>> getCollections();

    CompletableFuture<ApiResponse<?>> getTypes();

    CompletableFuture<ApiResponse<?>> getStats();

    CompletableFuture<ApiResponse<?>> getHealthStatus();

    CompletableFuture<ApiResponse<?>> getCategories();

    // ── Item surface (#1495) — read + roll, no mint/persist over HTTP ──────────────

    /** Item properties by numeric lore_item id. */
    CompletableFuture<ApiResponse<?>> getItemById(String id);

    /** Item properties by (case-insensitive) display name. */
    CompletableFuture<ApiResponse<?>> getItemByName(String name);

    /** Preset item properties bound to a quest id. */
    CompletableFuture<ApiResponse<?>> getPresetsForQuest(String questId);

    /** Roll a weighted item from an RNG pool; {@code requestBody} may carry {"rarityTier": "..."}. */
    CompletableFuture<ApiResponse<?>> rollPool(String poolId, String requestBody);

    /**
     * Mint a single lore item from a JSON body (#1517) — the write verb for the item surface.
     * Body: {material, name, itemType?, rarity?, lore?[], pages?[], enchantments?{ns:lvl},
     * enchantmentTier?, glow?, customModelData?, description?, createdBy?}. Creates a lore_entry
     * (type ITEM) + lore_item, returning the created item in the same shape as {@link #getItemById}.
     * Auth-gated by the existing AuthFilter on POST, same as {@link #submitEntry}/{@link #rollPool}.
     */
    CompletableFuture<ApiResponse<?>> createItem(String requestBody);

    // ── Versioned item write surface (#1528) ─────────────────────────────────────

    /** Update an item as a new version (PUT /lore/items/{id}); re-materializes the current instance. */
    CompletableFuture<ApiResponse<?>> updateItem(String id, String requestBody);

    /** Delete an item (DELETE /lore/items/{id}); {@code hard=false} soft-archives, {@code true} purges. */
    CompletableFuture<ApiResponse<?>> deleteItem(String id, boolean hard);

    /** Version history for an item (GET /lore/items/{id}/versions). */
    CompletableFuture<ApiResponse<?>> getItemVersions(String id);

    /** Roll an item back to a prior version (POST /lore/items/{id}/rollback, body {"version":N}). */
    CompletableFuture<ApiResponse<?>> rollbackItem(String id, String requestBody);

    /**
     * Lore locations within {@code radius} of a point, for cross-plugin spatial lookups (#1924).
     *
     * <p>Added so RVNKWorlds' site survey can answer "what lore is here?" without reaching into
     * RVNKLore's schema. Reading {@code rvnklore_lore_location} directly would couple a consumer to
     * another plugin's table name and prefix; this keeps the seam at the service boundary, matching
     * how RVNKQuests consumes {@code IRVNKWorldsApiService}.</p>
     *
     * <p><b>Deliberately a default method.</b> Plugins here deploy independently and do go out of
     * step — Event ran RVNKCore 1.5.71 against newer plugins for weeks. An abstract method would
     * make an older RVNKLore throw {@code AbstractMethodError} against a newer core at the first
     * call; this degrades to an honest "unavailable" instead, so a version-skewed tier loses the
     * feature rather than the plugin.</p>
     *
     * @param world  world name; locations are per-server and carry a world name
     * @param x      centre X
     * @param z      centre Z — Y is ignored, matching the repository's 2D distance filter
     * @param radius search radius in blocks
     */
    default CompletableFuture<ApiResponse<?>> findNearbyLocations(String world, double x, double z,
                                                                  double radius) {
        return CompletableFuture.completedFuture(
            ApiResponse.error("NOT_SUPPORTED",
                "Lore location lookup requires RVNKLore 1.0.108 or newer on this server."));
    }

    // ── location surface (#2053) — the validated twin of the raw two-table INSERT ──

    /**
     * Lore locations over HTTP (GET /lore/locations). With {@code world}, {@code x}, {@code z}
     * and {@code radius} in the query it answers nearby; otherwise a recent list, optionally
     * filtered by {@code world} and capped by {@code limit}.
     */
    default CompletableFuture<ApiResponse<?>> getLocations(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "Lore location listing requires RVNKLore 1.0.127 or newer on this server."));
    }

    /**
     * Create a lore location (POST /lore/locations). Body: {@code {name, description?, type,
     * world, x, y, z, createdBy?}}. Creates the entry AND mirrors the coordinate row through
     * the plugin's own validation - the governed replacement for direct SQL inserts.
     */
    default CompletableFuture<ApiResponse<?>> createLocation(String requestBody) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "Lore location creation requires RVNKLore 1.0.127 or newer on this server."));
    }
}
