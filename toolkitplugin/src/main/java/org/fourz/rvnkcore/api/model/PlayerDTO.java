package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for player information.
 * 
 * This class represents player data as stored and transferred within
 * the RVNKCore system. It contains essential player information and
 * supports additional metadata through a flexible map structure.
 */
public class PlayerDTO {
    private UUID id;
    private String username;
    private Timestamp firstJoin;
    private Timestamp lastSeen;
    private boolean banned;
    private Map<String, Object> metadata;
    
    /**
     * Creates a new PlayerDTO with default values.
     */
    public PlayerDTO() {
        this.metadata = new HashMap<>();
        this.banned = false;
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
    
    // Getters and Setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
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
        
        public Builder username(String username) {
            dto.username = username;
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
}
