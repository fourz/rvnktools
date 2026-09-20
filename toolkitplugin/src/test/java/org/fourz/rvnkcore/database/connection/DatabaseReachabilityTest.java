package org.fourz.rvnkcore.database.connection;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pre-flight probe that keeps an unreachable database from costing 30s per plugin (#2103). */
class DatabaseReachabilityTest {

    @Test
    void reachableWhenSomethingIsListening() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            DatabaseReachability.Result r =
                    DatabaseReachability.probe("127.0.0.1", server.getLocalPort(), 2000);
            assertTrue(r.reachable(), r.detail());
            assertTrue(r.describe("127.0.0.1", server.getLocalPort()).contains("reachable"));
        }
    }

    @Test
    void unreachableWhenNothingIsListening() throws Exception {
        int closedPort;
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            closedPort = server.getLocalPort();
        }   // closed again: nothing is listening on that port now
        DatabaseReachability.Result r = DatabaseReachability.probe("127.0.0.1", closedPort, 2000);
        assertFalse(r.reachable());
        assertNotNull(r.detail());
        assertTrue(r.describe("127.0.0.1", closedPort).contains("UNREACHABLE"));
    }

    @Test
    void blankHostIsUnreachableWithoutWaiting() {
        DatabaseReachability.Result r = DatabaseReachability.probe("  ", 3306, 5000);
        assertFalse(r.reachable());
        assertTrue(r.elapsedMs() < 1000, "must not spend the timeout on a config error");
    }

    @Test
    void unroutableHostGivesUpAtTheTimeout() {
        // 192.0.2.1 is TEST-NET-1: routable syntax, guaranteed no host.
        long start = System.currentTimeMillis();
        DatabaseReachability.Result r = DatabaseReachability.probe("192.0.2.1", 3306, 1000);
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(r.reachable());
        assertTrue(elapsed < 10_000, "probe must bound its own wait, took " + elapsed + "ms");
    }
}
