package org.fourz.rvnkcore.service.npc;

import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The bounded last-interaction map behind %rvnknpc_last_*% (#2213). */
class NpcInteractionTrackerTest {

    @Test
    void recordsAndReturnsLastInteraction() {
        AtomicLong clock = new AtomicLong(1_000L);
        NpcInteractionTracker tracker = new NpcInteractionTracker(8, clock::get);
        UUID player = UUID.randomUUID();

        tracker.record(player, "guide", "Guide", ClickType.RIGHT);
        clock.set(5_000L);
        tracker.record(player, "smith", "Smith", ClickType.LEFT);

        NpcInteraction last = tracker.last(player).orElseThrow();
        assertEquals("smith", last.key());
        assertEquals("Smith", last.displayName());
        assertEquals(ClickType.LEFT, last.clickType());
        assertEquals(5_000L, last.atMillis());
    }

    @Test
    void emptyForUnknownOrNullPlayer() {
        NpcInteractionTracker tracker = new NpcInteractionTracker();
        assertTrue(tracker.last(UUID.randomUUID()).isEmpty());
        assertTrue(tracker.last(null).isEmpty());
    }

    @Test
    void ignoresIncompleteRecords() {
        NpcInteractionTracker tracker = new NpcInteractionTracker();
        tracker.record(null, "guide", "Guide", ClickType.RIGHT);
        tracker.record(UUID.randomUUID(), null, "Guide", ClickType.RIGHT);
        tracker.record(UUID.randomUUID(), "guide", "Guide", null);
        assertEquals(0, tracker.size());
    }

    @Test
    void staysBoundedAndEvictsLeastRecentlyUsed() {
        NpcInteractionTracker tracker = new NpcInteractionTracker(3, () -> 0L);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        tracker.record(a, "k", "K", ClickType.RIGHT);
        tracker.record(b, "k", "K", ClickType.RIGHT);
        tracker.record(c, "k", "K", ClickType.RIGHT);
        tracker.last(a); // touch a, so b is now the eldest
        tracker.record(d, "k", "K", ClickType.RIGHT);

        assertEquals(3, tracker.size());
        assertTrue(tracker.last(a).isPresent());
        assertTrue(tracker.last(b).isEmpty(), "least recently used player should be evicted");
        assertTrue(tracker.last(c).isPresent());
        assertTrue(tracker.last(d).isPresent());
    }

    @Test
    void manyPlayersNeverExceedCapacity() {
        NpcInteractionTracker tracker = new NpcInteractionTracker(100, () -> 0L);
        for (int i = 0; i < 10_000; i++) {
            tracker.record(UUID.randomUUID(), "k", "K", ClickType.RIGHT);
        }
        assertEquals(100, tracker.size());
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new NpcInteractionTracker(0, () -> 0L));
    }
}
