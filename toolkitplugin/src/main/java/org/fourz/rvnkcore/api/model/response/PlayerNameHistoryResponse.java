package org.fourz.rvnkcore.api.model.response;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for player name history information in REST API.
 * Contains only name history data for external consumption.
 */
public class PlayerNameHistoryResponse {
    private UUID uuid;
    private String currentName;
    private List<String> nameHistory;

    // Default constructor for JSON deserialization
    public PlayerNameHistoryResponse() {}

    // Builder pattern for construction
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getUuid() { return uuid; }
    public String getCurrentName() { return currentName; }
    public List<String> getNameHistory() { return nameHistory; }

    // Builder class
    public static class Builder {
        private PlayerNameHistoryResponse response = new PlayerNameHistoryResponse();

        public Builder uuid(UUID uuid) {
            response.uuid = uuid;
            return this;
        }

        public Builder currentName(String currentName) {
            response.currentName = currentName;
            return this;
        }

        public Builder nameHistory(List<String> nameHistory) {
            response.nameHistory = nameHistory;
            return this;
        }

        public PlayerNameHistoryResponse build() {
            return response;
        }
    }
}
