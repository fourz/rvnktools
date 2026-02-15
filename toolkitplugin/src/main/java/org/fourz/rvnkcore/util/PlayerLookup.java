package org.fourz.rvnkcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.util.log.LogManager;

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
 */
public class PlayerLookup {

    private final LogManager logger;
    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();

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
     * Gets a player name by UUID. Tries RVNKCore cache first, falls back to Bukkit.
     *
     * @param uuid The player UUID
     * @return The player name, or a truncated UUID if unknown
     */
    public String getPlayerName(UUID uuid) {
        if (uuid == null) return "Unknown";

        String cached = nameCache.get(uuid);
        if (cached != null) return cached;

        // Try RVNKCore PlayerService
        if (rvnkCoreEnabled) {
            String name = lookupViaPlayerService(uuid);
            if (name != null) {
                nameCache.put(uuid, name);
                return name;
            }
        }

        // Fallback to Bukkit
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        if (name == null) {
            name = uuid.toString().substring(0, 8);
        }
        nameCache.put(uuid, name);
        return name;
    }

    private String lookupViaPlayerService(UUID uuid) {
        try {
            Optional<PlayerDTO> result = playerService.getPlayer(uuid).get(2, TimeUnit.SECONDS);
            if (result.isPresent()) {
                return result.get().getCurrentName();
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

    // ==========================================
    // Async Methods (with Mojang API support)
    // ==========================================

    /**
     * Async version of getPlayerName with Mojang API fallback.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Local name cache</li>
     *   <li>RVNKCore PlayerService (if available)</li>
     *   <li>Bukkit OfflinePlayer cache</li>
     *   <li>Mojang API (if enabled, rate limited)</li>
     * </ol>
     *
     * @param uuid The player UUID
     * @return CompletableFuture containing the player name, or truncated UUID if unknown
     */
    public CompletableFuture<String> getPlayerNameAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture("Unknown");
        }

        // Check local cache first
        String cached = nameCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Try RVNKCore PlayerService
        if (rvnkCoreEnabled) {
            String name = lookupViaPlayerService(uuid);
            if (name != null) {
                nameCache.put(uuid, name);
                return CompletableFuture.completedFuture(name);
            }
        }

        // Try Bukkit cache (synchronous, fast)
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.getName() != null) {
            nameCache.put(uuid, player.getName());
            return CompletableFuture.completedFuture(player.getName());
        }

        // Fallback to Mojang API if enabled
        if (mojangApiEnabled && mojangAPI != null) {
            return mojangAPI.getNameByUuid(uuid)
                .thenApply(opt -> {
                    String name = opt.orElse(uuid.toString().substring(0, 8));
                    nameCache.put(uuid, name);
                    return name;
                });
        }

        // Final fallback: truncated UUID
        String truncated = uuid.toString().substring(0, 8);
        nameCache.put(uuid, truncated);
        return CompletableFuture.completedFuture(truncated);
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
}
