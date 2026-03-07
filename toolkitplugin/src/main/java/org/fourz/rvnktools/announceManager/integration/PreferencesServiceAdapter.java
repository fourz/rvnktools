package org.fourz.rvnktools.announceManager.integration;

import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnktools.announceManager.preferences.PreferenceProperty;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter between AnnouncePreferences API and PlayerPreferencesService API.
 * Translates legacy preference format to new service-based model.
 */
public class PreferencesServiceAdapter {
    private static final String PLUGIN_ID = "rvnktools";
    private static final String ANNOUNCEMENT_TYPE = "ANNOUNCEMENT";
    private final PlayerPreferencesService service;

    public PreferencesServiceAdapter(PlayerPreferencesService service) {
        this.service = service;
    }

    /**
     * Get a preference value, returning mapped value from PlayerPreferencesService
     *
     * @param playerUuid The player's UUID
     * @param property The preference property key (e.g., "location")
     * @return CompletableFuture with preference value (mapped to service format)
     */
    public CompletableFuture<String> getPreference(UUID playerUuid, String property) {
        // Map LOCATION → channel preference
        if (PreferenceProperty.LOCATION.getKey().equals(property)) {
            return getChannelPreference(playerUuid, ANNOUNCEMENT_TYPE, "CHAT")
                .thenApply(enabled -> enabled ? "chat" : "none");
        }

        // Map SOUND → notification type enabled status
        if (PreferenceProperty.SOUND.getKey().equals(property)) {
            return service.isNotificationEnabled(playerUuid, PLUGIN_ID, ANNOUNCEMENT_TYPE)
                .thenApply(enabled -> enabled ? "block.note_block.pling" : "none");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Set a preference value in PlayerPreferencesService
     *
     * @param playerUuid The player's UUID
     * @param property The preference property key
     * @param value The preference value
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> setPreference(UUID playerUuid, String property, String value) {
        if (PreferenceProperty.LOCATION.getKey().equals(property)) {
            boolean enabled = !"none".equalsIgnoreCase(value);
            return service.setChannelEnabled(playerUuid, PLUGIN_ID, ANNOUNCEMENT_TYPE, "CHAT", enabled);
        }

        if (PreferenceProperty.SOUND.getKey().equals(property)) {
            boolean enabled = !"none".equalsIgnoreCase(value);
            return service.setNotificationEnabled(playerUuid, PLUGIN_ID, ANNOUNCEMENT_TYPE, enabled);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get all disabled notification types for the player
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with set of disabled type names
     */
    public CompletableFuture<Set<String>> getDisabledTypes(UUID playerUuid) {
        return service.getDisabledTypes(playerUuid, PLUGIN_ID);
    }

    /**
     * Mark a notification type as disabled
     *
     * @param playerUuid The player's UUID
     * @param type The notification type
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> addDisabledType(UUID playerUuid, String type) {
        return service.setNotificationEnabled(playerUuid, PLUGIN_ID, type, false);
    }

    /**
     * Mark a notification type as enabled
     *
     * @param playerUuid The player's UUID
     * @param type The notification type
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> removeDisabledType(UUID playerUuid, String type) {
        return service.setNotificationEnabled(playerUuid, PLUGIN_ID, type, true);
    }

    /**
     * Helper to get channel preference status
     *
     * @param playerUuid The player's UUID
     * @param notificationType The notification type
     * @param channel The channel name
     * @return CompletableFuture with channel enabled status
     */
    private CompletableFuture<Boolean> getChannelPreference(UUID playerUuid, String notificationType, String channel) {
        return service.isChannelEnabled(playerUuid, PLUGIN_ID, notificationType, channel);
    }
}
