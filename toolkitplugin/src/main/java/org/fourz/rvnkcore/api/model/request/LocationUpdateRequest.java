package org.fourz.rvnkcore.api.model.request;

/**
 * Request DTO for updating player location via REST API.
 */
public class LocationUpdateRequest {
    private String world;
    private double x;
    private double y;
    private double z;

    // Default constructor for JSON deserialization
    public LocationUpdateRequest() {}

    public LocationUpdateRequest(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Getters
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    // Setters for JSON deserialization
    public void setWorld(String world) { this.world = world; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setZ(double z) { this.z = z; }

    /**
     * Validates the location update request.
     *
     * @return true if request is valid, false otherwise
     */
    public boolean isValid() {
        return world != null && !world.trim().isEmpty();
    }
}
