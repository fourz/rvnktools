package org.fourz.rvnkcore.api.model;

/**
 * Data transfer object for a cross-server relayed chat message.
 *
 * <p>Carried as the JSON body of {@code POST /v1/chat/inbound} between RVNKCore peers.
 * A plain, gson-friendly POJO: every relayed line is uniquely identified by {@code msgId}
 * and tagged with its {@code originServerId} so receivers can suppress loops and duplicates.</p>
 *
 * @since 1.5.22
 */
public class ChatMessageDTO {

    private String msgId;
    private String originServerId;
    private String channel;
    private String senderUuid;
    private String senderName;
    private String message;
    private long timestamp;

    // ── Chatroom fields (#1729). Optional; older relay payloads omit them. ──
    /** Room the line was sent in: {@code GLOBAL} / {@code SERVER} / {@code WORLD}. */
    private String room;
    /** Sender's world name (for WORLD-scoped routing and detail-mode rendering). */
    private String world;
    /** Friendly origin-server label stamped by the sender (e.g. {@code nations}, {@code event}). */
    private String serverLabel;
    /**
     * Bracket tag for {@code BOT}-room lines (e.g. {@code Bot}), substituted into
     * {@code chat-relay.bot-format} as {@code {label}}. Ignored for player chat. (#1769)
     */
    private String label;
    /**
     * Raw tellraw JSON (array or object) for a styled {@code BOT}-room line (#1773). When present,
     * receivers render it via {@code tellraw @a <components>} — full colour/hover/click — instead of
     * the flat {@code bot-format}. Null falls back to {@link #message} + {@code bot-format}.
     */
    private String components;

    /** No-arg constructor for gson deserialization. */
    public ChatMessageDTO() {
    }

    /**
     * Creates a fully populated chat message DTO.
     *
     * @param msgId          Unique message identifier (UUID) for dedup
     * @param originServerId Server-id of the server that originated the message
     * @param channel        Channel classification (e.g. {@code "global"})
     * @param senderUuid     UUID of the sending player
     * @param senderName     Display name of the sending player
     * @param message        The plain chat text (channel trigger already stripped)
     * @param timestamp      Epoch milliseconds when the message was captured
     */
    public ChatMessageDTO(String msgId, String originServerId, String channel,
                          String senderUuid, String senderName, String message, long timestamp) {
        this.msgId = msgId;
        this.originServerId = originServerId;
        this.channel = channel;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMsgId() { return msgId; }
    public String getOriginServerId() { return originServerId; }
    public String getChannel() { return channel; }
    public String getSenderUuid() { return senderUuid; }
    public String getSenderName() { return senderName; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getServerLabel() { return serverLabel; }
    public void setServerLabel(String serverLabel) { this.serverLabel = serverLabel; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }
}
