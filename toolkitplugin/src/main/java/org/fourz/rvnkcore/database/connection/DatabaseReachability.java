package org.fourz.rvnkcore.database.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A cheap "is the database host answering?" TCP probe, with a short timeout (#2103).
 *
 * <p>Why this exists: HikariCP's own failure path is slow by design. It keeps retrying until
 * {@code connectionTimeout} elapses — 30 seconds in every RVNK config — so an unreachable MySQL
 * costs each plugin its own 30-second stall before it can decide to degrade. During the 2026-09-19
 * outage Event took {@code Done (164.865s)} to start, against 41s healthy: six plugins each paying
 * that same wait in sequence. The JDBC {@code connectTimeout=10000} parameter does not shorten it,
 * because Hikari retries inside its own window.</p>
 *
 * <p>A TCP connect answers the only question that matters for the fallback decision — is anything
 * listening — in milliseconds when the host is up, and in exactly {@code timeoutMs} when it is not.
 * It opens no MySQL session, sends no credentials, and is one connect with no retry, which keeps it
 * clear of the connection-flood behaviour that gets a host's firewall interested.</p>
 *
 * <p>A reachable port does not promise a working login or a healthy database. It is a fast negative
 * test: "unreachable" is trustworthy, "reachable" only means the real connection is worth trying.</p>
 */
public final class DatabaseReachability {

    /** Outcome of one probe. */
    public record Result(boolean reachable, long elapsedMs, String detail) {

        public String describe(String host, int port) {
            return (reachable ? "reachable" : "UNREACHABLE") + " " + host + ":" + port
                    + " (" + elapsedMs + "ms" + (detail == null ? "" : ", " + detail) + ")";
        }
    }

    private DatabaseReachability() {
    }

    /**
     * Opens and immediately closes a TCP connection.
     *
     * @param host      database host
     * @param port      database port
     * @param timeoutMs how long to wait for the handshake before calling it unreachable
     * @return the outcome; never throws
     */
    public static Result probe(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank()) {
            return new Result(false, 0L, "no host configured");
        }
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return new Result(true, System.currentTimeMillis() - start, null);
        } catch (IOException | RuntimeException e) {
            String detail = e.getClass().getSimpleName();
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                detail += ": " + e.getMessage();
            }
            return new Result(false, System.currentTimeMillis() - start, detail);
        }
    }
}
