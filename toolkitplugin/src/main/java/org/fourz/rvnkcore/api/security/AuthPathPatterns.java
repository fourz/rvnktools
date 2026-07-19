package org.fourz.rvnkcore.api.security;

import java.util.List;

/**
 * The URL patterns that receive a blanket {@link AuthFilter} mapping at server startup.
 *
 * <p>Two independent code paths attach {@code AuthFilter}: {@code ServletFactory} registers
 * this blanket list once, and {@code ServletRegistrationServiceImpl} adds a per-path filter
 * for every dynamically registered path declaring {@code requireAuth}. Their coverage
 * intersects — any dynamic path under {@code /v1/} is filtered twice (#1551).</p>
 *
 * <p>#1547 made that duplication harmless at runtime via a request-scoped guard in
 * {@code AuthFilter}, but the overlap itself was invisible: nothing reported that a path was
 * already covered. It cost real debugging time when the rate limiter appeared to enforce half
 * its configured value on the three affected paths. Centralising the patterns here lets the
 * registration service detect the overlap and say so.</p>
 *
 * <p><strong>The blanket mapping is the safe default and must stay.</strong> Removing it in
 * favour of per-path registration alone would leave any path that is never explicitly
 * registered <em>silently unauthenticated</em> — trading a logging problem for a security
 * hole.</p>
 */
public final class AuthPathPatterns {

    /** Patterns blanket-mapped to the shared AuthFilter by {@code ServletFactory}. */
    public static final List<String> BLANKET_PATTERNS = List.of(
            "/v1/*",
            "/bartershops/*",
            "/lore/*",
            "/rvnkworlds/*",
            "/docs/*"
    );

    private AuthPathPatterns() {
        // constants holder
    }

    /**
     * Whether a servlet path spec already receives auth from a blanket mapping.
     *
     * @param pathSpec a servlet path spec, e.g. {@code /v1/events/*}
     * @return the covering blanket pattern, or {@code null} if none covers it
     */
    public static String coveringPattern(String pathSpec) {
        if (pathSpec == null || pathSpec.isEmpty()) {
            return null;
        }
        for (String pattern : BLANKET_PATTERNS) {
            // Only prefix patterns ("/v1/*") can cover a nested path; an exact-match mapping
            // covers nothing but itself, which the servlet container already dedupes.
            if (!pattern.endsWith("/*")) {
                continue;
            }
            String prefix = pattern.substring(0, pattern.length() - 1); // "/v1/*" -> "/v1/"
            if (pathSpec.startsWith(prefix)) {
                return pattern;
            }
        }
        return null;
    }
}
