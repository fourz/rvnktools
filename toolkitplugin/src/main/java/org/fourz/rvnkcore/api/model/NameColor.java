package org.fourz.rvnkcore.api.model;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses and checks a player name color (#2468): {@code #RRGGBB}, {@code RRGGBB}, a color name, or a reset word.
 *
 * <p>Pure logic: no Bukkit calls, so the rules can be tested without a server. Every accepted color is returned as
 * lowercase {@code #rrggbb}. A color darker than {@link #MIN_BRIGHTNESS} is refused, because chat draws on a dark
 * background and a near-black name cannot be read.</p>
 */
public final class NameColor {

    /** Least perceived brightness (0-255, 0.299 R + 0.587 G + 0.114 B) a name color may have. */
    public static final int MIN_BRIGHTNESS = 50;

    /** Permission to set your own name color. */
    public static final String PERMISSION = "rvnk.chat.namecolor";
    /** Permission to set another player's name color. */
    public static final String PERMISSION_OTHERS = "rvnk.chat.namecolor.others";

    private static final Pattern HEX = Pattern.compile("#?[0-9a-f]{6}");
    private static final Pattern HEX_LINE = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Color names players may type. Black and dark_blue are left out: both fail the brightness floor. */
    public static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("dark_green", "#00aa00"),
            Map.entry("dark_aqua", "#00aaaa"),
            Map.entry("dark_red", "#aa0000"),
            Map.entry("dark_purple", "#aa00aa"),
            Map.entry("purple", "#aa00aa"),
            Map.entry("gold", "#ffaa00"),
            Map.entry("orange", "#ffaa00"),
            Map.entry("gray", "#aaaaaa"),
            Map.entry("grey", "#aaaaaa"),
            Map.entry("dark_gray", "#555555"),
            Map.entry("dark_grey", "#555555"),
            Map.entry("blue", "#5555ff"),
            Map.entry("green", "#55ff55"),
            Map.entry("aqua", "#55ffff"),
            Map.entry("red", "#ff5555"),
            Map.entry("light_purple", "#ff55ff"),
            Map.entry("pink", "#ff55ff"),
            Map.entry("yellow", "#ffff55"),
            Map.entry("white", "#ffffff"));

    private NameColor() {}

    /**
     * The outcome of {@link #parse}: a color ({@code hex}), a reset, or an error message for the player.
     */
    public record Result(String hex, boolean reset, String error) {
        public boolean ok() { return error == null; }

        static Result color(String hex) { return new Result(hex, false, null); }
        static Result cleared() { return new Result(null, true, null); }
        static Result fail(String error) { return new Result(null, false, error); }
    }

    /** Parses what a player typed. Never returns null. */
    public static Result parse(String raw) {
        if (raw == null || raw.isBlank()) return Result.fail("give a color: #RRGGBB, a name like gold, or reset");
        String s = raw.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "reset", "default", "none", "clear", "off" -> { return Result.cleared(); }
            default -> { }
        }
        String hex;
        if (HEX.matcher(s).matches()) {
            hex = s.startsWith("#") ? s : "#" + s;
        } else {
            hex = NAMED.get(s.replace(' ', '_').replace('-', '_'));
            if (hex == null) return Result.fail("unknown color '" + raw.trim() + "': use #RRGGBB or a name like gold");
        }
        int b = brightness(hex);
        if (b < MIN_BRIGHTNESS) {
            return Result.fail(hex + " is too dark to read in chat (brightness " + b + ", needs " + MIN_BRIGHTNESS + ")");
        }
        return Result.color(hex);
    }

    /** Perceived brightness of {@code #rrggbb}, 0-255. */
    public static int brightness(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, bl = rgb & 0xff;
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * bl);
    }

    /** True when a whole chat line is only a hex code, e.g. {@code #ff8800} (the chat shortcut). */
    public static boolean isHexLine(String message) {
        return message != null && HEX_LINE.matcher(message.trim()).matches();
    }

    /**
     * A stored value checked again: lowercase {@code #rrggbb}, or null when it is missing or no longer valid (for
     * example after the brightness floor went up).
     */
    public static String normalizeStored(String stored) {
        if (stored == null || stored.isBlank()) return null;
        Result r = parse(stored);
        return r.ok() && !r.reset() ? r.hex() : null;
    }
}
