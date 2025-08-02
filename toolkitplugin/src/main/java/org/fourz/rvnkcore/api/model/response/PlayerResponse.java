package org.fourz.rvnkcore.api.model.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for player information in REST API.
 * Contains comprehensive global player data for external consumption.
 * For world-specific data, see PlayerWorldDataResponse.
 */
public class PlayerResponse {
    private UUID uuid;
    private String name;
    private boolean online;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private int timesJoined;
    private String currentWorld;
    private long totalPlaytimeMinutes;
    private List<String> groups;
    private List<String> nameHistory;
    private List<String> visitedWorlds;

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
    public String getCurrentWorld() { return currentWorld; }
    public long getTotalPlaytimeMinutes() { return totalPlaytimeMinutes; }
    public List<String> getGroups() { return groups; }
    public List<String> getNameHistory() { return nameHistory; }
    public List<String> getVisitedWorlds() { return visitedWorlds; }

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

        public Builder currentWorld(String currentWorld) {
            response.currentWorld = currentWorld;
            return this;
        }

        public Builder totalPlaytimeMinutes(long totalPlaytimeMinutes) {
            response.totalPlaytimeMinutes = totalPlaytimeMinutes;
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

        public Builder visitedWorlds(List<String> visitedWorlds) {
            response.visitedWorlds = visitedWorlds;
            return this;
        }

        public PlayerResponse build() {
            return response;
        }
    }
}
