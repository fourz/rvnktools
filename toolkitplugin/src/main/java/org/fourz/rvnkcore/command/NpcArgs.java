package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.service.npc.harness.NpcSpecParser;
import org.fourz.rvnkcore.service.npc.harness.NpcZone;

import java.util.Locale;

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
