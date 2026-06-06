package org.fourz.rvnkcore.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired after a player's preference is written to PlayerPreferencesService.
 * Always asynchronous — listeners must not call sync Bukkit API.
 * Cache invalidation is the primary use case.
 */
public class PlayerPreferenceChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String pluginId;
    private final String notificationType; // null when a full save() occurred

    public PlayerPreferenceChangedEvent(UUID playerUuid, String pluginId, String notificationType) {
        super(true); // async
        this.playerUuid = playerUuid;
        this.pluginId = pluginId;
        this.notificationType = notificationType;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPluginId() { return pluginId; }
    /** Returns the affected type key, or null if a full preferences save occurred. */
    public String getNotificationType() { return notificationType; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
