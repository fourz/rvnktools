package org.fourz.rvnkcore.api.model;

/**
 * Data Transfer Object for a cross-server portal.
 *
 * <p>A portal is a trigger block at a fixed world location that, when stepped on, transfers the
 * player to a named target server (resolved by the existing {@code TransferService}). This DTO is
 * the persistence/transport shape; the trigger-detection listeners are built separately (#1713/#1714).</p>
 *
 * <p>Lives in {@code api/model} alongside {@link PlayerWorldDataDTO} to match the DTO convention.</p>
 *
 * @since 1.5.24
 */
public class PortalDTO {

    private String portalId;
    private String world;
    private int x;
    private int y;
    private int z;
    private String targetServer;
    private String ownerUuid;
    private long createdAt;

    /**
     * Creates an empty PortalDTO. Use setters or the full constructor to populate.
     */
    public PortalDTO() {
    }

    /**
     * Creates a fully-populated PortalDTO.
     *
     * @param portalId     Unique portal identifier (UUID string)
     * @param world        Bukkit world name the trigger block resides in
     * @param x            Block X coordinate of the trigger block
     * @param y            Block Y coordinate of the trigger block
     * @param z            Block Z coordinate of the trigger block
     * @param targetServer Name of the target server (resolved by TransferService)
     * @param ownerUuid    UUID string of the player who created the portal (may be null)
     * @param createdAt    Creation timestamp in epoch milliseconds
     */
    public PortalDTO(String portalId, String world, int x, int y, int z,
                     String targetServer, String ownerUuid, long createdAt) {
        this.portalId = portalId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetServer = targetServer;
        this.ownerUuid = ownerUuid;
        this.createdAt = createdAt;
    }

    public String getPortalId() {
        return portalId;
    }

    public void setPortalId(String portalId) {
        this.portalId = portalId;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PortalDTO{" +
                "portalId='" + portalId + '\'' +
                ", world='" + world + '\'' +
                ", location=(" + x + "," + y + "," + z + ")" +
                ", targetServer='" + targetServer + '\'' +
                ", ownerUuid='" + ownerUuid + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
