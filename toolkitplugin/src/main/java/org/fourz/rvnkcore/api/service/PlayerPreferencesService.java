package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.NotificationTypeDefinition;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing player notification preferences across the RVNK plugin ecosystem.
 *
 * Provides centralized access to player preference operations including master toggles,
 * notification type preferences, channel delivery preferences, and quiet hours.
 * All operations are asynchronous to prevent blocking the main thread.
 *
 * <p>Plugins consume this service via ServiceRegistry:</p>
 * <pre>{@code
 * PlayerPreferencesService prefs = ServiceRegistry.getService(PlayerPreferencesService.class);
 * boolean enabled = prefs.isNotificationEnabled(playerUuid, "rvnkquests", "QUEST_START").join();
 * }</pre>
 *
 * @since 1.5.0
 */
public interface PlayerPreferencesService {

    // ========== Master Toggle ==========

    /**
     * Checks if the master notification toggle is enabled for a player/plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier (e.g., "rvnkquests", "bartershops")
     * @return CompletableFuture containing true if notifications are enabled
     */
    CompletableFuture<Boolean> isMasterEnabled(UUID playerUuid, String pluginId);

    /**
     * Sets the master notification toggle for a player/plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param enabled Whether notifications should be enabled
     * @return CompletableFuture that completes when the setting is saved
     */
    CompletableFuture<Void> setMasterEnabled(UUID playerUuid, String pluginId, boolean enabled);

    // ========== Notification Type Preferences ==========

    /**
     * Checks if a specific notification type is enabled for a player.
     * This check considers the master toggle and quiet hours as well.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param notificationType The notification type (e.g., "QUEST_START", "TRADE_COMPLETE")
     * @return CompletableFuture containing true if the notification should be sent
     */
    CompletableFuture<Boolean> isNotificationEnabled(UUID playerUuid, String pluginId, String notificationType);

    /**
     * Sets whether a specific notification type is enabled for a player.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param notificationType The notification type
     * @param enabled Whether this type should be enabled
     * @return CompletableFuture that completes when the setting is saved
     */
    CompletableFuture<Void> setNotificationEnabled(UUID playerUuid, String pluginId, String notificationType, boolean enabled);

    /**
     * Gets all disabled notification types for a player/plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @return CompletableFuture containing set of disabled notification type names
     */
    CompletableFuture<Set<String>> getDisabledTypes(UUID playerUuid, String pluginId);

    // ========== Channel Preferences ==========

    /**
     * Checks if a specific delivery channel is enabled for a notification type.
     * Use "*" as notificationType for global channel preferences.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param notificationType The notification type, or "*" for global
     * @param channelName The channel (CHAT, TITLE, ACTION_BAR, BOSS_BAR, SOUND)
     * @return CompletableFuture containing true if the channel is enabled
     */
    CompletableFuture<Boolean> isChannelEnabled(UUID playerUuid, String pluginId, String notificationType, String channelName);

    /**
     * Sets whether a delivery channel is enabled for a notification type.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param notificationType The notification type, or "*" for global
     * @param channelName The channel name
     * @param enabled Whether the channel should be enabled
     * @return CompletableFuture that completes when the setting is saved
     */
    CompletableFuture<Void> setChannelEnabled(UUID playerUuid, String pluginId, String notificationType, String channelName, boolean enabled);

    /**
     * Gets all channel preferences for a specific notification type.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param notificationType The notification type, or "*" for global
     * @return CompletableFuture containing map of channel name -> enabled
     */
    CompletableFuture<Map<String, Boolean>> getChannelPreferences(UUID playerUuid, String pluginId, String notificationType);

    // ========== Quiet Hours ==========

    /**
     * Gets the quiet hours configuration for a player/plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @return CompletableFuture containing the quiet hours config
     */
    CompletableFuture<QuietHoursConfig> getQuietHours(UUID playerUuid, String pluginId);

    /**
     * Sets quiet hours for a player/plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @param startHour Start hour (0-23), or -1 to disable
     * @param endHour End hour (0-23), or -1 to disable
     * @return CompletableFuture that completes when the setting is saved
     */
    CompletableFuture<Void> setQuietHours(UUID playerUuid, String pluginId, int startHour, int endHour);

    /**
     * Checks if a player is currently in quiet hours for a plugin.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @return CompletableFuture containing true if currently in quiet hours
     */
    CompletableFuture<Boolean> isInQuietHours(UUID playerUuid, String pluginId);

    // ========== Bulk Operations ==========

    /**
     * Gets the full preferences DTO for a player/plugin.
     * Creates default preferences if none exist.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @return CompletableFuture containing the player's preferences
     */
    CompletableFuture<PlayerPreferencesDTO> getPreferences(UUID playerUuid, String pluginId);

    /**
     * Saves a complete preferences DTO for a player/plugin.
     *
     * @param preferences The preferences to save
     * @return CompletableFuture that completes when saved
     */
    CompletableFuture<Void> savePreferences(PlayerPreferencesDTO preferences);

    /**
     * Resets a player's preferences for a plugin to defaults.
     *
     * @param playerUuid The player's UUID
     * @param pluginId The plugin identifier
     * @return CompletableFuture that completes when reset
     */
    CompletableFuture<Void> resetPreferences(UUID playerUuid, String pluginId);

    // ========== Admin Defaults ==========

    /**
     * Gets the default preferences for a plugin (used for new players).
     *
     * @param pluginId The plugin identifier
     * @return CompletableFuture containing the default preferences
     */
    CompletableFuture<PlayerPreferencesDTO> getDefaultPreferences(String pluginId);

    /**
     * Sets an admin default preference value.
     *
     * @param pluginId The plugin identifier
     * @param key The preference key (e.g., "master_enabled")
     * @param value The preference value (JSON-encoded)
     * @return CompletableFuture that completes when saved
     */
    CompletableFuture<Void> setDefaultPreference(String pluginId, String key, String value);

    // ========== Cache Management ==========

    /**
     * Clears the in-memory preference cache.
     * Useful for admin operations or testing.
     */
    void clearCache();

    // ========== Notification Type Registry ==========

    /**
     * Registers notification types that a plugin supports.
     * Called during plugin startup to declare available notification types
     * for player preference management. Replaces any previously registered types
     * for the given plugin.
     *
     * @param pluginId The plugin identifier
     * @param types    List of notification type definitions (not persisted — runtime state only)
     */
    void registerNotificationTypes(String pluginId, List<NotificationTypeDefinition> types);

    /**
     * Gets all registered notification types for a specific plugin.
     *
     * @param pluginId The plugin identifier
     * @return List of notification type definitions, or empty list if none registered
     */
    List<NotificationTypeDefinition> getRegisteredTypes(String pluginId);

    /**
     * Gets all registered notification types across all plugins.
     *
     * @return Unmodifiable map of pluginId to list of notification type definitions
     */
    Map<String, List<NotificationTypeDefinition>> getAllRegisteredTypes();
}
