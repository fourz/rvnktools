package org.fourz.rvnkcore.api.model.worlds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed body of {@code POST /rvnkworlds/preview} (World Forge, #2201 / #2200).
 *
 * <p><b>Geometry.</b> The preview is a square of {@link #getSize() size} blocks per side centred on
 * ({@code centerX}, {@code centerZ}), sampled every {@link #getStep() step} blocks. Sample
 * {@code (i, j)} sits at block {@code (originX + i*step, originZ + j*step)} for
 * {@code 0 <= i, j < samplesPerSide()}. Use {@link #originX()}, {@link #originZ()} and
 * {@link #samplesPerSide()} rather than re-deriving them, so core and RVNKWorlds agree.</p>
 *
 * <p><b>Already checked and normalised by RVNKCore</b> before the service sees it: {@code size},
 * {@code step}, {@code centerX}, {@code centerZ} are non-null (defaults filled), within the caps
 * below, and {@code samplesPerSide()^2 <= }{@link #MAX_SAMPLES}; at least one of
 * {@code generator}/{@code preset} is set; {@code settings} is never null. Generator/preset
 * existence and settings validity are the implementation's.</p>
 *
 * @since 1.5.96
 */
public class PreviewRequest {

    /** Hard cap on {@code width * height} samples (256 x 256). */
    public static final int MAX_SAMPLES = 65_536;
    /** Hard cap on {@code size} in blocks. */
    public static final int MAX_SIZE = 16_384;
    /** Inside the vanilla world border. */
    public static final int MAX_ABS_CENTER = 29_999_984;
    public static final int DEFAULT_SIZE = 512;
    public static final int DEFAULT_STEP = 4;

    private String generator;
    /** {@code null} means random; the response echoes the seed used. */
    private Long seed;
    private String preset;
    private Map<String, Object> settings = new LinkedHashMap<>();
    private Integer centerX;
    private Integer centerZ;
    private Integer size;
    private Integer step;

    public PreviewRequest() {
    }

    /** Number of samples along each axis: {@code ceil(size / step)}. */
    public int samplesPerSide() {
        int s = size != null ? size : DEFAULT_SIZE;
        int st = step != null ? step : DEFAULT_STEP;
        return (s + st - 1) / st;
    }

    /** Block X of sample column 0. */
    public int originX() {
        return (centerX != null ? centerX : 0) - (size != null ? size : DEFAULT_SIZE) / 2;
    }

    /** Block Z of sample row 0. */
    public int originZ() {
        return (centerZ != null ? centerZ : 0) - (size != null ? size : DEFAULT_SIZE) / 2;
    }

    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public String getPreset() { return preset; }
    public void setPreset(String preset) { this.preset = preset; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }

    public Integer getCenterX() { return centerX; }
    public void setCenterX(Integer centerX) { this.centerX = centerX; }

    public Integer getCenterZ() { return centerZ; }
    public void setCenterZ(Integer centerZ) { this.centerZ = centerZ; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public Integer getStep() { return step; }
    public void setStep(Integer step) { this.step = step; }
}
