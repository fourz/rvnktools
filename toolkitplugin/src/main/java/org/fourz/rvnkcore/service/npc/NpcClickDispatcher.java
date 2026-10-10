package org.fourz.rvnkcore.service.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The one path that fires {@link RvnkNpcInteractEvent} (#2213, #2255).
 *
 * <p>A real click (the Citizens listener) and a simulated click ({@code /rvnk npc click}) both
 * come through {@link #dispatch}. So a consumer such as RVNKQuests' {@code NpcInteractionCoordinator}
 * cannot tell them apart: it gets the same event fields, on the same thread, and a click that no
 * listener cancelled is recorded as the player's last interaction in both cases.</p>
 *
 * <p>Names no Citizens type, so it loads on a server without Citizens.</p>
 *
 * @since 1.5.102-alpha
 */
public final class NpcClickDispatcher {

    private final NpcInteractionTracker tracker;
    private final Consumer<Event> eventCaller;

    /**
     * @param tracker     last-interaction tracker
     * @param eventCaller fires an event ({@code Bukkit.getPluginManager()::callEvent})
     */
    public NpcClickDispatcher(NpcInteractionTracker tracker, Consumer<Event> eventCaller) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.eventCaller = Objects.requireNonNull(eventCaller, "eventCaller");
    }

    /**
     * Builds the event, fires it, and records the interaction when no listener cancelled it.
     * Call on the main thread.
     *
     * @param player    the player who clicked
     * @param key       the NPC's RVNK key (lower-case)
     * @param npcName   the NPC's name
     * @param clickType LEFT or RIGHT
     * @param location  the NPC's location; may be null
     * @return the fired event; check {@link RvnkNpcInteractEvent#isCancelled()}
     */
    public RvnkNpcInteractEvent dispatch(Player player, String key, String npcName, ClickType clickType,
                                         Location location) {
        RvnkNpcInteractEvent event = new RvnkNpcInteractEvent(player, key, npcName, clickType, location);
        eventCaller.accept(event);
        if (!event.isCancelled()) {
            tracker.record(player.getUniqueId(), key, npcName, clickType);
        }
        return event;
    }
}
