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
    
    void savePlayerPreferences(UUID playerId, String preferences);
    String getPlayerPreferences(UUID playerId);
}