package org.fourz.rvnkcore.api.dungeon;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A resettable dungeon (#2367): a box in one world that can be put back to a saved state.
 *
 * <p>Plain values only, no Bukkit types, so the contract stays testable. Coordinates are block
 * coordinates; the box is inclusive on both corners.</p>
 *
 * @param id                   lower-case {@code [a-z0-9_-]{1,48}}, unique per server
 * @param world                world name as RVNKWorlds knows it (not the dimension key)
 * @param emptyCooldownMinutes minutes the box must stay empty after a clear before it resets
 *                             itself; 0 disables the empty reset
 * @since 1.5.112-alpha
 */
public record DungeonInfo(String id, String world,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          double exitX, double exitY, double exitZ,
                          int emptyCooldownMinutes) {

    /** Dungeon ids: lower case, digits, dash and underscore. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");

    public DungeonInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        int x0 = Math.min(minX, maxX), x1 = Math.max(minX, maxX);
        int y0 = Math.min(minY, maxY), y1 = Math.max(minY, maxY);
        int z0 = Math.min(minZ, maxZ), z1 = Math.max(minZ, maxZ);
        minX = x0; maxX = x1; minY = y0; maxY = y1; minZ = z0; maxZ = z1;
        emptyCooldownMinutes = Math.max(0, emptyCooldownMinutes);
    }

    /** True when the block lies inside the box of this dungeon's world (world compared ignoring case). */
    public boolean contains(String worldName, int x, int y, int z) {
        return worldName != null && world.equalsIgnoreCase(worldName)
                && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Box volume in blocks. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Lower-cases and checks an id; throws {@link IllegalArgumentException} when invalid. */
    public static String normaliseId(String raw) {
        String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid dungeon id: " + raw);
        return id;
    }
}
