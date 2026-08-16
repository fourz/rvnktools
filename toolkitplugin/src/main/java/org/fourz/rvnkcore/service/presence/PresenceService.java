package org.fourz.rvnkcore.service.presence;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.api.presence.PresenceEgress;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server presence service — merged online roster across peers (#1728).
 *
 * <p>Publishes this server's roster to configured peers through {@link PresenceEgress} on join/quit and
 * on a periodic heartbeat, and caches peers' rosters received via {@code PresenceController}. Remote
 * entries are evicted when a peer's heartbeat lapses ({@link #STALE_MULTIPLIER}× the heartbeat window),
 * so a peer going away drops its players within one window. Reuses the chat relay's peer set / auth /
 * TLS config (#1695); presence activates whenever chat relay is enabled with peers.</p>
 *
 * @since 1.5.37
 */
public class PresenceService {

    /** Heartbeat / full-roster republish interval, seconds. */
    public static final int HEARTBEAT_SECONDS = 20;
    /** A remote roster is evicted after this many missed heartbeats. */
    private static final int STALE_MULTIPLIER = 3;

    private final Plugin plugin;
    private final LogManager logger;
    private volatile ChatRelayConfig config;
    private volatile PresenceEgress egress;

    /** Remote rosters keyed by origin server-id. */
    private final Map<String, RemoteRoster> remotes = new ConcurrentHashMap<>();

    /** Listener notified (main thread) whenever the merged roster may have changed (e.g. scoreboard). */
    private volatile Runnable onChange;

    private BukkitTask heartbeatTask;

    /**
     * Creates a new PresenceService.
     *
     * @param plugin The owning plugin (main-thread scheduler + heartbeat task)
     * @param config Chat relay configuration (peers / auth / TLS reused for presence)
     * @param egress Egress dispatcher for peer roster POSTs
     * @param logger LogManager instance
     */
    public PresenceService(Plugin plugin, ChatRelayConfig config, PresenceEgress egress, LogManager logger) {
        this.plugin = plugin;
        this.config = config;
        this.egress = egress;
        this.logger = logger;
    }

    /** True when presence should be active (chat relay enabled with at least one peer). */
    public boolean isActive() {
        return config != null && config.isEnabled() && !config.getPeers().isEmpty();
    }

    /**
     * Rebuilds egress/config after a {@code /rvnkcore reload} (#1743).
     *
     * @param newConfig the freshly parsed chat relay config
     */
    public void refreshConfig(ChatRelayConfig newConfig) {
        this.config = newConfig;
        this.egress = new PresenceEgress(newConfig, logger);
        logger.info("PresenceService config refreshed - peers=" + newConfig.getPeers().size()
                + ", enabled=" + newConfig.isEnabled());
    }

    /** Registers the merged-roster change listener (invoked on the main thread). */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Starts the heartbeat: republishes the local roster to peers and evicts stale remotes on a fixed
     * interval. Safe to call once after registration.
     */
    public void startHeartbeat() {
        if (heartbeatTask != null || !isActive()) return;
        long ticks = HEARTBEAT_SECONDS * 20L;
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            broadcastLocalRoster();
            if (evictStale()) {
                fireChange();
            }
        }, ticks, ticks);
        logger.info("Presence heartbeat started (" + HEARTBEAT_SECONDS + "s, peers=" + config.getPeers().size() + ")");
    }

    /** Stops the heartbeat task. */
    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    /**
     * Publishes this server's current online roster to every peer. No-op when presence is inactive.
     * Safe to call from any thread — the roster is read on the main thread when called from listeners,
     * and this method only touches Bukkit's online-player view, so callers should invoke on the main
     * thread.
     */
    public void broadcastLocalRoster() {
        if (!isActive() || egress == null) return;
        List<PresenceDTO.Entry> entries = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String world = (p.getWorld() != null) ? p.getWorld().getName() : null;
            entries.add(new PresenceDTO.Entry(p.getName(), p.getUniqueId().toString(), world));
        }
        PresenceDTO dto = new PresenceDTO(config.getServerId(), config.getServerLabel(),
                System.currentTimeMillis(), entries);
        egress.send(dto);
    }

    /**
     * Accepts a peer's roster snapshot (called from {@code PresenceController} on a Jetty thread).
     * Overwrites that server's cached roster, drops self-origin snapshots, and notifies the change
     * listener on the main thread.
     *
     * @param dto the inbound roster snapshot
     */
    public void receiveInbound(PresenceDTO dto) {
        if (dto == null || dto.getOriginServerId() == null || dto.getOriginServerId().isBlank()) return;
        // Ignore a snapshot echoed back from ourselves.
        if (config != null && dto.getOriginServerId().equalsIgnoreCase(config.getServerId())) return;

        String label = (dto.getServerLabel() != null && !dto.getServerLabel().isBlank())
                ? dto.getServerLabel() : dto.getOriginServerId();
        List<PresenceDTO.Entry> players = (dto.getPlayers() != null) ? dto.getPlayers() : new ArrayList<>();
        remotes.put(dto.getOriginServerId(), new RemoteRoster(label, players, System.currentTimeMillis()));
        logger.debug("Presence inbound: " + label + " -> " + players.size() + " player(s)");
        fireChange();
    }

    /**
     * Evicts remote rosters whose last snapshot is older than the stale window.
     *
     * @return true when at least one roster was evicted
     */
    private boolean evictStale() {
        long cutoff = System.currentTimeMillis() - (STALE_MULTIPLIER * HEARTBEAT_SECONDS * 1000L);
        boolean removed = false;
        for (Map.Entry<String, RemoteRoster> e : remotes.entrySet()) {
            if (e.getValue().receivedAt < cutoff) {
                remotes.remove(e.getKey());
                logger.debug("Presence evicted stale roster: " + e.getValue().label);
                removed = true;
            }
        }
        return removed;
    }

    /** Runs the change listener on the main thread, if registered. */
    private void fireChange() {
        Runnable cb = onChange;
        if (cb == null) return;
        if (Bukkit.isPrimaryThread()) {
            cb.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, cb);
        }
    }

    /**
     * Returns the merged roster: this server first, then each non-stale remote, each group tagged with
     * its friendly server label. Local label comes from the chat relay config.
     *
     * @return an ordered list of server groups for display
     */
    public List<ServerGroup> getMergedRoster() {
        List<ServerGroup> groups = new ArrayList<>();
        // Local first.
        List<PresenceDTO.Entry> local = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String world = (p.getWorld() != null) ? p.getWorld().getName() : null;
            local.add(new PresenceDTO.Entry(p.getName(), p.getUniqueId().toString(), world));
        }
        local.sort(Comparator.comparing(e -> e.getName() == null ? "" : e.getName().toLowerCase()));
        String localLabel = (config != null) ? config.getServerLabel() : "local";
        groups.add(new ServerGroup(localLabel, true, local));

        // Remotes, stable by label.
        long cutoff = System.currentTimeMillis() - (STALE_MULTIPLIER * HEARTBEAT_SECONDS * 1000L);
        List<RemoteRoster> live = new ArrayList<>();
        for (RemoteRoster r : remotes.values()) {
            if (r.receivedAt >= cutoff) live.add(r);
        }
        live.sort(Comparator.comparing(r -> r.label == null ? "" : r.label.toLowerCase()));
        for (RemoteRoster r : live) {
            List<PresenceDTO.Entry> sorted = new ArrayList<>(r.players);
            sorted.sort(Comparator.comparing(e -> e.getName() == null ? "" : e.getName().toLowerCase()));
            groups.add(new ServerGroup(r.label, false, sorted));
        }
        return groups;
    }

    /** Total online across all servers (local + non-stale remotes). */
    public int totalCount() {
        int total = Bukkit.getOnlinePlayers().size();
        long cutoff = System.currentTimeMillis() - (STALE_MULTIPLIER * HEARTBEAT_SECONDS * 1000L);
        for (RemoteRoster r : remotes.values()) {
            if (r.receivedAt >= cutoff) total += r.players.size();
        }
        return total;
    }

    /** A cached remote roster with its receive time for eviction. */
    private static final class RemoteRoster {
        final String label;
        final List<PresenceDTO.Entry> players;
        final long receivedAt;

        RemoteRoster(String label, List<PresenceDTO.Entry> players, long receivedAt) {
            this.label = label;
            this.players = players;
            this.receivedAt = receivedAt;
        }
    }

    /** A server's roster group for display (label + local flag + players). */
    public static final class ServerGroup {
        private final String label;
        private final boolean local;
        private final List<PresenceDTO.Entry> players;

        ServerGroup(String label, boolean local, List<PresenceDTO.Entry> players) {
            this.label = label;
            this.local = local;
            this.players = players;
        }

        public String getLabel() { return label; }
        public boolean isLocal() { return local; }
        public List<PresenceDTO.Entry> getPlayers() { return players; }
    }
}
