package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.api.model.MatrixChannel;

import java.util.Optional;

/**
 * Rules for a chatbot line posted into the MATRIX room through this tier (#2383). Pure, so it is
 * unit-tested.
 *
 * <p>The tier, not the chatbot, builds the line: origin is this tier, there is no sender UUID, the
 * line kind is {@code bot}, and the persona is the sender and label. The text is cleaned and capped
 * like a Matrix inbound line, so a bot line can carry no more than a player line could.</p>
 */
public final class MatrixBotPost {

    /** Line kind the Matrix bridge reads as a bot post (no UUID, posted as a bot puppet). */
    public static final String CHANNEL = "bot";
    public static final String DEFAULT_PERSONA = "Bot";

    private MatrixBotPost() { }

    /**
     * Why a bot post is refused, or null when it is accepted.
     *
     * @param message the raw text
     * @param channel the resolved channel name ({@link MatrixChannel#resolveName}), null when malformed
     * @param listed  the channel as this tier lists it, empty when it is not listed
     */
    public static String refusal(String message, String channel, Optional<MatrixChannel> listed) {
        if (MatrixInbound.clean(message, MatrixInbound.MAX_MESSAGE).isEmpty()) return "message is empty";
        if (channel == null || listed.isEmpty()) return "unknown mchannel";
        if (listed.get().readOnly()) return "mchannel is read-only";
        return null;
    }

    /** Builds the MATRIX line for an accepted post. */
    public static ChatMessageDTO build(String msgId, String serverId, String serverLabel,
                                       String persona, String message, String channel, long now) {
        String sender = MatrixInbound.clean(persona, MatrixInbound.MAX_SENDER);
        if (sender.isEmpty()) sender = DEFAULT_PERSONA;
        ChatMessageDTO dto = new ChatMessageDTO(msgId, serverId, CHANNEL, null, sender,
                MatrixInbound.clean(message, MatrixInbound.MAX_MESSAGE), now);
        dto.setRoom(MatrixInbound.ROOM);
        dto.setMchannel(channel);
        dto.setLabel(sender);
        dto.setServerLabel(serverLabel);
        return dto;
    }
}
