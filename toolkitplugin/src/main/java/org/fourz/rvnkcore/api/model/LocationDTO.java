package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;

/**
 * Data Transfer Object representing a location in the Minecraft world.
 * 
 * This class encapsulates location data including world, coordinates, 
 * view angles, and optional metadata like biome information. Used throughout
 * the RVNKCore system for location-based operations and tracking.
 * 
 * @since 1.3.0
 */
public class LocationDTO {
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String biome;
    private Timestamp lastUpdated;
    
    /**
     * Default constructor for serialization frameworks.
     */
    public LocationDTO() {}
    
    /**
     * Constructor for basic location data.
     * 
     * @param worldName The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public LocationDTO(String worldName, double x, double y, double z) {
        this(worldName, x, y, z, 0.0f, 0.0f);
    }
    
    /**
     * Constructor with view angles.
     * 
     * @param worldName The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw View yaw angle
     * @param pitch View pitch angle
     */
    public LocationDTO(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Complete constructor with biome information.
     * 
     * @param worldName The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw View yaw angle
     * @param pitch View pitch angle
     * @param biome Biome name
     */
    public LocationDTO(String worldName, double x, double y, double z, float yaw, float pitch, String biome) {
        this(worldName, x, y, z, yaw, pitch);
        this.biome = biome;
    }
    
    // Getters and Setters
    
    public String getWorldName() {
        return worldName;
    }
    
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
    
    public double getX() {
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
    }
    
    public double getZ() {
        return z;
    }
    
    public void setZ(double z) {
        this.z = z;
    }
    
    public float getYaw() {
        return yaw;
    }
    
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
    
    public String getBiome() {
        return biome;
    }
    
    public void setBiome(String biome) {
        this.biome = biome;
    }
    
    public Timestamp getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    /**
     * Updates the last updated timestamp to current time.
     */
    public void updateTimestamp() {
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Calculates distance to another location (ignoring world differences).
     * 
     * @param other The other location
     * @return Distance in blocks
     */
    public double distanceTo(LocationDTO other) {
        if (other == null) return Double.MAX_VALUE;
        
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculates 2D distance (X and Z only) to another location.
     * 
     * @param other The other location
     * @return 2D distance in blocks
     */
    public double distance2DTo(LocationDTO other) {
        if (other == null) return Double.MAX_VALUE;
        
        double dx = this.x - other.x;
        double dz = this.z - other.z;
        
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Checks if this location is in the same world as another location.
     * 
     * @param other The other location
     * @return true if same world, false otherwise
     */
    public boolean isSameWorld(LocationDTO other) {
        if (other == null || this.worldName == null) return false;
        return this.worldName.equals(other.worldName);
    }
    
    /**
     * Creates a copy of this location.
     * 
     * @return New LocationDTO with same values
     */
    public LocationDTO copy() {
        LocationDTO copy = new LocationDTO(worldName, x, y, z, yaw, pitch, biome);
        copy.setLastUpdated(this.lastUpdated);
        return copy;
    }
    
    /**
     * Formats location as coordinates string.
     * 
     * @return Formatted coordinates (e.g., "100.5, 64.0, -200.3")
     */
    public String getCoordinatesString() {
        return String.format("%.1f, %.1f, %.1f", x, y, z);
    }
    
    /**
     * Formats location as a complete string with world.
     * 
     * @return Complete location string (e.g., "world: 100.5, 64.0, -200.3")
     */
    public String getLocationString() {
        return worldName + ": " + getCoordinatesString();
    }
    
    /**
     * Checks if location has valid coordinates (not NaN or infinite).
     * 
     * @return true if coordinates are valid
     */
    public boolean isValid() {
        return worldName != null && !worldName.trim().isEmpty() &&
               !Double.isNaN(x) && !Double.isInfinite(x) &&
               !Double.isNaN(y) && !Double.isInfinite(y) &&
               !Double.isNaN(z) && !Double.isInfinite(z);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        LocationDTO that = (LocationDTO) obj;
        
        return Double.compare(that.x, x) == 0 &&
               Double.compare(that.y, y) == 0 &&
               Double.compare(that.z, z) == 0 &&
               Float.compare(that.yaw, yaw) == 0 &&
               Float.compare(that.pitch, pitch) == 0 &&
               (worldName != null ? worldName.equals(that.worldName) : that.worldName == null);
    }
    
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = worldName != null ? worldName.hashCode() : 0;
        temp = Double.doubleToLongBits(x);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (yaw != +0.0f ? Float.floatToIntBits(yaw) : 0);
        result = 31 * result + (pitch != +0.0f ? Float.floatToIntBits(pitch) : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "LocationDTO{" +
                "world='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                (biome != null ? ", biome='" + biome + '\'' : "") +
                '}';
    }
}