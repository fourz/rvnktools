package org.fourz.rvnkcore.api.presence;

import org.fourz.rvnkcore.api.config.ChatRelayConfig;

/** Test access to the presence peer filter. */
public final class PresenceEgressAccess {
    private PresenceEgressAccess() { }

    public static boolean isTierPeer(ChatRelayConfig.Peer peer) {
        return PresenceEgress.isTierPeer(peer);
    }
}
