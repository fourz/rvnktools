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
     *
     * <p>This answers for <b>RVNKCore's</b> host. A plugin pointed at a different database must ask
     * {@link #isReachable(String, int)} instead — see that method for what went wrong without it.</p>
     */
    boolean isPrimaryReachable();

    /**
     * Whether {@code host:port} is answering, using the cached primary answer only when it is the
     * same host and port RVNKCore uses.
     *
     * <p>Callers should prefer this over {@link #isPrimaryReachable()}. On Event 2026-09-20, with
     * RVNKCore pointed at a dead host and RVNKLore still pointed at the live one, RVNKLore was told
     * "unreachable" and dropped to SQLite although its own database was healthy — and its recovery
     * timer kept being refused for the same reason, so it could never climb back out while
     * RVNKCore stayed down. Different host, different answer.</p>
     *
     * @param host the caller's own database host
     * @param port the caller's own database port
     * @return true when that host looks usable, or when the caller passes no host
     */
    boolean isReachable(String host, int port);

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
