package org.fourz.rvnktools.announceManager.data;

import java.util.List;

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
    List<Announcement> loadAnnouncements();
    
    void saveAnnounceType(AnnounceType announceType);
    List<AnnounceType> loadAnnounceTypes();
}