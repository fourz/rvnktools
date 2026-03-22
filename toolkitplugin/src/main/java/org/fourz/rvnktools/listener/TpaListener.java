package org.fourz.rvnktools.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fourz.rvnkcore.service.teleport.BackLocationService;
import org.fourz.rvnkcore.service.teleport.DefaultBackLocationService;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;

/**
 * Listener for TPA warmup cancellation on movement and cleanup on quit.
 */
public class TpaListener implements Listener {

    private final TpaRequestService tpaService;
    private final BackLocationService backService;

    public TpaListener(TpaRequestService tpaService, BackLocationService backService) {
        this.tpaService = tpaService;
        this.backService = backService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (tpaService.hasActiveWarmup(event.getPlayer().getUniqueId())) {
            tpaService.checkWarmupMovement(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        tpaService.handlePlayerQuit(uuid);
        if (backService instanceof DefaultBackLocationService) {
            ((DefaultBackLocationService) backService).handlePlayerQuit(uuid);
        }
    }
}
