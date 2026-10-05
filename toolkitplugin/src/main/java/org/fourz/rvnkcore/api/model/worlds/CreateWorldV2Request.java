package org.fourz.rvnkcore.api.model.worlds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed body of a v2 {@code POST /rvnkworlds/worlds} (World Forge, #2200).
 *
 * <p>The controller routes a create to v2 when the JSON body carries a non-null
 * {@code generator}, {@code preset} or {@code settings}. Any other body goes to the legacy
 * {@code createWorld(String)} untouched, so the old {@code {name, environment, seed,
 * templateName, groupName, autoLoad}} shape keeps working.</p>
 *
 * <p>Already checked by RVNKCore: {@code name} is non-blank, {@code seed} is an integer if
 * present, {@code settings} is a JSON object if present, {@code autoLoad} is a boolean if
 * present, and at least one of {@code generator}/{@code preset} is set. Everything about the
 * generator, preset and settings contents (existence, schema, ranges) is the implementation's.
 * The legacy {@code environment} and {@code templateName} keys are ignored on this path.</p>
 *
 * <p>Effective settings are: the preset's settings (if any), overlaid by {@link #settings}.
 * If both {@code generator} and {@code preset} are given they must agree.</p>
 *
 * @since 1.5.96
 */
public class CreateWorldV2Request {

    private String name;
    /** {@code null} means "pick a random seed"; the job result / gen-settings report the one used. */
    private Long seed;
    private String generator;
    private String preset;
    /** Overrides on top of the preset. Never null after parsing (empty map when absent). */
    private Map<String, Object> settings = new LinkedHashMap<>();
    private String groupName;
    private boolean autoLoad;

    public CreateWorldV2Request() {
    }

    public CreateWorldV2Request(String name, Long seed, String generator, String preset,
                                Map<String, Object> settings, String groupName, boolean autoLoad) {
        this.name = name;
        this.seed = seed;
        this.generator = generator;
        this.preset = preset;
        setSettings(settings);
        this.groupName = groupName;
        this.autoLoad = autoLoad;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }

    public String getPreset() { return preset; }
    public void setPreset(String preset) { this.preset = preset; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isAutoLoad() { return autoLoad; }
    public void setAutoLoad(boolean autoLoad) { this.autoLoad = autoLoad; }
}
