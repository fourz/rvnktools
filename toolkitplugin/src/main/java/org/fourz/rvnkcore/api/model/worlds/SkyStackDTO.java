package org.fourz.rvnkcore.api.model.worlds;

import java.util.ArrayList;
import java.util.List;

/**
 * A group's sky stack (#2218): its layers and its {@code skyStack} config block from {@code worlds.yml}.
 *
 * <p>Served by {@code GET /rvnkworlds/skystacks[/{group}]} and returned by {@code PUT} and
 * {@code DELETE /rvnkworlds/skystacks/{group}}. The config keys are the ones {@code worlds.yml} holds;
 * roof, floor and seal bands are not here because they are frozen generation settings of each world
 * (see {@link SkyStackLayerDTO#getBands()}).</p>
 *
 * @since 1.5.103
 */
public class SkyStackDTO {

    private String group;
    /** {@code skyStack.enabled}; false also after a {@code DELETE} (the block is gone). */
    private boolean enabled;
    /** True when the group has a {@code skyStack} block at all. */
    private boolean configured;
    private String ground;
    /** Top to bottom: sky layers, the ground, then the deep layers ({@code WorldGroup.getStack()}). */
    private List<String> stack = new ArrayList<>();
    private List<SkyStackLayerDTO> layers = new ArrayList<>();
    private String landingMode;
    private int ascendCooldownSeconds;
    private int descentTriggerYOffset;
    /** Null when the per-world default ({@code minHeight + 5}) applies. */
    private Integer deepTriggerY;
    private int deepAscendOffset;
    /** A plain sentence for the operator, e.g. what a DELETE kept; null (omitted) otherwise. */
    private String note;

    public SkyStackDTO() {
    }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }

    public String getGround() { return ground; }
    public void setGround(String ground) { this.ground = ground; }

    public List<String> getStack() { return stack; }
    public void setStack(List<String> stack) { this.stack = stack != null ? new ArrayList<>(stack) : new ArrayList<>(); }

    public List<SkyStackLayerDTO> getLayers() { return layers; }
    public void setLayers(List<SkyStackLayerDTO> layers) {
        this.layers = layers != null ? new ArrayList<>(layers) : new ArrayList<>();
    }

    public String getLandingMode() { return landingMode; }
    public void setLandingMode(String landingMode) { this.landingMode = landingMode; }

    public int getAscendCooldownSeconds() { return ascendCooldownSeconds; }
    public void setAscendCooldownSeconds(int ascendCooldownSeconds) { this.ascendCooldownSeconds = ascendCooldownSeconds; }

    public int getDescentTriggerYOffset() { return descentTriggerYOffset; }
    public void setDescentTriggerYOffset(int descentTriggerYOffset) { this.descentTriggerYOffset = descentTriggerYOffset; }

    public Integer getDeepTriggerY() { return deepTriggerY; }
    public void setDeepTriggerY(Integer deepTriggerY) { this.deepTriggerY = deepTriggerY; }

    public int getDeepAscendOffset() { return deepAscendOffset; }
    public void setDeepAscendOffset(int deepAscendOffset) { this.deepAscendOffset = deepAscendOffset; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
