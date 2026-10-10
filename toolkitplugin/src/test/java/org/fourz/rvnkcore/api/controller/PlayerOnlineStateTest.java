package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.model.response.PlayerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /v1/players reported online=false for a connected player (#2096). The row's online flag now reads
 * the main-thread live snapshot first and falls back to a direct lookup.
 */
@DisplayName("Player online state for /v1/players")
class PlayerOnlineStateTest {

    private static PlayerResponse row(UUID uuid) {
        return PlayerResponse.builder().uuid(uuid).name("p").online(true).build();
    }

    @Test
    @DisplayName("A connected player listed in the live snapshot is online, whatever the direct lookup says")
    void connectedPlayerInSnapshotIsOnline() {
        UUID connected = UUID.randomUUID();
        assertTrue(PlayerController.isOnline(connected, List.of(row(UUID.randomUUID()), row(connected)), id -> false));
    }

    @Test
    @DisplayName("Falls back to the direct lookup when the snapshot misses or is absent")
    void fallsBackToDirectLookup() {
        UUID player = UUID.randomUUID();
        assertTrue(PlayerController.isOnline(player, List.of(), id -> true));
        assertTrue(PlayerController.isOnline(player, null, id -> true));
    }

    @Test
    @DisplayName("Offline when neither source has the player, or the lookup throws")
    void offlineWhenNeitherSourceHasThePlayer() {
        UUID player = UUID.randomUUID();
        assertFalse(PlayerController.isOnline(player, List.of(row(UUID.randomUUID())), id -> false));
        assertFalse(PlayerController.isOnline(player, null, id -> { throw new IllegalStateException("off-thread"); }));
        assertFalse(PlayerController.isOnline(null, List.of(row(UUID.randomUUID())), id -> true));
    }
}
