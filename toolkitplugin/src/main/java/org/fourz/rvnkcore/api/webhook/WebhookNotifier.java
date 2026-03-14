package org.fourz.rvnkcore.api.webhook;

import org.fourz.rvnkcore.api.config.WebhookConfig;
import org.fourz.rvnkcore.util.log.LogManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends webhook POST notifications on player events.
 * Uses a leading-edge debounce to coalesce rapid join/quit bursts.
 *
 * @since 1.5.0
 */
public class WebhookNotifier {

    private final HttpClient httpClient;
    private final WebhookConfig config;
    private final LogManager logger;
    private final AtomicLong lastFiredAt = new AtomicLong(0);

    /**
     * Creates a new WebhookNotifier.
     *
     * @param config Webhook configuration (includes server-id for payload)
     * @param logger LogManager instance
     */
    public WebhookNotifier(WebhookConfig config, LogManager logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
            .build();
    }

    /**
     * Fires a webhook notification for a player change event.
     * Uses leading-edge debounce: the first call within the debounce window fires immediately,
     * subsequent calls within the window are dropped.
     * The HTTP POST is sent asynchronously and never blocks the calling thread.
     */
    public void notifyPlayerChange() {
        long now = System.currentTimeMillis();
        long last = lastFiredAt.get();
        long debounceMs = config.getDebounceSeconds() * 1000L;

        if (now - last < debounceMs) {
            logger.debug("Webhook debounced (within " + config.getDebounceSeconds() + "s window)");
            return;
        }
        if (!lastFiredAt.compareAndSet(last, now)) {
            // Another thread won the CAS race
            return;
        }

        String payload = "{\"event\":\"player_change\",\"server\":\""
            + escapeJson(config.getServerId())
            + "\",\"timestamp\":\""
            + Instant.now().toString()
            + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.getUrl()))
            .header("Content-Type", "application/json")
            .header("X-Webhook-Secret", config.getSecret())
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .timeout(Duration.ofMillis(config.getTimeoutMs()))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response ->
                logger.warning("Webhook " + config.getServerId() + " -> " + response.statusCode()))
            .exceptionally(e -> {
                logger.warning("Webhook failed: " + e.getMessage());
                return null;
            });
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
