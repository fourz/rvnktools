package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Event dispatch from mocked Citizens clicks (#2213). */
class CitizensNpcListenerTest {

    private final List<Event> fired = new ArrayList<>();
    private final List<String> debug = new ArrayList<>();
    private NpcInteractionTracker tracker;
    private CitizensNpcListener listener;
    private Player player;
    private UUID playerId;
    private World world;

    @BeforeEach
    void setUp() {
        tracker = new NpcInteractionTracker(16, () -> 42_000L);
        listener = new CitizensNpcListener(tracker, fired::add, debug::add);
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Shadowmelt");
        world = mock(World.class);
        when(world.getName()).thenReturn("southmesa");
    }

    private NPC keyedNpc(String key) {
        FakeMetadataStore data = new FakeMetadataStore();
        if (key != null) {
            data.setPersistent(NpcKeys.METADATA_KEY, key);
        }
        return CitizensNpcServiceTest.npc(12, "Harbour Master", data, new Location(world, 1, 64, 2));
    }

    @Test
    void rightClickOnKeyedNpcFiresTheRvnkEvent() {
        NPCRightClickEvent click = new NPCRightClickEvent(keyedNpc("harbour_master"), player);

        listener.onRightClick(click);

        assertEquals(1, fired.size());
        RvnkNpcInteractEvent event = assertInstanceOf(RvnkNpcInteractEvent.class, fired.get(0));
        assertSame(player, event.getPlayer());
        assertEquals("harbour_master", event.getNpcKey());
        assertEquals("Harbour Master", event.getNpcName());
        assertEquals(ClickType.RIGHT, event.getClickType());
        assertEquals("southmesa", event.getLocation().getWorld().getName());
        assertEquals(64, event.getLocation().getBlockY());
        assertFalse(event.isAsynchronous(), "the RVNK event is a main-thread event");
        assertFalse(click.isCancelled());
        assertTrue(debug.get(0).contains("key=harbour_master"), "debug log names the key");
    }

    @Test
    void leftClickFiresWithLeftClickType() {
        listener.onLeftClick(new NPCLeftClickEvent(keyedNpc("smith"), player));

        RvnkNpcInteractEvent event = (RvnkNpcInteractEvent) fired.get(0);
        assertEquals(ClickType.LEFT, event.getClickType());
        assertEquals("smith", event.getNpcKey());
    }

    @Test
    void untaggedNpcFiresNothing() {
        listener.onRightClick(new NPCRightClickEvent(keyedNpc(null), player));
        listener.onLeftClick(new NPCLeftClickEvent(keyedNpc(null), player));

        assertTrue(fired.isEmpty());
        assertTrue(tracker.last(playerId).isEmpty());
    }

    @Test
    void uncancelledClickIsRecordedAsLastInteraction() {
        listener.onRightClick(new NPCRightClickEvent(keyedNpc("harbour_master"), player));

        NpcInteraction last = tracker.last(playerId).orElseThrow();
        assertEquals("harbour_master", last.key());
        assertEquals("Harbour Master", last.displayName());
        assertEquals(ClickType.RIGHT, last.clickType());
        assertEquals(42_000L, last.atMillis());
    }

    @Test
    void cancellingTheRvnkEventCancelsTheCitizensClick() {
        CitizensNpcListener cancelling = new CitizensNpcListener(tracker,
                event -> ((RvnkNpcInteractEvent) event).setCancelled(true), null);

        NPCRightClickEvent right = new NPCRightClickEvent(keyedNpc("guard"), player);
        cancelling.onRightClick(right);
        NPCLeftClickEvent left = new NPCLeftClickEvent(keyedNpc("guard"), player);
        cancelling.onLeftClick(left);

        assertTrue(right.isCancelled());
        assertTrue(left.isCancelled());
        assertTrue(tracker.last(playerId).isEmpty(), "a cancelled interaction is not recorded");
    }

    @Test
    void handlersIgnoreAlreadyCancelledClicksAndRunEarly() throws Exception {
        for (String name : List.of("onRightClick", "onLeftClick")) {
            Class<?> type = name.equals("onRightClick") ? NPCRightClickEvent.class : NPCLeftClickEvent.class;
            EventHandler handler = CitizensNpcListener.class.getMethod(name, type).getAnnotation(EventHandler.class);
            assertNotNull(handler, name + " must be an @EventHandler");
            assertTrue(handler.ignoreCancelled(), name);
            assertEquals(EventPriority.LOW, handler.priority(), name);
        }
    }

    @Test
    void nullClickerIsIgnored() {
        listener.onRightClick(new NPCRightClickEvent(keyedNpc("guide"), null));
        assertTrue(fired.isEmpty());
    }
}
