package org.fourz.rvnkcore.api.chat;

import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;

/** Test access to the egress peer filter. */
public final class ChatRelayEgressAccess {
    private ChatRelayEgressAccess() { }

    public static boolean shouldSend(ChatRelayConfig.Peer peer, ChatMessageDTO dto) {
        return ChatRelayEgress.shouldSend(peer, dto);
    }
}
