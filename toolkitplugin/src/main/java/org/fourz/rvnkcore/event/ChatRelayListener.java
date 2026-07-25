package org.fourz.rvnkcore.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.fourz.rvnkcore.service.chatrelay.ChatRelayService;

/**
 * Captures player chat for the cross-server relay.
 *
 * <p>Listens on {@link AsyncPlayerChatEvent} — the chat event present on the {@code spigot-api}
 * classpath that RVNKCore builds against. (Paper's {@code AsyncChatEvent} is not available on
 * spigot-api.) The event is <b>not</b> cancelled: local delivery proceeds normally and the relay
 * runs alongside it. Classification of local vs global happens in {@link ChatRelayService}.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayListener implements Listener {

    private final ChatRelayService relayService;

    /**
     * Creates a new ChatRelayListener.
     *
     * @param relayService The chat relay service to hand captured messages to
     */
    public ChatRelayListener(ChatRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * Hands each chat line to the relay service without cancelling local delivery.
     * Runs at MONITOR priority (observe-only) and ignores messages already cancelled by
     * other plugins.
     *
     * @param event The async chat event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (relayService == null) return;
        relayService.relayOutbound(event.getPlayer(), event.getMessage());
    }
}
