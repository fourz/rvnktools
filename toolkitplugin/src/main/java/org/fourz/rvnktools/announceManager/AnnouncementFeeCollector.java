package org.fourz.rvnktools.announceManager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.service.announcement.DefaultAnnouncementService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Collects weekly fees for active announcements with weeklyFee > 0.
 * Deactivates announcements when the owner's balance is depleted,
 * setting deactivation_reason=payment_depleted in metadata.
 *
 * @since 1.5.0
 */
public class AnnouncementFeeCollector extends BukkitRunnable {

    private final AnnouncementService announcementService;
    private final Economy economy;
    private final LogManager logger;

    /** Tracks UUIDs whose announcements were deactivated for payment — cleared on next login */
    private final ConcurrentHashMap<UUID, List<String>> pendingNotifications = new ConcurrentHashMap<>();

    /** Type ID → weeklyFee cache, refreshed each cycle */
    private final ConcurrentHashMap<String, Integer> typeFeeCache = new ConcurrentHashMap<>();

    public AnnouncementFeeCollector(AnnouncementService announcementService, Economy economy, LogManager logger) {
        this.announcementService = announcementService;
        this.economy = economy;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            collectFees();
        } catch (Exception e) {
            logger.error("AnnouncementFeeCollector cycle failed", e);
        }
    }

    private void collectFees() {
        // Refresh type fee cache
        try {
            List<AnnouncementTypeDTO> types = announcementService.getAnnouncementTypes().get(15, TimeUnit.SECONDS);
            typeFeeCache.clear();
            for (AnnouncementTypeDTO type : types) {
                if (type.getWeeklyFee() != null && type.getWeeklyFee() > 0) {
                    typeFeeCache.put(type.getId(), type.getWeeklyFee());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to refresh type fee cache: " + e.getMessage());
            return;
        }

        if (typeFeeCache.isEmpty()) {
            logger.debug("AnnouncementFeeCollector: no types with weekly fees, skipping cycle");
            return;
        }

        try {
            List<AnnouncementDTO> all = announcementService.getAllAnnouncements().get(15, TimeUnit.SECONDS);
            int checked = 0;
            int collected = 0;
            int deactivated = 0;

            for (AnnouncementDTO ann : all) {
                if (!ann.isActive()) continue;
                if (ann.getOwnerUuid() == null) continue;

                Integer weeklyFee = typeFeeCache.get(ann.getType());
                if (weeklyFee == null || weeklyFee <= 0) continue;

                checked++;
                UUID ownerUuid = UUID.fromString(ann.getOwnerUuid());
                OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);

                if (economy.has(owner, weeklyFee)) {
                    economy.withdrawPlayer(owner, weeklyFee);
                    collected++;
                    logger.debug("Collected " + weeklyFee + " fee for announcement " + ann.getId() +
                        " from " + ann.getOwnerUuid());
                } else {
                    // Deactivate for payment
                    deactivateForPayment(ann);
                    deactivated++;

                    // Track for login notification
                    pendingNotifications.computeIfAbsent(ownerUuid, k -> new java.util.ArrayList<>())
                        .add(ann.getId());
                }
            }

            logger.info("AnnouncementFeeCollector: checked " + checked + " announcements, " +
                "collected " + collected + " fees, deactivated " + deactivated + " for payment");

        } catch (Exception e) {
            logger.error("Fee collection cycle failed", e);
        }
    }

    private void deactivateForPayment(AnnouncementDTO ann) {
        try {
            announcementService.deactivateAnnouncement(ann.getId()).get(15, TimeUnit.SECONDS);
            announcementService.updateAnnouncementMetadata(ann.getId(), "deactivation_reason", "payment_depleted")
                .get(15, TimeUnit.SECONDS);
            logger.info("Deactivated announcement " + ann.getId() + " for payment (owner: " + ann.getOwnerUuid() + ")");
        } catch (Exception e) {
            logger.error("Failed to deactivate announcement " + ann.getId() + " for payment", e);
        }
    }

    /**
     * Gets and clears pending payment-deactivation notifications for a player.
     *
     * @param uuid The player UUID
     * @return List of deactivated announcement IDs, or empty list
     */
    public List<String> getAndClearNotifications(UUID uuid) {
        List<String> ids = pendingNotifications.remove(uuid);
        return ids != null ? ids : List.of();
    }

    /**
     * Checks if a player has pending payment deactivation notifications.
     */
    public boolean hasPendingNotifications(UUID uuid) {
        return pendingNotifications.containsKey(uuid);
    }
}
