package org.fourz.rvnkcore.api.server.jetty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.model.response.PlayerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Main-thread snapshot of live Bukkit data for safe consumption by Jetty threads.
 *
 * A Bukkit scheduler task refreshes the snapshot every 10 seconds on the main thread.
 * Jetty handlers read the AtomicReference — no blocking, no main-thread contention.
 */
public class LiveDataCache {

    private static final int INTERVAL_TICKS = 200; // 10 seconds

    private final AtomicReference<BukkitSnapshot> ref = new AtomicReference<>(BukkitSnapshot.EMPTY);

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 0L, INTERVAL_TICKS);
    }

    private void refresh() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int maxPlayers = Bukkit.getMaxPlayers();

        List<PlayerResponse> players = online.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> worldGroups = new LinkedHashMap<>();
        for (Player p : online) {
            String world = p.getWorld().getName();
            worldGroups.computeIfAbsent(world, k -> new ArrayList<>())
                    .add(Map.of("name", p.getName(), "uuid", p.getUniqueId().toString()));
        }

        List<WorldSnapshot> worlds = Bukkit.getWorlds().stream()
                .map(w -> new WorldSnapshot(w.getName(), w.getEnvironment().name(), w.getPlayers().size()))
                .collect(Collectors.toList());

        ref.set(new BukkitSnapshot(
                Collections.unmodifiableList(players),
                Collections.unmodifiableMap(worldGroups),
                Collections.unmodifiableList(worlds),
                online.size(),
                maxPlayers,
                System.currentTimeMillis()
        ));
    }

    private PlayerResponse toResponse(Player p) {
        return PlayerResponse.builder()
                .uuid(p.getUniqueId())
                .name(p.getName())
                .online(true)
                .currentWorld(p.getWorld().getName())
                .timesJoined(1)
                .totalPlaytimeHours(0f)
                .build();
    }

    public BukkitSnapshot getSnapshot() {
        return ref.get();
    }

    public record WorldSnapshot(String name, String environment, int playerCount) {}

    public static class BukkitSnapshot {
        static final BukkitSnapshot EMPTY = new BukkitSnapshot(
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), 0, 0, 0L);

        public final List<PlayerResponse> onlinePlayers;
        public final Map<String, List<Map<String, Object>>> worldGroups;
        public final List<WorldSnapshot> worlds;
        public final int onlineCount;
        public final int maxPlayers;
        public final long capturedAt;

        BukkitSnapshot(List<PlayerResponse> onlinePlayers,
                       Map<String, List<Map<String, Object>>> worldGroups,
                       List<WorldSnapshot> worlds,
                       int onlineCount, int maxPlayers, long capturedAt) {
            this.onlinePlayers = onlinePlayers;
            this.worldGroups = worldGroups;
            this.worlds = worlds;
            this.onlineCount = onlineCount;
            this.maxPlayers = maxPlayers;
            this.capturedAt = capturedAt;
        }
    }
}
