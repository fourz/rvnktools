package org.fourz.rvnkcore.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a player clicks an NPC that carries an RVNK key (#2213).
 *
 * <p>This is the only NPC event consumers should listen to. RVNKQuests and RVNKEvents depend on
 * this contract, never on Citizens, so the NPC plugin behind the bridge can be swapped. NPCs without
 * an RVNK key never produce this event.</p>
 *
 * <p><b>Thread:</b> always fired synchronously on the server main thread.</p>
 *
 * <p><b>Cancellation:</b> cancelling this event cancels the underlying NPC click. With Citizens a
 * cancelled right-click also cancels the {@code PlayerInteractEntityEvent} and skips the NPC's
 * {@code /npc command} actions; a cancelled left-click skips the NPC's left-click commands. A
 * cancelled interaction is not recorded as the player's last interaction.</p>
 *
 * <pre>
 * &#64;EventHandler(ignoreCancelled = true)
 * public void onNpc(RvnkNpcInteractEvent event) {
 *     if (event.getClickType() == RvnkNpcInteractEvent.ClickType.RIGHT
 *             &amp;&amp; event.getNpcKey().equals("harbour_master")) {
 *         // advance a TALK_TO objective
 *     }
 * }
 * </pre>
 *
 * @since 1.5.99-alpha
 */
public class RvnkNpcInteractEvent extends Event implements Cancellable {

    /** Which mouse button the player used on the NPC. */
    public enum ClickType {
        /** Attack / left mouse button. */
        LEFT,
        /** Use / right mouse button. */
        RIGHT
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String npcKey;
    private final String npcName;
    private final ClickType clickType;
    private final Location location;
    private boolean cancelled;

    /**
     * @param player    the player who clicked
     * @param npcKey    the NPC's RVNK key (lower-case)
     * @param npcName   the NPC's name without colour codes; null becomes ""
     * @param clickType LEFT or RIGHT
     * @param location  the NPC's location at the click; may be null if the NPC has none
     */
    public RvnkNpcInteractEvent(Player player, String npcKey, String npcName, ClickType clickType, Location location) {
        super(false); // main thread
        this.player = Objects.requireNonNull(player, "player");
        this.npcKey = Objects.requireNonNull(npcKey, "npcKey");
        this.npcName = npcName == null ? "" : npcName;
        this.clickType = Objects.requireNonNull(clickType, "clickType");
        this.location = location == null ? null : location.clone();
    }

    /** @return the player who clicked the NPC */
    public Player getPlayer() {
        return player;
    }

    /** @return the NPC's RVNK key, lower-case {@code [a-z0-9_-]{1,48}} */
    public String getNpcKey() {
        return npcKey;
    }

    /** @return the NPC's name without colour codes; never null */
    public String getNpcName() {
        return npcName;
    }

    /** @return LEFT or RIGHT */
    public ClickType getClickType() {
        return clickType;
    }

    /** @return a copy of the NPC's location at the click, or null when unknown */
    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
