package org.fourz.rvnkcore.service.npc.harness;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Skin source rules (#2248): a Minecraft player name, or an http(s) URL to a skin PNG.
 *
 * @since 1.5.100-alpha
 */
public final class NpcSkins {

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private NpcSkins() {
    }

    public static boolean isUrl(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.trim().toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://") || lower.startsWith("http://")) && lower.length() > 10
                && !lower.contains(" ");
    }

    public static boolean isPlayerName(String source) {
        return source != null && PLAYER_NAME.matcher(source.trim()).matches();
    }

    /** @return true for a player name or a URL */
    public static boolean isValid(String source) {
        return isUrl(source) || isPlayerName(source);
    }

    /** @return true when two sources name the same skin: names ignore case, URLs must match exactly */
    public static boolean same(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        String x = a.trim();
        String y = b.trim();
        return isUrl(x) || isUrl(y) ? x.equals(y) : x.equalsIgnoreCase(y);
    }
}
