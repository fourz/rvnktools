package org.fourz.rvnkcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.UUID;
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
    private final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();

    private PlayerService playerService;
    private boolean rvnkCoreEnabled = false;

    public PlayerLookup(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, "PlayerLookup");
        initPlayerService();
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
    }
}
