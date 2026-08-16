package org.fourz.rvnkcore.event;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.service.portal.PortalService;
import org.fourz.rvnkcore.service.portal.PortalSignWriter;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Optional;

/**
 * Protects a registered cross-server portal's sign and anchor block from every destruction vector
 * other than a permitted player break (#1858).
 *
 * <p>{@link PortalSignListener} guards exactly one path: a <i>player</i> breaking the sign itself.
 * Everything else silently orphaned the portal — the DB row survived while the world state did not,
 * which is the drift #1614 documented and the reason a portal could be neither seen nor fixed.</p>
 *
 * <h3>The two protected blocks</h3>
 * <ul>
 *   <li>the <b>sign</b> carrying the portal-id PDC stamp, and</li>
 *   <li>the <b>anchor</b> — the trigger-material block that sign is mounted on. Breaking the anchor
 *       pops the sign off as an item, so leaving it unguarded made sign protection cosmetic.</li>
 * </ul>
 *
 * <h3>Two different responses</h3>
 * <p>A <b>player</b> breaking the anchor is treated exactly like breaking the sign: with
 * {@link PortalConfig#getPermissionDelete()} the portal is deleted properly (row and sign both), and
 * without it the break is cancelled. Every <b>non-player</b> vector — explosion, fire, piston,
 * entity — is simply prevented. None of them may delete a portal row: a creeper is not an
 * administrator, and a silently deleted portal is the failure this issue exists to stop.</p>
 *
 * <h3>Stale stamps are deliberately not protected</h3>
 * <p>Protection requires the stamped id to resolve to a <i>live</i> portal. A sign whose portal was
 * already deleted is left fully breakable — guarding it would strand an indestructible block in the
 * world with nothing behind it. This matches the same allowance in
 * {@link PortalSignListener#onBlockBreak}.</p>
 *
 * @since 1.5.69
 */
public class PortalProtectionListener implements Listener {

    private final RVNKCore plugin;
    private final LogManager logger;
    private final NamespacedKey portalIdKey;

    /**
     * Creates a new PortalProtectionListener.
     *
     * @param plugin The owning RVNKCore plugin
     */
    public PortalProtectionListener(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.portalIdKey = PortalSignWriter.portalIdKey(plugin);
    }

    // ---------------------------------------------------------------------------------------------
    // Player vector: breaking the anchor block the sign is mounted on
    // ---------------------------------------------------------------------------------------------

    /**
     * Handles a player breaking the anchor block beneath/behind a portal sign.
     *
     * <p>Signs are left to {@link PortalSignListener#onBlockBreak} so the two handlers never both act
     * on one break. With delete permission the portal is removed cleanly — including clearing the
     * sign, which is about to lose its support anyway. Without it, the break is cancelled.</p>
     *
     * @param event The block break event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isSign(block)) return; // PortalSignListener owns the sign-break path.

        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;

        String portalId = anchorPortalId(block, portalService);
        if (portalId == null) return;

        Player player = event.getPlayer();
        PortalConfig config = portalService.getConfig();

        if (config != null && !player.hasPermission(config.getPermissionDelete())) {
            event.setCancelled(true);
            player.sendMessage("§cThat block anchors a cross-server portal - you don't have "
                    + "permission to remove it.");
            return;
        }

        // Clear the sign too: it is about to pop off its support, and a stamped sign lying as an item
        // is exactly the orphan state #1860 has to reconcile afterwards.
        boolean removed = portalService.deletePortalById(portalId, true);
        if (removed) {
            player.sendMessage("§aCross-server portal removed (anchor block broken).");
            logger.info("Portal removed by " + player.getName() + " via anchor break (id " + portalId
                    + ") at " + block.getWorld().getName() + " "
                    + block.getX() + "," + block.getY() + "," + block.getZ());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Non-player vectors: prevent, never delete
    // ---------------------------------------------------------------------------------------------

    /**
     * Excludes portal signs and anchors from an entity explosion (creeper, TNT, ghast, wither).
     *
     * <p>Filters the block list rather than cancelling the event so the rest of the blast still
     * resolves normally — only the portal survives.</p>
     *
     * @param event The entity explode event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        shieldFromExplosion(event.blockList(), "entity explosion");
    }

    /**
     * Excludes portal signs and anchors from a block explosion (bed, respawn anchor).
     *
     * @param event The block explode event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        shieldFromExplosion(event.blockList(), "block explosion");
    }

    /**
     * Prevents fire from burning away a portal sign or its anchor.
     *
     * @param event The block burn event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (protectedPortalId(event.getBlock()) == null) return;
        event.setCancelled(true);
        logger.debug("Blocked fire burn on portal block at " + event.getBlock().getLocation());
    }

    /**
     * Prevents a piston from pushing a portal sign or anchor out of position.
     *
     * @param event The piston extend event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
            logger.debug("Blocked piston extend moving a portal block at "
                    + event.getBlock().getLocation());
        }
    }

    /**
     * Prevents a sticky piston from pulling a portal sign or anchor out of position.
     *
     * @param event The piston retract event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
            logger.debug("Blocked piston retract moving a portal block at "
                    + event.getBlock().getLocation());
        }
    }

    /**
     * Prevents an entity from removing or replacing a portal sign or anchor — enderman pickup,
     * wither shot, falling block landing, silverfish infestation.
     *
     * @param event The entity change block event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (protectedPortalId(event.getBlock()) == null) return;
        event.setCancelled(true);
        logger.debug("Blocked " + event.getEntityType() + " from changing portal block at "
                + event.getBlock().getLocation());
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Removes every protected portal block from an explosion's block list.
     *
     * @param blocks the mutable block list from the explode event
     * @param vector short description used in the debug log
     */
    private void shieldFromExplosion(List<Block> blocks, String vector) {
        if (blocks.isEmpty()) return;
        if (RVNKCore.getServiceSafe(PortalService.class) == null) return;

        boolean[] shielded = {false};
        blocks.removeIf(block -> {
            if (protectedPortalId(block) == null) return false;
            shielded[0] = true;
            return true;
        });
        if (shielded[0]) {
            logger.debug("Shielded portal block(s) from " + vector);
        }
    }

    /**
     * Tests whether any block in a piston movement list is protected.
     *
     * @param blocks the blocks the piston would move
     * @return true when at least one is a live portal sign or anchor
     */
    private boolean movesProtectedBlock(List<Block> blocks) {
        for (Block block : blocks) {
            if (protectedPortalId(block) != null) return true;
        }
        return false;
    }

    /**
     * Returns the id of the live portal this block belongs to, or null.
     *
     * <p>A block qualifies as either the stamped sign or the anchor that sign is mounted on. Both
     * checks are gated behind a cheap material test first — signs by {@link Tag#ALL_SIGNS}, anchors
     * by the configured trigger material — so an explosion sweeping a hundred blocks does not pay for
     * a {@code getState()} snapshot on each one.</p>
     *
     * @param block the block to test
     * @return the live portal id, or null when this block is not a protected portal component
     */
    private String protectedPortalId(Block block) {
        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return null;

        if (isSign(block)) {
            String id = PortalSignWriter.readPortalId(block, portalIdKey).orElse(null);
            return liveId(id, portalService);
        }
        return anchorPortalId(block, portalService);
    }

    /**
     * Returns the id of the live portal whose sign is mounted on this anchor block, or null.
     *
     * @param block         the candidate anchor block
     * @param portalService the resolved portal service
     * @return the live portal id, or null
     */
    private String anchorPortalId(Block block, PortalService portalService) {
        PortalConfig config = portalService.getConfig();
        if (config == null) return null;

        Material trigger = config.getTriggerMaterial();
        if (trigger == null || block.getType() != trigger) return null;

        Optional<Block> sign = PortalSignWriter.findSignOnAnchor(block, portalIdKey, null);
        if (sign.isEmpty()) return null;

        String id = PortalSignWriter.readPortalId(sign.get(), portalIdKey).orElse(null);
        return liveId(id, portalService);
    }

    /**
     * Narrows a stamped id to one that still resolves to a registered portal.
     *
     * <p>A stale stamp returns null on purpose — see the class javadoc.</p>
     *
     * @param portalId      the stamped id, may be null
     * @param portalService the resolved portal service
     * @return the id when the portal is live, otherwise null
     */
    private String liveId(String portalId, PortalService portalService) {
        if (portalId == null) return null;
        return portalService.getPortalById(portalId).isPresent() ? portalId : null;
    }

    /**
     * Cheap material-level test for any sign variant, avoiding a BlockState snapshot.
     *
     * @param block the block to test
     * @return true when the block is a standing, wall, or hanging sign
     */
    private static boolean isSign(Block block) {
        return Tag.ALL_SIGNS.isTagged(block.getType());
    }
}
