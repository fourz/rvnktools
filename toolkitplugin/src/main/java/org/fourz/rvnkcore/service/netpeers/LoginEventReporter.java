package org.fourz.rvnkcore.service.netpeers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tells z-office that a player joined, so a linked player keeps radio and music access (#2351).
 *
 * <p>Never blocks a join: {@link #report} only queues work. One worker thread sends, with a short
 * timeout and at most one retry. The queue is small and drops new work when it is full, so nothing
 * piles up while z-office is down.</p>
 *
 * <p>Sends only a public IPv4 address that is not on the proxy ignore list. An unknown or private
 * address sends nothing: a proxy address on the allowlist would let everyone behind it in.</p>
 */
public class LoginEventReporter {

    /** One HTTP POST. Throws {@link IOException} on a network failure or a timeout. */
    public interface Transport {
        Response post(String json) throws IOException;
    }

    public record Response(int status, String body) { }

    /** Where outcomes go. Never receives the secret. */
    public interface Log {
        void info(String message);
        void warning(String message);
        void debug(String message);
    }

    /** Result of one report, for logs and tests. */
    public enum Outcome { GRANTED, NOT_LINKED, REJECTED, UNAUTHORIZED, FAILED, SKIPPED }

    static final int QUEUE_CAPACITY = 16;

    private final NetPeersConfig config;
    private final Transport transport;
    private final Log log;
    private final ThreadPoolExecutor worker;

    public LoginEventReporter(NetPeersConfig config, Transport transport, Log log) {
        this.config = config;
        this.transport = transport;
        this.log = log;
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "rvnkcore-net-peers");
                    t.setDaemon(true);
                    return t;
                },
                (r, ex) -> log.warning("net-peers: queue full, dropped a login event"));
    }

    /**
     * Queues a login event. Returns at once.
     *
     * @return false when nothing was queued (inactive, or the address is not one to send)
     */
    public boolean report(UUID uuid, String name, InetAddress address) {
        if (!config.active() || uuid == null) return false;
        Optional<String> ip = address == null ? Optional.empty() : PublicIpv4.of(address);
        if (ip.isEmpty()) {
            log.debug("net-peers: " + name + " joined from a non-public address, no event");
            return false;
        }
        if (config.ignoreAddresses().contains(ip.get())) {
            log.debug("net-peers: " + name + " joined from an ignored proxy address, no event");
            return false;
        }
        String json = new com.google.gson.Gson().toJson(Map.of(
                "kind", "minecraft",
                "uuid", uuid.toString(),
                "name", name == null ? "" : name,
                "address", ip.get()));
        worker.execute(() -> send(name, json));
        return true;
    }

    /** Sends one event: one try plus at most one retry on a network failure or a 5xx. */
    Outcome send(String name, String json) {
        Response r = null;
        IOException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                r = transport.post(json);
                lastError = null;
                if (r.status() < 500) break;
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (r == null || lastError != null) {
            log.warning("net-peers: login event for " + name + " failed: "
                    + (lastError == null ? "no response" : lastError.getClass().getSimpleName()));
            return Outcome.FAILED;
        }
        JsonObject body = parse(r.body());
        switch (r.status()) {
            case 200 -> {
                log.info("net-peers: " + name + " granted, action=" + field(body, "action"));
                return Outcome.GRANTED;
            }
            case 202 -> {
                log.info("net-peers: " + name + " not granted, reason=" + field(body, "reason"));
                return Outcome.NOT_LINKED;
            }
            case 401 -> {
                log.warning("net-peers: z-office refused the secret (401); check net-peers.secret");
                return Outcome.UNAUTHORIZED;
            }
            default -> {
                log.warning("net-peers: login event for " + name + " returned " + r.status()
                        + (r.status() >= 500 ? " after one retry" : "") + ": " + errorText(body));
                return r.status() >= 500 ? Outcome.FAILED : Outcome.REJECTED;
            }
        }
    }

    /** Stops the worker; queued events are dropped. */
    public void shutdown() {
        worker.shutdownNow();
    }

    private static JsonObject parse(String body) {
        try {
            JsonElement e = JsonParser.parseString(body == null ? "" : body);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String field(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "?" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static String errorText(JsonObject o) {
        String text = o.has("error") ? field(o, "error") : o.has("reason") ? field(o, "reason") : "?";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
