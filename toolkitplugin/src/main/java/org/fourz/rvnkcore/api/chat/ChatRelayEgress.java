package org.fourz.rvnkcore.api.chat;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
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
 * Sends relayed chat messages to configured peer servers over HTTP.
 *
 * <p>Two egress paths, chosen once at construction by {@code insecure-tls}:</p>
 * <ul>
 *   <li><b>Secure (default)</b> — mirrors {@code WebhookNotifier}: one {@link HttpClient} built
 *       once, {@code sendAsync} with a bounded timeout, fire-and-forget, warn-on-failure.</li>
 *   <li><b>Insecure (self-signed peers)</b> — {@link HttpsURLConnection} with a trust-all
 *       {@link SSLSocketFactory} <b>and</b> an allow-all {@code HostnameVerifier}. This path exists
 *       because {@link HttpClient} ignores {@code SSLParameters.setEndpointIdentificationAlgorithm(null)}
 *       and always performs "HTTPS" hostname verification — which fails when peers are addressed by IP
 *       and the self-signed cert has no matching SAN. {@link HttpsURLConnection} honours a per-connection
 *       verifier, so it can bypass both cert-trust and hostname checks. Runs on the common pool so the
 *       calling (async chat) thread is never blocked.</li>
 * </ul>
 *
 * <p>Authentication reuses the peer's {@code X-API-Key} (the same header AuthFilter checks on
 * {@code /v1/*}); there is no HMAC.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayEgress {

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    /** Non-null only when insecure-tls is enabled; drives the HttpsURLConnection egress path. */
    private final SSLSocketFactory insecureSocketFactory;
    private final ChatRelayConfig config;
    private final LogManager logger;

    /**
     * Creates a new ChatRelayEgress.
     *
     * @param config Chat relay configuration (peers + timeout)
     * @param logger LogManager instance
     */
    public ChatRelayEgress(ChatRelayConfig config, LogManager logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
            .build();
        // When insecure-tls is on, peers present self-signed certs (RVNKCore's Jetty keystore is
        // self-generated) and are addressed by public IP with no matching cert SAN. Prepare a
        // trust-all socket factory for the HttpsURLConnection path; the allow-all HostnameVerifier
        // is set per-connection in sendInsecure().
        SSLSocketFactory factory = null;
        if (config.isInsecureTls()) {
            SSLContext ctx = buildTrustAllContext(logger);
            if (ctx != null) {
                factory = ctx.getSocketFactory();
            }
        }
        this.insecureSocketFactory = factory;
    }

    /**
     * Builds an {@link SSLContext} that trusts every certificate. Used only when the relay is
     * configured with {@code insecure-tls: true} to accept self-signed peers on a trusted network.
     *
     * @param logger LogManager for reporting setup failure
     * @return a trust-all SSLContext, or null if one could not be created (insecure sends then fail)
     */
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
            logger.warning("ChatRelay insecure-tls requested but trust-all SSLContext failed: "
                + e.getMessage() + " — self-signed peers will not be reachable");
            return null;
        }
    }

    /**
     * Asynchronously POSTs the message to every configured peer.
     * Each send is fire-and-forget; failures are logged at WARN and never propagate.
     *
     * @param dto The chat message to relay
     */
    public void send(ChatMessageDTO dto) {
        if (dto == null) return;
        String payload = GSON.toJson(dto);
        for (ChatRelayConfig.Peer peer : config.getPeers()) {
            if (peer.url() == null || peer.url().isEmpty()) {
                continue;
            }
            // Insecure path only applies to https peers; anything else uses the plain HttpClient.
            if (insecureSocketFactory != null && peer.url().startsWith("https://")) {
                sendInsecure(peer, payload);
            } else {
                sendToPeer(peer, payload);
            }
        }
    }

    /** Secure/default egress via the shared async {@link HttpClient}. */
    private void sendToPeer(ChatRelayConfig.Peer peer, String payload) {
        String logTag = "ChatRelay -> " + peer.id();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(peer.url()))
                .header("Content-Type", "application/json")
                .header("X-API-Key", peer.apiKey() != null ? peer.apiKey() : "")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.debug(logTag + " -> " + status);
                    } else {
                        logger.warning(logTag + " -> " + status);
                    }
                })
                .exceptionally(e -> {
                    logger.warning(logTag + " failed: " + e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            logger.warning(logTag + " failed to dispatch: " + e.getMessage());
        }
    }

    /**
     * Insecure egress via {@link HttpsURLConnection}: trust-all socket factory + allow-all hostname
     * verifier, so self-signed peers addressed by IP are reachable. Runs on the common pool
     * (fire-and-forget) so the async chat thread is never blocked on the network call.
     */
    private void sendInsecure(ChatRelayConfig.Peer peer, String payload) {
        String logTag = "ChatRelay -> " + peer.id();
        CompletableFuture.runAsync(() -> {
            HttpsURLConnection conn = null;
            try {
                URL url = URI.create(peer.url()).toURL();
                conn = (HttpsURLConnection) url.openConnection();
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
                int status = conn.getResponseCode();
                if (status >= 200 && status < 300) {
                    logger.debug(logTag + " -> " + status);
                } else {
                    logger.warning(logTag + " -> " + status);
                }
            } catch (Exception e) {
                logger.warning(logTag + " failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
