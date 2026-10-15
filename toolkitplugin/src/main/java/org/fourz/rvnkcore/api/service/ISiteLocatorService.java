package org.fourz.rvnkcore.api.service;

import java.util.List;

/**
 * Named sites of a world, computed by the plugin that generated it (#2316). A site is a place a quest or a tool
 * can point at without knowing its coordinates: they differ per seed, so they are looked up, never hard-coded.
 *
 * <p>RVNKWorlds provides this for its generators. A drowned_world world has, for example:</p>
 * <ul>
 *   <li>{@code drowned:spawn} - the spawn outpost on the atoll</li>
 *   <li>{@code drowned:trail:1} .. {@code drowned:trail:3} - the glyphs that lead to Atlantis</li>
 *   <li>{@code drowned:atlantis:gate}, {@code drowned:atlantis} - Atlantis's gate glyph and its temple</li>
 *   <li>{@code drowned:trail:4} .. {@code drowned:trail:6} - the glyphs that lead on to Dryland</li>
 *   <li>{@code drowned:dryland} - Dryland's lake shore</li>
 * </ul>
 *
 * <p>Thread-safe: callers may ask from any thread (the REST route does). Consumers resolve it with
 * {@code RVNKCore.getServiceSafe(ISiteLocatorService.class)} and treat a null service as "no sites".</p>
 *
 * @since 1.5.106
 */
public interface ISiteLocatorService {

    /**
     * One site: its key, its world, a block position to go to, its kind (for example {@code glyph}, {@code atlantis},
     * {@code dryland}) and a short label for players.
     */
    record Site(String key, String world, int x, int y, int z, String kind, String label) {
    }

    /** The site with this key in this world, or null when the world has none (or is not loaded). */
    Site locate(String world, String key);

    /** Every site of a world whose key starts with {@code prefix} ("" for all), in key order; empty when none. */
    List<Site> list(String world, String prefix);
}
