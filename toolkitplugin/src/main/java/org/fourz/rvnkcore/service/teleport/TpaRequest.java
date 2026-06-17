package org.fourz.rvnkcore.service.teleport;

import java.util.UUID;

/**
 * Represents a pending TPA teleport request between two players.
 */
public class TpaRequest {

    public enum Type { TPA, TPAHERE }

    private final UUID sender;
    private final UUID target;
    private final Type type;
    private final long createdAt;
    private int expiryTaskId = -1;

    public TpaRequest(UUID sender, UUID target, Type type) {
        this.sender = sender;
        this.target = target;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getSender() { return sender; }
    public UUID getTarget() { return target; }
    public Type getType() { return type; }
    public long getCreatedAt() { return createdAt; }

    public int getExpiryTaskId() { return expiryTaskId; }
    public void setExpiryTaskId(int taskId) { this.expiryTaskId = taskId; }

    public boolean isExpired(long expireMs) {
        return System.currentTimeMillis() - createdAt > expireMs;
    }
}
