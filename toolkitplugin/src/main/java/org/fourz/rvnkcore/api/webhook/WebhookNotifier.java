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
 * Sends webhook POST notifications on server events.
 * Player events use a leading-edge debounce to coalesce rapid join/quit bursts.
 * Shop CRUD events use a 30-second leading-edge debounce.
 * Trade completion events use a separate 30-second debounce (independent of shop CRUD).
 * Announcement events fire immediately (no debounce).
 *
 * @since 1.5.0
 */
public class WebhookNotifier {

    private static final long SHOP_DEBOUNCE_MS = 30_000L;
    private static final long TRADE_DEBOUNCE_MS = 30_000L;

    private final HttpClient httpClient;
    private final WebhookConfig config;
    private final LogManager logger;
    private final AtomicLong lastFiredAt = new AtomicLong(0);
    private final AtomicLong shopLastFiredAt = new AtomicLong(0);
    private final AtomicLong tradeLastFiredAt = new AtomicLong(0);

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
            .followRedirects(HttpClient.Redirect.NORMAL)
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

    /**
     * Fires a webhook notification for an announcement change event.
     * No debounce — announcements change infrequently and each mutation should
     * immediately invalidate the frontend cache.
     *
     * @param announcementId The ID of the changed announcement (included in payload for targeted cache invalidation)
     */
    public void notifyAnnouncementChange(String announcementId) {
        String payload = "{\"event\":\"announcement_change\",\"server\":\""
            + escapeJson(config.getServerId())
            + "\",\"announcementId\":\""
            + escapeJson(announcementId != null ? announcementId : "")
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
                logger.warning("Webhook announcement " + config.getServerId() + " -> " + response.statusCode()))
            .exceptionally(e -> {
                logger.warning("Webhook announcement failed: " + e.getMessage());
                return null;
            });
    }

    /**
     * Fires a webhook notification for an announcement change without a specific ID.
     * Backwards-compatible overload that invalidates all announcement caches.
     */
    public void notifyAnnouncementChange() {
        notifyAnnouncementChange(null);
    }

    /**
     * Fires a webhook notification for a shop CRUD event (create/update/delete).
     * Uses a 30-second leading-edge debounce separate from player and trade events.
     *
     * @param shopId The ID of the changed shop (included in payload for targeted cache invalidation)
     */
    public void notifyShopChange(String shopId) {
        long now = System.currentTimeMillis();
        long last = shopLastFiredAt.get();

        if (now - last < SHOP_DEBOUNCE_MS) {
            logger.debug("Webhook shop debounced (within 30s window)");
            return;
        }
        if (!shopLastFiredAt.compareAndSet(last, now)) {
            return;
        }

        String payload = "{\"event\":\"shop_change\",\"server\":\""
            + escapeJson(config.getServerId())
            + "\",\"shopId\":\""
            + escapeJson(shopId != null ? shopId : "")
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
                logger.warning("Webhook shop " + config.getServerId() + " -> " + response.statusCode()))
            .exceptionally(e -> {
                logger.warning("Webhook shop failed: " + e.getMessage());
                return null;
            });
    }

    /**
     * Fires a webhook notification for a shop change without a specific ID.
     * Backwards-compatible overload.
     */
    public void notifyShopChange() {
        notifyShopChange(null);
    }

    /**
     * Fires a webhook notification for a trade completion event.
     * Uses a separate 30-second leading-edge debounce independent of shop CRUD events,
     * so shop create/delete and trade completions don't suppress each other.
     *
     * @param shopId The ID of the shop where the trade occurred
     */
    public void notifyTradeComplete(String shopId) {
        long now = System.currentTimeMillis();
        long last = tradeLastFiredAt.get();

        if (now - last < TRADE_DEBOUNCE_MS) {
            logger.debug("Webhook trade debounced (within 30s window)");
            return;
        }
        if (!tradeLastFiredAt.compareAndSet(last, now)) {
            return;
        }

        String payload = "{\"event\":\"trade_complete\",\"server\":\""
            + escapeJson(config.getServerId())
            + "\",\"shopId\":\""
            + escapeJson(shopId != null ? shopId : "")
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
                logger.warning("Webhook trade " + config.getServerId() + " -> " + response.statusCode()))
            .exceptionally(e -> {
                logger.warning("Webhook trade failed: " + e.getMessage());
                return null;
            });
    }

    /**
     * Fires a webhook notification for a player ban event.
     * No debounce — bans are critical security events that must propagate immediately
     * to trigger session revocation on the web frontend.
     *
     * @param uuid       The banned player's UUID
     * @param playerName The banned player's name
     */
    public void notifyPlayerBanned(String uuid, String playerName) {
        String payload = "{\"event\":\"player_banned\",\"server\":\""
            + escapeJson(config.getServerId())
            + "\",\"uuid\":\""
            + escapeJson(uuid)
            + "\",\"playerName\":\""
            + escapeJson(playerName)
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
                logger.warning("Webhook ban " + config.getServerId() + " -> " + response.statusCode()))
            .exceptionally(e -> {
                logger.warning("Webhook ban failed: " + e.getMessage());
                return null;
            });
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
