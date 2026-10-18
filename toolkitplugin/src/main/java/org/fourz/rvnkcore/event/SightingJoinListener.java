package org.fourz.rvnkcore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.fourz.rvnkcore.service.sightings.SightingService;

import java.net.InetSocketAddress;

/**
 * Records a sighting on each join (#2351). {@code getAddress()} is the direct peer on a server with
 * no proxy, and the forwarded client address when Paper's proxy forwarding is on. The service only
 * queues work, so a join never waits on the database.
 */
public class SightingJoinListener implements Listener {

    private final SightingService service;

    public SightingJoinListener(SightingService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        InetSocketAddress addr = p.getAddress();
        service.recordJoin(p.getUniqueId(), p.getName(), addr == null ? null : addr.getAddress());
    }
}
