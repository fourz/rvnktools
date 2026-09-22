package org.fourz.rvnkcore.api.service.impl;

import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caching rules for the shared reachability answer (#2103). */
class DatabaseAvailabilityServiceImplTest {

    private static DatabaseConfig mysql() {
        return DatabaseConfig.builder().type("mysql").host("192.0.2.1").port(3306)
                .database("x").username("u").password("p").build();
    }

    private static DatabaseConfig sqlite() {
        return DatabaseConfig.builder().type("sqlite").database("core.db").build();
    }

    @Test
    void sqlitePrimaryIsAlwaysReachableAndNeverProbes() {
        DatabaseAvailabilityServiceImpl s = new DatabaseAvailabilityServiceImpl(sqlite(), 50, 60_000L);
        assertTrue(s.isPrimaryReachable());
        assertTrue(s.describe().contains("sqlite"));
    }

    @Test
    void cachedAnswerIsReusedInsideTheRecheckWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        DatabaseAvailabilityServiceImpl s =
                new DatabaseAvailabilityServiceImpl(mysql(), 50, 60_000L, now::get);

        s.recordProbe(false);                       // startup already paid for this answer
        long before = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            assertFalse(s.isPrimaryReachable());    // twenty callers, no new probe
        }
        assertTrue(System.currentTimeMillis() - before < 500,
                "cached answers must not re-probe the network");
    }

    @Test
    void answerIsRefreshedOnceTheWindowElapses() {
        AtomicLong now = new AtomicLong(1_000L);
        DatabaseAvailabilityServiceImpl s =
                new DatabaseAvailabilityServiceImpl(mysql(), 50, 60_000L, now::get);
        s.recordProbe(true);
        assertTrue(s.isPrimaryReachable());

        now.addAndGet(60_001L);                     // window elapsed -> real probe of 192.0.2.1
        assertFalse(s.isPrimaryReachable());
    }

    @Test
    void fallbackFlagIsReportedForCommandsAndLogs() {
        DatabaseAvailabilityServiceImpl s = new DatabaseAvailabilityServiceImpl(mysql(), 50, 60_000L);
        assertFalse(s.isCoreInFallback());
        s.setCoreInFallback(true);
        assertTrue(s.isCoreInFallback());
        assertTrue(s.describe().contains("SQLITE FALLBACK"));
    }

    @Test
    void aDifferentHostIsNotAnsweredFromThePrimaryCache() throws Exception {
        // Event 2026-09-20: core pointed at a dead host, RVNKLore at a live one. Lore must not be
        // told "unreachable" for a database that is answering, or it can never recover (#2103).
        AtomicLong now = new AtomicLong(1_000L);
        DatabaseAvailabilityServiceImpl s =
                new DatabaseAvailabilityServiceImpl(mysql(), 2000, 60_000L, now::get);
        s.recordProbe(false);                                   // core's host is down

        assertFalse(s.isPrimaryReachable());
        assertFalse(s.isReachable("192.0.2.1", 3306), "same host as core: reuse the cached no");

        try (java.net.ServerSocket live = new java.net.ServerSocket()) {
            live.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            assertTrue(s.isReachable("127.0.0.1", live.getLocalPort()),
                    "a different, live host must be probed on its own merits");
        }
    }

    @Test
    void callerWithoutAHostIsLeftToItsOwnConnection() {
        DatabaseAvailabilityServiceImpl s = new DatabaseAvailabilityServiceImpl(mysql(), 50, 60_000L);
        s.recordProbe(false);
        assertTrue(s.isReachable(null, 3306));
        assertTrue(s.isReachable("  ", 3306));
    }
}
