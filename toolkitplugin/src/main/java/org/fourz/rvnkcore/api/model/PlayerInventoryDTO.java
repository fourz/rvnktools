package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for player inventory data.
 *
 * Represents a player's inventory state for a specific world group.
 * Used for transferring inventory data between RVNKWorlds and database layer.
 *
 * @since 1.3.0
 */
public class PlayerInventoryDTO {

    private UUID playerId;
    private String worldGroup;
    private byte[] inventoryData;
    private Timestamp lastUpdated;

    /**
     * Creates a new PlayerInventoryDTO with default values.
     */
    public PlayerInventoryDTO() {
        this.lastUpdated = Timestamp.valueOf(LocalDateTime.now());
    }

    /**
     * Creates a new PlayerInventoryDTO with the specified player ID.
     *
     * @param playerId The player's UUID
     */
    public PlayerInventoryDTO(UUID playerId) {
        this();
        this.playerId = playerId;
    }

    // Getters and setters

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getWorldGroup() {
        return worldGroup;
    }

    public void setWorldGroup(String worldGroup) {
        this.worldGroup = worldGroup;
    }

    /**
     * Gets a defensive copy of the inventory data.
     *
     * @return Copy of the inventory data bytes, or null if not set
     */
    public byte[] getInventoryData() {
        return inventoryData != null ? inventoryData.clone() : null;
    }

    /**
     * Sets the inventory data with a defensive copy.
     *
     * @param inventoryData The inventory data bytes
     */
    public void setInventoryData(byte[] inventoryData) {
        this.inventoryData = inventoryData != null ? inventoryData.clone() : null;
    }

    /**
     * Gets the size of the inventory data in bytes.
     *
     * @return Size of inventory data, or 0 if null
     */
    public int getInventoryDataSize() {
        return inventoryData != null ? inventoryData.length : 0;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Updates the lastUpdated timestamp to current time.
     */
    public void touch() {
        this.lastUpdated = Timestamp.valueOf(LocalDateTime.now());
    }

    /**
     * Validates that required fields are set.
     *
     * @return true if playerId and worldGroup are non-null
     */
    public boolean isValid() {
        return playerId != null && worldGroup != null && !worldGroup.isBlank();
    }

    /**
     * Builder pattern for constructing PlayerInventoryDTO instances.
     */
    public static class Builder {
        private final PlayerInventoryDTO dto = new PlayerInventoryDTO();

        public Builder playerId(UUID playerId) {
            dto.playerId = playerId;
            return this;
        }

        public Builder worldGroup(String worldGroup) {
            dto.worldGroup = worldGroup;
            return this;
        }

        public Builder inventoryData(byte[] data) {
            dto.inventoryData = data != null ? data.clone() : null;
            return this;
        }

        public Builder lastUpdated(Timestamp timestamp) {
            dto.lastUpdated = timestamp;
            return this;
        }

        public PlayerInventoryDTO build() {
            return dto;
        }
    }

    /**
     * Creates a new Builder instance.
     *
     * @return A new Builder for PlayerInventoryDTO
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "PlayerInventoryDTO{" +
                "playerId=" + playerId +
                ", worldGroup='" + worldGroup + '\'' +
                ", dataSize=" + getInventoryDataSize() +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
