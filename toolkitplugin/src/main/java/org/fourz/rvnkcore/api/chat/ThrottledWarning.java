package org.fourz.rvnkcore.api.chat;

import java.util.function.LongSupplier;

/**
 * Collapses a repeated warning to one line per window, with a count (#2381 follow-up).
 *
 * <p>Used for the Matrix bridge's 429 answers: a busy minute above the bridge cap would otherwise
 * log one WARNING per refused line.</p>
 */
public final class ThrottledWarning {

    private final long windowMs;
    private final LongSupplier clock;
    private long windowStart = Long.MIN_VALUE;
    private int suppressed;

    public ThrottledWarning(long windowMs, LongSupplier clock) {
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /**
     * Records one occurrence. Returns null while inside the window (the occurrence is counted),
     * or the number of occurrences to report (1 + those suppressed since the last report).
     */
    public synchronized Integer hit() {
        long now = clock.getAsLong();
        if (windowStart != Long.MIN_VALUE && now - windowStart < windowMs) {
            suppressed++;
            return null;
        }
        int total = 1 + suppressed;
        suppressed = 0;
        windowStart = now;
        return total;
    }
}
