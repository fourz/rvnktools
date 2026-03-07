package org.fourz.rvnktools.announceManager.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.Announcement;

public interface DataStore {
    void connect();
    void disconnect();
    
    // Add table state tracking methods
    boolean areTablesInitialized();
    void setTablesInitialized(boolean initialized);
    void initializeTables();
    
    boolean isEmpty();
    
    boolean announcementExists(String id);
    void saveAnnouncement(Announcement announcement);
    void deleteAnnouncement(String id);
    
    // This method now returns List for backward compatibility
    // but internally uses Map for better performance
    List<Announcement> loadAnnouncements();
    
    void saveAnnounceType(AnnounceType announceType);
    List<AnnounceType> loadAnnounceTypes();
    
    void savePlayerDisabledType(UUID playerId, String type);
    void removePlayerDisabledType(UUID playerId, String type);
    Set<String> getPlayerDisabledTypes(UUID playerId);
    Map<UUID, Set<String>> getAllPlayerDisabledTypes();
    
    // New preference methods
    void setPlayerPreference(UUID playerId, String property, String value);
    String getPlayerPreference(UUID playerId, String property);
    Map<String, String> getPlayerPreferences(UUID playerId);
    void deletePlayerPreference(UUID playerId, String property);
    
    // Mark old methods as deprecated
    /**
     * @deprecated Use setPlayerPreference(UUID, String, String) instead
     */
    @Deprecated
    void savePlayerPreferences(UUID playerId, String preferences);

}