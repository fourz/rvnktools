package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Configuration for outbound webhook notifications.
 * Webhooks fire on player events to trigger cache revalidation in external systems.
 *
 * @since 1.5.0
 */
public class WebhookConfig {

    private final boolean enabled;
    private final String url;
    private final String secret;
    private final String serverId;
    private final int debounceSeconds;
    private final int timeoutMs;

    private WebhookConfig(boolean enabled, String url, String secret, String serverId, int debounceSeconds, int timeoutMs) {
        this.enabled = enabled;
        this.url = url;
        this.secret = secret;
        this.serverId = serverId;
        this.debounceSeconds = debounceSeconds;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Creates a WebhookConfig from a ConfigurationSection.
     *
     * @param section The "webhook" configuration section
     * @return WebhookConfig instance
     */
    public static WebhookConfig fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return new WebhookConfig(false, "", "", "", 10, 5000);
        }
        return new WebhookConfig(
            section.getBoolean("enabled", false),
            section.getString("url", ""),
            section.getString("secret", ""),
            section.getString("server-id", ""),
            section.getInt("debounce-seconds", 10),
            section.getInt("timeout-ms", 5000)
        );
    }

    /**
     * Validates the webhook configuration.
     *
     * @param logger Logger for reporting validation issues
     * @return true if valid, false otherwise
     */
    public boolean validate(LogManager logger) {
        if (!enabled) {
            return true;
        }
        boolean valid = true;
        if (url == null || url.trim().isEmpty()) {
            logger.error("Webhook enabled but URL is empty");
            valid = false;
        } else if (!url.startsWith("https://") && !url.startsWith("http://")) {
            logger.error("Webhook URL must start with https:// or http:// — got: " + url);
            valid = false;
        } else if (url.startsWith("http://") && !url.startsWith("https://")) {
            logger.warning("Webhook using insecure http:// — ensure this is a trusted network");
        }
        if (valid) {
            try {
                String host = URI.create(url).getHost();
                if (isInternalHost(host)) {
                    logger.error("Webhook URL resolves to a private/loopback address — SSRF risk blocked: " + host);
                    valid = false;
                }
            } catch (Exception e) {
                logger.error("Webhook URL is malformed: " + e.getMessage());
                valid = false;
            }
        }
        if (secret == null || secret.trim().isEmpty()) {
            logger.error("Webhook enabled but secret is empty");
            valid = false;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            logger.error("Webhook enabled but server-id is empty (must match WebUI server name)");
            valid = false;
        }
        return valid;
    }

    private static boolean isInternalHost(String host) {
        if (host == null) return true;
        String lower = host.toLowerCase();
        if (lower.equals("localhost")) return true;
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return false; // DNS failure — allow, let the HTTP client fail at send time
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public String getServerId() { return serverId; }
    public int getDebounceSeconds() { return debounceSeconds; }
    public int getTimeoutMs() { return timeoutMs; }
}
