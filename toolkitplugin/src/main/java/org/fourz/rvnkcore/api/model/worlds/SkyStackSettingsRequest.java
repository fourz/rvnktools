package org.fourz.rvnkcore.api.model.worlds;

/**
 * Parsed body of {@code PUT /rvnkworlds/skystacks/{group}} (#2218): a partial update of the group's
 * {@code skyStack} block in {@code worlds.yml}. A null field is left as it is.
 *
 * <p>These are the keys the config holds. Roof, floor and seal bands are frozen generation settings of
 * each world, fixed when the world is created; RVNKCore refuses those keys with a field error.</p>
 *
 * <p>Already checked by RVNKCore: every key is one of the fields below, with the right JSON type, and
 * at least one is present. {@code "deepTriggerY": null} sets {@link #resetDeepTriggerY} (back to the
 * per-world default). Ranges and the landing-mode name are RVNKWorlds' to check.</p>
 *
 * @since 1.5.103
 */
public class SkyStackSettingsRequest {

    private Boolean enabled;
    /** {@code SAFE_PLATFORM}, {@code VELOCITY_PRESERVED} or {@code SPAWN_TELEPORT}. */
    private String landingMode;
    private Integer ascendCooldownSeconds;
    private Integer descentTriggerYOffset;
    private Integer deepTriggerY;
    /** True when the body sent {@code "deepTriggerY": null}. */
    private boolean resetDeepTriggerY;
    private Integer deepAscendOffset;

    public SkyStackSettingsRequest() {
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getLandingMode() { return landingMode; }
    public void setLandingMode(String landingMode) { this.landingMode = landingMode; }

    public Integer getAscendCooldownSeconds() { return ascendCooldownSeconds; }
    public void setAscendCooldownSeconds(Integer ascendCooldownSeconds) { this.ascendCooldownSeconds = ascendCooldownSeconds; }

    public Integer getDescentTriggerYOffset() { return descentTriggerYOffset; }
    public void setDescentTriggerYOffset(Integer descentTriggerYOffset) { this.descentTriggerYOffset = descentTriggerYOffset; }

    public Integer getDeepTriggerY() { return deepTriggerY; }
    public void setDeepTriggerY(Integer deepTriggerY) { this.deepTriggerY = deepTriggerY; }

    public boolean isResetDeepTriggerY() { return resetDeepTriggerY; }
    public void setResetDeepTriggerY(boolean resetDeepTriggerY) { this.resetDeepTriggerY = resetDeepTriggerY; }

    public Integer getDeepAscendOffset() { return deepAscendOffset; }
    public void setDeepAscendOffset(Integer deepAscendOffset) { this.deepAscendOffset = deepAscendOffset; }
}
