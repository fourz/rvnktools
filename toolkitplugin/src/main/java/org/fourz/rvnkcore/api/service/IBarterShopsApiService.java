package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.response.ApiResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API service interface for BarterShops plugin endpoints.
 *
 * <p>Implemented by BarterShops and registered with ServiceRegistry.
 * RVNKCore's BarterShopsController routes HTTP requests to this service.</p>
 *
 * <p>Uses {@code ApiResponse<?>} wildcard generics so RVNKCore does not need
 * compile-time knowledge of plugin-specific DTOs (ShopDataDTO, TradeRecordDTO, etc.).
 * Gson serializes the concrete payload types at runtime.</p>
 *
 * @since 1.4.0
 */
public interface IBarterShopsApiService {

    /**
     * GET /api/bartershops/shops — list all shops with optional filters.
     *
     * @param filters query parameters (owner, type, world, page, limit, sort, order)
     */
    CompletableFuture<ApiResponse<?>> getShops(Map<String, String> filters);

    /**
     * GET /api/bartershops/shops/{id} — get shop by ID.
     */
    CompletableFuture<ApiResponse<?>> getShopById(String shopId);

    /**
     * GET /api/bartershops/shops/nearby — shops near coordinates.
     */
    CompletableFuture<ApiResponse<?>> getShopsNearby(String world, double x, double y, double z, double radius);

    /**
     * GET /api/bartershops/trades/recent — recent trade activity.
     */
    CompletableFuture<ApiResponse<?>> getRecentTrades(int limit, String shopId, String playerUuid);

    /**
     * GET /api/bartershops/trades/{id} — trade by transaction ID.
     */
    CompletableFuture<ApiResponse<?>> getTradeById(String transactionId);

    /**
     * GET /api/bartershops/stats — server-wide statistics.
     */
    CompletableFuture<ApiResponse<?>> getServerStats();

    /**
     * GET /api/bartershops/stats/shops[/{id}] — shop-specific statistics.
     */
    CompletableFuture<ApiResponse<?>> getShopStats(String shopId);

    /**
     * GET /api/bartershops/health — health check.
     */
    CompletableFuture<ApiResponse<?>> getHealthStatus();

    /**
     * GET /api/bartershops/shops/{id}/items — trade items configured for a shop.
     *
     * <p>Returns the offering item, price item/amount, and accepted payment types
     * extracted from the shop's metadata. Useful for listing what a shop trades
     * without fetching the full shop record.</p>
     */
    CompletableFuture<ApiResponse<?>> getShopItems(String shopId);

    /**
     * GET /api/bartershops/groups — list groups with optional filters.
     *
     * @param filters query parameters (owner, world, page, limit)
     */
    CompletableFuture<ApiResponse<?>> getGroups(Map<String, String> filters);

    /**
     * GET /api/bartershops/groups/{id} — get group by ID with shops and co-owners.
     */
    CompletableFuture<ApiResponse<?>> getGroupById(String groupId);

    /**
     * POST /api/bartershops/groups/{id}/coowners — add a co-owner to a group.
     *
     * @param groupId       group to modify
     * @param requesterUuid UUID of the player making the request (must be group owner)
     * @param coOwnerUuid   UUID of the player to add as co-owner
     */
    CompletableFuture<ApiResponse<?>> addGroupCoOwner(String groupId, String requesterUuid, String coOwnerUuid);

    /**
     * DELETE /api/bartershops/groups/{id}/coowners/{coOwnerUuid} — remove a co-owner from a group.
     *
     * @param groupId       group to modify
     * @param coOwnerUuid   UUID of the co-owner to remove
     * @param requesterUuid UUID of the player making the request (must be group owner)
     */
    CompletableFuture<ApiResponse<?>> removeGroupCoOwner(String groupId, String coOwnerUuid, String requesterUuid);

    /**
     * PATCH /api/bartershops/groups/{id} — rename a shop group.
     *
     * @param groupId       group to rename
     * @param requesterUuid UUID of the player making the request (must be group owner)
     * @param groupName     new name for the group
     */
    CompletableFuture<ApiResponse<?>> renameGroup(String groupId, String requesterUuid, String groupName);
}
