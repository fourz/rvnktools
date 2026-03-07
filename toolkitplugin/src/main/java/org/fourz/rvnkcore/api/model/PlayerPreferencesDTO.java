package org.fourz.rvnkcore.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for player notification preferences.
 *
 * Represents a player's notification preferences for a specific plugin,
 * including master toggle, quiet hours, notification type toggles,
 * channel preferences, and plugin-specific metadata.
 *
 * @since 1.5.0
 */
public class PlayerPreferencesDTO {

    private UUID playerUuid;
    private String pluginId;
    private boolean masterEnabled;
    private QuietHoursConfig quietHours;
    private Map<String, Boolean> notificationTypes;
    private Map<String, Map<String, Boolean>> channelPrefs;
    private Map<String, String> metadata;

    public PlayerPreferencesDTO() {
        this.masterEnabled = false;
        this.quietHours = QuietHoursConfig.DISABLED;
        this.notificationTypes = new HashMap<>();
        this.channelPrefs = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    // --- Getters ---

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPluginId() {
        return pluginId;
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public QuietHoursConfig getQuietHours() {
        return quietHours;
    }

    /**
     * Returns the notification type toggles. Key is the notification type name,
     * value is whether it's enabled. Types not present default to enabled.
     */
    public Map<String, Boolean> getNotificationTypes() {
        return notificationTypes;
    }

    /**
     * Returns channel preferences keyed by notification type.
     * Use "*" as the notification type key for global channel preferences.
     * Inner map: channel name -> enabled.
     */
    public Map<String, Map<String, Boolean>> getChannelPrefs() {
        return channelPrefs;
    }

    /**
     * Returns plugin-specific metadata as string key-value pairs.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    // --- Setters ---

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public void setMasterEnabled(boolean masterEnabled) {
        this.masterEnabled = masterEnabled;
    }

    public void setQuietHours(QuietHoursConfig quietHours) {
        this.quietHours = quietHours != null ? quietHours : QuietHoursConfig.DISABLED;
    }

    public void setNotificationTypes(Map<String, Boolean> notificationTypes) {
        this.notificationTypes = notificationTypes != null ? notificationTypes : new HashMap<>();
    }

    public void setChannelPrefs(Map<String, Map<String, Boolean>> channelPrefs) {
        this.channelPrefs = channelPrefs != null ? channelPrefs : new HashMap<>();
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Creates a default preferences DTO for a new player.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param masterEnabled Whether notifications are enabled by default
     * @return A new DTO with default settings
     */
    public static PlayerPreferencesDTO createDefaults(UUID playerUuid, String pluginId, boolean masterEnabled) {
        return new Builder()
                .playerUuid(playerUuid)
                .pluginId(pluginId)
                .masterEnabled(masterEnabled)
                .quietHours(QuietHoursConfig.DISABLED)
                .notificationTypes(Collections.emptyMap())
                .channelPrefs(Collections.emptyMap())
                .metadata(Collections.emptyMap())
                .build();
    }

    @Override
    public String toString() {
        return "PlayerPreferencesDTO{" +
                "playerUuid=" + playerUuid +
                ", pluginId='" + pluginId + '\'' +
                ", masterEnabled=" + masterEnabled +
                ", quietHours=" + quietHours +
                ", types=" + notificationTypes.size() +
                ", channels=" + channelPrefs.size() +
                '}';
    }

    /**
     * Builder for constructing PlayerPreferencesDTO instances.
     */
    public static class Builder {
        private final PlayerPreferencesDTO dto = new PlayerPreferencesDTO();

        public Builder playerUuid(UUID playerUuid) {
            dto.playerUuid = playerUuid;
            return this;
        }

        public Builder pluginId(String pluginId) {
            dto.pluginId = pluginId;
            return this;
        }

        public Builder masterEnabled(boolean masterEnabled) {
            dto.masterEnabled = masterEnabled;
            return this;
        }

        public Builder quietHours(QuietHoursConfig quietHours) {
            dto.quietHours = quietHours != null ? quietHours : QuietHoursConfig.DISABLED;
            return this;
        }

        public Builder notificationTypes(Map<String, Boolean> notificationTypes) {
            dto.notificationTypes = notificationTypes != null ? new HashMap<>(notificationTypes) : new HashMap<>();
            return this;
        }

        public Builder notificationType(String type, boolean enabled) {
            dto.notificationTypes.put(type, enabled);
            return this;
        }

        public Builder channelPrefs(Map<String, Map<String, Boolean>> channelPrefs) {
            if (channelPrefs != null) {
                dto.channelPrefs = new HashMap<>();
                channelPrefs.forEach((key, value) -> dto.channelPrefs.put(key, new HashMap<>(value)));
            } else {
                dto.channelPrefs = new HashMap<>();
            }
            return this;
        }

        public Builder channelPref(String notificationType, String channel, boolean enabled) {
            dto.channelPrefs.computeIfAbsent(notificationType, k -> new HashMap<>()).put(channel, enabled);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            dto.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public Builder metadata(String key, String value) {
            dto.metadata.put(key, value);
            return this;
        }

        public PlayerPreferencesDTO build() {
            return dto;
        }
    }
}
