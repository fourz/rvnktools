package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.service.npc.NpcClickDispatcher;
import org.fourz.rvnkcore.service.npc.NpcClickSimulator;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A simulated click and a real Citizens click on the same NPC produce the same event and the same
 * last-interaction record (#2255). Both run through one {@link NpcClickDispatcher}, as in
 * {@code NpcBridge.install}.
 */
class SimulatedClickParityTest {

    private final List<Event> fired = new ArrayList<>();
    private NpcInteractionTracker tracker;
    private CitizensNpcListener listener;
    private NpcClickSimulator simulator;
    private NPC npc;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        tracker = new NpcInteractionTracker(16, () -> 99L);
        NpcClickDispatcher dispatcher = new NpcClickDispatcher(tracker, fired::add);
        listener = new CitizensNpcListener(dispatcher, null);

        World world = mock(World.class);
        when(world.getName()).thenReturn("journey");
        FakeMetadataStore data = new FakeMetadataStore();
        data.setPersistent(NpcKeys.METADATA_KEY, "guide_cavern");
        npc = CitizensNpcServiceTest.npc(12, "Warden Tolla", data, new Location(world, 10.5, 65, 20.5));

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenAnswer(inv -> List.of(npc).iterator());
        CitizensNpcService service = new CitizensNpcService(() -> registry, () -> null, () -> true, tracker, null);
        simulator = new NpcClickSimulator(service, dispatcher);

        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Shadowmelt");
    }

    @Test
    void rightClickParity() {
        listener.onRightClick(new NPCRightClickEvent(npc, player));
        NpcInteraction realRecord = tracker.last(playerId).orElseThrow();
        simulator.click(player, "guide_cavern", ClickType.RIGHT);
        NpcInteraction simulatedRecord = tracker.last(playerId).orElseThrow();

        assertEquals(2, fired.size());
        assertSameEvent((RvnkNpcInteractEvent) fired.get(0), (RvnkNpcInteractEvent) fired.get(1));
        assertEquals(realRecord, simulatedRecord);
    }

    @Test
    void leftClickParity() {
        listener.onLeftClick(new NPCLeftClickEvent(npc, player));
        simulator.click(player, "guide_cavern", ClickType.LEFT);

        assertSameEvent((RvnkNpcInteractEvent) fired.get(0), (RvnkNpcInteractEvent) fired.get(1));
    }

    private static void assertSameEvent(RvnkNpcInteractEvent real, RvnkNpcInteractEvent simulated) {
        assertSame(real.getPlayer(), simulated.getPlayer());
        assertEquals(real.getNpcKey(), simulated.getNpcKey());
        assertEquals(real.getNpcName(), simulated.getNpcName());
        assertEquals(real.getClickType(), simulated.getClickType());
        assertEquals(real.getLocation(), simulated.getLocation());
        assertEquals(real.isAsynchronous(), simulated.isAsynchronous());
        assertEquals(real.isCancelled(), simulated.isCancelled());
        assertEquals(real.getClass(), simulated.getClass());
    }
}
