package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing announcements across the RVNK plugin ecosystem.
 * 
 * Provides centralized access to announcement operations including creation,
 * scheduling, targeting, and delivery management. All operations are asynchronous
 * to prevent blocking the main thread.
 * 
 * @since 1.0.0
 */
public interface AnnouncementService {
    
    /**
     * Creates a new announcement.
     * 
     * @param announcement The announcement data to create
     * @return CompletableFuture containing the created announcement with generated ID
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if creation fails
     * @throws IllegalArgumentException if announcement is null or has invalid fields
     * @since 1.0.0
     */
    CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Retrieves an announcement by its ID.
     * 
     * @param id The unique identifier of the announcement
     * @return CompletableFuture containing the announcement if found
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @throws IllegalArgumentException if id is null
     * @since 1.0.0
     */
    CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id);
    
    /**
     * Updates an existing announcement.
     * 
     * @param announcement The announcement data to update
     * @return CompletableFuture containing the updated announcement
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if update fails
     * @throws IllegalArgumentException if announcement is null or has invalid ID
     * @since 1.0.0
     */
    CompletableFuture<AnnouncementDTO> updateAnnouncement(AnnouncementDTO announcement);
    
    /**
     * Deletes an announcement by its ID.
     * 
     * @param id The unique identifier of the announcement to delete
     * @return CompletableFuture that completes when the deletion is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if deletion fails
     * @throws IllegalArgumentException if id is null
     * @since 1.0.0
     */
    CompletableFuture<Void> deleteAnnouncement(String id);
    
    /**
     * Retrieves all announcements from the database.
     * 
     * WARNING: This method can return a large number of announcements and should be used
     * with caution on servers with many announcements. Consider using pagination
     * or getActiveAnnouncements() for better performance.
     * 
     * @return CompletableFuture containing list of all announcements
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements();
    
    /**
     * Retrieves all active announcements that are currently valid for display.
     * 
     * This includes announcements that are:
     * - Active flag is true
     * - Not expired (expires_at is null or in the future)
     * - Scheduled time has passed (scheduled_for is null or in the past)
     * 
     * @return CompletableFuture containing list of active announcements
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements();
    
    /**
     * Retrieves announcements by their type.
     * 
     * @param type The announcement type (e.g., "broadcast", "welcome", "warning")
     * @return CompletableFuture containing list of announcements of the specified type
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @throws IllegalArgumentException if type is null
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type);
    
    /**
     * Searches for announcements whose titles or messages match the provided pattern.
     * 
     * @param searchPattern The pattern to match (supports SQL LIKE syntax)
     * @return CompletableFuture containing list of matching announcements
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if search fails
     * @throws IllegalArgumentException if searchPattern is null
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> searchAnnouncements(String searchPattern);
    
    /**
     * Retrieves announcements that target a specific world.
     * 
     * @param worldName The name of the world to filter by
     * @return CompletableFuture containing list of announcements targeting the world
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @throws IllegalArgumentException if worldName is null
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForWorld(String worldName);
    
    /**
     * Retrieves announcements that target a specific permission group.
     * 
     * @param groupName The name of the permission group to filter by
     * @return CompletableFuture containing list of announcements targeting the group
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if retrieval fails
     * @throws IllegalArgumentException if groupName is null
     * @since 1.0.0
     */
    CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForGroup(String groupName);
    
    /**
     * Gets the total count of announcements in the database.
     * 
     * @return CompletableFuture containing the total announcement count
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if count operation fails
     * @since 1.0.0
     */
    CompletableFuture<Long> getAnnouncementCount();
    
    /**
     * Gets the count of active announcements.
     * 
     * @return CompletableFuture containing the active announcement count
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if count operation fails
     * @since 1.0.0
     */
    CompletableFuture<Long> getActiveAnnouncementCount();
    
    /**
     * Checks if an announcement with the given ID exists.
     * 
     * @param id The announcement ID to check
     * @return CompletableFuture containing true if the announcement exists
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if check fails
     * @throws IllegalArgumentException if id is null
     * @since 1.0.0
     */
    CompletableFuture<Boolean> announcementExists(String id);
    
    /**
     * Activates an announcement by setting its active flag to true.
     * 
     * @param id The unique identifier of the announcement to activate
     * @return CompletableFuture that completes when the activation is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if activation fails
     * @throws IllegalArgumentException if id is null
     * @since 1.0.0
     */
    CompletableFuture<Void> activateAnnouncement(String id);
    
    /**
     * Deactivates an announcement by setting its active flag to false.
     * 
     * @param id The unique identifier of the announcement to deactivate
     * @return CompletableFuture that completes when the deactivation is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if deactivation fails
     * @throws IllegalArgumentException if id is null
     * @since 1.0.0
     */
    CompletableFuture<Void> deactivateAnnouncement(String id);
    
    /**
     * Updates the metadata of an announcement.
     * 
     * @param id The announcement ID
     * @param key The metadata key
     * @param value The metadata value
     * @return CompletableFuture that completes when the update is finished
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if update fails
     * @throws IllegalArgumentException if id or key is null
     * @since 1.0.0
     */
    CompletableFuture<Void> updateAnnouncementMetadata(String id, String key, Object value);
    
    /**
     * Bulk creates new announcements.
     * 
     * This method creates multiple new announcements at once, generating unique IDs
     * for each. Unlike bulk import, this method creates fresh announcements.
     * 
     * @param announcements The list of announcements to create (IDs will be generated)
     * @return CompletableFuture containing list of created announcements with their IDs
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if creation fails
     * @throws IllegalArgumentException if announcements list is null
     * @since 1.3.0
     */
    CompletableFuture<List<AnnouncementDTO>> bulkCreateAnnouncements(List<AnnouncementDTO> announcements);

    /**
     * Bulk imports announcements from external sources (e.g., YAML migration).
     * 
     * This method is designed for migration scenarios and will skip announcements
     * that already exist in the database.
     * 
     * @param announcements The list of announcements to import
     * @return CompletableFuture containing count of successfully imported announcements
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if import fails
     * @throws IllegalArgumentException if announcements list is null
     * @since 1.0.0
     */
    CompletableFuture<Integer> bulkImportAnnouncements(List<AnnouncementDTO> announcements);
    
    /**
     * Gets comprehensive metrics about the announcement system.
     * 
     * Returns statistics including total count, active count, counts by type,
     * recent activity, and performance metrics.
     * 
     * @return CompletableFuture containing announcement metrics as JSON-formatted map
     * @throws org.fourz.rvnkcore.api.exception.ServiceException if metrics retrieval fails
     * @since 1.0.0
     */
    CompletableFuture<java.util.Map<String, Object>> getAnnouncementMetrics();
}
