package org.fourz.rvnkcore.service.chatrelay;

import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Matrix bridge inbound rules, peer room filter and the scoped token (#2376, #2377). */
@DisplayName("Matrix inbound")
class MatrixInboundTest {

    private static ChatMessageDTO line(String room, String sender, String message) {
        ChatMessageDTO dto = new ChatMessageDTO("m1", "matrix", "chat", null, sender, message, 1L);
        dto.setRoom(room);
        return dto;
    }

    @Test
    @DisplayName("Only room MATRIX is accepted; BOT, GLOBAL and none are refused")
    void roomMustBeMatrix() {
        assertNull(MatrixInbound.refusal(line("MATRIX", "derek", "hi")));
        assertNull(MatrixInbound.refusal(line("matrix", "derek", "hi")));
        assertNotNull(MatrixInbound.refusal(line("BOT", "derek", "hi")));
        assertNotNull(MatrixInbound.refusal(line("GLOBAL", "derek", "hi")));
        assertNotNull(MatrixInbound.refusal(line(null, "derek", "hi")));
        assertNotNull(MatrixInbound.refusal(line("MATRIX", "derek", " §a ")), "empty after cleaning");
    }

    @Test
    @DisplayName("Sanitise: components dropped, section codes and controls removed, caps applied")
    void sanitize() {
        ChatMessageDTO dto = line("matrix", "derek (wizard) [Matrix]‮", "§khi\n&cthere\u0000 <click:run_command:/op x>");
        dto.setComponents("[{\"text\":\"x\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op x\"}}]");
        dto.setWorld("world");
        MatrixInbound.sanitize(dto);
        assertEquals("MATRIX", dto.getRoom());
        assertNull(dto.getComponents());
        assertNull(dto.getWorld());
        assertEquals("chat", dto.getChannel());
        assertEquals("derek (wizard) [Matrix]", dto.getSenderName());
        // & codes stay as text here: RVNKEvents renders MATRIX lines without colour translation.
        assertEquals("hi &cthere <click:run_command:/op x>", dto.getMessage());

        ChatMessageDTO longOne = line("MATRIX", "x".repeat(100), "y".repeat(400));
        MatrixInbound.sanitize(longOne);
        assertEquals(MatrixInbound.MAX_SENDER, longOne.getSenderName().length());
        assertEquals(MatrixInbound.MAX_MESSAGE, longOne.getMessage().length());

        ChatMessageDTO noName = line("MATRIX", null, "hi");
        MatrixInbound.sanitize(noName);
        assertEquals("Matrix", noName.getSenderName());
    }

    @Test
    @DisplayName("A peer with rooms gets only those rooms; no rooms = everything; never back to the origin")
    void peerRoomFilter() throws Exception {
        YamlConfiguration doc = new YamlConfiguration();
        doc.loadFromString("""
                chat-relay:
                  enabled: true
                  server-id: event
                  peers:
                    prod: {id: prod, url: "https://p/api/v1/chat/inbound", api-key: k}
                    bridge: {id: matrix, url: "https://b/v1/chat/inbound", api-key: k, rooms: [matrix]}
                """);
        ChatRelayConfig cfg = ChatRelayConfig.fromConfigurationSection(doc.getConfigurationSection("chat-relay"));
        ChatRelayConfig.Peer prod = cfg.getPeers().get(0);
        ChatRelayConfig.Peer bridge = cfg.getPeers().get(1);
        assertEquals(List.of("MATRIX"), bridge.rooms());

        assertTrue(prod.accepts("GLOBAL"));
        assertTrue(prod.accepts("MATRIX"));
        assertFalse(bridge.accepts("GLOBAL"));
        assertFalse(bridge.accepts(null), "a line with no room is GLOBAL");
        assertTrue(bridge.accepts("matrix"));

        ChatMessageDTO fromBridge = line("MATRIX", "derek", "hi");
        assertFalse(org.fourz.rvnkcore.api.chat.ChatRelayEgressAccess.shouldSend(bridge, fromBridge),
                "never echoed back to the origin peer");
        assertTrue(org.fourz.rvnkcore.api.chat.ChatRelayEgressAccess.shouldSend(prod, fromBridge));
        assertTrue(cfg.isMatrixOrigin("Matrix"));
        assertFalse(cfg.isMatrixOrigin("prod"));
    }

    @Test
    @DisplayName("A Matrix peer without a rooms list fails validation (it would get all chat)")
    void bridgeWithoutRoomsInvalid() throws Exception {
        YamlConfiguration doc = new YamlConfiguration();
        doc.loadFromString("""
                chat-relay:
                  enabled: true
                  server-id: event
                  peers:
                    bridge: {id: matrix, url: "https://b/v1/chat/inbound", api-key: k}
                """);
        ChatRelayConfig cfg = ChatRelayConfig.fromConfigurationSection(doc.getConfigurationSection("chat-relay"));
        assertFalse(cfg.validate(org.mockito.Mockito.mock(org.fourz.rvnkcore.util.log.LogManager.class)));
    }

    @Test
    @DisplayName("Scoped token: only the hashed token matches; config holds hashes only")
    void scopedToken() throws Exception {
        String token = "bridge-token-for-test";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        YamlConfiguration doc = new YamlConfiguration();
        doc.loadFromString("chat-relay:\n  matrix:\n    inbound-token-sha256: [\"" + hash + "\", \"not-hex\"]\n");
        ChatRelayConfig cfg = ChatRelayConfig.fromConfigurationSection(doc.getConfigurationSection("chat-relay"));
        assertEquals(1, ChatInboundToken.configure(cfg.getMatrixInboundTokenHashes()));
        assertTrue(ChatInboundToken.matches(token));
        assertFalse(ChatInboundToken.matches("wrong"));
        assertFalse(ChatInboundToken.matches(hash), "the hash itself is not a token");
        assertFalse(ChatInboundToken.matches(null));
        ChatInboundToken.configure(List.of());
        assertFalse(ChatInboundToken.matches(token), "no hashes = closed");
    }
}
