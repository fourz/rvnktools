package org.fourz.rvnkcore.api.model.worlds;

/**
 * One rung of a sky stack (#2218): a sky layer, the ground, or a deep layer.
 *
 * <p>{@link #bands} is the world's seal-band line ({@code "floor band Y -64..-61"},
 * {@code "no seal bands"}), read from its frozen generation settings. It is null when the world is not
 * loaded: the bands are read from the loaded generator, as {@code /world skystack info} does.</p>
 *
 * @since 1.5.103
 */
public class SkyStackLayerDTO {

    /** Role of a world in the stack. */
    public enum Role { SKY, GROUND, DEEP }

    private String world;
    private Role role;
    /** The {@code _sky_<n>} / {@code _deep_<n>} index; -1 for the ground. */
    private int index;
    private boolean loaded;
    private String bands;

    public SkyStackLayerDTO() {
    }

    public SkyStackLayerDTO(String world, Role role, int index, boolean loaded, String bands) {
        this.world = world;
        this.role = role;
        this.index = index;
        this.loaded = loaded;
        this.bands = bands;
    }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    public String getBands() { return bands; }
    public void setBands(String bands) { this.bands = bands; }
}
