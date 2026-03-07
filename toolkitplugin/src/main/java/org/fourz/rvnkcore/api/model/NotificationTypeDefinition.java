package org.fourz.rvnkcore.api.model;

/**
 * Defines a notification type that a plugin registers with PlayerPreferencesService.
 * Used at registration time and for admin display in /pref admin commands.
 *
 * <p>Plugins register their supported notification types during onEnable so players
 * can discover and control which notifications they receive via {@code /pref admin types}.</p>
 *
 * @param pluginId       Plugin identifier (e.g., "rvnkquests", "rvnklore", "bartershops")
 * @param typeId         Notification type identifier (e.g., "quest_start", "discovery")
 * @param description    Human-readable description of this notification type
 * @param defaultEnabled Whether this type is enabled by default for new players
 *
 * @since 1.5.0
 */
public record NotificationTypeDefinition(
        String pluginId,
        String typeId,
        String description,
        boolean defaultEnabled) {
}
