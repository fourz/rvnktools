package org.fourz.rvnkcore.api.service;

/**
 * Tells dependent plugins whether the shared primary database is answering, so they do not each
 * discover it the slow way (#2103).
 *
 * <p>Every RVNK plugin owns its own pool and its own fallback. Without this service each of them
 * learns that MySQL is down by waiting out its own HikariCP timeout — 30 seconds apiece, in
 * sequence, on the main thread during enable. Ask here first: a "no" is already known and costs
 * nothing, and the caller can go straight to its SQLite or YAML path.</p>
 *
 * <p>The answer is a cached TCP probe of the configured host, refreshed no more often than the
 * configured recheck interval, so twenty callers cost one probe. Treat {@code false} as reliable
 * and {@code true} as "worth trying" — a listening port is not a working login.</p>
 *
 * <p>Usage from another plugin:</p>
 * <pre>{@code
 * DatabaseAvailabilityService availability =
 *         registry.getService(DatabaseAvailabilityService.class);
 * if (availability != null && !availability.isPrimaryReachable()) {
 *     enterFallbackMode();   // skips a 30s stall
 * }
 * }</pre>
 *
 * @since 1.5.88
 */
public interface DatabaseAvailabilityService {

    /**
     * Whether the configured primary database host accepts TCP connections.
     *
     * <p>Cached; may trigger one probe when the cached answer is older than the recheck interval.
     * Always returns {@code true} when the primary is SQLite, which cannot be unreachable.</p>
     */
    boolean isPrimaryReachable();

    /** Forces a fresh probe, ignoring the cached answer. */
    boolean probeNow();

    /**
     * Whether RVNKCore itself is serving its data from the local SQLite fallback because the
     * configured MySQL primary could not be reached at startup.
     */
    boolean isCoreInFallback();

    /** One-line human-readable state, for command output and logs. */
    String describe();
}
