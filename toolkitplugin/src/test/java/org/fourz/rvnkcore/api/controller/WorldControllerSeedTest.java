package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code api.worlds.exposeSeed} gates the world seed on every {@code /api/v1/worlds} route that
 * returns a {@link WorldDTO} (1.5.98). The DTO itself keeps the seed for internal callers.
 */
@DisplayName("WorldController — seed exposure switch")
class WorldControllerSeedTest {

    private static final long SEED = 123456789012345L;

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
            @Override public void write(JsonWriter out, LocalDateTime v) throws IOException {
                out.value(v != null ? v.toString() : null);
            }
            @Override public LocalDateTime read(JsonReader in) throws IOException {
                return LocalDateTime.parse(in.nextString());
            }
        })
        .create();

    private static WorldDTO world(String name) {
        WorldDTO dto = new WorldDTO();
        dto.setName(name);
        dto.setEnvironment("NORMAL");
        dto.setSeed(SEED);
        dto.setFirstLoaded(LocalDateTime.of(2026, 1, 1, 0, 0));
        return dto;
    }

    private final WorldDTO single = world("survival");
    private final List<WorldDTO> many = List.of(world("survival"), world("nether"));

    private WorldService stubService() {
        WorldService svc = mock(WorldService.class);
        when(svc.getWorld("survival")).thenReturn(CompletableFuture.completedFuture(Optional.of(single)));
        when(svc.getActiveWorlds()).thenReturn(CompletableFuture.completedFuture(many));
        when(svc.getWorldsWithPlayers()).thenReturn(CompletableFuture.completedFuture(many));
        when(svc.getWorldsByEnvironment("NORMAL")).thenReturn(CompletableFuture.completedFuture(many));
        when(svc.getRecentlyAccessedWorlds(10)).thenReturn(CompletableFuture.completedFuture(many));
        return svc;
    }

    private JsonObject get(WorldController controller, String path) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(path);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        controller.doGet(req, resp);
        verify(resp).setStatus(200);
        return JsonParser.parseString(out.toString()).getAsJsonObject();
    }

    private WorldController controller(BooleanSupplier exposeSeed) {
        return new WorldController(stubService(), mock(PlayerWorldService.class), GSON,
            mock(LogManager.class), exposeSeed);
    }

    private static final String[] LIST_ROUTES = {"/active", "/with-players", "/environment/NORMAL", "/recent"};

    @Test
    @DisplayName("false: no route carries a seed field, and the DTO keeps its seed")
    void falseStripsSeedOnEveryRoute() throws Exception {
        WorldController c = controller(() -> false);

        JsonObject one = get(c, "/survival").getAsJsonObject("data");
        assertEquals("survival", one.get("name").getAsString());
        assertFalse(one.has("seed"), "GET /{name} leaked the seed: " + one);

        for (String route : LIST_ROUTES) {
            JsonArray arr = get(c, route).getAsJsonArray("data");
            assertEquals(2, arr.size(), route);
            arr.forEach(e -> assertFalse(e.getAsJsonObject().has("seed"), route + " leaked the seed: " + e));
        }

        assertEquals(SEED, single.getSeed(), "stripping must not mutate the service's DTO");
        assertEquals(SEED, many.get(0).getSeed());
    }

    @Test
    @DisplayName("true: the seed is serialized")
    void trueKeepsSeed() throws Exception {
        WorldController c = controller(() -> true);

        assertEquals(SEED, get(c, "/survival").getAsJsonObject("data").get("seed").getAsLong());
        for (String route : LIST_ROUTES) {
            JsonArray arr = get(c, route).getAsJsonArray("data");
            arr.forEach(e -> assertEquals(SEED, e.getAsJsonObject().get("seed").getAsLong(), route));
        }
    }

    @Test
    @DisplayName("the switch is read per response, so a config reload applies it")
    void switchIsReadPerResponse() throws Exception {
        AtomicBoolean flag = new AtomicBoolean(false);
        WorldController c = controller(flag::get);
        assertFalse(get(c, "/survival").getAsJsonObject("data").has("seed"));
        flag.set(true);
        assertTrue(get(c, "/survival").getAsJsonObject("data").has("seed"));
    }

    @Test
    @DisplayName("default: legacy constructor, a throwing supplier and a missing key all hide the seed")
    void defaultIsFalse() throws Exception {
        WorldController legacy = new WorldController(stubService(), mock(PlayerWorldService.class), GSON,
            mock(LogManager.class));
        assertFalse(get(legacy, "/survival").getAsJsonObject("data").has("seed"));

        WorldController broken = controller(() -> { throw new IllegalStateException("no config"); });
        assertFalse(get(broken, "/survival").getAsJsonObject("data").has("seed"));

        // Existing servers never get the new key written: a config without it must read false.
        YamlConfiguration existing = new YamlConfiguration();
        existing.set("api.enabled", true);
        assertFalse(existing.getBoolean(WorldController.EXPOSE_SEED_KEY, false));
    }

    @Test
    @DisplayName("the shipped config.yml carries api.worlds.exposeSeed: false")
    void shippedConfigDefaultsFalse() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml not on the test classpath");
            YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            assertTrue(shipped.contains(WorldController.EXPOSE_SEED_KEY), "key missing from shipped config.yml");
            assertFalse(shipped.getBoolean(WorldController.EXPOSE_SEED_KEY, true));
        }
    }
}
