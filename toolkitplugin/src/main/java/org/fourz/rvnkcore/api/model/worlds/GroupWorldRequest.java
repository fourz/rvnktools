package org.fourz.rvnkcore.api.model.worlds;

/**
 * Parsed body of {@code POST /rvnkworlds/groups/{name}/worlds} (#2218). Mirrors
 * {@code /world group add-world <group> <world> [--force]}.
 *
 * <p>Already checked by RVNKCore: {@code world} is a non-blank string and {@code force} is a boolean
 * when present (absent = false). With {@code force}, a world listed in another group is moved out of
 * it first, exactly as the console flag does.</p>
 *
 * @since 1.5.103
 */
public class GroupWorldRequest {

    private String world;
    private boolean force;

    public GroupWorldRequest() {
    }

    public GroupWorldRequest(String world, boolean force) {
        this.world = world;
        this.force = force;
    }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public boolean isForce() { return force; }
    public void setForce(boolean force) { this.force = force; }
}
