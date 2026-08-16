package org.fourz.rvnkcore.api.presence;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends online-roster snapshots to configured peer servers over HTTP (#1728).
 *
 * <p>A presence-topic twin of {@code ChatRelayEgress}: it reuses the chat relay's peer set,
 * {@code X-API-Key}, {@code insecure-tls} trust path, and timeout, but targets each peer's
 * {@code /v1/presence/inbound} endpoint (derived from the configured chat inbound URL by swapping the
 * path suffix). See {@code ChatRelayEgress} for the rationale behind the two TLS paths — the
 * {@link HttpsURLConnection} path exists because {@link HttpClient} always performs HTTPS hostname
 * verification, which fails for self-signed peers addressed by IP.</p>
 *
 * @since 1.5.37
 */
public class PresenceEgress {

    private static final Gson GSON = new Gson();

    // ── Failure backoff (#1784) ──────────────────────────────────────────────
    /** Consecutive failures before the poll interval is backed off and repeats drop to DEBUG. */
    private static final int BACKOFF_AFTER = 5;
    /** Consecutive failures before the peer is treated as offline and polling pauses entirely. */
    private static final int CIRCUIT_AFTER = 20;
    /** Quiet period once a peer is considered offline. */
    private static final long BACKOFF_MS = 2 * 60_000L;
    private static final long CIRCUIT_MS = 30 * 60_000L;

    private final HttpClient httpClient;
    private final SSLSocketFactory insecureSocketFactory;
    private final ChatRelayConfig config;
    private final LogManager logger;

    /** Per-peer health, keyed by peer id. Absent until that peer has been attempted at least once. */
    private final java.util.Map<String, PeerHealth> health = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Failure state for one peer (#1784). A peer that is simply down otherwise produces a WARN every
     * heartbeat — 3/minute, indefinitely — which buries real errors (same log-flood class as #1548).
     */
    private static final class PeerHealth {
        /** Consecutive failures; reset to 0 by any reachable response. */
        int failures;
        /** Epoch millis before which this peer is not polled at all. */
        long skipUntil;
        /** True once the "backing off" transition has been logged, so it is logged only once. */
        boolean backoffLogged;
        /** True once the "offline, pausing" transition has been logged. */
        boolean circuitLogged;
    }

    /**
     * Creates a new PresenceEgress.
     *
     * @param config Chat relay configuration (peers + timeout + insecure-tls are reused for presence)
     * @param logger LogManager instance
     */
    public PresenceEgress(ChatRelayConfig config, LogManager logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
            .build();
        SSLSocketFactory factory = null;
        if (config.isInsecureTls()) {
            SSLContext ctx = buildTrustAllContext(logger);
            if (ctx != null) {
                factory = ctx.getSocketFactory();
            }
        }
        this.insecureSocketFactory = factory;
    }

    private static SSLContext buildTrustAllContext(LogManager logger) {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            logger.warning("Presence insecure-tls requested but trust-all SSLContext failed: "
                + e.getMessage() + " - self-signed peers will not be reachable");
            return null;
        }
    }

    /**
     * Derives a peer's presence-inbound URL from its configured chat-inbound URL by swapping the path
     * suffix (both endpoints live under the same context-path on the same host/port).
     *
     * @param chatUrl the peer's chat inbound URL
     * @return the presence inbound URL, or null when {@code chatUrl} is blank
     */
    private static String presenceUrl(String chatUrl) {
        if (chatUrl == null || chatUrl.isEmpty()) return null;
        if (chatUrl.contains("/chat/inbound")) {
            return chatUrl.replace("/chat/inbound", "/presence/inbound");
        }
        // Fallback: strip a trailing chat path or append the presence path.
        String base = chatUrl.endsWith("/") ? chatUrl.substring(0, chatUrl.length() - 1) : chatUrl;
        return base + "/presence/inbound";
    }

    /**
     * Asynchronously POSTs the roster snapshot to every configured peer. Fire-and-forget; failures are
     * logged at WARN and never propagate.
     *
     * @param dto The roster snapshot to relay
     */
    /**
     * Logs an egress result, downgrading "peer has no presence endpoint" (404/405 — an older RVNKCore
     * that predates presence) to debug so it doesn't spam WARN every heartbeat.
     */
    private void reportStatus(String peerId, String logTag, int status) {
        if (status >= 200 && status < 300) {
            recordSuccess(peerId, logTag);
            logger.debug(logTag + " -> " + status);
        } else if (status == 404 || status == 405) {
            // Peer is reachable, just older than presence — connectivity is fine, so this clears the
            // failure ladder. Already debug-only, so it never spams.
            recordSuccess(peerId, logTag);
            logger.debug(logTag + " -> " + status + " (peer has no presence endpoint)");
        } else {
            recordFailure(peerId, logTag, "HTTP " + status);
        }
    }

    /**
     * Clears a peer's failure state (#1784). Announces recovery at INFO — and only when the peer had
     * actually been backed off — so an operator can see the mesh heal without noise from ordinary beats.
     */
    private void recordSuccess(String peerId, String logTag) {
        PeerHealth h = health.get(peerId);
        if (h == null) return;
        synchronized (h) {
            if (h.failures >= BACKOFF_AFTER) {
                logger.info(logTag + " recovered after " + h.failures + " consecutive failures");
            }
            h.failures = 0;
            h.skipUntil = 0L;
            h.backoffLogged = false;
            h.circuitLogged = false;
        }
    }

    /**
     * Records a failed attempt and advances the backoff ladder (#1784):
     * <ul>
     *   <li>failures 1–4 — normal cadence, WARN each time (an operator should see a new outage)</li>
     *   <li>5+ — poll every {@value #BACKOFF_MS} ms instead; one WARN, then DEBUG</li>
     *   <li>20+ — peer treated as offline: no polling for 30 minutes; one WARN, then DEBUG</li>
     * </ul>
     * Bounds an indefinite outage to a handful of WARN lines instead of ~180/hour per peer.
     */
    private void recordFailure(String peerId, String logTag, String reason) {
        PeerHealth h = health.computeIfAbsent(peerId, k -> new PeerHealth());
        synchronized (h) {
            h.failures++;
            long now = System.currentTimeMillis();
            if (h.failures >= CIRCUIT_AFTER) {
                h.skipUntil = now + CIRCUIT_MS;
                if (!h.circuitLogged) {
                    h.circuitLogged = true;
                    logger.warning(logTag + " appears offline after " + h.failures
                            + " consecutive failures (" + reason + ") - pausing presence polling for "
                            + (CIRCUIT_MS / 60_000L) + " minutes");
                } else {
                    logger.debug(logTag + " still offline (" + reason + ")");
                }
            } else if (h.failures >= BACKOFF_AFTER) {
                h.skipUntil = now + BACKOFF_MS;
                if (!h.backoffLogged) {
                    h.backoffLogged = true;
                    logger.warning(logTag + " failed " + h.failures + "x (" + reason
                            + ") - backing off to every " + (BACKOFF_MS / 1000L)
                            + "s; further failures at DEBUG until it recovers");
                } else {
                    logger.debug(logTag + " failed (" + reason + ")");
                }
            } else {
                logger.warning(logTag + " failed: " + reason);
            }
        }
    }

    /** True when this peer is inside a backoff/circuit window and should not be polled yet (#1784). */
    private boolean skip(String peerId) {
        PeerHealth h = health.get(peerId);
        if (h == null) return false;
        synchronized (h) {
            return h.skipUntil > System.currentTimeMillis();
        }
    }

    public void send(PresenceDTO dto) {
        if (dto == null) return;
        String payload = GSON.toJson(dto);
        for (ChatRelayConfig.Peer peer : config.getPeers()) {
            String url = presenceUrl(peer.url());
            if (url == null) continue;
            // Backoff is per peer, so one dead peer never delays or silences the others (#1784).
            if (skip(peer.id())) continue;
            if (insecureSocketFactory != null && url.startsWith("https://")) {
                sendInsecure(peer, url, payload);
            } else {
                sendToPeer(peer, url, payload);
            }
        }
    }

    private void sendToPeer(ChatRelayConfig.Peer peer, String url, String payload) {
        String logTag = "Presence -> " + peer.id();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", peer.apiKey() != null ? peer.apiKey() : "")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> reportStatus(peer.id(), logTag, response.statusCode()))
                .exceptionally(e -> {
                    recordFailure(peer.id(), logTag, String.valueOf(e.getMessage()));
                    return null;
                });
        } catch (Exception e) {
            recordFailure(peer.id(), logTag, "dispatch: " + e.getMessage());
        }
    }

    private void sendInsecure(ChatRelayConfig.Peer peer, String url, String payload) {
        String logTag = "Presence -> " + peer.id();
        CompletableFuture.runAsync(() -> {
            HttpsURLConnection conn = null;
            try {
                URL u = URI.create(url).toURL();
                conn = (HttpsURLConnection) u.openConnection();
                conn.setSSLSocketFactory(insecureSocketFactory);
                conn.setHostnameVerifier((hostname, session) -> true);
                conn.setConnectTimeout(config.getTimeoutMs());
                conn.setReadTimeout(config.getTimeoutMs());
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", peer.apiKey() != null ? peer.apiKey() : "");
                conn.setDoOutput(true);
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
                reportStatus(peer.id(), logTag, conn.getResponseCode());
            } catch (Exception e) {
                recordFailure(peer.id(), logTag, String.valueOf(e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
