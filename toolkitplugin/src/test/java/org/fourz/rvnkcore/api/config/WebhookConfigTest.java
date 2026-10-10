package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Webhook SSRF guard (#607) and its operator opt-in for an intended LAN target (#2122).
 */
@DisplayName("Webhook config validation")
class WebhookConfigTest {

    private static WebhookConfig config(String url, Boolean allowInternal) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("webhook.enabled", true);
        yaml.set("webhook.url", url);
        yaml.set("webhook.secret", "s3cret");
        yaml.set("webhook.server-id", "dev");
        if (allowInternal != null) yaml.set("webhook.allow-internal", allowInternal);
        return WebhookConfig.fromConfigurationSection(yaml.getConfigurationSection("webhook"));
    }

    @Test
    @DisplayName("An internal host is rejected by default")
    void internalHostRejectedByDefault() {
        LogManager logger = mock(LogManager.class);
        WebhookConfig cfg = config("http://localhost:3000/api/webhooks/revalidate", null);

        assertFalse(cfg.isAllowInternal());
        assertFalse(cfg.validate(logger));
        verify(logger).error(contains("SSRF risk blocked"));
    }

    @Test
    @DisplayName("An internal host is accepted with allow-internal, and the opt-in is logged")
    void internalHostAllowedWithFlag() {
        LogManager logger = mock(LogManager.class);
        WebhookConfig cfg = config("http://localhost:3000/api/webhooks/revalidate", true);

        assertTrue(cfg.isAllowInternal());
        assertTrue(cfg.validate(logger));
        verify(logger).warning(contains("allowed by webhook.allow-internal"));
        verify(logger, never()).error(anyString());
    }
}
