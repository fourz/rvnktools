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

    // Snapshot operations
    CompletableFuture<ApiResponse<?>> restoreWorldSnapshot(String worldName, String requestBody);

    // Metrics & Health
    CompletableFuture<ApiResponse<?>> getMetrics();
    CompletableFuture<ApiResponse<?>> getHealthStatus();

    // ---------------------------------------------------------------------------------------------
    // Runtime holds (#1883)
    // ---------------------------------------------------------------------------------------------

    /**
     * Claims a world as in-use so RVNKWorlds' inactivity cleanup will not reclaim it.
     *
     * <p>Loading a world is not the same as keeping it. {@code WorldCleanupScheduler} unloads any
     * unprotected world that has sat empty past the inactivity threshold and writes it back to
     * {@code IMPORTED}. A world loaded to satisfy a quest requirement has, by definition, nobody
     * standing in it yet — so without a hold it is reclaimed minutes later and the quest silently
     * becomes unplayable again (#1883).</p>
     *
     * <p>A hold is <b>runtime state, not config</b>. It is deliberately distinct from
     * {@code cleanup.protectedWorlds}: that list is an operator's permanent policy, while a hold is
     * a plugin saying "I am using this right now". Holds do not survive a restart, which is correct —
     * whatever placed the hold will re-place it when it loads again.</p>
     *
     * <p>Holds are tracked <b>per holder</b>, so two plugins claiming the same world do not clobber
     * one another; the world stays held until every holder has released it. Re-holding an
     * already-held world is a no-op success.</p>
     *
     * @param worldName World to hold (case-insensitive)
     * @param holder    Stable identifier for the claimant, e.g. the plugin name. Used so releases
     *                  only drop that claimant's hold
     * @return future completing success once the hold is registered
     * @since 1.5.70
     */
    default CompletableFuture<ApiResponse<?>> holdWorld(String worldName, String holder) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support runtime world holds"));
    }

    /**
     * Releases a hold previously placed by {@link #holdWorld}.
     *
     * <p>Only drops the named holder's claim. The world becomes cleanup-eligible again once no
     * holders remain and it is otherwise unprotected. Releasing a hold that was never placed is a
     * no-op success — callers should not have to track whether they hold something.</p>
     *
     * @param worldName World to release (case-insensitive)
     * @param holder    The same identifier passed to {@link #holdWorld}
     * @return future completing success once the hold is gone
     * @since 1.5.70
     */
    default CompletableFuture<ApiResponse<?>> releaseWorld(String worldName, String holder) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support runtime world holds"));
    }

    /**
     * Site survey — one dense read describing a location well enough for an agent to act on (#1923).
     *
     * <p>Answers, in a single call: where this is, whether it is built or wild, what it is made of,
     * what it might be, and who else is nearby. The alternative is several calls plus console
     * scraping, and console output is truncated and lossy.</p>
     *
     * <p>Optional capabilities report {@code available: false} rather than being omitted. An agent
     * must be able to distinguish "no claim here" from "claims cannot be evaluated" — those lead to
     * different decisions, and a missing key looks like the former while meaning the latter.</p>
     *
     * @param query Raw query string: {@code player=<name>} or {@code world=<w>&x=&y=&z=},
     *              plus optional {@code radius} and {@code include}
     * @return future completing with the survey payload
     * @since 1.5.72
     */
    default CompletableFuture<ApiResponse<?>> surveySite(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the survey endpoint"));
    }
}
