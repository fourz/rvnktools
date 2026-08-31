package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.response.ApiResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API service interface for RVNKQuests REST endpoints (#2042).
 * Implemented by the RVNKQuests plugin and registered with ServiceRegistry by reflection.
 * The QuestsController in RVNKCore routes HTTP requests to this service.
 *
 * <p>Read surface first: definitions, one definition, player progress. The assign
 * write verb is a {@code default} method so an older RVNKQuests build against a newer
 * core degrades to an honest "unavailable" instead of {@code AbstractMethodError} —
 * the same version-skew guard as {@link ILoreApiService#findNearbyLocations}.</p>
 *
 * @since 1.5.82
 */
public interface IQuestsApiService {

    /** Quest definitions, paginated ({@code page}/{@code limit}); optional {@code category} filter. */
    CompletableFuture<ApiResponse<?>> getQuests(Map<String, String> params);

    /** One quest definition with its objectives and rewards. */
    CompletableFuture<ApiResponse<?>> getQuestById(String questId);

    /** A player's quest progress by UUID. */
    CompletableFuture<ApiResponse<?>> getPlayerProgress(String playerUuid);

    /**
     * Assign a quest to a player (POST /quests/{id}/assign, body {"playerUuid": "..."}).
     * Deliberately a default: the read path ships first (#2042); an implementation
     * that has not added assign yet loses the verb, not the plugin.
     */
    default CompletableFuture<ApiResponse<?>> assignQuest(String questId, String requestBody) {
        return CompletableFuture.completedFuture(
            ApiResponse.error("NOT_SUPPORTED",
                "Quest assignment requires a newer RVNKQuests build on this server."));
    }
}
