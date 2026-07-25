package org.fourz.rvnkcore.service.chatrelay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.chat.ChatRelayEgress;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-server chat relay service.
 *
 * <p>Captures outbound global-channel chat (via {@code ChatRelayListener}) and fans it out to
 * configured peers through {@link ChatRelayEgress}. Receives peer messages (via
 * {@code ChatRelayController}) and re-broadcasts them locally on the main thread. Loop and
 * duplicate suppression is handled by a bounded LRU dedup set keyed on {@code msgId}, plus an
 * origin-self skip.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayService {

    private final Plugin plugin;
    private final ChatRelayConfig config;
    private final ChatRelayEgress egress;
    private final LogManager logger;

    // Bounded insertion-ordered dedup set (eldest evicted first). Guarded by its own monitor.
    private final Set<String> dedupCache = Collections.synchronizedSet(new LinkedHashSet<>());
    private final int dedupCacheSize;

    /**
     * Creates a new ChatRelayService.
     *
     * @param plugin The owning plugin (used for the main-thread scheduler)
     * @param config Chat relay configuration
     * @param egress Egress dispatcher for peer POSTs
     * @param logger LogManager instance
     */
    public ChatRelayService(Plugin plugin, ChatRelayConfig config, ChatRelayEgress egress, LogManager logger) {
        this.plugin = plugin;
        this.config = config;
        this.egress = egress;
        this.logger = logger;
        this.dedupCacheSize = config.getDedupCacheSize();
    }

    /**
     * Classifies and relays an outbound chat line typed on this server.
     *
     * <p>Local lines (no channel trigger) are ignored. A line beginning with the configured
     * {@code channel-trigger} is treated as global: the trigger is stripped, the line is tagged
     * with a fresh {@code msgId} and this server's id, recorded for dedup, and dispatched to all
     * peers. Local delivery of the original line is unaffected (the listener does not cancel).</p>
     *
     * @param sender     The player who sent the message
     * @param rawMessage The raw chat text as typed
     */
    public void relayOutbound(Player sender, String rawMessage) {
        if (!config.isEnabled()) return;
        if (sender == null || rawMessage == null) return;

        String trigger = config.getChannelTrigger();
        if (trigger == null || trigger.isEmpty() || !rawMessage.startsWith(trigger)) {
            // Local chat — stays on this server.
            return;
        }

        String message = rawMessage.substring(trigger.length()).trim();
        if (message.isEmpty()) {
            return;
        }

        String msgId = UUID.randomUUID().toString();
        markSeen(msgId);

        ChatMessageDTO dto = new ChatMessageDTO(
            msgId,
            config.getServerId(),
            "global",
            sender.getUniqueId().toString(),
            sender.getName(),
            message,
            System.currentTimeMillis()
        );

        logger.debug("Chat relay outbound: " + sender.getName() + " -> global (" + msgId + ")");
        egress.send(dto);
    }

    /**
     * Handles an inbound relayed message from a peer and re-broadcasts it locally.
     *
     * <p>Skips messages that originated on this server (loop guard) and messages whose
     * {@code msgId} has already been seen (duplicate guard). Surviving messages are formatted
     * with the peer's tag and broadcast to all online players on the main thread. The inbound
     * path never re-enters egress, so a re-broadcast is never re-relayed.</p>
     *
     * @param dto The inbound chat message
     */
    public void receiveInbound(ChatMessageDTO dto) {
        if (!config.isEnabled()) return;
        if (dto == null || dto.getMsgId() == null) return;

        if (config.getServerId().equals(dto.getOriginServerId())) {
            logger.debug("Chat relay inbound skipped (origin-self): " + dto.getMsgId());
            return;
        }
        if (!markSeen(dto.getMsgId())) {
            logger.debug("Chat relay inbound skipped (duplicate): " + dto.getMsgId());
            return;
        }

        String tag = config.resolvePeerTag(dto.getOriginServerId());
        String senderName = dto.getSenderName() != null ? dto.getSenderName() : "?";
        String message = dto.getMessage() != null ? dto.getMessage() : "";
        String formatted = "&8[&b" + tag + "&8]&r " + senderName + "&7: &f" + message;

        // Broadcast must run on the main thread — the inbound POST is handled off-thread by Jetty.
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(ChatFormat.colorize(formatted)));
        logger.debug("Chat relay inbound broadcast from " + tag + " (" + dto.getMsgId() + ")");
    }

    /**
     * Records a message id in the bounded dedup cache.
     *
     * @param msgId The message id
     * @return true if the id was newly recorded, false if it had already been seen
     */
    private boolean markSeen(String msgId) {
        synchronized (dedupCache) {
            if (dedupCache.contains(msgId)) {
                return false;
            }
            dedupCache.add(msgId);
            while (dedupCache.size() > dedupCacheSize) {
                Iterator<String> it = dedupCache.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                } else {
                    break;
                }
            }
            return true;
        }
    }
}
