package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object for announcement type information.
 * 
 * This class represents announcement type definitions within the RVNKCore system,
 * including display formatting, permissions, and optional fee structures.
 * 
 * @since Phase 1 Migration Framework
 */
public class AnnouncementTypeDTO {
    private String id;
    private String name;
    private String prefix;
    private String suffix;
    private String permission;
    private Integer listFee;
    private Integer weeklyFee;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Map<String, Object> metadata;
    
    /**
     * Creates a new AnnouncementTypeDTO with default values.
     */
    public AnnouncementTypeDTO() {
        this.active = true;
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates a new AnnouncementTypeDTO with the specified ID.
     * 
     * @param id The announcement type ID
     */
    public AnnouncementTypeDTO(String id) {
        this();
        this.id = id;
    }
    
    // Core type information
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id != null ? id.toLowerCase() : null;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public String getSuffix() {
        return suffix;
    }
    
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public void setPermission(String permission) {
        this.permission = permission;
    }
    
    // Fee structure (optional)
    
    public Integer getListFee() {
        return listFee;
    }
    
    public void setListFee(Integer listFee) {
        this.listFee = listFee;
    }
    
    public Integer getWeeklyFee() {
        return weeklyFee;
    }
    
    public void setWeeklyFee(Integer weeklyFee) {
        this.weeklyFee = weeklyFee;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    // Timestamp tracking
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Convenience method to set createdAt from LocalDateTime
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt != null ? Timestamp.valueOf(createdAt) : null;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Convenience method to set updatedAt from LocalDateTime
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt != null ? Timestamp.valueOf(updatedAt) : null;
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
     * Checks if this announcement type has fee requirements.
     * 
     * @return true if either list fee or weekly fee is set
     */
    public boolean hasFees() {
        return listFee != null || weeklyFee != null;
    }
    
    /**
     * Gets the total fee for this type.
     * 
     * @return sum of list fee and weekly fee, or 0 if no fees
     */
    public int getTotalFee() {
        int total = 0;
        if (listFee != null) total += listFee;
        if (weeklyFee != null) total += weeklyFee;
        return total;
    }
    
    /**
     * Builder pattern for constructing AnnouncementTypeDTO instances.
     */
    public static class Builder {
        private final AnnouncementTypeDTO dto = new AnnouncementTypeDTO();
        
        public Builder id(String id) {
            dto.id = id != null ? id.toLowerCase() : null;
            return this;
        }
        
        public Builder name(String name) {
            dto.name = name;
            return this;
        }
        
        public Builder prefix(String prefix) {
            dto.prefix = prefix;
            return this;
        }
        
        public Builder suffix(String suffix) {
            dto.suffix = suffix;
            return this;
        }
        
        public Builder permission(String permission) {
            dto.permission = permission;
            return this;
        }
        
        public Builder listFee(Integer listFee) {
            dto.listFee = listFee;
            return this;
        }
        
        public Builder weeklyFee(Integer weeklyFee) {
            dto.weeklyFee = weeklyFee;
            return this;
        }
        
        public Builder active(boolean active) {
            dto.active = active;
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
        
        public Builder metadata(String key, Object value) {
            dto.metadata.put(key, value);
            return this;
        }
        
        public AnnouncementTypeDTO build() {
            return dto;
        }
    }
    
    @Override
    public String toString() {
        return "AnnouncementTypeDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                ", permission='" + permission + '\'' +
                ", listFee=" + listFee +
                ", weeklyFee=" + weeklyFee +
                ", active=" + active +
                '}';
    }
}
