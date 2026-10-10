package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.fourz.rvnkcore.service.npc.harness.NpcSpecParser;
import org.fourz.rvnkcore.service.npc.harness.NpcZone;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure argument parsing for {@code /rvnk npc} admin verbs (#2248), unit-testable without a server.
 *
 * @since 1.5.100-alpha
 */
public final class NpcArgs {

    private NpcArgs() {
    }

    /**
     * A parsed {@code <world> <x> <y> <z> [yaw] [pitch]}.
     *
     * @param yaw   null when not given
     * @param pitch null when not given
     */
    public record Position(String world, double x, double y, double z, Float yaw, Float pitch) {
    }

    /** Parse outcome: a value or one error message. */
    public record Parsed<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }

        static <T> Parsed<T> fail(String error) {
            return new Parsed<>(null, error);
        }
    }

    /**
     * Parses {@code <world> <x> <y> <z> [yaw] [pitch]} from {@code args[start..]}; nothing may follow.
     */
    public static Parsed<Position> position(String[] args, int start) {
        int count = args.length - start;
        if (count < 4 || count > 6) {
            return Parsed.fail("expected <world> <x> <y> <z> [yaw] [pitch]");
        }
        String world = args[start];
        double[] xyz = new double[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            Double value = number(args[start + 1 + i]);
            if (value == null) {
                return Parsed.fail(axes[i] + " must be a number: " + args[start + 1 + i]);
            }
            double limit = i == 1 ? NpcSpecParser.MAX_Y : NpcSpecParser.MAX_XZ;
            if (Math.abs(value) > limit) {
                return Parsed.fail(axes[i] + " out of range (|" + axes[i] + "| <= " + (long) limit + "): " + value);
            }
            xyz[i] = value;
        }
        Float yaw = null;
        if (count >= 5) {
            Double value = number(args[start + 4]);
            if (value == null || value < -360 || value > 360) {
                return Parsed.fail("yaw must be a number from -360 to 360: " + args[start + 4]);
            }
            yaw = NpcSpecParser.normalizeYaw(value.floatValue());
        }
        Float pitch = null;
        if (count == 6) {
            Double value = number(args[start + 5]);
            if (value == null || value < -90 || value > 90) {
                return Parsed.fail("pitch must be a number from -90 to 90: " + args[start + 5]);
            }
            pitch = value.floatValue();
        }
        return new Parsed<>(new Position(world, xyz[0], xyz[1], xyz[2], yaw, pitch), null);
    }

    /**
     * Parses {@code [radius] [height]} for {@code protect}; each may also be written
     * {@code radius=N} / {@code height=N}.
     */
    public static Parsed<NpcZone> zone(String[] args, int start) {
        Integer radius = null;
        Integer height = null;
        int positional = 0;
        for (int i = start; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            String name;
            String raw;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                name = arg.substring(0, eq);
                raw = arg.substring(eq + 1);
            } else {
                name = positional == 0 ? "radius" : positional == 1 ? "height" : null;
                raw = arg;
                positional++;
            }
            if (name == null) {
                return Parsed.fail("too many arguments: expected [radius] [height]");
            }
            Integer value = integer(raw);
            if (value == null) {
                return Parsed.fail(name + " must be a whole number: " + raw);
            }
            switch (name) {
                case "radius", "r" -> radius = value;
                case "height", "h" -> height = value;
                default -> {
                    return Parsed.fail("unknown option " + name + " (use radius= or height=)");
                }
            }
        }
        NpcZone zone = new NpcZone(radius == null ? NpcZone.DEFAULT_RADIUS : radius,
                height == null ? NpcZone.DEFAULT_HEIGHT : height);
        String range = NpcZone.validate(zone.radius(), zone.height());
        return range == null ? new Parsed<>(zone, null) : Parsed.fail(range);
    }

    /**
     * A parsed {@code click <key> <player> [right|left]} (#2255).
     *
     * @param key    the normalised RVNK key
     * @param player the target player's name as typed
     * @param click  RIGHT when not given
     */
    public record Click(String key, String player, RvnkNpcInteractEvent.ClickType click) {
    }

    /** Usage line for {@code /rvnk npc click}. */
    public static final String CLICK_USAGE = "/rvnk npc click <key> <player> [right|left]";

    /**
     * Parses {@code <key> <player> [right|left]} from {@code args[start..]}; nothing may follow.
     * The click type is case-insensitive and defaults to right.
     */
    public static Parsed<Click> click(String[] args, int start) {
        int count = args.length - start;
        if (count < 2 || count > 3) {
            return Parsed.fail("expected <key> <player> [right|left]");
        }
        String key = NpcKeys.normalize(args[start]);
        if (key == null) {
            return Parsed.fail("invalid key '" + args[start] + "': use lower-case a-z, 0-9, _ or -, 1-"
                    + NpcKeys.MAX_LENGTH + " characters");
        }
        String player = args[start + 1].trim();
        if (!VALID_PLAYER_NAME.matcher(player).matches()) {
            return Parsed.fail("invalid player name '" + args[start + 1] + "'");
        }
        RvnkNpcInteractEvent.ClickType click = RvnkNpcInteractEvent.ClickType.RIGHT;
        if (count == 3) {
            click = clickType(args[start + 2]);
            if (click == null) {
                return Parsed.fail("click must be right or left: " + args[start + 2]);
            }
        }
        return new Parsed<>(new Click(key, player, click), null);
    }

    /** @return RIGHT for {@code right}/{@code r}, LEFT for {@code left}/{@code l}, else null */
    static RvnkNpcInteractEvent.ClickType clickType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "right", "r" -> RvnkNpcInteractEvent.ClickType.RIGHT;
            case "left", "l" -> RvnkNpcInteractEvent.ClickType.LEFT;
            default -> null;
        };
    }

    /** Minecraft names: 1-16 of a-z A-Z 0-9 _ (Bedrock/Geyser prefixes such as '.' allowed). */
    private static final Pattern VALID_PLAYER_NAME = Pattern.compile("[.*]?[A-Za-z0-9_]{1,16}");

    static Double number(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer integer(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
