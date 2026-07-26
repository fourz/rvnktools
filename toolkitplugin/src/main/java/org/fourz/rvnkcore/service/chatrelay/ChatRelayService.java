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
    private volatile ChatRelayConfig config;
    private volatile ChatRelayEgress egress;
    private final LogManager logger;

    // Bounded insertion-ordered dedup set (eldest evicted first). Guarded by its own monitor.
    private final Set<String> dedupCache = Collections.synchronizedSet(new LinkedHashSet<>());
    private final int dedupCacheSize;

    // Optional external inbound consumer (e.g. RVNKEvents chatroom). When registered, RVNKCore hands
    // off inbound relayed messages to it (main thread) instead of the legacy local broadcast, and the
    // built-in !-trigger listener stands down — the consumer owns chat routing/rendering. (#1729)
    private volatile Consumer<ChatMessageDTO> externalConsumer;

    // Optional room injector (#1777): RVNKEvents delivers bot posts into the SERVER/WORLD rooms with
    // per-viewer rendering + world-relay. Null => injectRoom falls back to the flat bot-format render.
    private volatile Consumer<ChatMessageDTO> roomInjector;

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
     * Registers the room injector (#1777) — the outbound mirror of {@link #registerChatConsumer}.
     * RVNKEvents implements it to deliver bot posts into the SERVER/WORLD rooms with the same
     * per-viewer rendering (and world-relay) that player room chat gets. While none is registered,
     * {@link #injectRoom} falls back to the flat bot-format render so a standalone RVNKCore still
     * shows the line.
     *
     * @param injector the room-delivery consumer, or null to clear
     */
    public void registerRoomInjector(Consumer<ChatMessageDTO> injector) {
        this.roomInjector = injector;
        logger.info("Chat room injector " + (injector != null ? "registered" : "cleared")
            + " — " + (injector != null ? "bot room posts render via the chatroom"
                                        : "bot room posts fall back to bot-format"));
    }

    /** @return true when a room injector (RVNKEvents chatroom) has been registered. */
    public boolean hasRoomInjector() {
        return roomInjector != null;
    }

    /**
     * Posts a bot/persona line into a chat room (#1777). Unlike {@link #broadcast}, this is delivered
     * by the registered room injector (RVNKEvents) so it renders exactly like player room chat —
     * per-viewer display modes, world scoping, and the WORLD-relay allowlist.
     *
     * <p>SERVER lines never leave this server. WORLD lines are scoped to {@code world} locally and are
     * relayed to peers only by the injector, when world-relay allows it. This method deliberately does
     * NOT call egress itself — room routing is the injector's decision.</p>
     *
     * @param room       {@code SERVER} or {@code WORLD}
     * @param world      the target world name (required for WORLD; ignored for SERVER)
     * @param senderName the persona name (defaults to {@code Bot})
     * @param message    the message text (required; blank is ignored)
     */
    public void injectRoom(String room, String world, String senderName, String message) {
        if (message == null || message.isBlank()) return;

        String roomName = (room != null && !room.isBlank()) ? room.trim().toUpperCase() : "SERVER";
        String sender = (senderName != null && !senderName.isBlank()) ? senderName.trim() : "Bot";

        String msgId = UUID.randomUUID().toString();
        markSeen(msgId);

        ChatMessageDTO dto = new ChatMessageDTO(
            msgId,
            config.getServerId(),
            roomName.toLowerCase(),
            null,
            sender,
            message,
            System.currentTimeMillis()
        );
        dto.setRoom(roomName);
        dto.setWorld(world);
        dto.setServerLabel(config.getServerLabel());

        Consumer<ChatMessageDTO> injector = this.roomInjector;
        if (injector != null) {
            Bukkit.getScheduler().runTask(plugin, () -> injector.accept(dto));
            logger.debug("Chat room inject: " + roomName
                + (world != null && !world.isEmpty() ? ":" + world : "")
                + " by " + sender + " (" + msgId + ")");
            return;
        }

        // No chatroom present — degrade to the flat bot-format render (local only).
        dto.setLabel(roomName);
        renderBot(dto);
        logger.debug("Chat room inject fallback (no injector): " + roomName + " (" + msgId + ")");
    }

    /**
     * Swaps in a freshly-parsed configuration (e.g. from {@code /rvnkcore reload}) so peer / server-id /
     * insecure-tls changes take effect without a restart (#1743). Rebuilds the egress client so new
     * peers and TLS settings apply. No-op on null. The registered consumer and dedup cache are kept.
     *
     * @param newConfig the new chat-relay configuration
     */
    public void refreshConfig(ChatRelayConfig newConfig) {
        if (newConfig == null) return;
        this.config = newConfig;
        this.egress = new ChatRelayEgress(newConfig, logger);
        logger.info("ChatRelayService config refreshed — server-id=" + config.getServerId()
            + ", peers=" + config.getPeers().size() + ", insecure-tls=" + config.isInsecureTls());
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

    /**
     * Broadcasts a bot/persona line to this server and every peer (#1769, #1773). Unlike player chat, a
     * REST injection has no local chat event to piggyback on, so the origin renders locally itself; peers
     * render it in {@link #receiveInbound} via the {@code BOT}-room branch. The line carries {@code
     * room=BOT}, this server's id as origin, and a fresh {@code msgId} for dedup. Fan-out to peers only
     * happens when the relay is enabled; a standalone server still shows the line locally.
     *
     * <p>When {@code components} is a non-blank tellraw JSON string, every server renders it via
     * {@code tellraw @a <components>} (full colour/hover/click); otherwise it falls back to {@code
     * message} + {@code chat-relay.bot-format}. Sending both lets peers still on a pre-1.5.43 build
     * degrade to the flat text.</p>
     *
     * @param message    The plain-text fallback (used when components is blank, and by older peers)
     * @param senderName The persona name substituted as {@code {sender}} (defaults to {@code Bot})
     * @param label      The bracket tag substituted as {@code {label}} (defaults to {@code Bot})
     * @param components Raw tellraw JSON (array/object) for a styled line, or null/blank for plain text
     */
    public void broadcast(String message, String senderName, String label, String components) {
        boolean hasText = message != null && !message.isBlank();
        boolean hasComponents = components != null && !components.isBlank();
        if (!hasText && !hasComponents) return;

        String msgId = UUID.randomUUID().toString();
        markSeen(msgId);

        String sender = (senderName != null && !senderName.isBlank()) ? senderName.trim() : "Bot";
        String tag = (label != null && !label.isBlank()) ? label.trim() : "Bot";

        ChatMessageDTO dto = new ChatMessageDTO(
            msgId,
            config.getServerId(),
            "bot",
            null,
            sender,
            message,
            System.currentTimeMillis()
        );
        dto.setRoom("BOT");
        dto.setLabel(tag);
        dto.setServerLabel(config.getServerLabel());
        if (hasComponents) {
            dto.setComponents(components);
        }

        // Origin local echo (main thread — the inbound REST POST is handled off-thread by Jetty).
        renderBot(dto);

        // Fan out to peers (no-op with no peers / relay disabled).
        if (config.isEnabled()) {
            egress.send(dto);
        }
        logger.debug("Chat broadcast (bot): [" + tag + "] " + sender
            + (hasComponents ? " [styled]" : "") + " (" + msgId + ")");
    }

    /**
     * Renders a {@code BOT}-room line on the main thread: as a styled {@code tellraw @a <components>}
     * when the DTO carries tellraw JSON (#1773), otherwise as a flat {@code bot-format} broadcast.
     *
     * @param dto The BOT-room message
     */
    private void renderBot(ChatMessageDTO dto) {
        String comp = dto.getComponents();
        if (comp != null && !comp.isBlank()) {
            final String json = comp;
            Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a " + json));
        } else {
            final String line = formatBot(dto);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(ChatFormat.colorize(line)));
        }
    }

    /**
     * Renders a {@code BOT}-room line via {@code chat-relay.bot-format}, substituting {@code {label}},
     * {@code {sender}}, and {@code {message}}. Colour codes are applied by the caller.
     *
     * @param dto The BOT-room message
     * @return the substituted (not-yet-colourised) format string
     */
    private String formatBot(ChatMessageDTO dto) {
        String label = (dto.getLabel() != null && !dto.getLabel().isEmpty()) ? dto.getLabel() : "Bot";
        String sender = (dto.getSenderName() != null && !dto.getSenderName().isEmpty()) ? dto.getSenderName() : "Bot";
        String message = dto.getMessage() != null ? dto.getMessage() : "";
        return config.getBotFormat()
            .replace("{label}", label)
            .replace("{sender}", sender)
            .replace("{message}", message);
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

        // BOT-room lines (#1769, #1773) render via renderBot (styled tellraw when components are present,
        // else bot-format) and BYPASS the external chatroom consumer — a persona broadcast is not a
        // player chat line and must look identical on every tier.
        if ("BOT".equalsIgnoreCase(dto.getRoom())) {
            renderBot(dto);
            logger.debug("Chat relay inbound bot broadcast"
                + (dto.getComponents() != null && !dto.getComponents().isBlank() ? " [styled]" : "")
                + " (" + dto.getMsgId() + ")");
            return;
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
