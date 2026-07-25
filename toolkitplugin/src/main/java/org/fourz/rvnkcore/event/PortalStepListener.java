package org.fourz.rvnkcore.event;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.service.portal.PortalService;
import org.fourz.rvnkcore.service.transfer.TransferService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects players stepping onto a portal trigger block and transfers them (#1714).
 *
 * <p>Cross-server portals use manually-placed trigger blocks, so {@code PlayerPortalEvent} never
 * fires for them (it only covers vanilla-lit nether/end portals). Following RVNKWorlds'
 * {@code PortalListener} model, detection runs on {@link PlayerMoveEvent} — the hot path — guarded
 * so the expensive work only happens when the player crosses a block boundary. The trigger block is
 * the solid block at the player's feet (the block the diamond block sits as, directly below the
 * standing position), tested against {@link PortalService}'s O(1) in-memory index.</p>
 *
 * <p>A per-player debounce suppresses repeated firing while the player stands on the block. On a
 * hit the portal's target server is handed to {@link TransferService#transfer}; the native transfer
 * disconnects the client, but the debounce/quit cleanup still runs for the unlikely cases where the
 * transfer is rejected (disabled, unknown target, cooldown) and the player stays put.</p>
 *
 * @since 1.5.25
 */
public class PortalStepListener implements Listener {

    /**
     * Per-player debounce window in milliseconds. Prevents re-firing every move tick while the
     * player remains on the trigger block; short enough that a deliberate re-entry still works.
     */
    private static final long TRIGGER_DEBOUNCE_MS = 1500L;

    private final RVNKCore plugin;
    private final LogManager logger;

    /** Per-player last successful trigger timestamp (epoch millis). */
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

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
     * Fires a cross-server transfer when a player steps onto a registered portal block.
     *
     * <p>Cheap guards first: ignores head-only movement (no block change) and returns immediately
     * when portals are unavailable/disabled. Only then does it perform the O(1) index lookup.</p>
     *
     * @param event The player move event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Cheap guard: only react to block-boundary movement (skip head rotation / sub-block drift).
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();
        if (config == null || !config.isEnabled()) return;

        // The player stands on top of the diamond block: the trigger is the block below their feet.
        Block feet = to.getBlock();
        Block triggerBlock = feet.getRelative(BlockFace.DOWN);
        String world = triggerBlock.getWorld().getName();
        int x = triggerBlock.getX();
        int y = triggerBlock.getY();
        int z = triggerBlock.getZ();

        if (!portalService.isPortalBlock(world, x, y, z)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(uuid);
        if (last != null && (now - last) < TRIGGER_DEBOUNCE_MS) return;
        lastTrigger.put(uuid, now);

        Optional<PortalDTO> portal = portalService.getPortal(world, x, y, z);
        if (portal.isEmpty()) return; // Raced with a delete; index no longer holds it.
        String targetServer = portal.get().getTargetServer();

        TransferService transferService = RVNKCore.getServiceSafe(TransferService.class);
        if (transferService == null) {
            player.sendMessage("§cCross-server transfer is unavailable.");
            return;
        }

        TransferService.TransferResult result = transferService.transfer(player, targetServer);
        player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.message());
        if (result.isSuccess()) {
            logger.info("Portal step transfer: " + player.getName() + " -> '" + targetServer + "'");
        } else {
            logger.debug("Portal step transfer rejected for " + player.getName()
                    + " -> '" + targetServer + "': " + result.status());
        }
    }

    /**
     * Removes the player's debounce entry on disconnect to prevent map growth.
     *
     * @param event The quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastTrigger.remove(event.getPlayer().getUniqueId());
    }
}
