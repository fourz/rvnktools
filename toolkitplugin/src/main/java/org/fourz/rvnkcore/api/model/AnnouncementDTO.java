package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Data Transfer Object for announcement information.
 * 
 * This class represents announcement data within the RVNKCore system,
 * including message content, scheduling information, and targeting options.
 * 
 * @since 1.0.0
 */
public class AnnouncementDTO {
    private String id;
    private String title;
    private String message;
    private String type;
    private boolean active;
    private boolean pinned;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp scheduledFor;
    private Timestamp expiresAt;
    private int intervalSeconds;
    private List<String> targetWorlds;
    private List<String> targetGroups;
    private Map<String, Object> metadata;
    private String ownerUuid;

    /**
     * Creates a new AnnouncementDTO with default values.
     */
    public AnnouncementDTO() {
        this.active = true;
        this.targetWorlds = new ArrayList<>();
        this.targetGroups = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.intervalSeconds = 0;
    }
    
    /**
     * Creates a new AnnouncementDTO with the specified ID.
     * 
     * @param id The announcement ID
     */
    public AnnouncementDTO(String id) {
        this();
        this.id = id;
    }
    
    // Core announcement information
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type != null ? type.toLowerCase() : null;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    // Timestamp tracking
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Timestamp getScheduledFor() {
        return scheduledFor;
    }
    
    public void setScheduledFor(Timestamp scheduledFor) {
        this.scheduledFor = scheduledFor;
    }
    
    public Timestamp getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    // Scheduling and targeting
    
    public int getIntervalSeconds() {
        return intervalSeconds;
    }
    
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
    
    public List<String> getTargetWorlds() {
        return targetWorlds;
    }
    
    public void setTargetWorlds(List<String> targetWorlds) {
        this.targetWorlds = targetWorlds != null ? targetWorlds : new ArrayList<>();
    }
    
    public List<String> getTargetGroups() {
        return targetGroups;
    }
    
    public void setTargetGroups(List<String> targetGroups) {
        this.targetGroups = targetGroups != null ? targetGroups : new ArrayList<>();
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
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
     * Checks if the announcement is currently valid (active and not expired).
     * 
     * @return true if the announcement is valid for display
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }
        
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        
        // Check if scheduled for future
        if (scheduledFor != null && now.before(scheduledFor)) {
            return false;
        }
        
        // Check if expired
        if (expiresAt != null && now.after(expiresAt)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if the announcement targets a specific world.
     * 
     * @param worldName The world name to check
     * @return true if the announcement targets the world or targets all worlds
     */
    public boolean targetsWorld(String worldName) {
        return targetWorlds.isEmpty() || targetWorlds.contains(worldName);
    }
    
    /**
     * Checks if the announcement targets a specific group.
     * 
     * @param groupName The group name to check
     * @return true if the announcement targets the group or targets all groups
     */
    public boolean targetsGroup(String groupName) {
        return targetGroups.isEmpty() || targetGroups.contains(groupName);
    }
    
    /**
     * Builder pattern for constructing AnnouncementDTO instances.
     */
    public static class Builder {
        private final AnnouncementDTO dto = new AnnouncementDTO();
        
        public Builder id(String id) {
            dto.id = id;
            return this;
        }
        
        public Builder title(String title) {
            dto.title = title;
            return this;
        }
        
        public Builder message(String message) {
            dto.message = message;
            return this;
        }
        
        public Builder type(String type) {
            dto.type = type != null ? type.toLowerCase() : null;
            return this;
        }
        
        public Builder active(boolean active) {
            dto.active = active;
            return this;
        }

        public Builder pinned(boolean pinned) {
            dto.pinned = pinned;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            dto.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Timestamp updatedAt) {
            dto.updatedAt = updatedAt;
            return this;
        }
        
        public Builder scheduledFor(Timestamp scheduledFor) {
            dto.scheduledFor = scheduledFor;
            return this;
        }
        
        public Builder expiresAt(Timestamp expiresAt) {
            dto.expiresAt = expiresAt;
            return this;
        }
        
        public Builder intervalSeconds(int intervalSeconds) {
            dto.intervalSeconds = intervalSeconds;
            return this;
        }
        
        public Builder targetWorlds(List<String> targetWorlds) {
            dto.targetWorlds = targetWorlds != null ? new ArrayList<>(targetWorlds) : new ArrayList<>();
            return this;
        }
        
        public Builder targetGroups(List<String> targetGroups) {
            dto.targetGroups = targetGroups != null ? new ArrayList<>(targetGroups) : new ArrayList<>();
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            dto.metadata.put(key, value);
            return this;
        }

        public Builder ownerUuid(String ownerUuid) {
            dto.ownerUuid = ownerUuid;
            return this;
        }

        public AnnouncementDTO build() {
            return dto;
        }
    }
    
    @Override
    public String toString() {
        return "AnnouncementDTO{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", active=" + active +
                ", ownerUuid='" + ownerUuid + '\'' +
                ", scheduledFor=" + scheduledFor +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
