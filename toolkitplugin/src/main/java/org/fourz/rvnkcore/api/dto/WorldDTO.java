package org.fourz.rvnkcore.api.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for world information.
 * Contains comprehensive world metadata including settings, spawn, and tracking data.
 * 
 * @since 1.0.0
 */
public class WorldDTO {
    
    /**
     * The world name (unique identifier).
     */
    private String name;
    
    /**
     * Display-friendly name for the world.
     */
    private String displayName;
    
    /**
     * World type (NORMAL, FLAT, LARGE_BIOMES, etc.).
     */
    private String worldType;
    
    /**
     * World environment (NORMAL, NETHER, THE_END, CUSTOM).
     */
    private String environment;
    
    /**
     * Path to the world folder.
     */
    private String worldFolder;
    
    /**
     * World seed used for generation.
     */
    private Long seed;
    
    /**
     * Name of the world generator plugin.
     */
    private String generatorName;
    
    /**
     * JSON string of generator settings.
     */
    private String generatorSettings;
    
    /**
     * World difficulty (PEACEFUL, EASY, NORMAL, HARD).
     */
    private String difficulty;
    
    /**
     * JSON string of game rule settings.
     */
    private String gameRuleSettings;
    
    // Spawn location
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;
    
    // World border settings
    private Double worldBorderSize;
    private Double worldBorderCenterX;
    private Double worldBorderCenterZ;
    
    // World settings
    private Boolean isActive;
    private Boolean isAutoSave;
    private Boolean keepSpawnInMemory;
    private Boolean allowAnimals;
    private Boolean allowMonsters;
    private Boolean allowPvp;
    private Boolean weatherEnabled;
    private Boolean thunderEnabled;
    
    // Tracking data
    private LocalDateTime firstLoaded;
    private LocalDateTime lastAccessed;
    private Long totalPlaytimeSeconds;
    private Integer playerCount;
    private Integer maxPlayersSeen;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Default constructor
    public WorldDTO() {}
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getWorldType() { return worldType; }
    public void setWorldType(String worldType) { this.worldType = worldType; }
    
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    
    public String getWorldFolder() { return worldFolder; }
    public void setWorldFolder(String worldFolder) { this.worldFolder = worldFolder; }
    
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
    
    public String getGeneratorName() { return generatorName; }
    public void setGeneratorName(String generatorName) { this.generatorName = generatorName; }
    
    public String getGeneratorSettings() { return generatorSettings; }
    public void setGeneratorSettings(String generatorSettings) { this.generatorSettings = generatorSettings; }
    
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    
    public String getGameRuleSettings() { return gameRuleSettings; }
    public void setGameRuleSettings(String gameRuleSettings) { this.gameRuleSettings = gameRuleSettings; }
    
    public Double getSpawnX() { return spawnX; }
    public void setSpawnX(Double spawnX) { this.spawnX = spawnX; }
    
    public Double getSpawnY() { return spawnY; }
    public void setSpawnY(Double spawnY) { this.spawnY = spawnY; }
    
    public Double getSpawnZ() { return spawnZ; }
    public void setSpawnZ(Double spawnZ) { this.spawnZ = spawnZ; }
    
    public Double getWorldBorderSize() { return worldBorderSize; }
    public void setWorldBorderSize(Double worldBorderSize) { this.worldBorderSize = worldBorderSize; }
    
    public Double getWorldBorderCenterX() { return worldBorderCenterX; }
    public void setWorldBorderCenterX(Double worldBorderCenterX) { this.worldBorderCenterX = worldBorderCenterX; }
    
    public Double getWorldBorderCenterZ() { return worldBorderCenterZ; }
    public void setWorldBorderCenterZ(Double worldBorderCenterZ) { this.worldBorderCenterZ = worldBorderCenterZ; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Boolean getIsAutoSave() { return isAutoSave; }
    public void setIsAutoSave(Boolean isAutoSave) { this.isAutoSave = isAutoSave; }
    
    public Boolean getKeepSpawnInMemory() { return keepSpawnInMemory; }
    public void setKeepSpawnInMemory(Boolean keepSpawnInMemory) { this.keepSpawnInMemory = keepSpawnInMemory; }
    
    public Boolean getAllowAnimals() { return allowAnimals; }
    public void setAllowAnimals(Boolean allowAnimals) { this.allowAnimals = allowAnimals; }
    
    public Boolean getAllowMonsters() { return allowMonsters; }
    public void setAllowMonsters(Boolean allowMonsters) { this.allowMonsters = allowMonsters; }
    
    public Boolean getAllowPvp() { return allowPvp; }
    public void setAllowPvp(Boolean allowPvp) { this.allowPvp = allowPvp; }
    
    public Boolean getWeatherEnabled() { return weatherEnabled; }
    public void setWeatherEnabled(Boolean weatherEnabled) { this.weatherEnabled = weatherEnabled; }
    
    public Boolean getThunderEnabled() { return thunderEnabled; }
    public void setThunderEnabled(Boolean thunderEnabled) { this.thunderEnabled = thunderEnabled; }
    
    public LocalDateTime getFirstLoaded() { return firstLoaded; }
    public void setFirstLoaded(LocalDateTime firstLoaded) { this.firstLoaded = firstLoaded; }
    
    public LocalDateTime getLastAccessed() { return lastAccessed; }
    public void setLastAccessed(LocalDateTime lastAccessed) { this.lastAccessed = lastAccessed; }
    
    public Long getTotalPlaytimeSeconds() { return totalPlaytimeSeconds; }
    public void setTotalPlaytimeSeconds(Long totalPlaytimeSeconds) { this.totalPlaytimeSeconds = totalPlaytimeSeconds; }
    
    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
    
    public Integer getMaxPlayersSeen() { return maxPlayersSeen; }
    public void setMaxPlayersSeen(Integer maxPlayersSeen) { this.maxPlayersSeen = maxPlayersSeen; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
