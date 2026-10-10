package org.fourz.rvnkcore.api.model.worlds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed body of {@code POST /rvnkworlds/skystacks} (#2218). Answered with HTTP 202 and a
 * {@link JobDTO} of type {@code CREATE_SKYSTACK}.
 *
 * <ul>
 *   <li>{@code UP}: {@link #count} sky layers over the group's ground, made by the same code as
 *       {@code /world skystack create <group> <count> [--template=<name>]}. The {@link #template} picks
 *       the layer generator and the config block; it takes no preset or settings.</li>
 *   <li>{@code DOWN}: {@link #count} deep layers ({@code <group>_deep_<n>}) under the ground, each made
 *       by the World Forge v2 create ({@link #generator} default {@code cavern}, {@link #preset},
 *       {@link #settings}) into the group.</li>
 *   <li>{@code BOTH}: {@code UP} then {@code DOWN}, {@link #count} each.</li>
 * </ul>
 *
 * <p>Already checked by RVNKCore: one of {@code group}/{@code bottomWorld} is set, {@code direction} is
 * up/down/both (default up), {@code count} is an integer of at least 1, {@code settings} is an object
 * when present. The count cap, the template, generator and preset names and every collision are
 * RVNKWorlds' to check.</p>
 *
 * @since 1.5.103
 */
public class CreateSkyStackRequest {

    /** Which way the stack grows from its ground. */
    public enum Direction { UP, DOWN, BOTH }

    /** The group to build on; null when only {@link #bottomWorld} is given. */
    private String group;
    /** The ground world; when set with {@link #group} it must be that group's ground. */
    private String bottomWorld;
    private Direction direction = Direction.UP;
    private int count;
    /** UP: the sky-stack template; null = {@code classic-3-tier}, as on the console. */
    private String template;
    /** DOWN: the generator of each deep layer; null with no preset = {@code cavern}. */
    private String generator;
    private String preset;
    /** DOWN: overrides on top of the preset. Never null after parsing. */
    private Map<String, Object> settings = new LinkedHashMap<>();

    public CreateSkyStackRequest() {
    }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getBottomWorld() { return bottomWorld; }
    public void setBottomWorld(String bottomWorld) { this.bottomWorld = bottomWorld; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction != null ? direction : Direction.UP; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }

    public String getPreset() { return preset; }
    public void setPreset(String preset) { this.preset = preset; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }

    /** True when {@code UP} or {@code BOTH}. */
    public boolean buildsUp() { return direction == Direction.UP || direction == Direction.BOTH; }

    /** True when {@code DOWN} or {@code BOTH}. */
    public boolean buildsDown() { return direction == Direction.DOWN || direction == Direction.BOTH; }
}
