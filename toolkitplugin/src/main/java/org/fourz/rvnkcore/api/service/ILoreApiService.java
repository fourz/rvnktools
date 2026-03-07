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
}
