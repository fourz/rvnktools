package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Data Transfer Object for teleport events.
 * 
 * Represents a single teleport event including source/destination locations,
 * reason for teleport, and timestamp. Used for privacy-focused location
 * tracking that only records meaningful location changes.
 * 
 * @since 1.3.0
 */
public class TeleportEventDTO {
    private Long id;
    private UUID playerId;
    private String fromWorld;
    private String toWorld;
    private double toX;
    private double toY;
    private double toZ;
    private float toYaw;
    private float toPitch;
    private TeleportReason reason;
    private Timestamp timestamp;
    
    /**
     * Enumeration of teleport reasons for categorization.
     */
    public enum TeleportReason {
        COMMAND("Command-based teleport"),
        PORTAL("Portal usage"),
        PLUGIN("Plugin-initiated teleport"),
        WORLD_CHANGE("World change event"),
        LOGIN("Player login/join"),
        DEATH("Death respawn"),
        HOME("Home teleport"),
        WARP("Warp usage"),
        OTHER("Other/Unknown");
        
        private final String description;
        
        TeleportReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Default constructor for database/serialization frameworks.
     */
    public TeleportEventDTO() {}
    
    /**
     * Constructor for creating new teleport events.
     * 
     * @param playerId The player's UUID
     * @param fromWorld Source world (null if first join)
     * @param toWorld Destination world
     * @param toX Destination X coordinate
     * @param toY Destination Y coordinate
     * @param toZ Destination Z coordinate
     * @param toYaw Destination yaw
     * @param toPitch Destination pitch
     * @param reason Reason for teleport
     */
    public TeleportEventDTO(UUID playerId, String fromWorld, String toWorld,
                           double toX, double toY, double toZ, float toYaw, float toPitch,
                           TeleportReason reason) {
        this.playerId = playerId;
        this.fromWorld = fromWorld;
        this.toWorld = toWorld;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.toYaw = toYaw;
        this.toPitch = toPitch;
        this.reason = reason;
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Convenience constructor using LocationDTO.
     * 
     * @param playerId The player's UUID
     * @param fromWorld Source world (null if first join)
     * @param toLocation Destination location
     * @param reason Reason for teleport
     */
    public TeleportEventDTO(UUID playerId, String fromWorld, LocationDTO toLocation, TeleportReason reason) {
        this(playerId, fromWorld, toLocation.getWorldName(),
             toLocation.getX(), toLocation.getY(), toLocation.getZ(),
             toLocation.getYaw(), toLocation.getPitch(), reason);
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
    
    public String getFromWorld() {
        return fromWorld;
    }
    
    public void setFromWorld(String fromWorld) {
        this.fromWorld = fromWorld;
    }
    
    public String getToWorld() {
        return toWorld;
    }
    
    public void setToWorld(String toWorld) {
        this.toWorld = toWorld;
    }
    
    public double getToX() {
        return toX;
    }
    
    public void setToX(double toX) {
        this.toX = toX;
    }
    
    public double getToY() {
        return toY;
    }
    
    public void setToY(double toY) {
        this.toY = toY;
    }
    
    public double getToZ() {
        return toZ;
    }
    
    public void setToZ(double toZ) {
        this.toZ = toZ;
    }
    
    public float getToYaw() {
        return toYaw;
    }
    
    public void setToYaw(float toYaw) {
        this.toYaw = toYaw;
    }
    
    public float getToPitch() {
        return toPitch;
    }
    
    public void setToPitch(float toPitch) {
        this.toPitch = toPitch;
    }
    
    public TeleportReason getReason() {
        return reason;
    }
    
    public void setReason(TeleportReason reason) {
        this.reason = reason;
    }
    
    public Timestamp getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Gets the destination location as a LocationDTO.
     * 
     * @return LocationDTO representing the destination
     */
    public LocationDTO getDestinationLocation() {
        return new LocationDTO(toWorld, toX, toY, toZ, toYaw, toPitch);
    }
    
    /**
     * Checks if this teleport represents a world change.
     * 
     * @return true if the teleport crosses world boundaries
     */
    public boolean isWorldChange() {
        return fromWorld != null && !fromWorld.equals(toWorld);
    }
    
    /**
     * Gets a human-readable description of this teleport event.
     * 
     * @return String description
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(reason.getDescription());
        
        if (fromWorld != null && !fromWorld.equals(toWorld)) {
            desc.append(" from ").append(fromWorld).append(" to ").append(toWorld);
        } else {
            desc.append(" in ").append(toWorld);
        }
        
        desc.append(" at (").append(String.format("%.1f", toX))
            .append(", ").append(String.format("%.1f", toY))
            .append(", ").append(String.format("%.1f", toZ)).append(")");
        
        return desc.toString();
    }
    
    @Override
    public String toString() {
        return "TeleportEventDTO{" +
                "id=" + id +
                ", playerId=" + playerId +
                ", fromWorld='" + fromWorld + '\'' +
                ", toWorld='" + toWorld + '\'' +
                ", location=(" + toX + "," + toY + "," + toZ + ")" +
                ", reason=" + reason +
                ", timestamp=" + timestamp +
                '}';
    }
}