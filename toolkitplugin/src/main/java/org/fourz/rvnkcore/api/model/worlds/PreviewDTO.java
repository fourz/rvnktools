package org.fourz.rvnkcore.api.model.worlds;

/**
 * Terrain preview grid (World Forge, #2201 / #2200), sampled from the real generator functions
 * without generating chunks.
 *
 * <p>All per-sample arrays are row-major with length {@code width * height}: index
 * {@code j * width + i} is the sample at block {@code (originX + i*step, originZ + j*step)}.
 * {@link #biomes} holds indices into {@link #biomePalette} (e.g. {@code "minecraft:ocean"}).
 * {@link #water} is {@code null} when the generator does not distinguish water columns; then a
 * client may derive water as {@code heights[k] < waterLevel}.</p>
 *
 * <p>Served with a compact (non-pretty) JSON encoder — at 256x256 a pretty-printed array is
 * several megabytes of whitespace.</p>
 *
 * @since 1.5.96
 */
public class PreviewDTO {

    private int originX;
    private int originZ;
    private int step;
    private int width;
    private int height;
    /** Surface height (top solid block Y) per sample. */
    private int[] heights;
    private int waterLevel;
    private boolean[] water;
    private String[] biomePalette;
    private int[] biomes;
    /** The seed actually sampled (echoes the request, or the random one chosen). */
    private Long seed;

    public PreviewDTO() {
    }

    public int getOriginX() { return originX; }
    public void setOriginX(int originX) { this.originX = originX; }

    public int getOriginZ() { return originZ; }
    public void setOriginZ(int originZ) { this.originZ = originZ; }

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int[] getHeights() { return heights; }
    public void setHeights(int[] heights) { this.heights = heights; }

    public int getWaterLevel() { return waterLevel; }
    public void setWaterLevel(int waterLevel) { this.waterLevel = waterLevel; }

    public boolean[] getWater() { return water; }
    public void setWater(boolean[] water) { this.water = water; }

    public String[] getBiomePalette() { return biomePalette; }
    public void setBiomePalette(String[] biomePalette) { this.biomePalette = biomePalette; }

    public int[] getBiomes() { return biomes; }
    public void setBiomes(int[] biomes) { this.biomes = biomes; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
}
