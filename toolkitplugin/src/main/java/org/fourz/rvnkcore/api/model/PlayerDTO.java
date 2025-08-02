package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for comprehensive player information tracking.
 * 
 * This class represents global player data as stored and transferred within
 * the RVNKCore system, including activity tracking, name changes, and 
 * permission group information. For world-specific data like locations
 * and playtime, see PlayerWorldDataDTO.
 * 
 * @since 1.0.0
 */
public class PlayerDTO {
    private UUID id;
    private String currentName;
    private List<String> nameHistory;
    private Timestamp firstJoin;
    private Timestamp lastSeen;
    private String currentWorld;
    private int timesJoined;
    private long totalPlaytimeSeconds;
    private String primaryGroup;
    private List<String> groups;
    private boolean banned;
    private Map<String, Object> metadata;
    
    /**
     * Creates a new PlayerDTO with default values.
     */
    public PlayerDTO() {
        this.metadata = new HashMap<>();
        this.nameHistory = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.banned = false;
        this.timesJoined = 0;
        this.totalPlaytimeSeconds = 0L;
    }
    
    /**
     * Creates a new PlayerDTO with the specified UUID.
     * 
     * @param id The player's UUID
     */
    public PlayerDTO(UUID id) {
        this();
        this.id = id;
    }
    
    // Core player information getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getCurrentName() {
        return currentName;
    }
    
    public void setCurrentName(String currentName) {
        this.currentName = currentName;
    }
    
    public List<String> getNameHistory() {
        return nameHistory;
    }
    
    public void setNameHistory(List<String> nameHistory) {
        this.nameHistory = nameHistory != null ? nameHistory : new ArrayList<>();
    }
    
    /**
     * Updates the player's name and adds previous name to history if changed.
     * 
     * @param newName The new player name
     */
    public void updateName(String newName) {
        if (this.currentName != null && !this.currentName.equals(newName)) {
            this.nameHistory.add(this.currentName);
        }
        this.currentName = newName;
    }
    
    public Timestamp getFirstJoin() {
        return firstJoin;
    }
    
    public void setFirstJoin(Timestamp firstJoin) {
        this.firstJoin = firstJoin;
    }
    
    public Timestamp getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(Timestamp lastSeen) {
        this.lastSeen = lastSeen;
    }
    
    // Current world and activity tracking
    
    public String getCurrentWorld() {
        return currentWorld;
    }
    
    public void setCurrentWorld(String currentWorld) {
        this.currentWorld = currentWorld;
    }
    
    public int getTimesJoined() {
        return timesJoined;
    }
    
    public void setTimesJoined(int timesJoined) {
        this.timesJoined = timesJoined;
    }
    
    /**
     * Records a new login/join event.
     */
    public void recordJoin() {
        this.timesJoined++;
        this.lastSeen = Timestamp.valueOf(LocalDateTime.now());
        
        if (this.firstJoin == null) {
            this.firstJoin = this.lastSeen;
        }
    }
    
    public long getTotalPlaytimeSeconds() {
        return totalPlaytimeSeconds;
    }
    
    public void setTotalPlaytimeSeconds(long totalPlaytimeSeconds) {
        this.totalPlaytimeSeconds = totalPlaytimeSeconds;
    }
    
    /**
     * Adds playtime to the total.
     * 
     * @param seconds Additional seconds of playtime
     */
    public void addTotalPlaytime(long seconds) {
        this.totalPlaytimeSeconds += seconds;
    }
    
    // Permission group tracking
    
    public String getPrimaryGroup() {
        return primaryGroup;
    }
    
    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }
    
    public List<String> getGroups() {
        return groups;
    }
    
    public void setGroups(List<String> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }
    
    /**
     * Updates the player's permission group information.
     * 
     * @param primaryGroup The primary permission group
     * @param allGroups List of all groups the player belongs to
     */
    public void updateGroups(String primaryGroup, List<String> allGroups) {
        this.primaryGroup = primaryGroup;
        this.groups = allGroups != null ? new ArrayList<>(allGroups) : new ArrayList<>();
    }
    
    public boolean isBanned() {
        return banned;
    }
    
    public void setBanned(boolean banned) {
        this.banned = banned;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    /**
     * Gets a metadata value by key.
     * 
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Sets a metadata value.
     * 
     * @param key The metadata key
     * @param value The metadata value
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Builder pattern for constructing PlayerDTO instances.
     */
    public static class Builder {
        private final PlayerDTO dto = new PlayerDTO();
        
        public Builder id(UUID id) {
            dto.id = id;
            return this;
        }
        
        public Builder currentName(String currentName) {
            dto.currentName = currentName;
            return this;
        }
        
        public Builder nameHistory(List<String> nameHistory) {
            dto.nameHistory = nameHistory != null ? new ArrayList<>(nameHistory) : new ArrayList<>();
            return this;
        }
        
        public Builder firstJoin(Timestamp firstJoin) {
            dto.firstJoin = firstJoin;
            return this;
        }
        
        public Builder lastSeen(Timestamp lastSeen) {
            dto.lastSeen = lastSeen;
            return this;
        }
        
        public Builder currentWorld(String currentWorld) {
            dto.currentWorld = currentWorld;
            return this;
        }
        
        public Builder timesJoined(int timesJoined) {
            dto.timesJoined = timesJoined;
            return this;
        }
        
        public Builder totalPlaytimeSeconds(long totalPlaytimeSeconds) {
            dto.totalPlaytimeSeconds = totalPlaytimeSeconds;
            return this;
        }
        
        public Builder primaryGroup(String primaryGroup) {
            dto.primaryGroup = primaryGroup;
            return this;
        }
        
        public Builder groups(List<String> groups) {
            dto.groups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
            return this;
        }
        
        public Builder banned(boolean banned) {
            dto.banned = banned;
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            dto.metadata.put(key, value);
            return this;
        }
        
        public PlayerDTO build() {
            return dto;
        }
    }
    
    @Override
    public String toString() {
        return "PlayerDTO{" +
                "id=" + id +
                ", currentName='" + currentName + '\'' +
                ", lastSeen=" + lastSeen +
                ", currentWorld='" + currentWorld + '\'' +
                ", timesJoined=" + timesJoined +
                ", primaryGroup='" + primaryGroup + '\'' +
                ", banned=" + banned +
                '}';
    }
}
