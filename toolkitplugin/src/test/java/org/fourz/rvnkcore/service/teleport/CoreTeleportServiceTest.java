package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link CoreTeleportService} completes with the result of {@code Player.teleport}, and only
 * after the teleport ran on the main thread (#2279).
 */
class CoreTeleportServiceTest {

    private Plugin plugin;
    /** Stands in for the main thread: tasks wait here until the test runs them. */
    private final Deque<Runnable> mainThreadQueue = new ArrayDeque<>();
    private CoreTeleportService service;
    private Player player;
    private Player target;
    private final Location targetLoc = new Location(null, 10, 64, 10);

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCore");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CoreTeleportServiceTest"));
        service = new CoreTeleportService(plugin, mainThreadQueue::add);

        player = mock(Player.class);
        target = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(target.isOnline()).thenReturn(true);
        when(target.getLocation()).thenReturn(targetLoc);
    }

    private void runMainThread() {
        while (!mainThreadQueue.isEmpty()) {
            mainThreadQueue.poll().run();
        }
    }

    @Test
    @DisplayName("a refused or cancelled teleport completes false")
    void teleportFalseCompletesFalse() {
        when(player.teleport(any(Location.class))).thenReturn(false);

        CompletableFuture<Boolean> result = service.teleportToPlayer(player, target);
        runMainThread();

        assertTrue(result.isDone());
        assertFalse(result.join());
        verify(player).teleport(targetLoc);
    }

    @Test
    @DisplayName("a landed teleport completes true")
    void teleportTrueCompletesTrue() {
        when(player.teleport(any(Location.class))).thenReturn(true);

        CompletableFuture<Boolean> result = service.teleportToPlayer(player, target);
        runMainThread();

        assertTrue(result.join());
    }

    @Test
    @DisplayName("the future completes after the teleport call, not when it is scheduled")
    void completesAfterTeleportNotBefore() {
        when(player.teleport(any(Location.class))).thenReturn(true);

        CompletableFuture<Boolean> result = service.teleportToPlayer(player, target);

        // Scheduled but not yet run: the 1.5.103 code had already completed true here
        assertFalse(result.isDone(), "must not complete before the teleport ran");
        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).isOnline();

        runMainThread();
        verify(player).teleport(targetLoc);
        assertTrue(result.isDone());
        assertTrue(result.join());
    }

    @Test
    @DisplayName("an offline player completes false without a teleport")
    void offlinePlayerCompletesFalse() {
        when(target.isOnline()).thenReturn(false);

        CompletableFuture<Boolean> result = service.teleportToPlayer(player, target);
        runMainThread();

        assertFalse(result.join());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    @DisplayName("an exception in the teleport completes false, not exceptionally")
    void exceptionCompletesFalse() {
        when(player.teleport(any(Location.class))).thenThrow(new IllegalStateException("boom"));

        CompletableFuture<Boolean> result = service.teleportToPlayer(player, target);
        runMainThread();

        assertFalse(result.join());
    }

    @Test
    @DisplayName("a scheduler that refuses the task completes false")
    void schedulerRefusalCompletesFalse() {
        CoreTeleportService refusing = new CoreTeleportService(plugin, task -> {
            throw new IllegalStateException("plugin disabled");
        });

        CompletableFuture<Boolean> result = refusing.teleportToPlayer(player, target);

        assertTrue(result.isDone());
        assertFalse(result.join());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    @DisplayName("teleportToCoordinates propagates the teleport result")
    void coordinatesPropagateResult() {
        when(player.teleport(any(Location.class))).thenReturn(false);

        CompletableFuture<Boolean> result = service.teleportToCoordinates(player, 1, 2, 3);
        assertFalse(result.isDone());
        runMainThread();

        assertFalse(result.join());
        verify(player).teleport(any(Location.class));
    }

    @Test
    @DisplayName("teleportPlayerToPlayer propagates the result and enforces the permission")
    void playerToPlayerPropagatesAndChecksPermission() {
        Player commander = mock(Player.class);
        when(commander.hasPermission(ITeleportService.PERM_TP_OTHERS)).thenReturn(false);

        CompletableFuture<Boolean> denied = service.teleportPlayerToPlayer(commander, player, target);
        runMainThread();
        assertFalse(denied.join());
        verify(player, never()).teleport(any(Location.class));

        CommandSender console = mock(CommandSender.class);
        when(player.teleport(any(Location.class))).thenReturn(true);
        CompletableFuture<Boolean> allowed = service.teleportPlayerToPlayer(console, player, target);
        assertFalse(allowed.isDone());
        runMainThread();
        assertTrue(allowed.join());
    }
}
