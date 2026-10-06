package org.fourz.rvnkcore.api.model.worlds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire contract of the optional {@code seed} on {@link WorldGenSettingsDTO} (#2209, 1.5.97).
 * The Gson instances match the controller: CoreServer's (pretty, no serializeNulls) and the
 * compact one. Neither calls {@code serializeNulls()}, so a null seed must vanish from the JSON.
 */
@DisplayName("WorldGenSettingsDTO — optional seed (#2209)")
class WorldGenSettingsDTOTest {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final long SEED = -4962768465676381896L;

    private static Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seaLevel", 63L);
        m.put("islandScale", 1.5);
        return m;
    }

    @Test
    @DisplayName("null seed: no seed key, JSON byte-identical to the 1.5.96 shape")
    void nullSeedIsOmittedAndUnchanged() {
        String expected1596 = "{\"world\":\"isles\",\"generator\":\"archipelago\",\"schemaVersion\":1,"
            + "\"settings\":{\"seaLevel\":63,\"islandScale\":1.5}}";
        WorldGenSettingsDTO fourArg = new WorldGenSettingsDTO("isles", "archipelago", 1, settings());
        WorldGenSettingsDTO fiveArgNull = new WorldGenSettingsDTO("isles", "archipelago", 1, settings(), null);

        assertNull(fourArg.getSeed());
        assertEquals(expected1596, COMPACT.toJson(fourArg));
        assertEquals(expected1596, COMPACT.toJson(fiveArgNull));
        assertFalse(PRETTY.toJson(fourArg).contains("seed"));
        assertEquals(PRETTY.toJson(fourArg), PRETTY.toJson(fiveArgNull));
    }

    @Test
    @DisplayName("set seed: serialized as an exact int64 and round-trips")
    void seedRoundTripsExactly() {
        WorldGenSettingsDTO dto = new WorldGenSettingsDTO("isles", "archipelago", 1, settings(), SEED);
        String json = COMPACT.toJson(dto);
        assertTrue(json.endsWith(",\"seed\":-4962768465676381896}"), json);

        WorldGenSettingsDTO back = COMPACT.fromJson(json, WorldGenSettingsDTO.class);
        assertEquals(Long.valueOf(SEED), back.getSeed());
        assertEquals(Long.valueOf(Long.MAX_VALUE),
            COMPACT.fromJson("{\"seed\":9223372036854775807}", WorldGenSettingsDTO.class).getSeed());
        assertEquals(Long.valueOf(Long.MIN_VALUE),
            COMPACT.fromJson("{\"seed\":-9223372036854775808}", WorldGenSettingsDTO.class).getSeed());
    }

    @Test
    @DisplayName("1.5.96 JSON (no seed key) parses with seed null")
    void legacyJsonParses() {
        WorldGenSettingsDTO dto = COMPACT.fromJson(
            "{\"world\":\"isles\",\"generator\":\"archipelago\",\"schemaVersion\":1,\"settings\":{}}",
            WorldGenSettingsDTO.class);
        assertEquals("isles", dto.getWorld());
        assertNull(dto.getSeed());
    }

    @Test
    @DisplayName("setter clears and sets the seed")
    void setter() {
        WorldGenSettingsDTO dto = new WorldGenSettingsDTO();
        dto.setSeed(SEED);
        assertEquals(Long.valueOf(SEED), dto.getSeed());
        dto.setSeed(null);
        assertFalse(COMPACT.toJson(dto).contains("seed"));
    }
}
