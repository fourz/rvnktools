package org.fourz.rvnkcore.service.portal;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.service.transfer.TransferService;

import java.util.Optional;

/**
 * Shared read/write logic for cross-server portal registration signs (#1859).
 *
 * <p>Extracted from {@code PortalSignListener} so the listener, {@link PortalService} and the
 * {@code /portal} command all render and identify portal signs the same way. Before this existed,
 * sign rendering and the PDC stamp lived privately inside the listener, which is why a portal whose
 * sign had been destroyed could not be repaired — and why {@code deletePortalById} had exactly one
 * reachable call site.</p>
 *
 * <p>All methods are main-thread Bukkit operations. Callers are responsible for confirming the
 * world and chunk are loaded first; nothing here force-loads a chunk, because treating an unloaded
 * chunk as "the sign is gone" would report a healthy portal as broken.</p>
 *
 * @since 1.5.64
 */
public final class PortalSignWriter {

    /** PersistentDataContainer key under which a registration sign stores its portal id. */
    public static final String PORTAL_ID_KEY = "portal_id";

    private PortalSignWriter() {
    }

    /**
     * Builds the {@link NamespacedKey} used for the portal-id PDC stamp.
     *
     * @param plugin the owning plugin
     * @return the namespaced key
     */
    public static NamespacedKey portalIdKey(Plugin plugin) {
        return new NamespacedKey(plugin, PORTAL_ID_KEY);
    }

    /**
     * Builds the four display lines of a portal sign: header, destination, world, short id.
     *
     * @param transferService the transfer service, for the target's friendly name and world; may be null
     * @param targetServer    the target server name
     * @param portalId        the portal id, which drives the short-id line
     * @return four legacy-coded sign lines
     */
    public static String[] buildDisplayLines(TransferService transferService, String targetServer, String portalId) {
        TransferConfig.Target tgt = transferService != null
                ? transferService.getConfig().resolveTarget(targetServer) : null;
        String display = (tgt != null && tgt.display() != null && !tgt.display().isEmpty())
                ? tgt.display() : targetServer;
        String world = (tgt != null && tgt.world() != null && !tgt.world().isEmpty())
                ? tgt.world() : "";
        String shortId = (portalId != null && portalId.length() >= 8)
                ? portalId.substring(0, 8) : (portalId != null ? portalId : "");
        return new String[]{
                "§9[server]",
                "§a" + display,
                world.isEmpty() ? "" : "§7" + world,
                "§8" + shortId
        };
    }

    /**
     * Reads the portal id stamped on a sign block.
     *
     * @param block the candidate sign block
     * @param key   the portal-id key
     * @return the stamped id, or empty when the block is not a sign or carries no stamp
     */
    public static Optional<String> readPortalId(Block block, NamespacedKey key) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sign.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /**
     * Stamps a portal id into a sign block's PersistentDataContainer.
     *
     * @param block    the sign block
     * @param key      the portal-id key
     * @param portalId the id to store
     * @return true when the stamp was applied; false when the block is no longer a sign
     */
    public static boolean stamp(Block block, NamespacedKey key, String portalId) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return false;
        }
        sign.getPersistentDataContainer().set(key, PersistentDataType.STRING, portalId);
        sign.update();
        return true;
    }

    /**
     * Writes both the display lines and the PDC stamp onto a sign block.
     *
     * @param block    the sign block
     * @param key      the portal-id key
     * @param portalId the portal id
     * @param lines    the four display lines
     * @return true when written; false when the block is no longer a sign
     */
    public static boolean write(Block block, NamespacedKey key, String portalId, String[] lines) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return false;
        }
        for (int i = 0; i < 4 && i < lines.length; i++) {
            sign.setLine(i, lines[i]);
        }
        sign.getPersistentDataContainer().set(key, PersistentDataType.STRING, portalId);
        sign.update();
        return true;
    }

    /**
     * Strips a sign back to a plain, reusable sign: removes the portal-id stamp and blanks the
     * four lines.
     *
     * <p>Called when a portal is removed. Without it the sign keeps a stamp pointing at a portal
     * that no longer exists, which makes the sign inert — it cannot be re-registered and its text
     * cannot be changed, because the stamp short-circuits the sign-change handler.</p>
     *
     * @param block the sign block
     * @param key   the portal-id key
     * @return true when the sign was cleared; false when the block is not (or is no longer) a sign
     */
    public static boolean clearStamp(Block block, NamespacedKey key) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return false;
        }
        sign.getPersistentDataContainer().remove(key);
        for (int i = 0; i < 4; i++) {
            sign.setLine(i, "");
        }
        sign.update();
        return true;
    }

    /**
     * Resolves the block a sign is mounted on: the block behind a wall sign, or the block below a
     * standing sign.
     *
     * @param signBlock the sign block
     * @return the mounting block, or null when the block is not a sign
     */
    public static Block resolveMountBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            // A wall sign faces outward; the block it is attached to sits behind it.
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        if (data instanceof org.bukkit.block.data.type.Sign) {
            // A standing sign is mounted on the block directly below it.
            return signBlock.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    /**
     * Finds the registration sign mounted on a portal's anchor block.
     *
     * <p>Searches the six neighbours of the anchor for a sign that is actually mounted on it. When
     * {@code portalId} is supplied, only a sign carrying that exact stamp matches — which is what
     * distinguishes "this portal's sign is intact" from "some other sign happens to be adjacent".</p>
     *
     * @param anchor   the portal anchor (the frame block the sign is mounted on)
     * @param key      the portal-id key
     * @param portalId the id the sign must carry, or null to accept any sign mounted on the anchor
     * @return the matching sign block, or empty
     */
    public static Optional<Block> findSignOnAnchor(Block anchor, NamespacedKey key, String portalId) {
        BlockFace[] faces = {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        };
        for (BlockFace face : faces) {
            Block candidate = anchor.getRelative(face);
            if (!(candidate.getState() instanceof Sign)) {
                continue;
            }
            Block mount = resolveMountBlock(candidate);
            if (mount == null || !mount.equals(anchor)) {
                continue;
            }
            if (portalId == null) {
                return Optional.of(candidate);
            }
            if (portalId.equals(readPortalId(candidate, key).orElse(null))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
