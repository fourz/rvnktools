package org.fourz.rvnkcore.api.model.response;

/**
 * Response DTO for count operations in REST API.
 */
public class CountResponse {
    private long count;
    private String description;

    // Default constructor for JSON deserialization
    public CountResponse() {}

    public CountResponse(long count, String description) {
        this.count = count;
        this.description = description;
    }

    // Getters
    public long getCount() { return count; }
    public String getDescription() { return description; }

    // Setters for JSON deserialization
    public void setCount(long count) { this.count = count; }
    public void setDescription(String description) { this.description = description; }
}
