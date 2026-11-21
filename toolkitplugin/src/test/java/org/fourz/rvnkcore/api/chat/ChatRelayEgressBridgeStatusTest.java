package org.fourz.rvnkcore.api.chat;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.service.chatrelay.MatrixBridgeOutcome;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** #2443: egress reports the Matrix bridge's HTTP status, so a bot post can tell a dropped line. */
@DisplayName("Chat relay egress - Matrix bridge status (#2443)")
class ChatRelayEgressBridgeStatusTest {

    private HttpServer bridge;
    private final AtomicInteger answer = new AtomicInteger(200);
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        bridge = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        bridge.createContext("/v1/chat/inbound", ex -> {
            ex.getRequestBody().readAllBytes();
            hits.incrementAndGet();
            ex.sendResponseHeaders(answer.get(), -1);
            ex.close();
        });
        bridge.start();
    }

    @AfterEach
    void stop() {
        bridge.stop(0);
    }

    private ChatRelayConfig config(boolean withBridge) {
        return config(withBridge, bridge.getAddress().getPort());
    }

    private ChatRelayConfig config(boolean withBridge, int port) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled", true);
        yml.set("server-id", "dev");
        yml.set("timeout-ms", 2000);
        if (withBridge) {
            yml.set("peers.matrix.id", "matrix");
            yml.set("peers.matrix.url", "http://127.0.0.1:" + port + "/v1/chat/inbound");
            yml.set("peers.matrix.api-key", "k");
            yml.set("peers.matrix.rooms", List.of("MATRIX"));
        }
        return ChatRelayConfig.fromConfigurationSection(yml);
    }

    private static ChatMessageDTO botLine(String id) {
        ChatMessageDTO dto = new ChatMessageDTO(id, "dev", "bot", null, "Trace", "hi", 1);
        dto.setRoom("MATRIX");
        return dto;
    }

    @Test
    @DisplayName("A 429 from the bridge completes the future with 429")
    void rateLimited() throws Exception {
        answer.set(429);
        ChatRelayEgress egress = new ChatRelayEgress(config(true), mock(LogManager.class));
        List<ChatMessageDTO> told = new ArrayList<>();
        egress.setRateLimitedHandler(told::add);

        Integer status = egress.send(botLine("a")).get(5, TimeUnit.SECONDS);

        assertEquals(429, status);
        assertEquals(MatrixBridgeOutcome.RATE_LIMITED, MatrixBridgeOutcome.of(status));
        assertEquals(1, hits.get());
        assertEquals(1, told.size(), "the existing 429 notice path still runs");
    }

    @Test
    @DisplayName("A 2xx from the bridge completes the future with that status")
    void delivered() throws Exception {
        answer.set(200);
        ChatRelayEgress egress = new ChatRelayEgress(config(true), mock(LogManager.class));
        assertEquals(200, egress.send(botLine("b")).get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("No Matrix peer: the future is already complete with NOT_SENT")
    void noBridge() {
        ChatRelayEgress egress = new ChatRelayEgress(config(false), mock(LogManager.class));
        var f = egress.send(botLine("c"));
        assertTrue(f.isDone());
        assertEquals(MatrixBridgeOutcome.NOT_SENT, f.getNow(null));
        assertEquals(0, hits.get());
    }

    @Test
    @DisplayName("A line the Matrix peer filters out (GLOBAL) does not wait on the bridge")
    void filteredRoom() {
        ChatRelayEgress egress = new ChatRelayEgress(config(true), mock(LogManager.class));
        ChatMessageDTO global = botLine("d");
        global.setRoom("GLOBAL");
        assertEquals(MatrixBridgeOutcome.NOT_SENT, egress.send(global).getNow(null));
    }

    @Test
    @DisplayName("An unreachable bridge completes the future with FAILED")
    void unreachable() throws Exception {
        int closedPort;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = s.getLocalPort();
        }
        ChatRelayEgress egress = new ChatRelayEgress(config(true, closedPort), mock(LogManager.class));
        assertEquals(MatrixBridgeOutcome.FAILED, egress.send(botLine("e")).get(5, TimeUnit.SECONDS));
    }
}
