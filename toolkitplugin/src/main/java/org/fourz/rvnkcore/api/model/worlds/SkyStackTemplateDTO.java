package org.fourz.rvnkcore.api.model.worlds;

/**
 * A sky-stack template (#2218): the built-ins ({@code classic-3-tier}, {@code skyland-1}) and any in
 * {@code worlds.yml} under {@code worlds.skyStackTemplates}. Served by
 * {@code GET /rvnkworlds/skystack-templates}; a create names one in {@code template}.
 *
 * @since 1.5.103
 */
public class SkyStackTemplateDTO {

    private String name;
    /** The generator of each sky layer: {@code skyblock}, {@code skylands} or {@code void}. */
    private String layerGenerator;
    private int defaultLayerCount;
    private String layerNamePattern;
    private String landingMode;
    private int ascendCooldownSeconds;
    private int descentTriggerYOffset;

    public SkyStackTemplateDTO() {
    }

    public SkyStackTemplateDTO(String name, String layerGenerator, int defaultLayerCount, String layerNamePattern,
                               String landingMode, int ascendCooldownSeconds, int descentTriggerYOffset) {
        this.name = name;
        this.layerGenerator = layerGenerator;
        this.defaultLayerCount = defaultLayerCount;
        this.layerNamePattern = layerNamePattern;
        this.landingMode = landingMode;
        this.ascendCooldownSeconds = ascendCooldownSeconds;
        this.descentTriggerYOffset = descentTriggerYOffset;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLayerGenerator() { return layerGenerator; }
    public void setLayerGenerator(String layerGenerator) { this.layerGenerator = layerGenerator; }

    public int getDefaultLayerCount() { return defaultLayerCount; }
    public void setDefaultLayerCount(int defaultLayerCount) { this.defaultLayerCount = defaultLayerCount; }

    public String getLayerNamePattern() { return layerNamePattern; }
    public void setLayerNamePattern(String layerNamePattern) { this.layerNamePattern = layerNamePattern; }

    public String getLandingMode() { return landingMode; }
    public void setLandingMode(String landingMode) { this.landingMode = landingMode; }

    public int getAscendCooldownSeconds() { return ascendCooldownSeconds; }
    public void setAscendCooldownSeconds(int ascendCooldownSeconds) { this.ascendCooldownSeconds = ascendCooldownSeconds; }

    public int getDescentTriggerYOffset() { return descentTriggerYOffset; }
    public void setDescentTriggerYOffset(int descentTriggerYOffset) { this.descentTriggerYOffset = descentTriggerYOffset; }
}
