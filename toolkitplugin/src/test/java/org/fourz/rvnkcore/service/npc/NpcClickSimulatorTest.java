package org.fourz.rvnkcore.service.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Event construction and outcomes of the /rvnk npc click simulator (#2255). */
class NpcClickSimulatorTest {

    /** A fake event bus: records every event, then lets each registered listener see it. */
    private final List<Event> fired = new ArrayList<>();
    private final List<Consumer<RvnkNpcInteractEvent>> listeners = new ArrayList<>();
    private NpcInteractionTracker tracker;
    private NpcClickSimulator simulator;
    private Player player;
    private UUID playerId;
    private World world;
    private boolean available = true;

    /** A fake bridge holding one NPC by key. */
    private final class FakeNpcService implements INpcService {
        private final Map<String, NpcRef> npcs;

        FakeNpcService(Map<String, NpcRef> npcs) {
            this.npcs = npcs;
        }

        @Override public boolean isAvailable() { return available; }
        @Override public Optional<NpcRef> findByKey(String key) {
            String normalized = NpcKeys.normalize(key);
            return normalized == null ? Optional.empty() : Optional.ofNullable(npcs.get(normalized));
        }
        @Override public List<String> listKeys() { return List.copyOf(npcs.keySet()); }
        @Override public Optional<String> keyOf(Entity entity) { return Optional.empty(); }
        @Override public TagResult tag(int backingId, String key) { return TagResult.UNAVAILABLE; }
        @Override public TagResult untag(String key) { return TagResult.UNAVAILABLE; }
        @Override public Optional<NpcInteraction> lastInteraction(UUID p) { return tracker.last(p); }
    }

    @BeforeEach
    void setUp() {
        tracker = new NpcInteractionTracker(16, () -> 7_000L);
        world = mock(World.class);
        when(world.getName()).thenReturn("journey");
        NpcRef guide = new NpcRef("guide_cavern", "Warden Tolla", "journey",
                new Location(world, 10.5, 65, 20.5, 180f, 0f), 12, true);
        NpcClickDispatcher dispatcher = new NpcClickDispatcher(tracker, event -> {
            fired.add(event);
            if (event instanceof RvnkNpcInteractEvent rvnk) {
                listeners.forEach(l -> l.accept(rvnk));
            }
        });
        simulator = new NpcClickSimulator(new FakeNpcService(Map.of("guide_cavern", guide)), dispatcher);
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Shadowmelt");
    }

    @Test
    void rightClickFiresTheEventWithTheNpcFields() {
        List<RvnkNpcInteractEvent> seen = new ArrayList<>();
        listeners.add(seen::add);

        NpcClickSimulator.Outcome outcome = simulator.click(player, "guide_cavern", ClickType.RIGHT);

        assertEquals(NpcClickSimulator.Status.FIRED, outcome.status());
        assertTrue(outcome.fired());
        assertEquals(1, fired.size());
        assertEquals(1, seen.size(), "a listener on the bus saw the event");
        RvnkNpcInteractEvent event = seen.get(0);
        assertSame(outcome.event(), event);
        assertSame(player, event.getPlayer());
        assertEquals("guide_cavern", event.getNpcKey());
        assertEquals("Warden Tolla", event.getNpcName());
        assertEquals(ClickType.RIGHT, event.getClickType());
        assertEquals("journey", event.getLocation().getWorld().getName());
        assertEquals(10.5, event.getLocation().getX());
        assertEquals(65, event.getLocation().getBlockY());
        assertFalse(event.isAsynchronous(), "same main-thread event as a real click");
        assertFalse(event.isCancelled());
        assertEquals(12, outcome.npc().getBackingId());
    }

    @Test
    void leftClickCarriesLeft() {
        NpcClickSimulator.Outcome outcome = simulator.click(player, "guide_cavern", ClickType.LEFT);
        assertEquals(ClickType.LEFT, outcome.event().getClickType());
    }

    @Test
    void keyIsCaseInsensitive() {
        NpcClickSimulator.Outcome outcome = simulator.click(player, "Guide_Cavern", ClickType.RIGHT);
        assertEquals(NpcClickSimulator.Status.FIRED, outcome.status());
        assertEquals("guide_cavern", outcome.event().getNpcKey());
    }

    @Test
    void uncancelledClickIsRecordedLikeARealClick() {
        simulator.click(player, "guide_cavern", ClickType.RIGHT);

        NpcInteraction last = tracker.last(playerId).orElseThrow();
        assertEquals("guide_cavern", last.key());
        assertEquals("Warden Tolla", last.displayName());
        assertEquals(ClickType.RIGHT, last.clickType());
        assertEquals(7_000L, last.atMillis());
    }

    @Test
    void cancelledClickIsReportedAndNotRecorded() {
        listeners.add(event -> event.setCancelled(true));

        NpcClickSimulator.Outcome outcome = simulator.click(player, "guide_cavern", ClickType.RIGHT);

        assertEquals(NpcClickSimulator.Status.CANCELLED, outcome.status());
        assertTrue(outcome.fired(), "a cancelled click still fired");
        assertTrue(outcome.event().isCancelled());
        assertTrue(tracker.last(playerId).isEmpty());
    }

    @Test
    void unknownKeyFiresNothing() {
        NpcClickSimulator.Outcome outcome = simulator.click(player, "nobody", ClickType.RIGHT);

        assertEquals(NpcClickSimulator.Status.NOT_FOUND, outcome.status());
        assertFalse(outcome.fired());
        assertNull(outcome.event());
        assertTrue(fired.isEmpty());
    }

    @Test
    void invalidKeyFiresNothing() {
        assertEquals(NpcClickSimulator.Status.NOT_FOUND, simulator.click(player, "bad key!", ClickType.RIGHT).status());
        assertTrue(fired.isEmpty());
    }

    @Test
    void unavailableBridgeFiresNothing() {
        available = false;

        assertEquals(NpcClickSimulator.Status.UNAVAILABLE, simulator.click(player, "guide_cavern", ClickType.RIGHT).status());
        assertTrue(fired.isEmpty());
    }
}
