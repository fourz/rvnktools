package org.fourz.rvnkcore.api.model.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for player world data in REST API.
 * Contains world-specific player information for external consumption.
 */
public class PlayerWorldDataResponse {
    private UUID playerId;
    private String playerName;
    private String worldName;
    private LocalDateTime firstVisit;
    private LocalDateTime lastVisit;
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

    // Default constructor for JSON deserialization
    public PlayerWorldDataResponse() {}

    // Builder pattern for construction
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getWorldName() { return worldName; }
    public LocalDateTime getFirstVisit() { return firstVisit; }
    public LocalDateTime getLastVisit() { return lastVisit; }
    public int getVisitCount() { return visitCount; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public double getLastX() { return lastX; }
    public double getLastY() { return lastY; }
    public double getLastZ() { return lastZ; }
    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public String getLastBiome() { return lastBiome; }
    public int getDeathCount() { return deathCount; }
    public Map<String, Object> getWorldSpecificData() { return worldSpecificData; }

    // Builder class
    public static class Builder {
        private PlayerWorldDataResponse response = new PlayerWorldDataResponse();

        public Builder playerId(UUID playerId) {
            response.playerId = playerId;
            return this;
        }

        public Builder playerName(String playerName) {
            response.playerName = playerName;
            return this;
        }

        public Builder worldName(String worldName) {
            response.worldName = worldName;
            return this;
        }

        public Builder firstVisit(LocalDateTime firstVisit) {
            response.firstVisit = firstVisit;
            return this;
        }

        public Builder lastVisit(LocalDateTime lastVisit) {
            response.lastVisit = lastVisit;
            return this;
        }

        public Builder visitCount(int visitCount) {
            response.visitCount = visitCount;
            return this;
        }

        public Builder playtimeSeconds(long playtimeSeconds) {
            response.playtimeSeconds = playtimeSeconds;
            return this;
        }

        public Builder location(double x, double y, double z, float yaw, float pitch) {
            response.lastX = x;
            response.lastY = y;
            response.lastZ = z;
            response.lastYaw = yaw;
            response.lastPitch = pitch;
            return this;
        }

        public Builder lastBiome(String lastBiome) {
            response.lastBiome = lastBiome;
            return this;
        }

        public Builder deathCount(int deathCount) {
            response.deathCount = deathCount;
            return this;
        }

        public Builder worldSpecificData(Map<String, Object> worldSpecificData) {
            response.worldSpecificData = worldSpecificData;
            return this;
        }

        public PlayerWorldDataResponse build() {
            return response;
        }
    }
}
