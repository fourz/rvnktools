package org.fourz.rvnkcore.service.portal;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detects a nether-style portal frame around an anchor block and fills / clears its interior.
 *
 * <p>Ports the interior fill from RVNKWorlds' {@code NetherPortalFactory} (set {@code NETHER_PORTAL}
 * with the correct {@link Orientable} axis) and adds a planar flood-fill frame detector so a player
 * can build the frame themselves out of the configured trigger material (default {@code DIAMOND_BLOCK})
 * and light it with a registration sign (#1709).</p>
 *
 * <h3>Detection</h3>
 * <p>A portal frame is planar and vertical: it lies either in the X-Y plane (interior blocks share a
 * fixed Z, portal blocks {@code axis=X}) or the Z-Y plane (fixed X, portal blocks {@code axis=Z}).
 * Starting from an {@code AIR} block adjacent to the anchor, a 4-connected flood fill within a single
 * plane collects the contiguous air region. The region is a valid interior only when it is fully
 * enclosed by the trigger material (every boundary neighbour is trigger), stays within the size
 * limits, and meets the minimum {@value #MIN_WIDTH}x{@value #MIN_HEIGHT} interior. Both plane
 * orientations are tried; the first that validates wins.</p>
 *
 * <p>Pure helper — no logging, no state. All world reads/writes happen on the caller's thread
 * (the sign-change / block-break handlers, which run on the main thread).</p>
 *
 * @since 1.5.26
 */
public class PortalFrameBuilder {

    /** Minimum interior width in blocks (a 1-wide column is allowed). */
    private static final int MIN_WIDTH = 1;
    /** Minimum interior height in blocks. */
    private static final int MIN_HEIGHT = 2;
    /** Maximum interior extent in either dimension. */
    private static final int MAX_DIM = 21;
    /**
     * Hard flood-fill cap. Above this the region is treated as open (not enclosed) and rejected,
     * bounding the work done on the main thread. Sized just over the {@value #MAX_DIM}^2 maximum.
     */
    private static final int FLOOD_CAP = MAX_DIM * MAX_DIM + 64;

    /**
     * Result of a successful frame detection: the interior air-block coordinates and the axis the
     * {@code NETHER_PORTAL} blocks should carry.
     *
     * @param interior interior block coordinates as {@code int[]{x, y, z}}
     * @param axis     the {@link Axis} for the portal blocks (X for an X-Y frame, Z for a Z-Y frame)
     */
    public record Frame(List<int[]> interior, Axis axis) {
    }

    /**
     * Detects a valid trigger-material frame enclosing an air interior around {@code anchor}.
     *
     * @param anchor  a frame block the registration sign is mounted on
     * @param trigger the frame material (e.g. {@code DIAMOND_BLOCK})
     * @return the detected {@link Frame}, or empty when no enclosed interior is found
     */
    public Optional<Frame> detect(Block anchor, Material trigger) {
        if (trigger == null) {
            return Optional.empty();
        }
        World world = anchor.getWorld();
        // X-Y plane: interior shares the anchor's Z, portal blocks use axis X.
        Frame xy = tryPlane(world, anchor, trigger, Axis.X);
        if (xy != null) {
            return Optional.of(xy);
        }
        // Z-Y plane: interior shares the anchor's X, portal blocks use axis Z.
        Frame zy = tryPlane(world, anchor, trigger, Axis.Z);
        if (zy != null) {
            return Optional.of(zy);
        }
        return Optional.empty();
    }

    /**
     * Fills the interior blocks with {@code NETHER_PORTAL} oriented on the given axis.
     *
     * @param world    the world
     * @param interior interior block coordinates ({@code int[]{x, y, z}})
     * @param axis     the portal-block axis
     */
    public void fill(World world, List<int[]> interior, Axis axis) {
        for (int[] c : interior) {
            Block b = world.getBlockAt(c[0], c[1], c[2]);
            b.setType(Material.NETHER_PORTAL, false);
            BlockData data = b.getBlockData();
            if (data instanceof Orientable orientable) {
                orientable.setAxis(axis);
                b.setBlockData(orientable, false);
            }
        }
    }

    /**
     * Clears interior blocks that are still {@code NETHER_PORTAL} back to {@code AIR}.
     *
     * @param world    the world
     * @param interior interior block coordinates ({@code int[]{x, y, z}})
     */
    public void clear(World world, List<int[]> interior) {
        for (int[] c : interior) {
            Block b = world.getBlockAt(c[0], c[1], c[2]);
            if (b.getType() == Material.NETHER_PORTAL) {
                b.setType(Material.AIR, false);
            }
        }
    }

    /**
     * Attempts to detect a frame in one plane orientation.
     *
     * @param world   the world
     * @param anchor  the frame anchor block
     * @param trigger the frame material
     * @param axis    {@link Axis#X} for the X-Y plane (fixed Z) or {@link Axis#Z} for the Z-Y plane (fixed X)
     * @return a valid {@link Frame}, or null when this orientation has no enclosed interior
     */
    private Frame tryPlane(World world, Block anchor, Material trigger, Axis axis) {
        boolean fixedZ = (axis == Axis.X);
        int fixed = fixedZ ? anchor.getZ() : anchor.getX();

        // Candidate seeds: the anchor's in-plane air neighbours (the interior sits beside the frame).
        for (int[] seed : planarNeighbors(anchor, fixedZ)) {
            Block seedBlock = world.getBlockAt(seed[0], seed[1], seed[2]);
            if (seedBlock.getType() != Material.AIR) {
                continue;
            }
            List<int[]> interior = floodFill(world, seed, fixed, fixedZ, trigger);
            if (interior != null && withinSize(interior)) {
                return new Frame(interior, axis);
            }
        }
        return null;
    }

    /**
     * The four in-plane neighbours of the anchor (the two horizontal + two vertical directions in
     * the chosen plane).
     */
    private int[][] planarNeighbors(Block anchor, boolean fixedZ) {
        int x = anchor.getX();
        int y = anchor.getY();
        int z = anchor.getZ();
        if (fixedZ) {
            return new int[][]{{x + 1, y, z}, {x - 1, y, z}, {x, y + 1, z}, {x, y - 1, z}};
        }
        return new int[][]{{x, y, z + 1}, {x, y, z - 1}, {x, y + 1, z}, {x, y - 1, z}};
    }

    /**
     * 4-connected planar flood fill of the contiguous air region containing {@code seed}.
     *
     * <p>Returns null (reject) when the region escapes past {@link #FLOOD_CAP} (open frame) or when
     * any boundary neighbour is neither air nor the trigger material (frame not made purely of the
     * trigger block). Otherwise returns the enclosed interior air coordinates.</p>
     *
     * @param world   the world
     * @param seed    the seed air block {@code int[]{x, y, z}}
     * @param fixed   the fixed coordinate value (Z when {@code fixedZ}, else X)
     * @param fixedZ  true for an X-Y plane (fixed Z), false for a Z-Y plane (fixed X)
     * @param trigger the frame material
     * @return the interior air coordinates, or null when the region is not a valid enclosed interior
     */
    private List<int[]> floodFill(World world, int[] seed, int fixed, boolean fixedZ, Material trigger) {
        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        List<int[]> interior = new ArrayList<>();

        queue.add(seed);
        visited.add(packKey(seed[0], seed[1], seed[2]));

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            interior.add(cur);
            if (interior.size() > FLOOD_CAP) {
                return null; // Open region — escaped the frame.
            }

            for (int[] n : planarNeighbors4(cur, fixedZ)) {
                // Never leave the plane (defensive; neighbours are already in-plane).
                if (fixedZ ? n[2] != fixed : n[0] != fixed) {
                    continue;
                }
                long key = packKey(n[0], n[1], n[2]);
                Block nb = world.getBlockAt(n[0], n[1], n[2]);
                Material type = nb.getType();
                if (type == Material.AIR) {
                    if (visited.add(key)) {
                        queue.add(n);
                    }
                } else if (type != trigger) {
                    // Boundary is not the frame material — this air region is not a valid interior.
                    return null;
                }
                // type == trigger: a valid frame boundary; stop expanding here.
            }
        }
        return interior;
    }

    /** The four in-plane neighbours of a coordinate in the chosen plane. */
    private int[][] planarNeighbors4(int[] c, boolean fixedZ) {
        if (fixedZ) {
            return new int[][]{{c[0] + 1, c[1], c[2]}, {c[0] - 1, c[1], c[2]},
                    {c[0], c[1] + 1, c[2]}, {c[0], c[1] - 1, c[2]}};
        }
        return new int[][]{{c[0], c[1], c[2] + 1}, {c[0], c[1], c[2] - 1},
                {c[0], c[1] + 1, c[2]}, {c[0], c[1] - 1, c[2]}};
    }

    /**
     * Validates the interior against the minimum and maximum size limits.
     *
     * @param interior the interior air coordinates
     * @return true when the bounding box is within limits and meets the minimum dimensions
     */
    private boolean withinSize(List<int[]> interior) {
        if (interior.size() < MIN_WIDTH * MIN_HEIGHT) {
            return false;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : interior) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
        }
        int width = Math.max(maxX - minX, maxZ - minZ) + 1; // one axis is flat (fixed)
        int height = (maxY - minY) + 1;
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            return false;
        }
        return width <= MAX_DIM && height <= MAX_DIM;
    }

    /**
     * Packs a block coordinate into a single long for visited-set membership. Uses 21-bit signed
     * fields, matching Bukkit's own block-key packing range.
     */
    private long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
