package org.fourz.rvnkcore.service.npc.papi;

import org.bukkit.OfflinePlayer;
import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent.ClickType;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.UnavailableNpcService;
import org.fourz.rvnkcore.service.npc.citizens.CitizensNpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code %rvnknpc_*%} values (#2213). */
class RvnkNpcPlaceholderExpansionTest {

    private final AtomicLong clock = new AtomicLong(100_000L);
    private NpcInteractionTracker tracker;
    private RvnkNpcPlaceholderExpansion expansion;
    private OfflinePlayer player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        tracker = new NpcInteractionTracker(16, clock::get);
        CitizensNpcService service = new CitizensNpcService(() -> null, () -> null, () -> false, tracker, null);
        expansion = new RvnkNpcPlaceholderExpansion("fourz", "1.5.99-alpha", service, clock::get);
        player = mock(OfflinePlayer.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    void metadata() {
        assertEquals("rvnknpc", expansion.getIdentifier());
        assertEquals("fourz", expansion.getAuthor());
        assertEquals("1.5.99-alpha", expansion.getVersion());
        assertTrue(expansion.persist());
        assertEquals(3, expansion.getPlaceholders().size());
    }

    @Test
    void valuesAfterAnInteraction() {
        tracker.record(playerId, "harbour_master", "Harbour Master", ClickType.RIGHT);
        clock.addAndGet(12_900L);

        assertEquals("harbour_master", expansion.onRequest(player, "last_key"));
        assertEquals("Harbour Master", expansion.onRequest(player, "last_name"));
        assertEquals("12", expansion.onRequest(player, "last_ago_seconds"));
        assertEquals("harbour_master", expansion.onRequest(player, "LAST_KEY"), "params are case-insensitive");
    }

    @Test
    void noneValuesWithoutAnInteraction() {
        assertEquals("", expansion.onRequest(player, "last_key"));
        assertEquals("", expansion.onRequest(player, "last_name"));
        assertEquals("-1", expansion.onRequest(player, "last_ago_seconds"));
    }

    @Test
    void nullPlayerGetsNoneValues() {
        // Citizens resolves NPC names and holograms with no player.
        assertEquals("", expansion.onRequest(null, "last_key"));
        assertEquals("-1", expansion.onRequest(null, "last_ago_seconds"));
    }

    @Test
    void unknownParamsReturnNull() {
        assertNull(expansion.onRequest(player, "nonsense"));
        assertNull(expansion.onRequest(player, null));
    }

    @Test
    void agoNeverNegativeWhenTheClockStepsBack() {
        tracker.record(playerId, "guide", "Guide", ClickType.LEFT);
        clock.addAndGet(-5_000L);
        assertEquals("0", expansion.onRequest(player, "last_ago_seconds"));
    }

    @Test
    void worksAgainstTheUnavailableService() {
        RvnkNpcPlaceholderExpansion offline = new RvnkNpcPlaceholderExpansion(
                "fourz", "1", new UnavailableNpcService(null), clock::get);
        assertEquals("", offline.onRequest(player, "last_key"));
        assertEquals("-1", offline.onRequest(player, "last_ago_seconds"));
    }
}
