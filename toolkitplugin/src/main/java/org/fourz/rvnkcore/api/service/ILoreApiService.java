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
}
