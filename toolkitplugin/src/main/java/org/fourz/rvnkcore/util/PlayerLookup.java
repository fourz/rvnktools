package org.fourz.rvnkcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Centralized player name resolution with RVNKCore PlayerService integration.
 * Tries RVNKCore's cached player database first, falls back to Bukkit.getOfflinePlayer().
 *
 * <p>This utility lives in RVNKCore so consumer plugins (BarterShops, RVNKLore, etc.)
 * can use it without duplicating reflection-based lookups.</p>
 *
 * <p>Cache entries use a TTL-based expiry:</p>
 * <ul>
 *   <li>Online players → permanent (evicted on next miss after they leave)</li>
 *   <li>DB / Mojang resolutions → 8-hour TTL, background stale re-check triggers Mojang</li>
 * </ul>
 */
public class PlayerLookup {

    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(8);

    private final LogManager logger;
    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, TimedName> nameCache = new ConcurrentHashMap<>();

    private PlayerService playerService;
    private boolean rvnkCoreEnabled = false;
    private MojangAPI mojangAPI;
    private boolean mojangApiEnabled = false;

    public PlayerLookup(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerLookup");
        initPlayerService();
    }

    /**
     * Enables Mojang API integration for name/UUID resolution.
     * Creates a MojangAPI instance with rate limiting.
     *
     * @return this PlayerLookup for chaining
     */
    public PlayerLookup enableMojangAPI() {
        if (mojangAPI == null) {
            mojangAPI = new MojangAPI(plugin);
            mojangApiEnabled = true;
            logger.info("MojangAPI integration enabled with rate limiting");
        }
        return this;
    }

    /**
     * Checks if Mojang API integration is enabled.
     */
    public boolean isMojangApiEnabled() {
        return mojangApiEnabled && mojangAPI != null;
    }

    /**
     * Gets the MojangAPI instance if enabled.
     *
     * @return Optional containing MojangAPI, or empty if not enabled
     */
    public Optional<MojangAPI> getMojangAPI() {
        return Optional.ofNullable(mojangAPI);
    }

    private void initPlayerService() {
        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core == null) {
                logger.debug("RVNKCore instance not available - using Bukkit fallback");
                return;
            }

            PlayerService service = core.getPlayerService();
            if (service != null) {
                this.playerService = service;
                this.rvnkCoreEnabled = true;
                logger.info("RVNKCore PlayerService integration enabled");
            } else {
                logger.info("RVNKCore PlayerService not registered - using Bukkit fallback");
            }
        } catch (IllegalStateException e) {
            logger.debug("RVNKCore not initialized - using Bukkit fallback");
        } catch (IllegalArgumentException e) {
            logger.debug("PlayerService not found in registry - using Bukkit fallback");
        } catch (Exception e) {
            logger.debug("PlayerService init failed: " + e.getMessage());
        }
    }

    /**
     * Gets a player name by UUID. Tries cache, online players, RVNKCore DB, then Bukkit.
     * Cache entries expire after 8 hours; expired entries are removed on access.
     *
     * @param uuid The player UUID
     * @return The player name, or a truncated UUID if unknown
     */
    public String getPlayerName(UUID uuid) {
        if (uuid == null) return "Unknown";

        // Check TTL-aware cache
        TimedName cached = nameCache.get(uuid);
        if (cached != null) {
            if (!cached.isExpired()) return cached.name();
            nameCache.remove(uuid);
        }

        // Check online players first (most reliable)
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            String name = onlinePlayer.getName();
            nameCache.put(uuid, TimedName.permanent(name));
            return name;
        }

        // Try RVNKCore PlayerService (sync, 2s timeout)
        if (rvnkCoreEnabled) {
            String name = lookupViaPlayerService(uuid);
            if (name != null) {
                nameCache.put(uuid, TimedName.expiring(name));
                return name;
            }
        }

        // Fallback to Bukkit
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        if (name != null) {
            nameCache.put(uuid, TimedName.expiring(name));
            return name;
        }

        // Last resort: Mojang API (sync, safe on non-main threads like Jetty)
        if (mojangApiEnabled) {
            try {
                Optional<String> mojangName = mojangAPI.getNameByUuid(uuid).get(3, TimeUnit.SECONDS);
                if (mojangName.isPresent()) {
                    nameCache.put(uuid, TimedName.expiring(mojangName.get()));
                    persistExternalPlayer(uuid, mojangName.get());
                    return mojangName.get();
                }
            } catch (Exception e) {
                logger.debug("Mojang API lookup failed for " + uuid + ": " + e.getMessage());
            }
        }

        // Final fallback: short UUID
        name = uuid.toString().substring(0, 8);
        nameCache.put(uuid, TimedName.expiring(name));
        return name;
    }

    private String lookupViaPlayerService(UUID uuid) {
        try {
            Optional<PlayerDTO> result = playerService.getPlayer(uuid).get(2, TimeUnit.SECONDS);
            if (result.isPresent()) {
                PlayerDTO p = result.get();
                String name = p.getCurrentName();
                if (name != null) {
                    // Background stale check — Mojang re-validates if last seen > 8h ago
                    boolean isStale = p.getLastSeen() != null &&
                        (System.currentTimeMillis() - p.getLastSeen().getTime()) > CACHE_TTL_MS;
                    if (isStale && mojangApiEnabled) {
                        mojangAPI.getNameByUuid(uuid).thenAccept(opt ->
                            opt.ifPresent(freshName -> persistExternalPlayer(uuid, freshName))
                        );
                    }
                    return name;
                }
            }
        } catch (Exception e) {
            logger.debug("RVNKCore lookup failed for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if RVNKCore PlayerService is available.
     */
    public boolean isRVNKCoreEnabled() {
        return rvnkCoreEnabled;
    }

    /**
     * Clears the name cache. Call on reload.
     */
    public void clearCache() {
        nameCache.clear();
        if (mojangAPI != null) {
            mojangAPI.clearCache();
        }
    }

    /**
     * Pre-loads all known player names from the RVNKCore database into the local cache.
     * Called once at startup so cross-server players (never joined this server) resolve fast.
     * Runs asynchronously and does not block startup.
     */
    public void preloadFromDatabase() {
        if (!rvnkCoreEnabled) return;
        playerService.getAllPlayers().thenAccept(players -> {
            int loaded = 0;
            for (PlayerDTO player : players) {
                if (player.getId() != null && player.getCurrentName() != null) {
                    nameCache.put(player.getId(), TimedName.expiring(player.getCurrentName()));
                    loaded++;
                }
            }
            logger.info("Pre-loaded " + loaded + " player names from database");
        }).exceptionally(e -> {
            logger.debug("Failed to pre-load player names: " + e.getMessage());
            return null;
        });
    }

    // ==========================================
    // Async Methods (with Mojang API support)
    // ==========================================

    /**
     * Async version of getPlayerName with Mojang API fallback.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Local TTL-aware name cache</li>
     *   <li>Online player (authoritative, permanent cache)</li>
     *   <li>RVNKCore PlayerService async (expiring cache; background staleness re-check)</li>
     *   <li>Bukkit OfflinePlayer cache (expiring cache)</li>
     *   <li>Mojang API (if enabled, rate limited; triggers DB write-back)</li>
     * </ol>
     *
     * @param uuid The player UUID
     * @return CompletableFuture containing the player name, or truncated UUID if unknown
     */
    public CompletableFuture<String> getPlayerNameAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture("Unknown");
        }

        // Check TTL-aware cache
        TimedName cached = nameCache.get(uuid);
        if (cached != null) {
            if (!cached.isExpired()) return CompletableFuture.completedFuture(cached.name());
            nameCache.remove(uuid);
        }

        // Online player (sync, authoritative — permanent TTL)
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            String name = onlinePlayer.getName();
            nameCache.put(uuid, TimedName.permanent(name));
            return CompletableFuture.completedFuture(name);
        }

        // RVNKCore PlayerService (async — expiring TTL + background stale check)
        if (rvnkCoreEnabled) {
            return playerService.getPlayer(uuid).thenCompose(result -> {
                if (result.isPresent()) {
                    PlayerDTO p = result.get();
                    String dbName = p.getCurrentName();
                    if (dbName != null) {
                        boolean isStale = p.getLastSeen() != null &&
                            (System.currentTimeMillis() - p.getLastSeen().getTime()) > CACHE_TTL_MS;
                        if (isStale && mojangApiEnabled) {
                            mojangAPI.getNameByUuid(uuid).thenAccept(opt ->
                                opt.ifPresent(freshName -> persistExternalPlayer(uuid, freshName))
                            );
                        }
                        nameCache.put(uuid, TimedName.expiring(dbName));
                        return CompletableFuture.completedFuture(dbName);
                    }
                }
                return resolveExternally(uuid);
            }).exceptionally(e -> {
                logger.debug("RVNKCore async lookup failed for " + uuid + ": " + e.getMessage());
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() != null ? player.getName() : uuid.toString().substring(0, 8);
                nameCache.put(uuid, TimedName.expiring(name));
                return name;
            });
        }

        return resolveExternally(uuid);
    }

    /**
     * Resolves a player name from Bukkit cache or Mojang API.
     * Used as a fallback when the DB is unavailable or has no record.
     */
    private CompletableFuture<String> resolveExternally(UUID uuid) {
        // Bukkit offline cache (sync, fast)
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.getName() != null) {
            nameCache.put(uuid, TimedName.permanent(player.getName()));
            return CompletableFuture.completedFuture(player.getName());
        }

        // Mojang API fallback (async, rate-limited; triggers DB write-back)
        if (mojangApiEnabled && mojangAPI != null) {
            return mojangAPI.getNameByUuid(uuid).thenApply(opt -> {
                if (opt.isPresent()) {
                    String mojangName = opt.get();
                    nameCache.put(uuid, TimedName.expiring(mojangName));
                    persistExternalPlayer(uuid, mojangName);
                    return mojangName;
                }
                String truncated = uuid.toString().substring(0, 8);
                nameCache.put(uuid, TimedName.expiring(truncated));
                return truncated;
            });
        }

        // Final fallback: truncated UUID
        String truncated = uuid.toString().substring(0, 8);
        nameCache.put(uuid, TimedName.expiring(truncated));
        return CompletableFuture.completedFuture(truncated);
    }

    /**
     * Writes a Mojang-resolved player to the RVNKCore database and detects name changes.
     * Called asynchronously after Mojang resolution — never blocks the caller.
     */
    private void persistExternalPlayer(UUID uuid, String mojangName) {
        if (!rvnkCoreEnabled) return;
        playerService.getPlayer(uuid).thenAccept(existing -> {
            if (existing.isPresent()) {
                String storedName = existing.get().getCurrentName();
                if (!storedName.equalsIgnoreCase(mojangName)) {
                    logger.info("Name change detected: " + storedName + " -> " + mojangName + " (" + uuid + ")");
                    playerService.updatePlayerName(uuid, mojangName)
                        .exceptionally(e -> {
                            logger.debug("Failed to update player name: " + e.getMessage());
                            return null;
                        });
                }
                // else: name unchanged, DB already correct — no-op
            } else {
                // Player unknown to DB — create minimal external-player row
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                PlayerDTO newPlayer = new PlayerDTO.Builder()
                    .id(uuid)
                    .currentName(mojangName)
                    .firstJoin(now)
                    .lastSeen(now)
                    .build();
                playerService.savePlayer(newPlayer)
                    .exceptionally(e -> {
                        logger.debug("Failed to persist external player: " + e.getMessage());
                        return null;
                    });
            }
        }).exceptionally(e -> {
            logger.debug("Failed to check existing player for write-back: " + e.getMessage());
            return null;
        });
    }

    /**
     * Resolves a player name to UUID.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Online players</li>
     *   <li>Bukkit OfflinePlayer cache</li>
     *   <li>Mojang API (if enabled, rate limited)</li>
     * </ol>
     *
     * @param name The player name to resolve
     * @return CompletableFuture containing Optional with UUID, or empty if not found
     */
    public CompletableFuture<Optional<UUID>> getUuidByName(String name) {
        if (name == null || name.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        // Check online players first (instant)
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return CompletableFuture.completedFuture(Optional.of(player.getUniqueId()));
            }
        }

        // Check Bukkit cache (may still need validation)
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer.hasPlayedBefore()) {
            return CompletableFuture.completedFuture(Optional.of(offlinePlayer.getUniqueId()));
        }

        // Fallback to Mojang API if enabled
        if (mojangApiEnabled && mojangAPI != null) {
            return mojangAPI.getUuidByName(name);
        }

        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Verifies that a UUID exists in Mojang's database.
     *
     * @param uuid The UUID to verify
     * @return CompletableFuture containing true if valid, false otherwise
     */
    public CompletableFuture<Boolean> verifyUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }

        // If player has been seen on this server, UUID is valid
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.hasPlayedBefore()) {
            return CompletableFuture.completedFuture(true);
        }

        // Use Mojang API if enabled
        if (mojangApiEnabled && mojangAPI != null) {
            return mojangAPI.verifyUuid(uuid);
        }

        return CompletableFuture.completedFuture(false);
    }

    /**
     * Verifies that a username exists in Mojang's database.
     *
     * @param username The username to verify
     * @return CompletableFuture containing true if valid, false otherwise
     */
    public CompletableFuture<Boolean> verifyUsername(String username) {
        if (!MojangAPI.isValidUsername(username)) {
            return CompletableFuture.completedFuture(false);
        }

        // Check online players first
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(username)) {
                return CompletableFuture.completedFuture(true);
            }
        }

        // Check Bukkit cache
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) {
            return CompletableFuture.completedFuture(true);
        }

        // Use Mojang API if enabled
        if (mojangApiEnabled && mojangAPI != null) {
            return mojangAPI.verifyUsername(username);
        }

        return CompletableFuture.completedFuture(false);
    }

    /**
     * Shuts down the PlayerLookup and releases resources.
     */
    public void shutdown() {
        if (mojangAPI != null) {
            mojangAPI.shutdown();
            mojangAPI = null;
            mojangApiEnabled = false;
        }
        nameCache.clear();
        logger.info("PlayerLookup shutdown complete");
    }

    // ==========================================
    // Internal Cache Entry
    // ==========================================

    /**
     * A cached player name entry with optional TTL expiry.
     * Permanent entries (online players) never expire. Expiring entries (DB / Mojang) use 8h TTL.
     */
    private record TimedName(String name, long expiryMs) {
        boolean isExpired() {
            return expiryMs > 0 && System.currentTimeMillis() > expiryMs;
        }

        static TimedName permanent(String name) {
            return new TimedName(name, 0);
        }

        static TimedName expiring(String name) {
            return new TimedName(name, System.currentTimeMillis() + CACHE_TTL_MS);
        }
    }
}
