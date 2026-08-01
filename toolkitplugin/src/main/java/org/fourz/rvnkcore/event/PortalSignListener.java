package org.fourz.rvnkcore.event;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.service.portal.PortalFrameBuilder;
import org.fourz.rvnkcore.service.portal.PortalService;
import org.fourz.rvnkcore.service.portal.PortalSignWriter;
import org.fourz.rvnkcore.service.transfer.TransferService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;

/**
 * Registers and removes cross-server portals from signs (#1713).
 *
 * <p>A portal sign has the {@link PortalConfig#getSignHeader() sign-header} on line 1 (default
 * {@code [server]}, case-insensitive) and a configured transfer target name on line 2. The sign
 * must be mounted on the {@link PortalConfig#getTriggerMaterial() trigger block} (default
 * {@code DIAMOND_BLOCK}); that mounting block becomes the portal's trigger location. On success the
 * sign block is stamped with the portal id in its {@link org.bukkit.persistence.PersistentDataContainer}
 * so a later break can identify and delete the portal.</p>
 *
 * <p>Mirrors RVNKWorlds' {@code SignPortalListener} structure but adapts it for the RVNKCore
 * spigot-api build: line 2 names a <b>transfer target server</b> (validated against
 * {@link TransferService}'s {@code TransferConfig} targets) rather than another portal, and the
 * String-based {@code SignChangeEvent#getLine} API is used instead of Paper's Adventure components.
 * Services are resolved at event time via {@link RVNKCore#getServiceSafe} (matching
 * {@link ChatRelayListener}) so the listener stays valid across service re-registration.</p>
 *
 * @since 1.5.25
 */
public class PortalSignListener implements Listener {

    /** PersistentDataContainer key storing the portal id on the sign block. */
    private static final String PORTAL_ID_KEY = PortalSignWriter.PORTAL_ID_KEY;

    private final RVNKCore plugin;
    private final LogManager logger;
    private final NamespacedKey portalIdKey;

    /**
     * Creates a new PortalSignListener.
     *
     * @param plugin The owning RVNKCore plugin
     */
    public PortalSignListener(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.portalIdKey = new NamespacedKey(plugin, PORTAL_ID_KEY);
    }

    /**
     * Registers a cross-server portal when a valid portal sign is written.
     *
     * <p>Validates the header, permission, target server, and trigger-block mount before persisting
     * via {@link PortalService#createPortal}. All failure paths message the player and return without
     * side effects. On success the sign block is PDC-stamped (deferred one tick so the finished sign
     * state is stamped after the event applies its lines).</p>
     *
     * @param event The sign change event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();
        if (config == null || !config.isEnabled()) return;

        // Autoheal: if this sign is already a registered portal (PDC-stamped), re-editing it restores
        // the portal display and keeps the portal — the sign cannot be vandalised or its info wiped.
        BlockState existingState = event.getBlock().getState();
        if (existingState instanceof Sign existingSign) {
            String existingId = existingSign.getPersistentDataContainer()
                    .get(portalIdKey, PersistentDataType.STRING);
            if (existingId != null) {
                Optional<PortalDTO> existing = portalService.getPortalById(existingId);
                if (existing.isPresent()) {
                    String[] lines = buildDisplayLines(
                            RVNKCore.getServiceSafe(TransferService.class),
                            existing.get().getTargetServer(), existingId, config);
                    for (int i = 0; i < 4; i++) event.setLine(i, lines[i]);
                    return;
                }
                // Stamp survives but the portal is gone (deleted, rolled back, DB resync). Returning
                // here would leave the sign inert forever: never re-registerable, never editable.
                // Drop the stale stamp and fall through so this edit is treated as a fresh sign.
                existingSign.getPersistentDataContainer().remove(portalIdKey);
                existingSign.update();
                logger.debug("Cleared stale portal stamp " + existingId + " from sign at "
                        + event.getBlock().getLocation() + " — portal no longer registered");
            }
        }

        String header = event.getLine(0);
        if (header == null || !header.trim().equalsIgnoreCase(config.getSignHeader())) {
            return; // Not a portal sign.
        }

        Player player = event.getPlayer();

        if (!player.hasPermission(config.getPermissionCreate())) {
            player.sendMessage("§cYou don't have permission to create cross-server portals.");
            return;
        }

        String line2 = event.getLine(1);
        String targetServer = line2 != null ? line2.trim() : "";
        if (targetServer.isEmpty()) {
            player.sendMessage("§cLine 2 must be the target server name.");
            return;
        }

        // Validate the target against the configured transfer targets so we never persist a portal
        // that transfer() can never satisfy.
        TransferService transferService = RVNKCore.getServiceSafe(TransferService.class);
        if (transferService == null || transferService.getConfig().resolveTarget(targetServer) == null) {
            String valid = transferService != null
                    ? String.join(", ", transferService.getConfig().getTargetNames()) : "";
            if (valid.isEmpty()) valid = "(none configured)";
            player.sendMessage("§cUnknown server target '" + targetServer + "' (valid: " + valid + ").");
            return;
        }

        // Resolve the trigger block the sign is mounted on.
        Block signBlock = event.getBlock();
        Block triggerBlock = resolveMountBlock(signBlock);
        if (triggerBlock == null) {
            player.sendMessage("§cThe portal sign must be a placed sign.");
            return;
        }

        Material triggerMaterial = config.getTriggerMaterial();
        if (triggerMaterial == null || triggerBlock.getType() != triggerMaterial) {
            String needed = triggerMaterial != null ? triggerMaterial.name() : config.getTriggerBlock();
            player.sendMessage("§cThe sign must be placed on a " + needed + " block.");
            return;
        }

        // Detect the framed interior built out of the trigger material around the sign's mount block.
        PortalFrameBuilder frameBuilder = portalService.getFrameBuilder();
        Optional<PortalFrameBuilder.Frame> frameOpt = frameBuilder.detect(triggerBlock, triggerMaterial);
        if (frameOpt.isEmpty()) {
            player.sendMessage("§cNo valid " + triggerMaterial.name()
                    + " portal frame around this sign.");
            return;
        }
        PortalFrameBuilder.Frame frame = frameOpt.get();

        PortalService.PortalResult result = portalService.createFramePortal(
                triggerBlock.getWorld().getName(),
                triggerBlock.getX(), triggerBlock.getY(), triggerBlock.getZ(),
                targetServer, player.getUniqueId().toString(), frame.interior());

        if (!result.isSuccess()) {
            player.sendMessage("§c" + result.message());
            return;
        }

        // Light the interior with portal material once registration succeeds.
        frameBuilder.fill(triggerBlock.getWorld(), frame.interior(), frame.axis());

        final String portalId = result.portal().getPortalId();

        // Reformat the sign to a friendly display: [server] | destination server | world | id.
        String[] lines = buildDisplayLines(transferService, targetServer, portalId, config);
        for (int i = 0; i < 4; i++) {
            event.setLine(i, lines[i]);
        }

        // Defer the PDC stamp one tick: the sign's text/state is finalized after this event returns.
        Bukkit.getScheduler().runTask(plugin, () -> stampSign(signBlock, portalId));

        player.sendMessage("§aCross-server portal to '" + targetServer + "' lit.");
        logger.info("Framed portal registered by " + player.getName() + " -> '" + targetServer
                + "' (" + frame.interior().size() + " blocks, axis " + frame.axis() + ") at "
                + triggerBlock.getWorld().getName() + " "
                + triggerBlock.getX() + "," + triggerBlock.getY() + "," + triggerBlock.getZ());
    }

    /**
     * Removes a portal when its stamped sign is broken.
     *
     * <p>Only signs carrying the {@link #PORTAL_ID_KEY} PDC tag are treated as portal signs. Breaking
     * one requires the delete permission (otherwise the break is cancelled); the portal row is
     * resolved from the sign's still-current mount block and removed via
     * {@link PortalService#deletePortal}.</p>
     *
     * @param event The block break event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) return;

        String portalId = sign.getPersistentDataContainer().get(portalIdKey, PersistentDataType.STRING);
        if (portalId == null) return; // Not a portal sign.

        PortalService portalService = RVNKCore.getServiceSafe(PortalService.class);
        if (portalService == null) return;
        PortalConfig config = portalService.getConfig();

        Player player = event.getPlayer();

        // A stamp pointing at a portal that no longer exists protects nothing. Blocking the break
        // would strand an un-removable sign in the world for anyone without delete permission.
        if (portalService.getPortalById(portalId).isEmpty()) {
            logger.debug("Sign at " + block.getLocation() + " carried stale portal stamp " + portalId
                    + " — allowing break, portal is not registered");
            return;
        }

        if (config != null && !player.hasPermission(config.getPermissionDelete())) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to remove cross-server portals.");
            return;
        }

        // Remove by the id stamped on the sign: clears all index entries and returns the interior
        // NETHER_PORTAL blocks to AIR. The sign is already being destroyed, so skip clearing it.
        boolean removed = portalService.deletePortalById(portalId, false);

        if (removed) {
            player.sendMessage("§aCross-server portal removed.");
            logger.info("Portal removed by " + player.getName() + " (id " + portalId + ")");
        }
    }

    /**
     * Resolves the block a sign is mounted on: the attached block behind a wall sign, or the block
     * below a standing sign.
     *
     * @param signBlock The sign block
     * @return the mounting block, or null when the block is not a sign
     */
    private Block resolveMountBlock(Block signBlock) {
        return PortalSignWriter.resolveMountBlock(signBlock);
    }

    /**
     * Builds the four portal-sign display lines: {@code [server]} / friendly server name / world / id.
     * Shared by creation and by the re-edit autoheal so a portal sign always shows the same info.
     *
     * @param transferService The transfer service (for the target's friendly name/world); may be null
     * @param targetServer    The target server name
     * @param portalId        The portal id (drives the short-id line)
     * @return four legacy-coded sign lines
     */
    private String[] buildDisplayLines(TransferService transferService, String targetServer,
                                       String portalId, PortalConfig config) {
        return PortalSignWriter.buildDisplayLines(transferService, targetServer, portalId,
                config != null ? config.getSignHeader() : null);
    }

    /**
     * Stamps the portal id into the sign block's PersistentDataContainer.
     *
     * @param signBlock The sign block
     * @param portalId  The portal id to store
     */
    private void stampSign(Block signBlock, String portalId) {
        if (!PortalSignWriter.stamp(signBlock, portalIdKey, portalId)) {
            logger.warning("Portal sign PDC stamp skipped: block at " + signBlock.getLocation()
                    + " is no longer a sign");
        }
    }
}
