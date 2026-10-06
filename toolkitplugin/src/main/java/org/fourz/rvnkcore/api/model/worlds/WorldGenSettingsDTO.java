package org.fourz.rvnkcore.api.model.worlds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The frozen generation settings of one world (World Forge, #2198 / #2200).
 *
 * <p>Served by {@code GET /rvnkworlds/worlds/{name}/gen-settings}. Read-only: the snapshot is
 * immutable after creation. A world with no snapshot (vanilla, or not yet backfilled) is a
 * {@code NOT_FOUND} from the implementation, not an empty map.</p>
 *
 * <p>{@link #seed} is optional (#2209, 1.5.97). It is {@code null} when the implementation does not
 * know the seed, and Gson then omits the key, so the JSON is the same as a 1.5.96 response.</p>
 *
 * @since 1.5.96
 */
public class WorldGenSettingsDTO {

    private String world;
    private String generator;
    private int schemaVersion;
    private Map<String, Object> settings = new LinkedHashMap<>();
    /** World seed, or {@code null} when unknown. Boxed so Gson omits it when absent. @since 1.5.97 */
    private Long seed;

    public WorldGenSettingsDTO() {
    }

    public WorldGenSettingsDTO(String world, String generator, int schemaVersion, Map<String, Object> settings) {
        this.world = world;
        this.generator = generator;
        this.schemaVersion = schemaVersion;
        setSettings(settings);
    }

    /** @since 1.5.97 */
    public WorldGenSettingsDTO(String world, String generator, int schemaVersion, Map<String, Object> settings,
                               Long seed) {
        this(world, generator, schemaVersion, settings);
        this.seed = seed;
    }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }

    /** @return the world seed, or {@code null} when unknown. @since 1.5.97 */
    public Long getSeed() { return seed; }
    /** @since 1.5.97 */
    public void setSeed(Long seed) { this.seed = seed; }
}
