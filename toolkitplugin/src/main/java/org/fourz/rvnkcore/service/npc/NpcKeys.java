package org.fourz.rvnkcore.service.npc;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * RVNK NPC key rules (#2213): lower-case {@code [a-z0-9_-]{1,48}}, unique per server.
 *
 * <p>Input is trimmed and lower-cased before the check, so {@code "Harbour_Master"} and
 * {@code "harbour_master"} are the same key.</p>
 *
 * @since 1.5.99-alpha
 */
public final class NpcKeys {

    /** The Citizens persistent-metadata name that holds the key in saves.yml. */
    public static final String METADATA_KEY = "rvnk-key";

    /** Longest key allowed. */
    public static final int MAX_LENGTH = 48;

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

    private NpcKeys() {
    }

    /**
     * Normalises a key: trim, lower-case, then validate.
     *
     * @param raw the key as typed or stored; null is allowed
     * @return the normalised key, or null when it is not a valid key
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return VALID.matcher(key).matches() ? key : null;
    }

    /**
     * @param raw the key as typed or stored
     * @return true when {@link #normalize(String)} accepts it
     */
    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }
}
