package org.fourz.rvnkcore.api.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/** 429 from the Matrix bridge: sender told, warning throttled, no retry (#2381 follow-up). */
@DisplayName("Chat relay 429")
class ChatRelayRateLimitTest {

    @Test
    @DisplayName("ThrottledWarning reports the first hit, then one line per window with the count")
    void throttle() {
        AtomicLong now = new AtomicLong(1_000);
        ThrottledWarning w = new ThrottledWarning(60_000, now::get);
        assertEquals(1, w.hit());
        assertNull(w.hit());
        assertNull(w.hit());
        now.addAndGet(59_999);
        assertNull(w.hit());
        now.addAndGet(1);
        assertEquals(4, w.hit(), "this hit + the 3 suppressed");
        assertNull(w.hit());
    }

    private static ChatRelayConfig config() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled", true);
        yml.set("server-id", "event");
        yml.set("peers.matrix.id", "matrix");
        yml.set("peers.matrix.url", "https://matrix-bridge.ravenkraft.org/v1/chat/inbound");
        yml.set("peers.matrix.api-key", "k");
        yml.set("peers.matrix.rooms", List.of("MATRIX"));
        yml.set("peers.prod.id", "prod");
        yml.set("peers.prod.url", "https://prod.example/api/v1/chat/inbound");
        yml.set("peers.prod.api-key", "k2");
        return ChatRelayConfig.fromConfigurationSection(yml);
    }

    @Test
    @DisplayName("A 429 from the Matrix peer tells the sender every time and logs once a minute")
    void matrix429() {
        LogManager log = mock(LogManager.class);
        ChatRelayConfig cfg = config();
        ChatRelayEgress egress = new ChatRelayEgress(cfg, log);
        List<ChatMessageDTO> told = new ArrayList<>();
        egress.setRateLimitedHandler(told::add);
        ChatRelayConfig.Peer matrix = cfg.getPeers().stream().filter(p -> p.id().equals("matrix")).findFirst().orElseThrow();

        ChatMessageDTO a = new ChatMessageDTO("a", "event", "matrix", "00000000-0000-0000-0000-000000000001", "x", "hi", 1);
        ChatMessageDTO b = new ChatMessageDTO("b", "event", "matrix", "00000000-0000-0000-0000-000000000001", "x", "hi", 2);
        egress.onFailureStatus(matrix, a, 429, "ChatRelay -> matrix");
        egress.onFailureStatus(matrix, b, 429, "ChatRelay -> matrix");

        assertEquals(List.of(a, b), told, "each refused line tells its sender");
        verify(log, times(1)).warning(contains("429 rate limited (1 line(s)"));
    }

    @Test
    @DisplayName("Other statuses, and a 429 from a tier peer, keep the plain warning and tell nobody")
    void otherStatuses() {
        LogManager log = mock(LogManager.class);
        ChatRelayConfig cfg = config();
        ChatRelayEgress egress = new ChatRelayEgress(cfg, log);
        List<ChatMessageDTO> told = new ArrayList<>();
        egress.setRateLimitedHandler(told::add);
        ChatRelayConfig.Peer matrix = cfg.getPeers().stream().filter(p -> p.id().equals("matrix")).findFirst().orElseThrow();
        ChatRelayConfig.Peer prod = cfg.getPeers().stream().filter(p -> p.id().equals("prod")).findFirst().orElseThrow();
        ChatMessageDTO dto = new ChatMessageDTO("c", "event", "matrix", null, "x", "hi", 3);

        egress.onFailureStatus(matrix, dto, 403, "ChatRelay -> matrix");
        egress.onFailureStatus(prod, dto, 429, "ChatRelay -> prod");

        assertTrue(told.isEmpty());
        verify(log).warning("ChatRelay -> matrix -> 403");
        verify(log).warning("ChatRelay -> prod -> 429");
    }
}
