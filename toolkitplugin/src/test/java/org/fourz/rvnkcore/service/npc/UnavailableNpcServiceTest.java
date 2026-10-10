package org.fourz.rvnkcore.service.npc;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.fourz.rvnkcore.api.service.INpcService;
import org.fourz.rvnkcore.api.service.INpcService.TagResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Without Citizens the bridge reports "unavailable" and nothing throws (#2213 acceptance). */
class UnavailableNpcServiceTest {

    private final INpcService service = new UnavailableNpcService("Citizens is not installed or not enabled");

    @Test
    void reportsUnavailable() {
        assertFalse(service.isAvailable());
        assertEquals("none", service.getProviderName());
        assertEquals("Citizens is not installed or not enabled", ((UnavailableNpcService) service).getReason());
    }

    @Test
    void readsReturnEmptyAndNeverThrow() {
        assertDoesNotThrow(() -> {
            assertTrue(service.findByKey("guide").isEmpty());
            assertTrue(service.findByKey(null).isEmpty());
            assertTrue(service.listKeys().isEmpty());
            assertTrue(service.keyOf(mock(Entity.class)).isEmpty());
            assertTrue(service.keyOf(null).isEmpty());
            assertTrue(service.lastInteraction(UUID.randomUUID()).isEmpty());
            assertTrue(service.lastInteraction(null).isEmpty());
            assertTrue(service.selectedBackingId(mock(CommandSender.class)).isEmpty());
        });
    }

    @Test
    void writesReturnUnavailableAndNeverThrow() {
        assertEquals(TagResult.UNAVAILABLE, service.tag(1, "guide"));
        assertEquals(TagResult.UNAVAILABLE, service.tag(1, "NOT VALID"));
        assertEquals(TagResult.UNAVAILABLE, service.untag("guide"));
        assertFalse(TagResult.UNAVAILABLE.isSuccess());
    }

    @Test
    void nullReasonGetsDefault() {
        assertEquals("no NPC plugin", new UnavailableNpcService(null).getReason());
    }

    @Test
    void selectServiceWithoutCitizensNeverCallsTheFactory() {
        INpcService selected = NpcBridge.selectService(false,
                () -> { throw new AssertionError("Citizens factory must not run when Citizens is absent"); },
                null);
        assertFalse(selected.isAvailable());
        assertInstanceOf(UnavailableNpcService.class, selected);
    }

    @Test
    void selectServiceFallsBackWhenTheAdapterCannotLink() {
        // A Citizens build whose API changed surfaces as a LinkageError at the first call.
        INpcService selected = NpcBridge.selectService(true,
                () -> { throw new NoClassDefFoundError("net/citizensnpcs/api/CitizensAPI"); },
                null);
        assertFalse(selected.isAvailable());
        assertTrue(((UnavailableNpcService) selected).getReason().contains("NoClassDefFoundError"));
    }

    @Test
    void selectServiceFallsBackWhenTheAdapterThrows() {
        INpcService selected = NpcBridge.selectService(true,
                () -> { throw new IllegalStateException("no registry"); }, null);
        assertFalse(selected.isAvailable());
    }

    @Test
    void selectServiceUsesTheAdapterWhenCitizensIsEnabled() {
        INpcService citizens = mock(INpcService.class);
        assertSame(citizens, NpcBridge.selectService(true, () -> citizens, null));
    }
}
