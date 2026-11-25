package org.fourz.rvnkcore.api.model;

import java.util.Locale;

/**
 * One channel of the MATRIX chat room, as published by the Matrix bridge at
 * {@code GET /v1/channels} (#2380, #2381). Destinations (Matrix room id, Discord channel) are never
 * part of it; they stay on the bridge.
 *
 * @param name     {@code [a-z0-9-]{1,32}}, unique; {@code general} is today's game-chat room
 * @param label    display text, plain, at most 24 characters
 * @param readOnly true: players can join and read, but cannot speak there
 * @param join     {@code all}, {@code staff}, or {@code perm:<node>}
 */
public record MatrixChannel(String name, String label, boolean readOnly, String join) {

    /** The channel of a MATRIX line that carries no {@code mchannel}. */
    public static final String GENERAL = "general";
    public static final String JOIN_ALL = "all";
    public static final String JOIN_STAFF = "staff";
    public static final String JOIN_PERM_PREFIX = "perm:";

    /** The only channel a tier offers before its first good fetch. */
    public static final MatrixChannel DEFAULT_GENERAL =
            new MatrixChannel(GENERAL, "Game chat", false, JOIN_ALL);

    /** True for {@code join: staff}. */
    public boolean staffOnly() {
        return JOIN_STAFF.equals(join);
    }

    /** The permission node of a {@code perm:<node>} rule, or null for any other rule. */
    public String permissionNode() {
        return join != null && join.startsWith(JOIN_PERM_PREFIX)
                ? join.substring(JOIN_PERM_PREFIX.length()) : null;
    }

    /**
     * The channel name a MATRIX line belongs to: {@code general} when {@code raw} is null or blank,
     * the lower-case name when it is valid, or null when it is not a valid name.
     */
    public static String resolveName(String raw) {
        if (raw == null || raw.isBlank()) return GENERAL;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return isValidName(n) ? n : null;
    }

    /** True when {@code name} matches {@code [a-z0-9-]{1,32}}. */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 32) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return false;
        }
        return true;
    }
}
