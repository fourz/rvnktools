package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for player-specific world data tracking.
 * 
 * This class represents player data tied to a specific world, enabling
 * world-specific location tracking, playtime, and statistics. This supports
 * the 'worldswap' teleport functionality that remembers player locations
 * in each world they've visited.
 * 
 * @since 1.0.0
 */
public class PlayerWorldDataDTO {
    private UUID playerId;
    private String worldName;
    private Timestamp firstVisit;
    private Timestamp lastVisit;
    private int visitCount;
    private long playtimeSeconds;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private String lastBiome;
    private int deathCount;
    private Map<String, Object> worldSpecificData;
    
    /**
     * Creates a new PlayerWorldDataDTO with default values.
     */
    public PlayerWorldDataDTO() {
        this.worldSpecificData = new HashMap<>();
        this.visitCount = 0;
        this.playtimeSeconds = 0L;
        this.deathCount = 0;
    }
    
    /**
     * Creates a new PlayerWorldDataDTO for the specified player and world.
     * 
     * @param playerId The player's UUID
     * @param worldName The world name
     */
    public PlayerWorldDataDTO(UUID playerId, String worldName) {
        this();
        this.playerId = playerId;
        this.worldName = worldName;
        this.firstVisit = Timestamp.valueOf(LocalDateTime.now());
        this.lastVisit = this.firstVisit;
        this.visitCount = 1;
    }
    
    // Core identifiers
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
    
    // Visit tracking
    
    public Timestamp getFirstVisit() {
        return firstVisit;
    }
    
    public void setFirstVisit(Timestamp firstVisit) {
        this.firstVisit = firstVisit;
    }
    
    public Timestamp getLastVisit() {
        return lastVisit;
    }
    
    public void setLastVisit(Timestamp lastVisit) {
        this.lastVisit = lastVisit;
    }
    
    public int getVisitCount() {
        return visitCount;
    }
    
    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }
    
    /**
     * Records a new visit to this world.
     */
    public void recordVisit() {
        this.lastVisit = Timestamp.valueOf(LocalDateTime.now());
        this.visitCount++;
        
        if (this.firstVisit == null) {
            this.firstVisit = this.lastVisit;
        }
    }
    
    // Playtime tracking
    
    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }
    
    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }
    
    /**
     * Adds playtime to this world.
     * 
     * @param seconds Additional seconds of playtime
     */
    public void addPlaytime(long seconds) {
        this.playtimeSeconds += seconds;
    }
    
    // Location tracking
    
    public double getLastX() {
        return lastX;
    }
    
    public void setLastX(double lastX) {
        this.lastX = lastX;
    }
    
    public double getLastY() {
        return lastY;
    }
    
    public void setLastY(double lastY) {
        this.lastY = lastY;
    }
    
    public double getLastZ() {
        return lastZ;
    }
    
    public void setLastZ(double lastZ) {
        this.lastZ = lastZ;
    }
    
    public float getLastYaw() {
        return lastYaw;
    }
    
    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
    }
    
    public float getLastPitch() {
        return lastPitch;
    }
    
    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
    }
    
    /**
     * Updates the player's last location in this world.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw View yaw angle
     * @param pitch View pitch angle
     */
    public void updateLocation(double x, double y, double z, float yaw, float pitch) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastVisit = Timestamp.valueOf(LocalDateTime.now());
    }
    
    // World-specific metadata
    
    public String getLastBiome() {
        return lastBiome;
    }
    
    public void setLastBiome(String lastBiome) {
        this.lastBiome = lastBiome;
    }
    
    public int getDeathCount() {
        return deathCount;
    }
    
    public void setDeathCount(int deathCount) {
        this.deathCount = deathCount;
    }
    
    /**
     * Records a death in this world.
     */
    public void recordDeath() {
        this.deathCount++;
    }
    
    public Map<String, Object> getWorldSpecificData() {
        return worldSpecificData;
    }
    
    public void setWorldSpecificData(Map<String, Object> worldSpecificData) {
        this.worldSpecificData = worldSpecificData != null ? worldSpecificData : new HashMap<>();
    }
    
    /**
     * Gets world-specific data by key.
     * 
     * @param key The data key
     * @return The data value, or null if not found
     */
    public Object getWorldData(String key) {
        return worldSpecificData.get(key);
    }
    
    /**
     * Sets world-specific data.
     * 
     * @param key The data key
     * @param value The data value
     */
    public void setWorldData(String key, Object value) {
        worldSpecificData.put(key, value);
    }
    
    /**
     * Builder pattern for constructing PlayerWorldDataDTO instances.
     */
    public static class Builder {
        private final PlayerWorldDataDTO dto = new PlayerWorldDataDTO();
        
        public Builder playerId(UUID playerId) {
            dto.playerId = playerId;
            return this;
        }
        
        public Builder worldName(String worldName) {
            dto.worldName = worldName;
            return this;
        }
        
        public Builder firstVisit(Timestamp firstVisit) {
            dto.firstVisit = firstVisit;
            return this;
        }
        
        public Builder lastVisit(Timestamp lastVisit) {
            dto.lastVisit = lastVisit;
            return this;
        }
        
        public Builder visitCount(int visitCount) {
            dto.visitCount = visitCount;
            return this;
        }
        
        public Builder playtimeSeconds(long playtimeSeconds) {
            dto.playtimeSeconds = playtimeSeconds;
            return this;
        }
        
        public Builder location(double x, double y, double z, float yaw, float pitch) {
            dto.lastX = x;
            dto.lastY = y;
            dto.lastZ = z;
            dto.lastYaw = yaw;
            dto.lastPitch = pitch;
            return this;
        }
        
        public Builder lastBiome(String lastBiome) {
            dto.lastBiome = lastBiome;
            return this;
        }
        
        public Builder deathCount(int deathCount) {
            dto.deathCount = deathCount;
            return this;
        }
        
        public Builder worldData(String key, Object value) {
            dto.worldSpecificData.put(key, value);
            return this;
        }
        
        public PlayerWorldDataDTO build() {
            if (dto.playerId == null || dto.worldName == null) {
                throw new IllegalStateException("PlayerID and WorldName are required");
            }
            return dto;
        }
    }
    
    @Override
    public String toString() {
        return "PlayerWorldDataDTO{" +
                "playerId=" + playerId +
                ", worldName='" + worldName + '\'' +
                ", visitCount=" + visitCount +
                ", playtimeSeconds=" + playtimeSeconds +
                ", lastVisit=" + lastVisit +
                ", location=(" + lastX + "," + lastY + "," + lastZ + ")" +
                '}';
    }
}
