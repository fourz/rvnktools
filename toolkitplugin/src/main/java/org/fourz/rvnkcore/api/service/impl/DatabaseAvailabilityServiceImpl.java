package org.fourz.rvnkcore.api.service.impl;

import org.fourz.rvnkcore.api.service.DatabaseAvailabilityService;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.DatabaseReachability;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Caching TCP-probe implementation of {@link DatabaseAvailabilityService} (#2103).
 *
 * <p>One probe serves every caller inside the recheck window, so a room full of plugins asking at
 * enable time costs a single connect. Time is injected so the caching is testable without sleeping.
 * Thread-safe.</p>
 */
public class DatabaseAvailabilityServiceImpl implements DatabaseAvailabilityService {

    private final String host;
    private final int port;
    private final boolean primaryIsMySql;
    private final int probeTimeoutMs;
    private final long recheckMs;
    private final LongSupplier clock;

    private final AtomicBoolean lastReachable = new AtomicBoolean(true);
    private final AtomicLong lastProbeMs = new AtomicLong(0L);
    private final AtomicBoolean coreInFallback = new AtomicBoolean(false);

    public DatabaseAvailabilityServiceImpl(DatabaseConfig primary, int probeTimeoutMs, long recheckMs) {
        this(primary, probeTimeoutMs, recheckMs, System::currentTimeMillis);
    }

    DatabaseAvailabilityServiceImpl(DatabaseConfig primary, int probeTimeoutMs, long recheckMs, LongSupplier clock) {
        this.primaryIsMySql = primary != null && "mysql".equalsIgnoreCase(primary.getType());
        this.host = primary == null ? null : primary.getHost();
        this.port = primary == null ? 0 : primary.getPort();
        this.probeTimeoutMs = probeTimeoutMs;
        this.recheckMs = recheckMs;
        this.clock = clock;
    }

    /** Records that RVNKCore fell back to SQLite (or recovered), for callers and command output. */
    public void setCoreInFallback(boolean inFallback) {
        coreInFallback.set(inFallback);
    }

    /** Seeds the cache with an answer the caller already paid for, so startup probes are not repeated. */
    public void recordProbe(boolean reachable) {
        lastReachable.set(reachable);
        lastProbeMs.set(clock.getAsLong());
    }

    @Override
    public boolean isPrimaryReachable() {
        if (!primaryIsMySql) {
            return true;   // a local SQLite file is never "unreachable" in this sense
        }
        long now = clock.getAsLong();
        if (lastProbeMs.get() != 0L && now - lastProbeMs.get() < recheckMs) {
            return lastReachable.get();
        }
        return probeNow();
    }

    @Override
    public boolean isReachable(String callerHost, int callerPort) {
        if (callerHost == null || callerHost.isBlank()) {
            return true;
        }
        if (primaryIsMySql && callerHost.equalsIgnoreCase(host) && callerPort == port) {
            return isPrimaryReachable();        // same target: reuse the shared cached answer
        }
        // A different database than RVNKCore's: the cached answer says nothing about it.
        return DatabaseReachability.probe(callerHost, callerPort, probeTimeoutMs).reachable();
    }

    @Override
    public boolean probeNow() {
        if (!primaryIsMySql) {
            return true;
        }
        DatabaseReachability.Result result = DatabaseReachability.probe(host, port, probeTimeoutMs);
        lastReachable.set(result.reachable());
        lastProbeMs.set(clock.getAsLong());
        return result.reachable();
    }

    @Override
    public boolean isCoreInFallback() {
        return coreInFallback.get();
    }

    @Override
    public String describe() {
        if (!primaryIsMySql) {
            return "primary=sqlite (local file, always available)";
        }
        return "primary=mysql " + host + ":" + port
                + ", lastProbe=" + (lastProbeMs.get() == 0L ? "never" : (lastReachable.get() ? "reachable" : "UNREACHABLE"))
                + ", core=" + (coreInFallback.get() ? "SQLITE FALLBACK" : "primary");
    }
}
