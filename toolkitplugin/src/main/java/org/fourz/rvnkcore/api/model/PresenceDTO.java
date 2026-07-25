package org.fourz.rvnkcore.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object for a cross-server online-roster snapshot (#1728).
 *
 * <p>Carried as the JSON body of {@code POST /v1/presence/inbound} between RVNKCore peers, reusing the
 * same peer set, {@code X-API-Key}, and {@code insecure-tls} trust path as the chat relay (#1695). Each
 * snapshot is a full replacement of the sending server's roster — receivers overwrite that server's
 * cached entry and stamp a receive time for heartbeat-gap eviction.</p>
 *
 * @since 1.5.37
 */
public class PresenceDTO {

    private String originServerId;
    private String serverLabel;
    private long timestamp;
    private List<Entry> players = new ArrayList<>();

    /** No-arg constructor for gson deserialization. */
    public PresenceDTO() {
    }

    /**
     * Creates a fully populated presence snapshot.
     *
     * @param originServerId Server-id of the sending server (loop/self suppression + cache key)
     * @param serverLabel    Friendly origin label (e.g. {@code nations}, {@code event})
     * @param timestamp      Epoch milliseconds when the snapshot was captured
     * @param players        The sending server's online roster
     */
    public PresenceDTO(String originServerId, String serverLabel, long timestamp, List<Entry> players) {
        this.originServerId = originServerId;
        this.serverLabel = serverLabel;
        this.timestamp = timestamp;
        this.players = (players != null) ? players : new ArrayList<>();
    }

    public String getOriginServerId() { return originServerId; }
    public void setOriginServerId(String originServerId) { this.originServerId = originServerId; }

    public String getServerLabel() { return serverLabel; }
    public void setServerLabel(String serverLabel) { this.serverLabel = serverLabel; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public List<Entry> getPlayers() { return players; }
    public void setPlayers(List<Entry> players) { this.players = (players != null) ? players : new ArrayList<>(); }

    /**
     * A single online player in a roster snapshot.
     */
    public static class Entry {
        private String name;
        private String uuid;
        private String world;

        /** No-arg constructor for gson deserialization. */
        public Entry() {
        }

        /**
         * @param name  Player display name
         * @param uuid  Player UUID string
         * @param world World display name the player is in (may be null)
         */
        public Entry(String name, String uuid, String world) {
            this.name = name;
            this.uuid = uuid;
            this.world = world;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }

        public String getWorld() { return world; }
        public void setWorld(String world) { this.world = world; }
    }
}
