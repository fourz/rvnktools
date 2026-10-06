package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.npc.NPCSelector;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService.TagResult;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tag / untag / find for the Citizens adapter (#2213), with Citizens' registry and NPCs mocked and a
 * {@link FakeMetadataStore} holding the keys.
 */
class CitizensNpcServiceTest {

    private final List<NPC> npcs = new ArrayList<>();
    private NPCRegistry registry;
    private NPCSelector selector;
    private final AtomicBoolean citizensReady = new AtomicBoolean(true);
    private final List<String> warnings = new ArrayList<>();
    private CitizensNpcService service;

    @BeforeEach
    void setUp() {
        registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenAnswer(inv -> new ArrayList<>(npcs).iterator());
        when(registry.getById(anyInt())).thenAnswer(inv -> npcs.stream()
                .filter(n -> n.getId() == inv.<Integer>getArgument(0)).findFirst().orElse(null));
        selector = mock(NPCSelector.class);
        service = new CitizensNpcService(() -> registry, () -> selector, citizensReady::get,
                new NpcInteractionTracker(), warnings::add);
    }

    /** A mocked NPC backed by Citizens' real metadata store. */
    static NPC npc(int id, String name, MetadataStore data, Location stored) {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(id);
        when(npc.getName()).thenReturn(name);
        when(npc.data()).thenReturn(data);
        when(npc.isSpawned()).thenReturn(false);
        when(npc.getStoredLocation()).thenReturn(stored);
        return npc;
    }

    private NPC addNpc(int id, String name) {
        NPC npc = npc(id, name, new FakeMetadataStore(), null);
        npcs.add(npc);
        return npc;
    }

    @Test
    void tagStoresPersistentMetadataAndSaves() {
        NPC guide = addNpc(7, "Guide");

        assertEquals(TagResult.TAGGED, service.tag(7, "Harbour_Master"));

        assertEquals("harbour_master", guide.data().get(NpcKeys.METADATA_KEY));
        assertTrue(((FakeMetadataStore) guide.data()).isPersistent(NpcKeys.METADATA_KEY),
                "the key must use setPersistent, the only metadata Citizens writes to saves.yml");
        verify(registry).saveToStore();
        assertTrue(service.findByKey("harbour_master").isPresent());
    }

    @Test
    void tagIsIdempotentForTheSameKey() {
        addNpc(7, "Guide");
        service.tag(7, "guide");
        clearInvocations(registry);

        assertEquals(TagResult.TAGGED, service.tag(7, "guide"));
        verify(registry, never()).saveToStore();
    }

    @Test
    void retagReplacesThePreviousKey() {
        addNpc(7, "Guide");
        service.tag(7, "old_key");

        assertEquals(TagResult.RETAGGED, service.tag(7, "new_key"));
        assertTrue(service.findByKey("old_key").isEmpty());
        assertTrue(service.findByKey("new_key").isPresent());
    }

    @Test
    void keysAreUniquePerServer() {
        addNpc(1, "First");
        addNpc(2, "Second");
        assertEquals(TagResult.TAGGED, service.tag(1, "guide"));

        assertEquals(TagResult.KEY_IN_USE, service.tag(2, "GUIDE"));
        assertEquals(1, service.findByKey("guide").orElseThrow().getBackingId());
    }

    @Test
    void tagRejectsInvalidKeyAndMissingNpc() {
        addNpc(1, "First");
        assertEquals(TagResult.INVALID_KEY, service.tag(1, "bad key"));
        assertEquals(TagResult.INVALID_KEY, service.tag(1, null));
        assertEquals(TagResult.NPC_NOT_FOUND, service.tag(99, "guide"));
        verify(registry, never()).saveToStore();
    }

    @Test
    void untagRemovesTheKey() {
        NPC guide = addNpc(7, "Guide");
        service.tag(7, "guide");

        assertEquals(TagResult.UNTAGGED, service.untag("Guide"));
        assertNull(guide.data().get(NpcKeys.METADATA_KEY));
        assertTrue(service.findByKey("guide").isEmpty());
        assertEquals(TagResult.KEY_NOT_FOUND, service.untag("guide"));
        assertEquals(TagResult.INVALID_KEY, service.untag("no way"));
    }

    @Test
    void findByKeyBuildsTheRef() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("southmesa");
        Location stored = new Location(world, 10.5, 64, -3.2);
        FakeMetadataStore data = new FakeMetadataStore();
        npcs.add(npc(12, "Harbour Master", data, stored));
        service.tag(12, "harbour_master");

        NpcRef ref = service.findByKey("HARBOUR_MASTER").orElseThrow();
        assertEquals("harbour_master", ref.getKey());
        assertEquals("Harbour Master", ref.getDisplayName());
        assertEquals("southmesa", ref.getWorld());
        assertEquals(10, ref.getLocation().getBlockX());
        assertEquals(12, ref.getBackingId());
        assertFalse(ref.isSpawned());
    }

    @Test
    void findByKeyPrefersTheLiveEntityLocation() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        NPC npc = addNpc(3, "Walker");
        Entity entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(world, 100, 70, 100));
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(entity);
        service.tag(3, "walker");

        NpcRef ref = service.findByKey("walker").orElseThrow();
        assertTrue(ref.isSpawned());
        assertEquals(100, ref.getLocation().getBlockX());
        assertEquals("world", ref.getWorld());
    }

    @Test
    void findByKeyToleratesAMissingLocation() {
        addNpc(4, "Nowhere");
        service.tag(4, "nowhere");
        NpcRef ref = service.findByKey("nowhere").orElseThrow();
        assertNull(ref.getLocation());
        assertNull(ref.getWorld());
    }

    @Test
    void listKeysIsSortedAndSkipsUntaggedAndInvalidValues() {
        addNpc(1, "A");
        addNpc(2, "B");
        addNpc(3, "Untagged");
        NPC handEdited = addNpc(4, "HandEdited");
        service.tag(1, "zeta");
        service.tag(2, "alpha");
        handEdited.data().setPersistent(NpcKeys.METADATA_KEY, "not a valid key!");

        assertEquals(List.of("alpha", "zeta"), service.listKeys());
    }

    @Test
    void keyOfResolvesAnNpcEntity() {
        NPC npc = addNpc(5, "Smith");
        service.tag(5, "smith");
        Entity npcEntity = mock(Entity.class);
        Entity plainEntity = mock(Entity.class);
        when(registry.getNPC(npcEntity)).thenReturn(npc);
        when(registry.getNPC(plainEntity)).thenReturn(null);

        assertEquals("smith", service.keyOf(npcEntity).orElseThrow());
        assertTrue(service.keyOf(plainEntity).isEmpty());
        assertTrue(service.keyOf(null).isEmpty());
    }

    @Test
    void selectedBackingIdUsesTheCitizensSelection() {
        NPC npc = addNpc(9, "Selected");
        CommandSender sender = mock(CommandSender.class);
        when(selector.getSelected(sender)).thenReturn(npc);
        assertEquals(9, service.selectedBackingId(sender).getAsInt());

        CommandSender nobody = mock(CommandSender.class);
        assertTrue(service.selectedBackingId(nobody).isEmpty());
    }

    @Test
    void unavailableOnceCitizensShutsDown() {
        addNpc(1, "A");
        service.tag(1, "guide");
        citizensReady.set(false);

        assertFalse(service.isAvailable());
        assertTrue(service.findByKey("guide").isEmpty());
        assertTrue(service.listKeys().isEmpty());
        assertEquals(TagResult.UNAVAILABLE, service.tag(1, "other"));
        assertEquals(TagResult.UNAVAILABLE, service.untag("guide"));
    }

    @Test
    void isAvailableSwallowsAThrowingCitizens() {
        CitizensNpcService broken = new CitizensNpcService(
                () -> { throw new IllegalStateException("Citizens disabled"); },
                () -> selector, () -> true, new NpcInteractionTracker(), null);
        assertFalse(broken.isAvailable());
        assertTrue(broken.findByKey("guide").isEmpty());
    }

    @Test
    void saveFailureIsAWarningNotAnError() {
        addNpc(1, "A");
        doThrow(new IllegalStateException("disk full")).when(registry).saveToStore();

        assertEquals(TagResult.TAGGED, service.tag(1, "guide"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("disk full"));
    }
}
