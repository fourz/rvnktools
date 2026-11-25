package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parsing of per-target access gates (#2378).
 *
 * <p>A gate turns a crossing to RVNK Dev into a refusal for a player without {@code mc-dev}. A gate
 * that silently fails to parse leaves the target open, so the absent and malformed cases matter as
 * much as the happy path.</p>
 */
@DisplayName("Transfer target gates")
class TransferGateConfigTest {

    private static TransferConfig parse(String yaml) throws Exception {
        YamlConfiguration doc = new YamlConfiguration();
        doc.loadFromString(yaml);
        return TransferConfig.fromConfigurationSection(doc.getConfigurationSection("transfer"));
    }

    @Test
    @DisplayName("A target without a gate is open")
    void noGate() throws Exception {
        TransferConfig cfg = parse("""
                transfer:
                  enabled: true
                  targets:
                    prod: {host: prod.example, port: 25565}
                """);
        assertNull(cfg.getGate("prod"));
    }

    @Test
    @DisplayName("A gate parses entitlement, bypass, sound and message lines")
    void fullGate() throws Exception {
        TransferConfig cfg = parse("""
                transfer:
                  enabled: true
                  targets:
                    dev:
                      host: dev.example
                      port: 25565
                      gate:
                        entitlement: MC-Dev
                        bypass-permission: staff.cross
                        deny-sound: minecraft:ambient.cave
                        deny-message:
                          - "&8one {player}"
                          - "&7two"
                """);
        TransferConfig.Gate gate = cfg.getGate("dev");
        assertNotNull(gate);
        assertEquals("mc-dev", gate.entitlement(), "entitlement is normalised to lower case");
        assertEquals("staff.cross", gate.bypassPermission());
        assertEquals("minecraft:ambient.cave", gate.denySound());
        assertEquals(List.of("&8one {player}", "&7two"), gate.denyMessage());
    }

    @Test
    @DisplayName("Defaults: bypass node, no sound, fallback line; a single-string message is one line")
    void defaults() throws Exception {
        TransferConfig cfg = parse("""
                transfer:
                  enabled: true
                  targets:
                    dev:
                      host: dev.example
                      port: 25565
                      gate: {entitlement: mc-dev}
                    test:
                      host: dev.example
                      port: 25565
                      gate: {entitlement: mc-dev, deny-message: "&8only line"}
                """);
        TransferConfig.Gate dev = cfg.getGate("dev");
        assertEquals(TransferConfig.DEFAULT_GATE_BYPASS, dev.bypassPermission());
        assertEquals("", dev.denySound());
        assertEquals(TransferConfig.DEFAULT_GATE_DENY, dev.denyMessage());
        assertEquals(List.of("&8only line"), cfg.getGate("test").denyMessage());
    }

    @Test
    @DisplayName("A gate without an entitlement is ignored (target stays open)")
    void noEntitlement() throws Exception {
        TransferConfig cfg = parse("""
                transfer:
                  enabled: true
                  targets:
                    dev:
                      host: dev.example
                      port: 25565
                      gate: {deny-message: "&8nope"}
                """);
        assertNull(cfg.getGate("dev"));
    }

    @Test
    @DisplayName("Gate lookup by target name is case-insensitive; unknown and null are open")
    void caseInsensitive() throws Exception {
        TransferConfig cfg = parse("""
                transfer:
                  enabled: true
                  targets:
                    Dev:
                      host: dev.example
                      port: 25565
                      gate: {entitlement: mc-dev}
                """);
        assertNotNull(cfg.getGate("dev"));
        assertNotNull(cfg.getGate("DEV"));
        assertNull(cfg.getGate("prod"));
        assertNull(cfg.getGate(null));
    }
}
