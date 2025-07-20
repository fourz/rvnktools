package org.fourz.rvnkcore.api.model.request;

import java.util.List;

/**
 * Request DTO for updating player groups via REST API.
 */
public class GroupUpdateRequest {
    private List<String> groups;
    private String action; // "add", "remove", "set"

    // Default constructor for JSON deserialization
    public GroupUpdateRequest() {}

    public GroupUpdateRequest(List<String> groups, String action) {
        this.groups = groups;
        this.action = action;
    }

    // Getters
    public List<String> getGroups() { return groups; }
    public String getAction() { return action; }

    // Setters for JSON deserialization
    public void setGroups(List<String> groups) { this.groups = groups; }
    public void setAction(String action) { this.action = action; }

    /**
     * Validates the group update request.
     *
     * @return true if request is valid, false otherwise
     */
    public boolean isValid() {
        return groups != null && !groups.isEmpty() &&
               action != null && isValidAction(action);
    }

    private boolean isValidAction(String action) {
        return "add".equals(action) || "remove".equals(action) || "set".equals(action);
    }
}
