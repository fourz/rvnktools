package org.fourz.rvnkcore.service.namecolor;

import org.bukkit.Bukkit;
import org.fourz.rvnkcore.api.model.NameColor;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.service.NameColorService;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * {@link NameColorService} on top of the player preferences (#2468): plugin id {@value #PLUGIN_ID}, metadata key
 * {@value #KEY}.
 */
public class DefaultNameColorService implements NameColorService {

    public static final String PLUGIN_ID = "rvnkcore";
    public static final String KEY = "chat.namecolor";

    private final PlayerPreferencesService prefs;
    private final LogManager logger;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private final List<BiConsumer<UUID, String>> listeners = new CopyOnWriteArrayList<>();

    public DefaultNameColorService(PlayerPreferencesService prefs, LogManager logger) {
        this.prefs = prefs;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Optional<String>> getColor(UUID player) {
        return prefs.getPreferences(player, PLUGIN_ID).thenApply(dto -> {
            String stored = dto != null && dto.getMetadata() != null ? dto.getMetadata().get(KEY) : null;
            String hex = NameColor.normalizeStored(stored);
            if (hex != null) cache.put(player, hex); else cache.remove(player);
            return Optional.ofNullable(hex);
        });
    }

    @Override
    public String cachedColor(UUID player) {
        return cache.get(player);
    }

    @Override
    public CompletableFuture<String> setColor(UUID player, String raw) {
        NameColor.Result r = NameColor.parse(raw);
        if (!r.ok()) return CompletableFuture.failedFuture(new IllegalArgumentException(r.error()));
        if (r.reset()) return reset(player).thenApply(v -> null);
        return store(player, r.hex()).thenApply(v -> r.hex());
    }

    @Override
    public CompletableFuture<Void> reset(UUID player) {
        return store(player, null);
    }

    private CompletableFuture<Void> store(UUID player, String hex) {
        return prefs.getPreferences(player, PLUGIN_ID).thenCompose(dto -> {
            if (dto == null) throw new IllegalStateException("no preferences row for " + player);
            // Copy: a default DTO may carry an unmodifiable map.
            Map<String, String> meta = new HashMap<>(dto.getMetadata() != null ? dto.getMetadata() : Map.of());
            if (hex == null) meta.remove(KEY); else meta.put(KEY, hex);
            dto.setMetadata(meta);
            return prefs.savePreferences(dto);
        }).thenRun(() -> {
            if (hex == null) cache.remove(player); else cache.put(player, hex);
            logger.debug("Name color of " + player + " is now " + (hex == null ? "default" : hex));
            for (BiConsumer<UUID, String> l : listeners) {
                try {
                    l.accept(player, hex);
                } catch (RuntimeException e) {
                    logger.warning("Name color listener failed: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> mayUse(UUID player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return CompletableFuture.completedFuture(null);
        try {
            return LuckPermsNodeCheck.has(player, NameColor.PERMISSION);
        } catch (RuntimeException | NoClassDefFoundError e) {
            logger.warning("LuckPerms check for " + player + " failed: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void addChangeListener(BiConsumer<UUID, String> listener) {
        listeners.add(listener);
    }

    @Override
    public void forget(UUID player) {
        cache.remove(player);
    }
}
