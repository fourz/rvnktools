package org.fourz.rvnkcore.service.region;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Cuboid#volume()} saturates instead of overflowing (#2280). */
class CuboidTest {

    @Test
    @DisplayName("volume counts blocks inclusively on both corners")
    void smallVolume() {
        assertEquals(1L, Cuboid.of(5, 5, 5, 5, 5, 5).volume());
        assertEquals(2L * 3L * 4L, Cuboid.of(1, 1, 1, 0, 3, -2).volume());
    }

    @Test
    @DisplayName("the largest RegionArgs box saturates at Long.MAX_VALUE instead of going negative")
    void largestRegionArgsBoxSaturates() {
        Cuboid box = Cuboid.of(-30_000_000, -2048, -30_000_000, 30_000_000, 2048, 30_000_000);
        assertEquals(Long.MAX_VALUE, box.volume());
    }

    @Test
    @DisplayName("an int-overflowing span is computed as a long")
    void fullIntSpan() {
        Cuboid box = Cuboid.of(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0);
        assertEquals(1L << 32, box.volume());
    }
}
