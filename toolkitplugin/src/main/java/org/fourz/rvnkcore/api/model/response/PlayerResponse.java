package org.fourz.rvnkcore.api.model.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for player information in REST API.
 * Contains comprehensive player data for external consumption.
 */
public class PlayerResponse {
    private UUID uuid;
    private String name;
    private boolean online;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private int timesJoined;
    private String lastWorld;
    private double lastX;
    private double lastY;
    private double lastZ;
    private List<String> groups;
    private List<String> nameHistory;
    private long playtimeMinutes;

    // Default constructor for JSON deserialization
    public PlayerResponse() {}

    // Builder pattern for construction
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public boolean isOnline() { return online; }
    public LocalDateTime getFirstSeen() { return firstSeen; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public int getTimesJoined() { return timesJoined; }
    public String getLastWorld() { return lastWorld; }
    public double getLastX() { return lastX; }
    public double getLastY() { return lastY; }
    public double getLastZ() { return lastZ; }
    public List<String> getGroups() { return groups; }
    public List<String> getNameHistory() { return nameHistory; }
    public long getPlaytimeMinutes() { return playtimeMinutes; }

    // Builder class
    public static class Builder {
        private PlayerResponse response = new PlayerResponse();

        public Builder uuid(UUID uuid) {
            response.uuid = uuid;
            return this;
        }

        public Builder name(String name) {
            response.name = name;
            return this;
        }

        public Builder online(boolean online) {
            response.online = online;
            return this;
        }

        public Builder firstSeen(LocalDateTime firstSeen) {
            response.firstSeen = firstSeen;
            return this;
        }

        public Builder lastSeen(LocalDateTime lastSeen) {
            response.lastSeen = lastSeen;
            return this;
        }

        public Builder timesJoined(int timesJoined) {
            response.timesJoined = timesJoined;
            return this;
        }

        public Builder lastWorld(String lastWorld) {
            response.lastWorld = lastWorld;
            return this;
        }

        public Builder lastX(double lastX) {
            response.lastX = lastX;
            return this;
        }

        public Builder lastY(double lastY) {
            response.lastY = lastY;
            return this;
        }

        public Builder lastZ(double lastZ) {
            response.lastZ = lastZ;
            return this;
        }

        public Builder groups(List<String> groups) {
            response.groups = groups;
            return this;
        }

        public Builder nameHistory(List<String> nameHistory) {
            response.nameHistory = nameHistory;
            return this;
        }

        public Builder playtimeMinutes(long playtimeMinutes) {
            response.playtimeMinutes = playtimeMinutes;
            return this;
        }

        public PlayerResponse build() {
            return response;
        }
    }
}
