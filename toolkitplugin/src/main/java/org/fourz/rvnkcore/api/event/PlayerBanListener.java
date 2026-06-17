package org.fourz.rvnkcore.api.event;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.webhook.WebhookNotifier;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Listens for player kick events and detects bans.
 * When a ban is detected, updates the player record and fires a webhook
 * to trigger session revocation on the web frontend.
 *
 * @since 1.6.0
 */
public class PlayerBanListener implements Listener {

    private final ServiceRegistry registry;
    private final LogManager logger;

    public PlayerBanListener(ServiceRegistry registry, LogManager logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Check if this kick is due to a ban (ban list is updated before kick fires)
        if (!Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) {
            return;
        }

        logger.info("Ban detected for " + playerName + " — updating record and firing webhook");

        // Update player record with banned status
        try {
            PlayerService playerService = registry.getService(PlayerService.class);
            if (playerService != null) {
                playerService.getPlayer(player.getUniqueId())
                    .thenAccept(optDto -> {
                        optDto.ifPresent(dto -> {
                            dto.setBanned(true);
                            playerService.savePlayer(dto)
                                .exceptionally(ex -> {
                                    logger.error("Failed to save ban status for " + playerName, (Throwable) ex);
                                    return null;
                                });
                        });
                    })
                    .exceptionally(ex -> {
                        logger.error("Failed to fetch player for ban update: " + playerName, (Throwable) ex);
                        return null;
                    });
            }
        } catch (Exception e) {
            logger.error("Failed to update ban status for " + playerName, e);
        }

        // Fire webhook immediately (no debounce — bans are critical)
        try {
            WebhookNotifier notifier = registry.getService(WebhookNotifier.class);
            if (notifier != null) {
                notifier.notifyPlayerBanned(player.getUniqueId().toString(), playerName);
            }
        } catch (Exception e) {
            logger.debug("Webhook notifier not available for ban event: " + e.getMessage());
        }
    }
}
