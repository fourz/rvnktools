package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.service.teleport.BackLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * {@code /tpaccept} writes {@code /back} and announces success only when the teleport
 * service completes {@code true}, and only once it completes (#2127, #2279).
 */
class TpAcceptCommandTest {

    private final UUID teleportingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private Player teleporting;
    private Player destination;
    private BackLocationService backService;
    private ITeleportService teleportService;
    private CompletableFuture<Boolean> teleportResult;
    private final Location from = new Location(null, 1, 70, 1);

    @BeforeEach
    void setUp() {
        teleporting = mock(Player.class);
        destination = mock(Player.class);
        when(teleporting.getUniqueId()).thenReturn(teleportingId);
        when(teleporting.getName()).thenReturn("Alice");
        when(destination.getName()).thenReturn("Bob");
        backService = mock(BackLocationService.class);
        teleportService = mock(ITeleportService.class);
        teleportResult = new CompletableFuture<>();
        when(teleportService.teleportToPlayer(teleporting, destination)).thenReturn(teleportResult);
    }

    @Test
    @DisplayName("a failed teleport records nothing and tells both players it failed")
    void failedTeleportRecordsNothing() {
        TpAcceptCommand.teleportAndRecord(teleportService, teleporting, destination, from, backService);
        teleportResult.complete(false);

        verifyNoInteractions(backService);
        verify(teleporting).sendMessage(contains("failed"));
        verify(destination).sendMessage(contains("could not be teleported"));
        // No success line (marked with a check) reaches either player
        verify(teleporting, never()).sendMessage(contains("✓"));
        verify(destination, never()).sendMessage(contains("✓"));
    }

    @Test
    @DisplayName("a landed teleport records the from location and announces success")
    void landedTeleportRecordsFrom() {
        TpAcceptCommand.teleportAndRecord(teleportService, teleporting, destination, from, backService);
        teleportResult.complete(true);

        verify(backService).setBackLocation(teleportingId, from);
        verify(teleporting).sendMessage(contains("Teleported to"));
        verify(destination).sendMessage(contains("teleported to you"));
    }

    @Test
    @DisplayName("nothing is recorded or announced before the teleport completes")
    void nothingBeforeCompletion() {
        TpAcceptCommand.teleportAndRecord(teleportService, teleporting, destination, from, backService);

        verifyNoInteractions(backService);
        verify(teleporting, never()).sendMessage(any(String.class));
        verify(destination, never()).sendMessage(any(String.class));

        teleportResult.complete(true);
        verify(backService).setBackLocation(teleportingId, from);
    }

    @Test
    @DisplayName("an exceptional completion counts as a failure and leaves /back alone")
    void exceptionalCompletionIsFailure() {
        TpAcceptCommand.teleportAndRecord(teleportService, teleporting, destination, from, backService);
        teleportResult.completeExceptionally(new IllegalStateException("boom"));

        verifyNoInteractions(backService);
        verify(teleporting).sendMessage(contains("failed"));
        verify(destination).sendMessage(contains("could not be teleported"));
    }
}
