package org.fourz.rvnkcore.service.transfer;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.service.sightings.SightingService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Native cross-server transfer service.
 *
 * <p>Resolves a named target, enforces enablement / permission / cooldown, records an audit
 * line, then sends the player via Minecraft's native Transfer packet
 * ({@link Player#transfer(String, int)}). The client disconnects and reconnects directly to the
 * destination address itself — no proxy is involved.</p>
 *
 * <p><b>MVP scope: movement only.</b> No inventory/economy/quest state travels with the player.
 * Spigot-API exposes no {@code PlayerTransferEvent} (or cookie event), so the audit hook lives at
 * this call site rather than in a listener.</p>
 *
 * @since 1.5.23
 */
public class TransferService {

    /** Outcome status of a transfer request. */
    public enum Status {
        SUCCESS,
        DISABLED,
        UNKNOWN_TARGET,
        NO_PERMISSION,
        COOLDOWN,
        /** A gated target (#2378) refused the player: no entitlement and no bypass node. */
        GATED,
        /**
         * A gated target is reading the player's entitlements. The service itself tells the player
         * the outcome when the read completes; the result message is blank.
         */
        PENDING
    }

    /**
     * Result of a transfer request.
     *
     * @param status  The outcome status
     * @param message A player-facing message describing the outcome
     */
    public record TransferResult(Status status, String message) {

        /** @return true when the transfer was dispatched. */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    private final Plugin plugin;
    private volatile TransferConfig config;
    private final LogManager logger;

    /** Per-player last successful transfer timestamp (epoch millis) for cooldown enforcement. */
    private final ConcurrentHashMap<UUID, Long> lastTransfer = new ConcurrentHashMap<>();
    /**
     * Players whose imminent disconnect is a cross-server transfer, not a real quit (#1763). A quit
     * handler suppresses the vanilla leave message and broadcasts the themed notice for these.
     */
    private final ConcurrentHashMap<UUID, String> transferring = new ConcurrentHashMap<>();

    /**
     * Reads a player's entitlement lists for a transfer gate (#2378). Defaults to the sighting
     * service's cluster-shared store, resolved per call so a late registration still works.
     */
    private volatile Function<UUID, CompletableFuture<List<String>>> entitlementLookup = id -> {
        SightingService sightings = RVNKCore.getServiceSafe(SightingService.class);
        return sightings != null
                ? sightings.lists(id)
                : CompletableFuture.failedFuture(new IllegalStateException("no entitlement source"));
    };

    /** Replaces the entitlement read (tests). */
    public void setEntitlementLookup(Function<UUID, CompletableFuture<List<String>>> lookup) {
        if (lookup != null) {
            this.entitlementLookup = lookup;
        }
    }

    /** True when the player's pending disconnect is a transfer (checked by the quit handler). */
    public boolean isTransferring(UUID playerId) {
        return transferring.containsKey(playerId);
    }

    /**
     * The in-fiction realm a departing player is bound for.
     *
     * <p>The quit handler fires after {@link #transfer} has already dispatched the packet, and a
     * {@link org.bukkit.event.player.PlayerQuitEvent} carries no destination — so the departure
     * broadcast can only name where someone went if it was recorded here first.</p>
     *
     * @param playerId The departing player
     * @return The destination realm name, or {@link TransferConfig#UNKNOWN_REALM} when the
     *         transfer was not recorded (flag expired, or a disconnect that was not a transfer)
     */
    public String getTransferRealm(UUID playerId) {
        String realm = transferring.get(playerId);
        return (realm == null || realm.isBlank()) ? TransferConfig.UNKNOWN_REALM : realm;
    }

    /** Clears a player's transfer flag (called once the quit is handled, or if it never happens). */
    public void clearTransferring(UUID playerId) {
        transferring.remove(playerId);
    }

    /**
     * Creates a new TransferService.
     *
     * @param plugin The owning plugin
     * @param config Transfer configuration
     * @param logger LogManager instance
     */
    public TransferService(Plugin plugin, TransferConfig config, LogManager logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Attempts to transfer a player to a named target.
     *
     * <p>Applies, in order: enablement, target resolution, permission, cooldown, then the target's
     * access gate when it has one (#2378; async, returns {@link Status#PENDING}). On success the
     * cooldown timestamp is recorded, an audit line is logged, and the native transfer packet is
     * sent. All failure paths return a descriptive {@link TransferResult} without side effects.</p>
     *
     * @param player     The player to transfer (must not be null)
     * @param targetName The configured target name (case-insensitive)
     * @return A {@link TransferResult} describing the outcome
     */
    public TransferResult transfer(Player player, String targetName) {
        return transfer(player, targetName, config.getPermission());
    }

    /**
     * Attempts a transfer gated by {@code permission} instead of the command node.
     *
     * <p>Portals pass {@code rvnkcore.portal.use}: walking into a portal is a different act from
     * running {@code /server transfer}, and gating it on the command node (default op) blocked every
     * non-op at the portal while the portal node, which says "cross-server portal", never did (#2032).</p>
     *
     * @param permission Node the player must hold; null or blank skips the check
     */
    public TransferResult transfer(Player player, String targetName, String permission) {
        if (player == null) {
            return new TransferResult(Status.DISABLED, "No player to transfer.");
        }

        if (!config.isEnabled()) {
            return new TransferResult(Status.DISABLED, "Cross-server transfer is disabled on this server.");
        }

        TransferConfig.Target target = config.resolveTarget(targetName);
        if (target == null) {
            String known = String.join(", ", config.getTargetNames());
            String hint = known.isEmpty() ? "(no targets configured)" : known;
            return new TransferResult(Status.UNKNOWN_TARGET,
                    "Unknown target '" + targetName + "'. Available: " + hint);
        }

        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            // Name the node: the old message named neither, so the #2032 denial looked unexplained
            return new TransferResult(Status.NO_PERMISSION,
                    "You don't have permission to transfer between servers (" + permission + ").");
        }

        long now = System.currentTimeMillis();
        long cooldownMs = config.getCooldownSeconds() * 1000L;
        Long last = lastTransfer.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownMs) {
            long remaining = (cooldownMs - (now - last) + 999L) / 1000L;
            return new TransferResult(Status.COOLDOWN,
                    "Please wait " + remaining + "s before transferring again.");
        }

        // #2378: a gated target needs the entitlement (or the bypass node). The read is async; the
        // outcome reaches the player from here, so the caller gets a blank PENDING result.
        TransferConfig.Gate gate = config.getGate(targetName);
        if (gate != null && !player.hasPermission(gate.bypassPermission())) {
            checkGate(player, targetName, gate);
            return new TransferResult(Status.PENDING, "");
        }

        return dispatch(player, targetName, target);
    }

    /**
     * Reads the player's entitlements off the main thread, then crosses or refuses on it.
     *
     * <p>Fails closed: a missing entitlement source or a failed read refuses the crossing and logs a
     * warning. A refusal uses no cooldown, so the player can try again once granted.</p>
     */
    private void checkGate(Player player, String targetName, TransferConfig.Gate gate) {
        final UUID id = player.getUniqueId();
        final String name = player.getName();
        CompletableFuture<List<String>> read;
        try {
            read = entitlementLookup.apply(id);
        } catch (RuntimeException e) {
            read = CompletableFuture.failedFuture(e);
        }
        read.whenComplete((lists, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online == null || !online.isOnline()) {
                return;
            }
            if (ex != null) {
                logger.warning("Transfer gate: entitlement read failed for " + name + " -> '"
                        + targetName.toLowerCase() + "', refusing: " + ex.getMessage());
                deny(online, targetName, gate);
                return;
            }
            boolean entitled = lists != null && lists.stream()
                    .anyMatch(l -> l != null && l.equalsIgnoreCase(gate.entitlement()));
            if (!entitled) {
                deny(online, targetName, gate);
                return;
            }
            TransferConfig.Target target = config.resolveTarget(targetName);
            if (target == null) {
                online.sendMessage(ChatColor.RED + "Unknown target '" + targetName + "'.");
                return;
            }
            TransferResult result = dispatch(online, targetName, target);
            online.sendMessage((result.isSuccess() ? ChatColor.AQUA : ChatColor.RED) + result.message());
        }));
    }

    /** Sends the gate's deny sound and lines. */
    private void deny(Player player, String targetName, TransferConfig.Gate gate) {
        logger.info("Transfer gated: " + player.getName() + " (" + player.getUniqueId() + ") -> '"
                + targetName.toLowerCase() + "' lacks '" + gate.entitlement() + "'");
        if (gate.denySound() != null && !gate.denySound().isBlank()) {
            try {
                player.playSound(player.getLocation(), gate.denySound(), 1.0f, 0.6f);
            } catch (RuntimeException e) {
                logger.debug("Transfer gate: deny sound '" + gate.denySound() + "' failed: " + e.getMessage());
            }
        }
        for (String line : gate.denyMessage()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    line.replace("{player}", player.getName())));
        }
    }

    /** Records the cooldown and transfer flag, logs the audit line and sends the transfer packet. */
    private TransferResult dispatch(Player player, String targetName, TransferConfig.Target target) {
        long now = System.currentTimeMillis();
        // Record cooldown before dispatch; a sent transfer disconnects the client immediately.
        lastTransfer.put(player.getUniqueId(), now);

        // #1763: flag the imminent disconnect as a transfer so the quit handler suppresses the vanilla
        // leave message and broadcasts the themed notice. Auto-expire in case the transfer fails and no
        // quit follows.
        // Records the destination realm, not just a flag: the quit handler needs it to say where the
        // player went, and PlayerQuitEvent carries no destination of its own.
        final UUID transferId = player.getUniqueId();
        transferring.put(transferId, target.realm());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> transferring.remove(transferId), 200L);

        // Audit at the call site (no PlayerTransferEvent exists on spigot-api).
        logger.info("Transfer: " + player.getName() + " (" + player.getUniqueId() + ") -> '"
                + targetName.toLowerCase() + "' (" + target.host() + ":" + target.port() + ")");

        try {
            player.transfer(target.host(), target.port());
        } catch (Exception e) {
            transferring.remove(transferId);
            logger.error("Transfer failed for " + player.getName() + " -> " + targetName, e);
            lastTransfer.remove(player.getUniqueId());
            return new TransferResult(Status.DISABLED, "Transfer failed - see server log.");
        }

        return new TransferResult(Status.SUCCESS,
                "Transferring you to '" + targetName.toLowerCase()
                        + "' (movement only - your items do not travel).");
    }

    /** @return the backing transfer configuration. */
    public TransferConfig getConfig() {
        return config;
    }

    /**
     * Swaps in a freshly-parsed configuration (e.g. from {@code /rvnkcore reload}) so target/permission
     * changes take effect without a restart (#1743). No-op on null. Cooldown state is preserved.
     *
     * @param newConfig the new transfer configuration
     */
    public void refreshConfig(TransferConfig newConfig) {
        if (newConfig == null) return;
        this.config = newConfig;
        logger.info("TransferService config refreshed - targets: " + config.getTargetNames());
    }
}
