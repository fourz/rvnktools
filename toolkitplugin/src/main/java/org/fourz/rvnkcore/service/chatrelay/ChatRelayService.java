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
import java.util.function.Consumer;

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

    // Optional external inbound consumer (e.g. RVNKEvents chatroom). When registered, RVNKCore hands
    // off inbound relayed messages to it (main thread) instead of the legacy local broadcast, and the
    // built-in !-trigger listener stands down — the consumer owns chat routing/rendering. (#1729)
    private volatile Consumer<ChatMessageDTO> externalConsumer;

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

    // ────────────────────────────────────────────────────────────────────────
    // Chatroom contract (#1729) — RVNKEvents owns rooms/rendering; RVNKCore = transport.
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Registers an external consumer (e.g. RVNKEvents' chatroom) to receive inbound relayed
     * messages. While one is registered, {@link #receiveInbound} delegates to it on the main thread
     * instead of broadcasting locally, and the built-in {@code !}-trigger listener stands down.
     *
     * @param consumer The inbound message consumer, or null to clear and restore built-in behaviour
     */
    public void registerChatConsumer(Consumer<ChatMessageDTO> consumer) {
        this.externalConsumer = consumer;
        logger.info("Chat relay consumer " + (consumer != null ? "registered" : "cleared")
            + " — " + (consumer != null ? "external chatroom owns routing/rendering"
                                        : "built-in !-trigger relay active"));
    }

    /** @return true when an external chatroom consumer has been registered. */
    public boolean hasExternalConsumer() {
        return externalConsumer != null;
    }

    /**
     * Relays a fully-built chat message to all peers. Used by an external chatroom that has already
     * classified the line (room/world/label). Records the id for dedup, then dispatches via egress.
     *
     * @param dto The message to relay (must carry a {@code msgId})
     */
    public void relay(ChatMessageDTO dto) {
        if (!config.isEnabled() || dto == null || dto.getMsgId() == null) return;
        markSeen(dto.getMsgId());
        egress.send(dto);
    }

    /** @return this server's id (e.g. {@code event}, {@code prod}). */
    public String getServerId() { return config.getServerId(); }

    /** @return this server's friendly label (e.g. {@code event}, {@code nations}). */
    public String getServerLabel() { return config.getServerLabel(); }

    /**
     * Resolves a friendly label for an origin server-id from the configured peer tags.
     *
     * @param originServerId The origin server-id
     * @return the peer tag, or the id itself when no peer matches
     */
    public String resolveServerLabel(String originServerId) {
        return config.resolvePeerTag(originServerId);
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

        // Fallback-stamp the friendly label from peer config when the origin did not (older payloads).
        if (dto.getServerLabel() == null || dto.getServerLabel().isEmpty()) {
            dto.setServerLabel(config.resolvePeerTag(dto.getOriginServerId()));
        }

        // Delegate to the external chatroom consumer (RVNKEvents) when registered — it owns rendering.
        Consumer<ChatMessageDTO> consumer = this.externalConsumer;
        if (consumer != null) {
            Bukkit.getScheduler().runTask(plugin, () -> consumer.accept(dto));
            logger.debug("Chat relay inbound -> external consumer (" + dto.getMsgId() + ")");
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
