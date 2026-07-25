package org.fourz.rvnkcore.api.chat;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends relayed chat messages to configured peer servers over HTTP.
 *
 * <p>Mirrors {@code WebhookNotifier}'s async pattern exactly: a single {@link HttpClient} built
 * once, {@code sendAsync} with a bounded timeout, fire-and-forget, and warn-on-failure. It never
 * blocks the calling thread. Authentication reuses the peer's {@code X-API-Key} (the same header
 * AuthFilter already checks on {@code /v1/*}); there is no HMAC.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayEgress {

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
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
            sendToPeer(peer, payload);
        }
    }

    private void sendToPeer(ChatRelayConfig.Peer peer, String payload) {
        if (peer.url() == null || peer.url().isEmpty()) {
            return;
        }
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
}
