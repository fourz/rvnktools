package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object for world database records.
 *
 * Represents a managed world's metadata including status, type, spawn location,
 * and custom properties. Used for transferring world data between RVNKWorlds
 * and database/API layers.
 *
 * @since 1.3.0
 */
public class WorldDataDTO {

    private String worldName;
    private String status;
    private String templateName;
    private String worldType;
    private String environment;
    private String generator;
    private boolean generateStructures;
    private SpawnLocation spawn;
    private Timestamp importTime;
    private Timestamp lastUsed;
    private Map<String, String> properties;

    /**
     * Creates a new WorldDataDTO with default values.
     */
    public WorldDataDTO() {
        this.generateStructures = true;
        this.properties = new HashMap<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        this.importTime = now;
        this.lastUsed = now;
    }

    /**
     * Creates a new WorldDataDTO with the specified world name.
     *
     * @param worldName The world name (primary key)
     */
    public WorldDataDTO(String worldName) {
        this();
        this.worldName = worldName;
    }

    // Getters and setters

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getWorldType() {
        return worldType;
    }

    public void setWorldType(String worldType) {
        this.worldType = worldType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public boolean isGenerateStructures() {
        return generateStructures;
    }

    public void setGenerateStructures(boolean generateStructures) {
        this.generateStructures = generateStructures;
    }

    public SpawnLocation getSpawn() {
        return spawn;
    }

    public void setSpawn(SpawnLocation spawn) {
        this.spawn = spawn;
    }

    public Timestamp getImportTime() {
        return importTime;
    }

    public void setImportTime(Timestamp importTime) {
        this.importTime = importTime;
    }

    public Timestamp getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Timestamp lastUsed) {
        this.lastUsed = lastUsed;
    }

    /**
     * Updates the lastUsed timestamp to current time.
     */
    public void touch() {
        this.lastUsed = Timestamp.valueOf(LocalDateTime.now());
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    /**
     * Gets a property value by key.
     *
     * @param key The property key
     * @return The property value, or null if not found
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Sets a property value.
     *
     * @param key   The property key
     * @param value The property value
     */
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    /**
     * Validates that required fields are set.
     *
     * @return true if worldName is non-null and non-blank
     */
    public boolean isValid() {
        return worldName != null && !worldName.isBlank();
    }

    /**
     * Nested class for spawn location data.
     */
    public static class SpawnLocation {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        /**
         * Creates a new SpawnLocation with default values.
         */
        public SpawnLocation() {
            this.x = 0;
            this.y = 64;
            this.z = 0;
            this.yaw = 0;
            this.pitch = 0;
        }

        /**
         * Creates a new SpawnLocation with specified coordinates.
         *
         * @param x     X coordinate
         * @param y     Y coordinate
         * @param z     Z coordinate
         * @param yaw   Yaw rotation
         * @param pitch Pitch rotation
         */
        public SpawnLocation(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        // Getters and setters

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

        @Override
        public String toString() {
            return "SpawnLocation{" +
                    "x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    ", yaw=" + yaw +
                    ", pitch=" + pitch +
                    '}';
        }
    }

    /**
     * Builder pattern for constructing WorldDataDTO instances.
     */
    public static class Builder {
        private final WorldDataDTO dto = new WorldDataDTO();

        public Builder worldName(String worldName) {
            dto.worldName = worldName;
            return this;
        }

        public Builder status(String status) {
            dto.status = status;
            return this;
        }

        public Builder templateName(String templateName) {
            dto.templateName = templateName;
            return this;
        }

        public Builder worldType(String worldType) {
            dto.worldType = worldType;
            return this;
        }

        public Builder environment(String environment) {
            dto.environment = environment;
            return this;
        }

        public Builder generator(String generator) {
            dto.generator = generator;
            return this;
        }

        public Builder generateStructures(boolean generateStructures) {
            dto.generateStructures = generateStructures;
            return this;
        }

        public Builder spawn(SpawnLocation spawn) {
            dto.spawn = spawn;
            return this;
        }

        public Builder spawn(double x, double y, double z, float yaw, float pitch) {
            dto.spawn = new SpawnLocation(x, y, z, yaw, pitch);
            return this;
        }

        public Builder importTime(Timestamp importTime) {
            dto.importTime = importTime;
            return this;
        }

        public Builder lastUsed(Timestamp lastUsed) {
            dto.lastUsed = lastUsed;
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            dto.setProperties(properties);
            return this;
        }

        public Builder property(String key, String value) {
            dto.properties.put(key, value);
            return this;
        }

        public WorldDataDTO build() {
            return dto;
        }
    }

    /**
     * Creates a new Builder instance.
     *
     * @return A new Builder for WorldDataDTO
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "WorldDataDTO{" +
                "worldName='" + worldName + '\'' +
                ", status='" + status + '\'' +
                ", templateName='" + templateName + '\'' +
                ", lastUsed=" + lastUsed +
                '}';
    }
}
