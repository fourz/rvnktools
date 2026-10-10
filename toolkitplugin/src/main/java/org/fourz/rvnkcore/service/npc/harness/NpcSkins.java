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

    /** Marker in a skin check line that is not a failure: Citizens still fetches the texture. */
    public static final String NOT_YET = "not yet loaded";

    /**
     * The immediate result line of a player-name skin request (1.5.101).
     *
     * @param spawned whether the NPC is spawned now; Citizens fetches a skin only for a spawned NPC
     */
    public static String requestMessage(String source, boolean spawned) {
        return "skin set to player '" + source + "'; Citizens fetches it "
                + (spawned ? "in the background" : "when the NPC spawns");
    }

    /**
     * The line of the delayed check of a player-name skin (1.5.101). Only a hard error is FAILED:
     * a texture that has not arrived yet is normal for a despawned NPC (Citizens fetches on spawn)
     * and while Mojang is slow or rate-limiting (Citizens retries).
     *
     * @param key           RVNK key
     * @param source        the player name
     * @param spawned       whether the NPC is spawned at check time
     * @param textureLoaded whether Citizens has a texture
     * @param hardError     an error Citizens or the check reported, or null
     */
    public static String checkMessage(String key, String source, boolean spawned, boolean textureLoaded,
                                      String hardError) {
        String prefix = "skin " + key + ": ";
        if (textureLoaded) {
            return prefix + "texture for '" + source + "' loaded";
        }
        if (hardError != null) {
            return prefix + "FAILED - " + hardError;
        }
        if (!spawned) {
            return prefix + "skin set to '" + source + "'; Citizens fetches it when the NPC spawns";
        }
        return prefix + "texture for '" + source + "' " + NOT_YET + " after 5 s (Citizens retries). If it never "
                + "loads, check the player name exists; Mojang may also be rate-limiting.";
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
