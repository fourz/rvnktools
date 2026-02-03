package org.fourz.rvnkcore.service.announce;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Service for managing announcements.
 * This provides async access to announcement functionality.
 */
public interface AnnouncementService {
    /**
     * Gets all active announcements.
     * 
     * @return CompletableFuture containing list of active announcements
     */
    CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements();
    
    /**
     * Creates a new announcement.
     * 
     * @param announcement The announcement data
     * @return CompletableFuture containing the created announcement
     */
    CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Deletes an announcement.
     * 
     * @param id The announcement ID
     * @return CompletableFuture containing true if deleted, false otherwise
     */
    CompletableFuture<Boolean> deleteAnnouncement(String id);
    
    /**
     * Broadcasts an announcement to all players.
     * 
     * @param announcement The announcement to broadcast
     * @return CompletableFuture for operation completion
     */
    CompletableFuture<Void> broadcastAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Broadcasts an announcement to a specific player.
     * 
     * @param announcement The announcement to broadcast
     * @param player The player to receive the announcement
     * @return CompletableFuture for operation completion
     */
    CompletableFuture<Void> broadcastAnnouncementToPlayer(AnnouncementDTO announcement, Player player);
}