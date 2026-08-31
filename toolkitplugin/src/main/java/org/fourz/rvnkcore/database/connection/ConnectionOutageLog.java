package org.fourz.rvnkcore.database.connection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides how loudly a connection failure should be logged during a database outage.
 *
 * <p>A cross-host database outage is an expected condition, not a surprise: the provider already
 * signals it to callers by throwing. Logging a full stack trace on every retry therefore adds no
 * diagnostic value after the first one, and it is an availability risk in its own right — a caller
 * that retries on a timer turns a multi-hour outage into tens of thousands of log lines. RVNKWorlds'
 * world sync runs every 60 seconds, so a single unattended outage produced roughly 35 lines a
 * minute indefinitely (#2017). The same shape as the DEBUG-plus-load incident that queued ~150k
 * lines and blocked shutdown for ~11 minutes (#1548).</p>
 *
 * <p>The policy: the <b>first</b> failure of an outage carries the full trace, because that is the
 * one that explains the cause. Everything after it is the same cause repeating, so it collapses
 * into a one-line summary emitted at most once per summary interval. Recovery reports once.</p>
 *
 * <p>This class decides only what to <i>log</i>. It never affects control flow — the caller
 * receives the exception either way.</p>
 *
 * <p>Time is injected so the policy is testable without sleeping. Thread-safe.</p>
 */
public class ConnectionOutageLog {

    /** What the caller should emit for this failure. */
    public enum Action {
        /** First failure of an outage — log the full stack trace so the cause is diagnosable. */
        FULL_TRACE,
        /** Outage continuing and the summary interval has elapsed — log one summary line. */
        SUMMARY,
        /** Outage continuing, already summarised recently — say nothing above debug. */
        SUPPRESS
    }

    private final long summaryIntervalMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong outageStartMs = new AtomicLong(0L);
    private final AtomicLong lastSummaryMs = new AtomicLong(0L);

    public ConnectionOutageLog(long summaryIntervalMs) {
        this.summaryIntervalMs = summaryIntervalMs;
    }

    /**
     * Records a failure and returns what the caller should log.
     *
     * @param nowMs current epoch millis
     */
    public Action recordFailure(long nowMs) {
        int failures = consecutiveFailures.incrementAndGet();

        if (failures == 1) {
            outageStartMs.set(nowMs);
            lastSummaryMs.set(nowMs);
            return Action.FULL_TRACE;
        }

        long lastSummary = lastSummaryMs.get();
        if (nowMs - lastSummary >= summaryIntervalMs
                && lastSummaryMs.compareAndSet(lastSummary, nowMs)) {
            return Action.SUMMARY;
        }
        return Action.SUPPRESS;
    }

    /**
     * Records a success and clears any outage state.
     *
     * <p>Read {@link #outageDurationMs(long)} <b>before</b> calling this — it resets the clock.</p>
     *
     * @return the number of consecutive failures this success ended, or 0 if already healthy.
     *         A non-zero return means the caller should log recovery exactly once.
     */
    public int recordSuccess() {
        int failures = consecutiveFailures.getAndSet(0);
        if (failures > 0) {
            outageStartMs.set(0L);
            lastSummaryMs.set(0L);
        }
        return failures;
    }

    /** Consecutive failures in the current outage, or 0 when healthy. */
    public int failureCount() {
        return consecutiveFailures.get();
    }

    /** How long the current outage has been running, or 0 when healthy. */
    public long outageDurationMs(long nowMs) {
        long began = outageStartMs.get();
        return began > 0 ? nowMs - began : 0L;
    }

    /**
     * Renders a millisecond duration as a short operator-facing string.
     */
    public static String describeDuration(long millis) {
        long totalSeconds = millis / 1000L;
        if (totalSeconds < 60L) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        return (minutes / 60L) + "h" + (minutes % 60L) + "m";
    }
}
