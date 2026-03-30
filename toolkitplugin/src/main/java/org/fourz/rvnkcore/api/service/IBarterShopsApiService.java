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
     * GET /api/bartershops/groups — list groups with optional filters.
     *
     * @param filters query parameters (owner, world, page, limit)
     */
    CompletableFuture<ApiResponse<?>> getGroups(Map<String, String> filters);

    /**
     * GET /api/bartershops/groups/{id} — get group by ID with shops and co-owners.
     */
    CompletableFuture<ApiResponse<?>> getGroupById(String groupId);
}
