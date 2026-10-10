package org.fourz.rvnkcore.service.npc;

import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.model.NpcInteraction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers the last keyed NPC each player interacted with (#2213).
 *
 * <p>Bounded: an access-ordered map evicts the least recently touched player once
 * {@code capacity} players are held, so a long uptime with many visitors cannot grow it without
 * limit. In memory only; it is empty after a restart.</p>
 *
 * <p>Thread-safe. The click listener writes on the main thread, and PlaceholderAPI may read from
 * any thread (scoreboard and tab plugins resolve placeholders asynchronously).</p>
 *
 * @since 1.5.99-alpha
 */
public class NpcInteractionTracker {

    /** Default number of players remembered. */
    public static final int DEFAULT_CAPACITY = 1024;

    private final int capacity;
    private final LongSupplier clock;
    private final Map<UUID, NpcInteraction> lastByPlayer;

    public NpcInteractionTracker() {
        this(DEFAULT_CAPACITY, System::currentTimeMillis);
    }

    /**
     * @param capacity most players remembered; at least 1
     * @param clock    epoch-millisecond clock, injectable for tests
     */
    public NpcInteractionTracker(int capacity, LongSupplier clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.clock = clock;
        this.lastByPlayer = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, NpcInteraction> eldest) {
                return size() > NpcInteractionTracker.this.capacity;
            }
        };
    }

    /** Records an interaction at the current clock time. */
    public void record(UUID player, String key, String displayName, RvnkNpcInteractEvent.ClickType clickType) {
        if (player == null || key == null || clickType == null) {
            return;
        }
        NpcInteraction interaction = new NpcInteraction(key, displayName, clickType, clock.getAsLong());
        synchronized (lastByPlayer) {
            lastByPlayer.put(player, interaction);
        }
    }

    /** @return the player's last interaction, or empty */
    public Optional<NpcInteraction> last(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        synchronized (lastByPlayer) {
            return Optional.ofNullable(lastByPlayer.get(player));
        }
    }

    /** @return how many players are held now; never above the capacity */
    public int size() {
        synchronized (lastByPlayer) {
            return lastByPlayer.size();
        }
    }

    /** @return the configured capacity */
    public int capacity() {
        return capacity;
    }
}
