package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages TPA teleport requests, warmup timers, and cooldowns.
 *
 * <p>Request model: one outbound request per sender, one inbound per target.
 * Requests expire after a configurable timeout (default 60s).</p>
 *
 * <p>Warmup: configurable delay before teleport executes. Cancelled if player
 * moves more than 1 block. Bypassed with permission.</p>
 *
 * <p>Cooldown: configurable delay between sending requests. Bypassed with permission.</p>
 */
public class TpaRequestService {

    private final Plugin plugin;
    private final LogManager logger;

    // Keyed by target UUID — the player who received the request
    private final ConcurrentHashMap<UUID, TpaRequest> inboundRequests = new ConcurrentHashMap<>();
    // Keyed by sender UUID — the player who sent the request
    private final ConcurrentHashMap<UUID, TpaRequest> outboundRequests = new ConcurrentHashMap<>();
    // Cooldown expiry timestamps keyed by player UUID
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    // Active warmup tasks keyed by player UUID (the player being teleported)
    private final ConcurrentHashMap<UUID, WarmupState> activeWarmups = new ConcurrentHashMap<>();

    // Config values
    private int requestExpireSeconds = 60;
    private int warmupSeconds = 3;
    private int cooldownSeconds = 10;

    public static final String BYPASS_COOLDOWN_PERM = "rvnktools.tpa.bypass.cooldown";
    public static final String BYPASS_WARMUP_PERM = "rvnktools.tpa.bypass.warmup";

    public TpaRequestService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    public void setRequestExpireSeconds(int seconds) { this.requestExpireSeconds = seconds; }
    public void setWarmupSeconds(int seconds) { this.warmupSeconds = seconds; }
    public void setCooldownSeconds(int seconds) { this.cooldownSeconds = seconds; }
    public int getWarmupSeconds() { return warmupSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }

    /**
     * Send a TPA request from sender to target.
     * @return empty if successful, or error message
     */
    public Optional<String> sendRequest(Player sender, Player target, TpaRequest.Type type) {
        UUID senderUUID = sender.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        if (senderUUID.equals(targetUUID)) {
            return Optional.of("You cannot send a teleport request to yourself.");
        }

        // Check cooldown
        if (isOnCooldown(sender)) {
            long remaining = getRemainingCooldown(senderUUID);
            return Optional.of("You must wait " + remaining + "s before sending another request.");
        }

        // Check for existing outbound request
        TpaRequest existing = outboundRequests.get(senderUUID);
        if (existing != null) {
            // Cancel the old one
            cancelRequest(senderUUID);
        }

        // Check if target already has a pending inbound
        TpaRequest existingInbound = inboundRequests.get(targetUUID);
        if (existingInbound != null) {
            // Cancel the old inbound so the new one takes priority
            cancelRequestByTarget(targetUUID);
        }

        TpaRequest request = new TpaRequest(senderUUID, targetUUID, type);

        // Schedule expiry
        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            expireRequest(senderUUID, targetUUID);
        }, requestExpireSeconds * 20L);
        request.setExpiryTaskId(expiryTask.getTaskId());

        outboundRequests.put(senderUUID, request);
        inboundRequests.put(targetUUID, request);

        logger.debug("TPA request: " + sender.getName() + " -> " + target.getName() + " (" + type + ")");
        return Optional.empty();
    }

    /**
     * Get the pending inbound request for a target player.
     */
    public Optional<TpaRequest> getInboundRequest(UUID targetUUID) {
        return Optional.ofNullable(inboundRequests.get(targetUUID));
    }

    /**
     * Accept the pending request for the target player.
     * Removes the request from both maps and sets cooldown on sender.
     * @return the accepted request, or empty if none pending
     */
    public Optional<TpaRequest> acceptRequest(UUID targetUUID) {
        TpaRequest request = inboundRequests.remove(targetUUID);
        if (request == null) return Optional.empty();

        outboundRequests.remove(request.getSender());
        cancelExpiryTask(request);
        setCooldown(request.getSender());

        logger.debug("TPA accepted: target=" + targetUUID);
        return Optional.of(request);
    }

    /**
     * Deny the pending request for the target player.
     * @return the denied request, or empty if none pending
     */
    public Optional<TpaRequest> denyRequest(UUID targetUUID) {
        TpaRequest request = inboundRequests.remove(targetUUID);
        if (request == null) return Optional.empty();

        outboundRequests.remove(request.getSender());
        cancelExpiryTask(request);

        logger.debug("TPA denied: target=" + targetUUID);
        return Optional.of(request);
    }

    /**
     * Cancel an outbound request by sender UUID.
     */
    public void cancelRequest(UUID senderUUID) {
        TpaRequest request = outboundRequests.remove(senderUUID);
        if (request != null) {
            inboundRequests.remove(request.getTarget());
            cancelExpiryTask(request);
        }
    }

    private void cancelRequestByTarget(UUID targetUUID) {
        TpaRequest request = inboundRequests.remove(targetUUID);
        if (request != null) {
            outboundRequests.remove(request.getSender());
            cancelExpiryTask(request);
        }
    }

    private void expireRequest(UUID senderUUID, UUID targetUUID) {
        TpaRequest request = outboundRequests.remove(senderUUID);
        if (request != null) {
            inboundRequests.remove(targetUUID);

            Player sender = Bukkit.getPlayer(senderUUID);
            Player target = Bukkit.getPlayer(targetUUID);
            if (sender != null && sender.isOnline()) {
                sender.sendMessage("§c✖ Your teleport request to " +
                    (target != null ? target.getName() : "player") + " has expired.");
            }
            if (target != null && target.isOnline()) {
                target.sendMessage("§7Teleport request from " +
                    (sender != null ? sender.getName() : "player") + " has expired.");
            }
        }
    }

    // --- Cooldown ---

    public boolean isOnCooldown(Player player) {
        if (player.hasPermission(BYPASS_COOLDOWN_PERM)) return false;
        Long expiry = cooldowns.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private long getRemainingCooldown(UUID playerUUID) {
        Long expiry = cooldowns.get(playerUUID);
        if (expiry == null) return 0;
        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    private void setCooldown(UUID playerUUID) {
        cooldowns.put(playerUUID, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    // --- Warmup ---

    /**
     * Start a warmup countdown before teleporting.
     * @param player the player being teleported
     * @param onComplete callback to execute when warmup finishes
     * @return true if warmup started, false if bypassed (instant teleport)
     */
    public boolean startWarmup(Player player, Runnable onComplete) {
        if (player.hasPermission(BYPASS_WARMUP_PERM) || warmupSeconds <= 0) {
            onComplete.run();
            return false;
        }

        UUID uuid = player.getUniqueId();
        Location startLoc = player.getLocation().clone();

        // Cancel any existing warmup
        cancelWarmup(uuid);

        player.sendMessage("§e⚠ Teleporting in " + warmupSeconds + " seconds... Don't move!");

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeWarmups.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                onComplete.run();
            }
        }, warmupSeconds * 20L);

        activeWarmups.put(uuid, new WarmupState(startLoc, task.getTaskId()));
        return true;
    }

    /**
     * Check if a player moved during warmup and cancel if so.
     * Called from PlayerMoveEvent listener.
     * @return true if warmup was cancelled
     */
    public boolean checkWarmupMovement(Player player) {
        UUID uuid = player.getUniqueId();
        WarmupState state = activeWarmups.get(uuid);
        if (state == null) return false;

        Location current = player.getLocation();
        Location start = state.startLocation;

        // Check block-level movement (ignore head rotation)
        if (current.getBlockX() != start.getBlockX() ||
            current.getBlockY() != start.getBlockY() ||
            current.getBlockZ() != start.getBlockZ()) {

            cancelWarmup(uuid);
            player.sendMessage("§c✖ Teleport cancelled — you moved!");
            return true;
        }
        return false;
    }

    public boolean hasActiveWarmup(UUID playerUUID) {
        return activeWarmups.containsKey(playerUUID);
    }

    public void cancelWarmup(UUID playerUUID) {
        WarmupState state = activeWarmups.remove(playerUUID);
        if (state != null) {
            Bukkit.getScheduler().cancelTask(state.taskId);
        }
    }

    // --- Cleanup ---

    /**
     * Clean up all data for a disconnecting player.
     */
    public void handlePlayerQuit(UUID playerUUID) {
        cancelRequest(playerUUID);
        cancelRequestByTarget(playerUUID);
        cancelWarmup(playerUUID);
        cooldowns.remove(playerUUID);
    }

    /**
     * Cancel all pending tasks. Call on plugin disable.
     */
    public void shutdown() {
        for (TpaRequest req : outboundRequests.values()) {
            cancelExpiryTask(req);
        }
        for (WarmupState state : activeWarmups.values()) {
            Bukkit.getScheduler().cancelTask(state.taskId);
        }
        outboundRequests.clear();
        inboundRequests.clear();
        activeWarmups.clear();
        cooldowns.clear();
    }

    private void cancelExpiryTask(TpaRequest request) {
        if (request.getExpiryTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(request.getExpiryTaskId());
        }
    }

    private static class WarmupState {
        final Location startLocation;
        final int taskId;

        WarmupState(Location startLocation, int taskId) {
            this.startLocation = startLocation;
            this.taskId = taskId;
        }
    }
}
