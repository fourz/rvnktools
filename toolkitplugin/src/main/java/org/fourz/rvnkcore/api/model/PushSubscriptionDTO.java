package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;

/**
 * Data transfer object for web push subscriptions.
 * Stores browser push endpoint and encryption keys per player.
 *
 * @since 1.6.0
 */
public class PushSubscriptionDTO {

    private int id;
    private String playerId;
    private String endpoint;
    private String p256dh;
    private String authKey;
    private Timestamp createdAt;

    public PushSubscriptionDTO() {}

    public PushSubscriptionDTO(String playerId, String endpoint, String p256dh, String authKey) {
        this.playerId = playerId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.authKey = authKey;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }

    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
