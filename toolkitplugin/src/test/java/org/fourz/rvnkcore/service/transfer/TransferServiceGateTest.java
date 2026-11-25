package org.fourz.rvnkcore.service.transfer;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The access gate on a transfer target (#2378): a player without the entitlement and without the
 * bypass node is refused with the gate's lines and is NOT transferred.
 *
 * <p>The scheduler runs tasks inline, so the async entitlement read completes inside
 * {@code transfer(...)} and each test can assert the outcome directly.</p>
 */
@DisplayName("Transfer gate")
class TransferServiceGateTest {

    private static final String BYPASS = "rvnkcore.transfer.gate.bypass";

    private Player player;
    private TransferService service;
    private final AtomicInteger lookups = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        UUID id = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Wanderer");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.hasPermission(anyString())).thenReturn(true); // portal / command node
        when(player.hasPermission(BYPASS)).thenReturn(false);

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(id)).thenReturn(player);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);

        YamlConfiguration doc = new YamlConfiguration();
        doc.loadFromString("""
                transfer:
                  enabled: true
                  cooldown-seconds: 30
                  targets:
                    dev:
                      host: dev.example
                      port: 25566
                      gate:
                        entitlement: mc-dev
                        deny-sound: minecraft:ambient.cave
                        deny-message: ["&8no page for {player}", "&7find Tolla"]
                    prod: {host: prod.example, port: 25565}
                """);
        service = new TransferService(plugin,
                TransferConfig.fromConfigurationSection(doc.getConfigurationSection("transfer")),
                mock(LogManager.class));
    }

    private void entitlements(List<String> lists) {
        service.setEntitlementLookup(id -> {
            lookups.incrementAndGet();
            return CompletableFuture.completedFuture(lists);
        });
    }

    @Test
    @DisplayName("Without mc-dev: refused with the gate lines and sound, not transferred, no cooldown used")
    void deniedWithoutEntitlement() {
        entitlements(List.of());

        TransferService.TransferResult first = service.transfer(player, "dev");
        assertEquals(TransferService.Status.PENDING, first.status());
        assertEquals("", first.message(), "callers print nothing for PENDING");
        verify(player, never()).transfer(anyString(), anyInt());
        verify(player).sendMessage(contains("no page for Wanderer"));
        verify(player).sendMessage(contains("find Tolla"));
        verify(player).playSound(any(Location.class), eq("minecraft:ambient.cave"), anyFloat(), anyFloat());

        // A refusal uses no cooldown: the next try is gated again, not told to wait.
        assertEquals(TransferService.Status.PENDING, service.transfer(player, "dev").status());
        assertEquals(2, lookups.get());
    }

    @Test
    @DisplayName("With mc-dev: transferred to the target's host and port")
    void entitledCrosses() {
        entitlements(List.of("radio", "MC-DEV"));

        assertEquals(TransferService.Status.PENDING, service.transfer(player, "dev").status());
        verify(player).transfer("dev.example", 25566);
        assertTrue(service.isTransferring(player.getUniqueId()));
    }

    @Test
    @DisplayName("Bypass node: transferred at once, entitlements never read")
    void bypassSkipsGate() {
        entitlements(List.of());
        when(player.hasPermission(BYPASS)).thenReturn(true);

        TransferService.TransferResult result = service.transfer(player, "dev");
        assertEquals(TransferService.Status.SUCCESS, result.status());
        verify(player).transfer("dev.example", 25566);
        assertEquals(0, lookups.get());
    }

    @Test
    @DisplayName("Failed entitlement read: refused (fail closed)")
    void lookupFailureRefuses() {
        service.setEntitlementLookup(id -> CompletableFuture.failedFuture(new IllegalStateException("db down")));

        service.transfer(player, "dev");
        verify(player, never()).transfer(anyString(), anyInt());
        verify(player).sendMessage(contains("find Tolla"));
    }

    @Test
    @DisplayName("An open target is not gated")
    void openTargetUnaffected() {
        entitlements(List.of());

        assertEquals(TransferService.Status.SUCCESS, service.transfer(player, "prod").status());
        verify(player).transfer("prod.example", 25565);
        assertEquals(0, lookups.get());
    }
}
