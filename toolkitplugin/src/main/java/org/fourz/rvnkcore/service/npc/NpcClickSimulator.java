package org.fourz.rvnkcore.service.npc;

import org.bukkit.entity.Player;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;

import java.util.Objects;
import java.util.Optional;

/**
 * Simulates a player's click on a keyed NPC for QA: {@code /rvnk npc click} (#2255).
 *
 * <p>Looks the key up in {@link INpcService} and fires the event through the same
 * {@link NpcClickDispatcher} the Citizens listener uses. The event carries the same fields a real
 * click carries: the NPC's name and its live location (stored location when despawned), both read
 * through {@link NpcRef}, which takes them from the same Citizens calls as the listener.</p>
 *
 * <p><b>What does not run.</b> Only {@link RvnkNpcInteractEvent} fires. Citizens' own
 * {@code NPCRightClickEvent}/{@code NPCLeftClickEvent} and the NPC's {@code /npc command} actions
 * do not, because no entity was clicked. The player's position is not checked, as for a real click
 * no RVNK consumer checks distance either.</p>
 *
 * <p>No permission or tier gate here; the command applies {@code NpcClickGate} first.</p>
 *
 * @since 1.5.102-alpha
 */
public final class NpcClickSimulator {

    /** What a simulated click did. */
    public enum Status {
        /** The event fired and no listener cancelled it. */
        FIRED,
        /** The event fired and a listener cancelled it. */
        CANCELLED,
        /** No NPC carries the key, or the key is invalid. */
        NOT_FOUND,
        /** The NPC bridge has no NPC plugin behind it. */
        UNAVAILABLE
    }

    /**
     * @param status the outcome
     * @param npc    the NPC clicked; null for NOT_FOUND and UNAVAILABLE
     * @param event  the fired event; null for NOT_FOUND and UNAVAILABLE
     */
    public record Outcome(Status status, NpcRef npc, RvnkNpcInteractEvent event) {
        /** @return true when the event fired, cancelled or not */
        public boolean fired() {
            return status == Status.FIRED || status == Status.CANCELLED;
        }
    }

    private final INpcService service;
    private final NpcClickDispatcher dispatcher;

    /**
     * @param service    the NPC bridge service
     * @param dispatcher the dispatcher shared with the real-click listener
     */
    public NpcClickSimulator(INpcService service, NpcClickDispatcher dispatcher) {
        this.service = Objects.requireNonNull(service, "service");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /**
     * Fires a simulated click. Call on the main thread.
     *
     * @param player    the online player who "clicks"
     * @param rawKey    the NPC's RVNK key; case-insensitive
     * @param clickType LEFT or RIGHT
     * @return the outcome; never null
     */
    public Outcome click(Player player, String rawKey, ClickType clickType) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clickType, "clickType");
        if (!service.isAvailable()) {
            return new Outcome(Status.UNAVAILABLE, null, null);
        }
        Optional<NpcRef> found = service.findByKey(rawKey);
        if (found.isEmpty()) {
            return new Outcome(Status.NOT_FOUND, null, null);
        }
        NpcRef npc = found.get();
        RvnkNpcInteractEvent event = dispatcher.dispatch(player, npc.getKey(), npc.getDisplayName(), clickType,
                npc.getLocation());
        return new Outcome(event.isCancelled() ? Status.CANCELLED : Status.FIRED, npc, event);
    }
}
