package org.fourz.rvnkcore.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.PortalConfig;
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
 * vanilla would treat as a nether portal and send them to the Nether. Porting RVNKWorlds'
 * {@code PortalListener} model (three layers, so the transfer always wins the race to the Nether):</p>
 * <ol>
 *   <li><b>{@link #onEntityPortalEnter}</b> ({@code LOWEST}) — the primary trigger. Fires the instant
 *       the player's body enters a registered portal block, <b>before</b> vanilla's teleport timer.</li>
 *   <li><b>{@link #onPlayerPortal}</b> ({@code HIGHEST}, {@code ignoreCancelled=false}) — the safety
 *       net. Vanilla can fire {@code PlayerPortalEvent} instantly in creative mode; this cancels it
 *       whenever a registered portal block sits in the entry column (scanning <b>Y-1..Y+3</b> to cover
 *       2–4 block-tall frames and creative entry angles), so the player never routes to the Nether.</li>
 *   <li><b>{@link #onPlayerMove}</b> ({@code NORMAL}) — a tertiary trigger for a player who ends up
 *       standing in the portal without an enter event (e.g. teleported in).</li>
 * </ol>
 *
 * <p>Suppression is gated strictly on the O(1) index membership, so the server's real nether portals
 * are never in the index and pass through unaffected. A per-player debounce keeps repeated
 * enter/move ticks from firing more than one transfer.</p>
 *
 * @since 1.5.25
 */
public class PortalStepListener implements Listener {

    /**
     * Per-player debounce window in milliseconds. {@code EntityPortalEnterEvent} fires every tick the
     * player stands in the portal, so this must be long enough to swallow the burst while still
     * allowing a deliberate re-entry.
     */
    private static final long TRIGGER_DEBOUNCE_MS = 2000L;

    private final RVNKCore plugin;
    private final LogManager logger;

    /** Per-player last trigger timestamp (epoch millis). */
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

    /**
     * Players who just arrived via a cross-server transfer and may be standing in the return portal.
     * They are not re-transferred until they step <b>out</b> of the portal once — otherwise a player
     * whose arrival location is inside a portal ping-pongs between servers.
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
     * Primary trigger: the player's body enters a portal block. Fires before vanilla's nether timer,
     * so a registered cross-server portal block transfers the player immediately.
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

        triggerTransfer(player, portal);
    }

    /**
     * Safety net: cancel vanilla nether teleportation for registered cross-server portal blocks and
     * fire the transfer. Runs even on an already-cancelled event and scans the whole entry column so
     * creative-mode instant fires cannot slip through to the Nether.
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

        // Scan Y-1..Y+3 to cover 2-4 block tall frames and creative-mode entry angles.
        for (int dy = -1; dy <= 3; dy++) {
            Optional<PortalDTO> portal = portalService.getPortal(world, bx, by + dy, bz);
            if (portal.isPresent()) {
                event.setCancelled(true); // Never let vanilla send them to the Nether.
                triggerTransfer(event.getPlayer(), portal.get());
                return;
            }
        }
    }

    /**
     * Tertiary trigger: a player standing in a registered portal block (e.g. teleported in) with no
     * enter event. Cheap block-boundary guard first, then O(1) index lookups for feet and eye.
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
            // Once they have stepped fully out of the portal, the grace is over and a later
            // re-entry may transfer them again.
            if (portal == null) {
                arrivalGrace.remove(uuid);
            }
            return;
        }

        if (portal == null) return;
        triggerTransfer(event.getPlayer(), portal);
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
     * Applies the per-player debounce and dispatches the cross-server transfer.
     *
     * @param player The player standing in the portal
     * @param portal The portal they are in
     */
    private void triggerTransfer(Player player, PortalDTO portal) {
        UUID uuid = player.getUniqueId();
        // Just arrived via transfer and still inside the return portal — do not bounce them back.
        if (arrivalGrace.contains(uuid)) return;
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(uuid);
        if (last != null && (now - last) < TRIGGER_DEBOUNCE_MS) return;
        lastTrigger.put(uuid, now);

        String targetServer = portal.getTargetServer();

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
     * Marks a player who arrived via a cross-server transfer so they are not immediately re-transferred
     * if their arrival location is inside a portal (the ping-pong guard). Cleared once they step out
     * of the portal (see {@link #onPlayerMove}).
     *
     * @param event The join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().isTransferred()) {
            arrivalGrace.add(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Removes the player's debounce and arrival-grace entries on disconnect to prevent map growth.
     *
     * @param event The quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastTrigger.remove(uuid);
        arrivalGrace.remove(uuid);
    }
}
