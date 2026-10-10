package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.service.npc.NpcClickDispatcher;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;

import java.util.function.Consumer;

/**
 * Turns Citizens click events on keyed NPCs into {@link RvnkNpcInteractEvent} (#2213).
 *
 * <p>Citizens fires its click events synchronously from Bukkit's interact and damage handlers, so
 * the RVNK event is fired on the main thread too. NPCs without an RVNK key are ignored.</p>
 *
 * <p><b>Cancellation.</b> Citizens checks {@code isCancelled()} on both click events after they
 * return: a cancelled right-click cancels the {@code PlayerInteractEntityEvent} and skips
 * {@code CommandTrait}, a cancelled left-click skips {@code CommandTrait}. So cancelling the RVNK
 * event is honoured, and this listener copies the cancel back. It runs at LOW priority so a
 * consumer's cancel is visible to other plugins' NORMAL handlers of the Citizens events, and it
 * ignores clicks another plugin already cancelled.</p>
 *
 * <p>The event is built and fired by {@link NpcClickDispatcher}, the same path
 * {@code /rvnk npc click} uses (#2255), so a simulated click reaches consumers exactly as a real
 * one does.</p>
 *
 * @since 1.5.99-alpha
 */
public class CitizensNpcListener implements Listener {

    private final NpcClickDispatcher dispatcher;
    private final Consumer<String> debug;

    /**
     * @param tracker     last-interaction tracker
     * @param eventCaller fires an event ({@code Bukkit.getPluginManager()::callEvent})
     * @param debug       debug-log sink
     */
    public CitizensNpcListener(NpcInteractionTracker tracker, Consumer<Event> eventCaller, Consumer<String> debug) {
        this(new NpcClickDispatcher(tracker, eventCaller), debug);
    }

    /**
     * @param dispatcher the click dispatcher shared with {@code /rvnk npc click} (#2255)
     * @param debug      debug-log sink
     */
    public CitizensNpcListener(NpcClickDispatcher dispatcher, Consumer<String> debug) {
        this.dispatcher = dispatcher;
        this.debug = debug != null ? debug : message -> { };
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRightClick(NPCRightClickEvent event) {
        if (dispatch(event.getNPC(), event.getClicker(), ClickType.RIGHT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeftClick(NPCLeftClickEvent event) {
        if (dispatch(event.getNPC(), event.getClicker(), ClickType.LEFT)) {
            event.setCancelled(true);
        }
    }

    /**
     * Fires the RVNK event for a keyed NPC.
     *
     * @return true when a consumer cancelled the RVNK event
     */
    boolean dispatch(NPC npc, Player player, ClickType clickType) {
        if (npc == null || player == null) {
            return false;
        }
        String key = CitizensNpcService.keyOfNpc(npc);
        if (key == null) {
            return false;
        }

        RvnkNpcInteractEvent rvnkEvent = dispatcher.dispatch(
                player, key, npc.getName(), clickType, CitizensNpcService.locationOf(npc));

        debug.accept("RvnkNpcInteractEvent key=" + key + " player=" + player.getName()
                + " click=" + clickType + " npcId=" + npc.getId()
                + (rvnkEvent.isCancelled() ? " CANCELLED" : ""));

        return rvnkEvent.isCancelled();
    }
}
