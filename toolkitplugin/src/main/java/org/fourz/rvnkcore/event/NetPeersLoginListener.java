package org.fourz.rvnkcore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.fourz.rvnkcore.service.netpeers.LoginEventReporter;

import java.net.InetSocketAddress;

/**
 * Reports each join to z-office (#2351). {@code getAddress()} is the forwarded client address when
 * Paper's proxy forwarding or PROXY protocol is on; otherwise it is the direct peer.
 */
public class NetPeersLoginListener implements Listener {

    private final LoginEventReporter reporter;

    public NetPeersLoginListener(LoginEventReporter reporter) {
        this.reporter = reporter;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        InetSocketAddress addr = p.getAddress();
        reporter.report(p.getUniqueId(), p.getName(), addr == null ? null : addr.getAddress());
    }
}
