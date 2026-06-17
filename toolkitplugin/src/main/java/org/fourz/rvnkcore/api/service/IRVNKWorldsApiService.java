package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.response.ApiResponse;

import java.util.concurrent.CompletableFuture;

/**
 * API service interface for RVNKWorlds REST endpoints.
 * Implemented by the RVNKWorlds plugin and registered with ServiceRegistry.
 * The RVNKWorldsController in RVNKCore routes HTTP requests to this service.
 *
 * @since 1.4.0
 */
public interface IRVNKWorldsApiService {

    // World operations
    CompletableFuture<ApiResponse<?>> listWorlds();
    CompletableFuture<ApiResponse<?>> getWorld(String worldName);
    CompletableFuture<ApiResponse<?>> createWorld(String requestBody);
    CompletableFuture<ApiResponse<?>> loadWorld(String worldName);
    CompletableFuture<ApiResponse<?>> unloadWorld(String worldName);
    CompletableFuture<ApiResponse<?>> deleteWorld(String worldName, boolean deleteFiles);

    // Template operations
    CompletableFuture<ApiResponse<?>> listTemplates();
    CompletableFuture<ApiResponse<?>> createTemplate(String requestBody);

    // Group operations
    CompletableFuture<ApiResponse<?>> listGroups();
    CompletableFuture<ApiResponse<?>> getGroup(String groupName);

    // Metrics & Health
    CompletableFuture<ApiResponse<?>> getMetrics();
    CompletableFuture<ApiResponse<?>> getHealthStatus();
}
