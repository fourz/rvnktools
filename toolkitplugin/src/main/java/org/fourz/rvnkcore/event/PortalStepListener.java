package org.fourz.rvnkcore.event;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.service.portal.PortalService;
import org.fourz.rvnkcore.service.transfer.TransferService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects players walking through a framed cross-server portal and transfers them (#1709/#1714).
 *
 * <p>A framed portal stands the player <b>inside</b> its lit {@code NETHER_PORTAL} interior, which
 * vanilla would treat as a nether portal. Porting RVNKWorlds' {@code PortalListener} model in three
 * layers so the transfer always wins the race to the Nether:</p>
 * <ol>
 *   <li><b>{@link #onEntityPortalEnter}</b> ({@code LOWEST}) — primary trigger, before vanilla's timer.</li>
 *   <li><b>{@link #onPlayerPortal}</b> ({@code HIGHEST}, {@code ignoreCancelled=false}) — cancels the
 *       nether route whenever a registered portal block is in the entry column (Y-1..Y+3).</li>
 *   <li><b>{@link #onPlayerMove}</b> ({@code NORMAL}) — tertiary trigger for a player standing in the
 *       portal without an enter event.</li>
 * </ol>
 *
 * <h3>Charge-up</h3>
 * <p>Entering does not transfer instantly: it starts a short <b>charge-up</b> ({@link #CHARGE_TICKS})
 * with portal particles, a rising portal hum, and an action-bar countdown. Stepping out of the portal
 * cancels it. This gives a visible portal effect and a moment to back out.</p>
 *
 * <h3>Arrival ejection (#1726)</h3>
 * <p>Departure fires while the player stands on the {@code DIAMOND_BLOCK} trigger, and the ARG worlds
 * are mirror-coordinate, so a naive transfer deposits the player onto a trigger on the far end — an
 * immediate re-transfer (ping-pong) and, if they log off there, a transfer-on-login loop. On join, if
 * the player is standing in a registered portal, {@link #computeExitLocation} places them one block in
 * front of it facing outward — the outward direction read deterministically from the frame's wall sign
 * ({@link #resolveOutwardFace}), stepping out to a spot with solid ground and headroom. This fires for
 * <b>any</b> join, so it also covers the case {@code arrivalGrace} alone missed: a player who logged off
 * inside a portal and rejoins normally (where {@link Player#isTransferred()} is {@code false}).</p>
 *
 * <h3>Arrival guard</h3>
 * <p>{@code arrivalGrace} is armed synchronously on join (before the one-tick eject defer) and retained
 * as a transient fallback when no safe exit can be computed: the player is not re-transferred until they
 * step out of the portal once.</p>
 *
 * @since 1.5.25
 */
public class PortalStepListener implements Listener {

    /** Charge-up duration in ticks before the transfer fires (20 ticks = 1 second). */
    private static final int CHARGE_TICKS = 40;
    /** Effect/countdown tick interval. */
    private static final long EFFECT_INTERVAL = 5L;
    /** Debounce window after a completed transfer (ms). */
    private static final long TRIGGER_DEBOUNCE_MS = 2000L;
    /** Horizontal faces scanned around the anchor to find the portal sign / outward direction. */
    private static final BlockFace[] HORIZONTAL =
            {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final RVNKCore plugin;
    private final LogManager logger;

    /** Per-player last completed-transfer timestamp (epoch millis). */
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();
    /** Players currently charging up a transfer -> their running task. */
    private final Map<UUID, BukkitTask> charging = new ConcurrentHashMap<>();
    /**
     * Players who just arrived via a cross-server transfer and may be standing in the return portal.
     * Not re-transferred until they step <b>out</b> of the portal once.
     */
    private final Set<UUID> arrivalGrace = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new PortalStepListener.
     *
     * @param plugin The owning RVNKCore plugin
     */
    public PortalStepListener(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * Primary trigger: the player's body enters a portal block. Begins the charge-up before vanilla's
     * nether timer.
     *
     * @param event The entity portal enter event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();
        if (config == null || !config.isEnabled()) return;

        Block block = event.getLocation().getBlock();
        if (block.getType() != Material.NETHER_PORTAL) return;
        PortalDTO portal = matchPortal(portalService, block);
        if (portal == null) return;
        beginTransfer(player, portal);
    }

    /**
     * Safety net: cancel vanilla nether teleportation for registered cross-server portal blocks and
     * begin the charge-up. Runs even on an already-cancelled event and scans a 3x3 horizontal
     * neighbourhood of the entry column so creative-mode instant fires and frame-edge rounding cannot
     * slip through to the Nether.
     *
     * @param event The player portal event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();
        if (config == null || !config.isEnabled()) return;

        Location from = event.getFrom();
        if (from == null || from.getWorld() == null) return;
        String world = from.getWorld().getName();
        int bx = from.getBlockX();
        int by = from.getBlockY();
        int bz = from.getBlockZ();

        // Scan a 3x3 horizontal neighbourhood at each vertical offset. The player's from-block can round
        // to a column one off the portal plane (sub-block position at the frame edge), which let vanilla's
        // nether teleport slip through intermittently on servers without RVNKWorlds' portal handling
        // (e.g. nations). Widening the scan makes the cancel deterministic — the transfer always wins
        // the race to the Nether (#1722).
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Optional<PortalDTO> portal = portalService.getPortal(world, bx + dx, by + dy, bz + dz);
                    if (portal.isPresent()) {
                        event.setCancelled(true); // Never let vanilla send them to the Nether.
                        beginTransfer(event.getPlayer(), portal.get());
                        return;
                    }
                }
            }
        }
    }

    /**
     * Tertiary trigger + housekeeping. Clears arrival grace once the player steps out of the portal,
     * and begins a charge-up for a player standing in a portal block with no enter event.
     *
     * @param event The player move event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();
        if (config == null || !config.isEnabled()) return;

        Block feet = to.getBlock();
        PortalDTO portal = matchPortal(portalService, feet);
        if (portal == null) {
            portal = matchPortal(portalService, feet.getRelative(BlockFace.UP));
        }

        UUID uuid = event.getPlayer().getUniqueId();
        if (arrivalGrace.contains(uuid)) {
            if (portal == null) {
                arrivalGrace.remove(uuid); // stepped out — grace over
            }
            return;
        }

        if (portal == null) return;
        beginTransfer(event.getPlayer(), portal);
    }

    /**
     * Returns the registered portal for a block, or null when the block is not a portal block.
     *
     * @param portalService The portal service
     * @param block         The block to test
     * @return the portal at that block, or null
     */
    private PortalDTO matchPortal(PortalService portalService, Block block) {
        return portalService.getPortal(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).orElse(null);
    }

    /**
     * True when the player is currently standing in a registered portal block (feet or eye).
     */
    private boolean stillInPortal(Player player, PortalService portalService) {
        Block feet = player.getLocation().getBlock();
        return matchPortal(portalService, feet) != null
                || matchPortal(portalService, feet.getRelative(BlockFace.UP)) != null;
    }

    /**
     * Starts the charge-up: portal particles + hum + an action-bar countdown, then the transfer. A
     * no-op if the player is in arrival grace, already charging, or within the post-transfer debounce.
     * Cancels itself if the player leaves the portal before it completes.
     *
     * @param player The player standing in the portal
     * @param portal The portal they are in
     */
    private void beginTransfer(Player player, PortalDTO portal) {
        UUID uuid = player.getUniqueId();
        if (arrivalGrace.contains(uuid)) return;
        if (charging.containsKey(uuid)) return;
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(uuid);
        if (last != null && (now - last) < TRIGGER_DEBOUNCE_MS) return;

        // #1723: enforce portal.use in the step path (the node was defined in PortalConfig but never
        // checked). Stamp lastTrigger on denial so the per-tick move trigger can't spam the message.
        PortalService permPortalService = RVNKCore.getServiceSafe(PortalService.class);
        PortalConfig portalConfig = permPortalService != null ? permPortalService.getConfig() : null;
        if (portalConfig != null && !player.hasPermission(portalConfig.getPermissionUse())) {
            lastTrigger.put(uuid, now);
            player.sendMessage("§cYou don't have permission to use cross-server portals.");
            return;
        }

        TransferService transferService = RVNKCore.getServiceSafe(TransferService.class);
        if (transferService == null) {
            player.sendMessage("§cCross-server transfer is unavailable.");
            return;
        }
        final String targetServer = portal.getTargetServer();
        final String display = friendlyName(transferService, targetServer);

        if (CHARGE_TICKS <= 0) {
            lastTrigger.put(uuid, now);
            doTransfer(player, targetServer);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.2f);
        final int[] elapsed = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            PortalService ps = RVNKCore.getServiceSafe(PortalService.class);
            if (p == null || ps == null || !stillInPortal(p, ps)) {
                cancelCharge(uuid);
                return;
            }
            elapsed[0] += (int) EFFECT_INTERVAL;
            Location loc = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 25, 0.4, 0.8, 0.4, 0.08);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.6f);
            int remainSecs = Math.max(1, (CHARGE_TICKS - elapsed[0] + 19) / 20);
            actionBar(p, "§dTransferring to §f" + display + "§d in " + remainSecs + "s...");
            if (elapsed[0] >= CHARGE_TICKS) {
                cancelCharge(uuid);
                lastTrigger.put(uuid, System.currentTimeMillis());
                p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.4f);
                doTransfer(p, targetServer);
            }
        }, 0L, EFFECT_INTERVAL);
        charging.put(uuid, task);
    }

    /**
     * Performs the actual native transfer and reports the result.
     */
    private void doTransfer(Player player, String targetServer) {
        TransferService transferService = RVNKCore.getServiceSafe(TransferService.class);
        if (transferService == null) {
            player.sendMessage("§cCross-server transfer is unavailable.");
            return;
        }
        TransferService.TransferResult result = transferService.transfer(player, targetServer);
        player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.message());
        if (result.isSuccess()) {
            logger.info("Portal walk-through transfer: " + player.getName() + " -> '" + targetServer + "'");
        } else {
            logger.debug("Portal walk-through transfer rejected for " + player.getName()
                    + " -> '" + targetServer + "': " + result.status());
        }
    }

    /**
     * Resolves the friendly display name of a transfer target (falls back to the raw name).
     */
    private String friendlyName(TransferService transferService, String targetServer) {
        TransferConfig.Target tgt = transferService.getConfig().resolveTarget(targetServer);
        return (tgt != null && tgt.display() != null && !tgt.display().isEmpty())
                ? tgt.display() : targetServer;
    }

    /** Sends a legacy-coded action-bar message, best-effort. */
    private void actionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) {
            // Action bar is cosmetic; never let it break the charge-up.
        }
    }

    /** Cancels and clears a player's running charge-up task. */
    private void cancelCharge(UUID uuid) {
        BukkitTask task = charging.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Ejects a player who joins standing inside a registered portal to a safe spot <b>1 block in front
     * of the portal, facing out</b> (#1726), keeping the arrival-grace guard as a fallback.
     *
     * <p>Fixes the portal loop for both paths at once: a transfer arrival that lands in the return
     * portal, and — the case {@code arrivalGrace} alone missed — a player who logged off inside a
     * portal and rejoins <b>normally</b> (so {@link Player#isTransferred()} is {@code false} and grace
     * was never armed). In both cases the saved location is a trigger block that the first move would
     * re-fire; placing them clear of the frame on join prevents the ping-pong and the
     * transfer-on-login loop.</p>
     *
     * @param event The join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        // Arm the guard synchronously so a portal-enter/move on the join tick cannot fire a transfer
        // during the one-tick defer below (the defer lets the arrival chunk load before block reads).
        arrivalGrace.add(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                arrivalGrace.remove(uuid);
                return;
            }
            PortalService ps = RVNKCore.getServiceSafe(PortalService.class);
            PortalDTO portal = (ps != null) ? portalAtPlayer(player, ps) : null;
            if (portal == null) {
                arrivalGrace.remove(uuid); // joined clear of any portal — nothing to guard
                return;
            }
            Location exit = computeExitLocation(portal, player);
            if (exit != null) {
                player.teleport(exit);
                arrivalGrace.remove(uuid);
                logger.info("Portal arrival eject: " + player.getName()
                        + " placed in front of portal " + portal.getPortalId());
                return;
            }
            // No safe exit computed — keep grace so they don't instantly re-transfer while standing in
            // the portal; onPlayerMove clears it when they step out.
            logger.debug("Portal arrival: no safe exit for " + player.getName()
                    + " at portal " + portal.getPortalId() + " — holding arrival grace");
        });
    }

    /**
     * Returns the registered portal the player is standing in (feet or eye block), or null.
     *
     * @param player        The player to test
     * @param portalService The portal service
     * @return the portal at the player, or null
     */
    private PortalDTO portalAtPlayer(Player player, PortalService portalService) {
        Block feet = player.getLocation().getBlock();
        PortalDTO portal = matchPortal(portalService, feet);
        if (portal == null) {
            portal = matchPortal(portalService, feet.getRelative(BlockFace.UP));
        }
        return portal;
    }

    /**
     * Computes a safe standing location 1+ blocks in front of the portal, facing outward (#1726).
     * Steps outward from the player's block along the sign's outward face until a block with solid
     * ground and two clear blocks above is found.
     *
     * @param portal The portal the player is in
     * @param player The player to eject
     * @return the exit location, or null when the world is unloaded or no safe stand is nearby
     */
    private Location computeExitLocation(PortalDTO portal, Player player) {
        World world = Bukkit.getWorld(portal.getWorld());
        if (world == null) return null;
        BlockFace out = resolveOutwardFace(world, portal);
        if (out == null) return null;

        Block base = player.getLocation().getBlock();
        for (int step = 1; step <= 3; step++) {
            Block cand = base.getRelative(out.getModX() * step, 0, out.getModZ() * step);
            Block ground = findGround(cand);
            if (ground != null && isSafeStand(ground)) {
                Location loc = ground.getLocation().add(0.5, 1, 0.5);
                loc.setYaw(yawOf(out));
                loc.setPitch(0f);
                return loc;
            }
        }
        return null;
    }

    /**
     * Resolves the portal's outward-facing direction from the wall sign on its anchor block. A wall
     * sign's {@code getFacing()} points away from the frame — the "out" direction.
     *
     * @param world  The portal's world
     * @param portal The portal
     * @return the outward horizontal face, or null when no wall sign is found on the anchor
     */
    private BlockFace resolveOutwardFace(World world, PortalDTO portal) {
        Block anchor = world.getBlockAt(portal.getX(), portal.getY(), portal.getZ());
        for (BlockFace face : HORIZONTAL) {
            BlockData data = anchor.getRelative(face).getBlockData();
            if (data instanceof WallSign wallSign) {
                return wallSign.getFacing();
            }
        }
        return null;
    }

    /**
     * Finds the solid floor block at or just below the candidate column (searching down a few blocks).
     *
     * @param candidate The candidate column block
     * @return the solid block whose top a player would stand on, or null
     */
    private Block findGround(Block candidate) {
        for (int dy = 0; dy >= -3; dy--) {
            Block b = candidate.getRelative(0, dy, 0);
            if (b.getType().isSolid()) {
                return b;
            }
        }
        return null;
    }

    /**
     * True when the two blocks above {@code ground} are clear (and not portal blocks), so a player can
     * stand there without suffocating or re-entering a portal.
     *
     * @param ground The floor block
     * @return whether the spot above ground is safe to stand
     */
    private boolean isSafeStand(Block ground) {
        Block feet = ground.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        return !feet.getType().isSolid() && !head.getType().isSolid()
                && feet.getType() != Material.NETHER_PORTAL
                && head.getType() != Material.NETHER_PORTAL;
    }

    /**
     * Maps a horizontal block face to the yaw a player needs to look in that direction.
     *
     * @param face The horizontal face
     * @return the corresponding yaw
     */
    private float yawOf(BlockFace face) {
        switch (face) {
            case SOUTH: return 0f;
            case WEST:  return 90f;
            case NORTH: return 180f;
            case EAST:  return -90f;
            default:    return 0f;
        }
    }

    /**
     * Cleans up per-player state on disconnect.
     *
     * @param event The quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // #1763: a transfer disconnect is not a real quit — suppress the vanilla leave message and
        // broadcast the themed notice so cross-server travel reads as "moved on the network", not "quit".
        TransferService ts = RVNKCore.getServiceSafe(TransferService.class);
        if (ts != null && ts.isTransferring(uuid)) {
            event.setQuitMessage(null);
            TransferConfig tc = ts.getConfig();
            String template = (tc != null) ? tc.getBroadcastMessage() : null;
            if (template != null && !template.isBlank()) {
                Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        template.replace("{player}", event.getPlayer().getName())));
            }
            ts.clearTransferring(uuid);
        }

        lastTrigger.remove(uuid);
        arrivalGrace.remove(uuid);
        cancelCharge(uuid);
    }
}
