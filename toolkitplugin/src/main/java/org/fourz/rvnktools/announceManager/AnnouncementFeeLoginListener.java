package org.fourz.rvnktools.announceManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;

/**
 * Notifies players on login if any of their announcements were deactivated
 * due to insufficient funds for weekly fee payment.
 *
 * @since 1.5.0
 */
public class AnnouncementFeeLoginListener implements Listener {

    private final AnnouncementFeeCollector feeCollector;
    private final LogManager logger;

    public AnnouncementFeeLoginListener(AnnouncementFeeCollector feeCollector, LogManager logger) {
        this.feeCollector = feeCollector;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!feeCollector.hasPendingNotifications(player.getUniqueId())) {
            return;
        }

        // Delay message slightly so it appears after join messages
        player.getServer().getScheduler().runTaskLater(
            player.getServer().getPluginManager().getPlugins()[0], // RVNKCore is always first
            () -> {
                List<String> deactivatedIds = feeCollector.getAndClearNotifications(player.getUniqueId());
                if (deactivatedIds.isEmpty()) return;

                player.sendMessage("§6[Announcements] §c" + deactivatedIds.size() +
                    " of your announcement(s) were deactivated due to insufficient funds.");
                player.sendMessage("§6[Announcements] §7IDs: " + String.join(", ", deactivatedIds));
                player.sendMessage("§6[Announcements] §7Deposit funds and re-activate them to resume.");
            },
            60L // 3 seconds delay
        );
    }
}
