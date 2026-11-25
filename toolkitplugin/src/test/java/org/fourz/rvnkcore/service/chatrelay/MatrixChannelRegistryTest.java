package org.fourz.rvnkcore.service.chatrelay;

import com.google.gson.Gson;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.api.model.MatrixChannel;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Matrix channel list: parse, cache, URL and the mchannel field (#2381). */
@DisplayName("Matrix channels")
class MatrixChannelRegistryTest {

    private static final String SAMPLE = """
        { "channels": [
          { "name": "general",   "label": "Game chat", "readOnly": false, "join": "all" },
          { "name": "hats",      "label": "Hats",      "readOnly": false, "join": "all" },
          { "name": "news",      "label": "News",      "readOnly": true,  "join": "all" },
          { "name": "staff",     "label": "Staff",     "readOnly": false, "join": "staff" },
          { "name": "vet-chat",  "label": "Vets",      "join": "perm:rvnkevents.chat.mchannel.vet-chat" }
        ] }""";

    @Test
    @DisplayName("A good list parses with every field")
    void parsesList() {
        List<String> warnings = new ArrayList<>();
        List<MatrixChannel> list = MatrixChannelRegistry.parse(SAMPLE, warnings::add);
        assertNotNull(list);
        assertEquals(List.of("general", "hats", "news", "staff", "vet-chat"),
                list.stream().map(MatrixChannel::name).toList());
        assertTrue(list.get(2).readOnly());
        assertTrue(list.get(3).staffOnly());
        assertEquals("rvnkevents.chat.mchannel.vet-chat", list.get(4).permissionNode());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("Bad entries are dropped; general is always present and first")
    void dropsBadEntries() {
        String body = """
            { "channels": [
              { "name": "hats", "join": "all" },
              { "name": "Bad Name!", "join": "all" },
              { "name": "hats", "join": "all" },
              { "name": "x", "join": "everyone" },
              { "name": "y", "join": "perm:op *" },
              { "name": "long", "label": "§cA very long label that goes past the cap", "join": "all" }
            ] }""";
        List<String> warnings = new ArrayList<>();
        List<MatrixChannel> list = MatrixChannelRegistry.parse(body, warnings::add);
        assertNotNull(list);
        assertEquals(List.of("general", "hats", "long"), list.stream().map(MatrixChannel::name).toList());
        assertTrue(list.get(2).label().length() <= MatrixChannelRegistry.MAX_LABEL);
        assertFalse(list.get(2).label().contains("§"));
        assertFalse(warnings.isEmpty());

        List<MatrixChannel> moved = MatrixChannelRegistry.parse(
                "{\"channels\":[{\"name\":\"hats\"},{\"name\":\"general\",\"label\":\"G\"}]}", w -> { });
        assertEquals("general", moved.get(0).name());
        assertEquals("G", moved.get(0).label());
    }

    @Test
    @DisplayName("A body that is not a channel list is refused (null)")
    void refusesNonList() {
        assertNull(MatrixChannelRegistry.parse("<html>404</html>", w -> { }));
        assertNull(MatrixChannelRegistry.parse("[]", w -> { }));
        assertNull(MatrixChannelRegistry.parse("{\"channels\":{}}", w -> { }));
        assertNull(MatrixChannelRegistry.parse(null, w -> { }));
    }

    @Test
    @DisplayName("Before any fetch only general is offered; a fetched list replaces it")
    void cacheFallback() {
        MatrixChannelRegistry reg = new MatrixChannelRegistry(mock(LogManager.class));
        assertEquals(List.of("general"), reg.names());
        assertEquals(0, reg.getFetchedAt());
        assertTrue(reg.contains(null), "a missing mchannel is general");
        assertFalse(reg.contains("hats"));

        reg.apply(MatrixChannelRegistry.parse(SAMPLE, w -> { }), 42L);
        assertTrue(reg.contains("hats"));
        assertTrue(reg.contains("HATS"));
        assertFalse(reg.contains("support"), "an unpublished channel is unknown");
        assertEquals(42L, reg.getFetchedAt());
    }

    @Test
    @DisplayName("The list URL comes from the Matrix peer url, or the override")
    void deriveUrl() {
        assertEquals("https://matrix-bridge.ravenkraft.org/v1/channels",
                MatrixChannelRegistry.deriveUrl("https://matrix-bridge.ravenkraft.org/v1/chat/inbound"));
        assertEquals("https://h/v1/channels", MatrixChannelRegistry.deriveUrl("https://h/v1/chat/inbound/"));
        assertNull(MatrixChannelRegistry.deriveUrl("https://h/other"));

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled", true);
        yml.set("server-id", "dev");
        yml.set("peers.matrix.id", "matrix");
        yml.set("peers.matrix.url", "https://matrix-bridge.ravenkraft.org/v1/chat/inbound");
        yml.set("peers.matrix.api-key", "k");
        yml.set("peers.matrix.rooms", List.of("MATRIX"));
        MatrixChannelRegistry reg = new MatrixChannelRegistry(mock(LogManager.class));
        reg.configure(ChatRelayConfig.fromConfigurationSection(yml));
        assertEquals("https://matrix-bridge.ravenkraft.org/v1/channels", reg.getUrl().toString());

        yml.set("matrix.channels-url", "https://other.example/v1/channels");
        reg.configure(ChatRelayConfig.fromConfigurationSection(yml));
        assertEquals("https://other.example/v1/channels", reg.getUrl().toString());

        yml.set("matrix.channels-url", "http://plain.example/v1/channels");
        reg.configure(ChatRelayConfig.fromConfigurationSection(yml));
        assertNull(reg.getUrl(), "the key never goes over plain http");

        yml.set("peers.matrix", null);
        reg.configure(ChatRelayConfig.fromConfigurationSection(yml));
        assertNull(reg.getUrl(), "no Matrix peer = no fetch");
    }

    @Test
    @DisplayName("mchannel: absent or blank = general, valid lower-cased, malformed = null")
    void resolveName() {
        assertEquals("general", MatrixChannel.resolveName(null));
        assertEquals("general", MatrixChannel.resolveName("  "));
        assertEquals("hats", MatrixChannel.resolveName(" Hats "));
        assertNull(MatrixChannel.resolveName("no spaces"));
        assertNull(MatrixChannel.resolveName("x".repeat(33)));
    }

    @Test
    @DisplayName("mchannel round-trips through the relay JSON; old payloads read as absent")
    void dtoRoundTrip() {
        Gson gson = new Gson();
        ChatMessageDTO dto = new ChatMessageDTO("m1", "dev", "chat", null, "derek", "hi", 1L);
        dto.setRoom("MATRIX");
        dto.setMchannel("hats");
        ChatMessageDTO back = gson.fromJson(gson.toJson(dto), ChatMessageDTO.class);
        assertEquals("hats", back.getMchannel());

        ChatMessageDTO old = gson.fromJson("{\"msgId\":\"m2\",\"room\":\"MATRIX\",\"message\":\"x\"}",
                ChatMessageDTO.class);
        assertNull(old.getMchannel());
        assertEquals("general", MatrixChannel.resolveName(old.getMchannel()));
    }
}
