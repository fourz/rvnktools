package org.fourz.rvnkcore.database;

import org.fourz.rvnkcore.database.connection.ConnectionOutageLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the outage log policy from #2017.
 *
 * <p>The behaviour under test is the one that made a two-minute database blip fill the console:
 * a full stack trace on every retry. These tests assert that a sustained outage produces exactly
 * one trace and then goes quiet, because nothing else in the codebase covered this path.</p>
 */
class ConnectionOutageLogTest {

    private static final long SUMMARY_INTERVAL_MS = 300_000L;

    @Test
    @DisplayName("first failure of an outage asks for the full stack trace")
    void firstFailureLogsFullTrace() {
        ConnectionOutageLog log = new ConnectionOutageLog(SUMMARY_INTERVAL_MS);
        assertEquals(ConnectionOutageLog.Action.FULL_TRACE, log.recordFailure(0L));
    }

    @Test
    @DisplayName("a 60s retry loop produces ONE trace, not one per attempt")
    void sustainedOutageDoesNotRepeatTheTrace() {
        ConnectionOutageLog log = new ConnectionOutageLog(SUMMARY_INTERVAL_MS);

        int traces = 0;
        int summaries = 0;
        // One hour of RVNKWorlds' 60-second world sync against a dead database.
        for (int minute = 0; minute < 60; minute++) {
            ConnectionOutageLog.Action action = log.recordFailure(minute * 60_000L);
            if (action == ConnectionOutageLog.Action.FULL_TRACE) traces++;
            if (action == ConnectionOutageLog.Action.SUMMARY) summaries++;
        }

        assertEquals(1, traces, "an hour-long outage must yield exactly one stack trace");
        // Summaries at 5,10,...,55 minutes — bounded and readable, not 60 traces.
        assertEquals(11, summaries, "summaries should be paced at the summary interval");
        assertEquals(60, log.failureCount());
    }

    @Test
    @DisplayName("summary is withheld until the interval has actually elapsed")
    void summaryIsPacedByTheInterval() {
        ConnectionOutageLog log = new ConnectionOutageLog(SUMMARY_INTERVAL_MS);
        log.recordFailure(0L);
        assertEquals(ConnectionOutageLog.Action.SUPPRESS, log.recordFailure(60_000L));
        assertEquals(ConnectionOutageLog.Action.SUPPRESS, log.recordFailure(299_999L));
        assertEquals(ConnectionOutageLog.Action.SUMMARY, log.recordFailure(300_000L));
        assertEquals(ConnectionOutageLog.Action.SUPPRESS, log.recordFailure(300_001L));
    }

    @Test
    @DisplayName("recovery reports once, and the next outage starts clean")
    void recoveryResetsTheCycle() {
        ConnectionOutageLog log = new ConnectionOutageLog(SUMMARY_INTERVAL_MS);
        log.recordFailure(0L);
        log.recordFailure(60_000L);

        assertEquals(2, log.recordSuccess(), "recovery reports how many failures it ended");
        assertEquals(0, log.recordSuccess(), "a healthy success must not log recovery again");
        assertEquals(0, log.failureCount());

        // A later outage is a new outage, so it earns its own trace.
        assertEquals(ConnectionOutageLog.Action.FULL_TRACE, log.recordFailure(600_000L));
    }

    @Test
    @DisplayName("outage duration is measured from the first failure and cleared on recovery")
    void outageDurationTracksTheOutage() {
        ConnectionOutageLog log = new ConnectionOutageLog(SUMMARY_INTERVAL_MS);
        log.recordFailure(10_000L);
        assertEquals(50_000L, log.outageDurationMs(60_000L));

        log.recordSuccess();
        assertEquals(0L, log.outageDurationMs(60_000L), "healthy provider reports no outage");
    }

    @Test
    @DisplayName("durations render in operator-readable units")
    void durationsRenderReadably() {
        assertEquals("0s", ConnectionOutageLog.describeDuration(0L));
        assertEquals("45s", ConnectionOutageLog.describeDuration(45_000L));
        assertEquals("2m", ConnectionOutageLog.describeDuration(125_000L));
        assertEquals("1h5m", ConnectionOutageLog.describeDuration(3_900_000L));
        assertTrue(ConnectionOutageLog.describeDuration(59_999L).endsWith("s"));
    }
}
