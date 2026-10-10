package org.fourz.rvnkcore.service.region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure argument parsing and validation for {@code /rvnk region} (#2248). No Bukkit or WorldGuard
 * state: flag-name checks take a predicate, so the rules are unit-testable.
 *
 * @since 1.5.100-alpha
 */
public final class RegionArgs {

    /** WorldGuard's own id rule ({@code ProtectedRegion.VALID_ID_PATTERN}), capped at 64 characters. */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_,'\\-+/]{1,64}$");

    /** Pseudo-flag accepted by {@code define}: sets the region priority. */
    public static final String PRIORITY = "priority";

    /** Horizontal world-border limit. */
    public static final int MAX_XZ = 30_000_000;
    /** Vertical limit; generous so custom-height worlds pass. WorldGuard clamps nothing itself. */
    public static final int MAX_Y = 2048;

    /** Value of {@code /rvnk region flag ... clear}. */
    public static final String CLEAR = "clear";

    private RegionArgs() {
    }

    /**
     * Parsed {@code define} arguments.
     *
     * @param id       region id, lower-case
     * @param world    world name as typed
     * @param box      the bounds
     * @param flags    flag name (lower-case) to raw value, in input order
     * @param priority value of a {@code priority=N} token, or null
     */
    public record DefineArgs(String id, String world, Cuboid box, Map<String, String> flags, Integer priority) {
    }

    /**
     * Parsed {@code flag} arguments.
     *
     * @param value the raw value, or null for {@code clear}
     */
    public record FlagArgs(String id, String world, String flag, String value) {
    }

    /** A parse outcome: a value, or one or more errors. */
    public record Parsed<T>(T value, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }

        static <T> Parsed<T> error(String message) {
            return new Parsed<>(null, List.of(message));
        }
    }

    /**
     * @return the id lower-cased, or null when it breaks WorldGuard's id rule or names the
     *         global region
     */
    public static String normalizeId(String raw) {
        if (raw == null || !VALID_ID.matcher(raw).matches()) {
            return null;
        }
        String id = raw.toLowerCase(Locale.ROOT);
        return "__global__".equals(id) ? null : id;
    }

    /**
     * Parses {@code <name> <world> x1 y1 z1 x2 y2 z2 [flag=value ...]}.
     *
     * @param args    the arguments after {@code define}
     * @param isFlag  true for a registered flag name; flag names failing it are errors
     */
    public static Parsed<DefineArgs> parseDefine(String[] args, Predicate<String> isFlag) {
        if (args.length < 8) {
            return Parsed.error("Usage: /rvnk region define <name> <world> <x1> <y1> <z1> <x2> <y2> <z2> [flag=value ...]");
        }
        List<String> errors = new ArrayList<>();
        String id = normalizeId(args[0]);
        if (id == null) {
            errors.add("Invalid region name '" + args[0] + "'. Use letters, digits and _ , ' - + /, 1-64 characters"
                    + " (not __global__).");
        }
        String world = args[1];
        int[] c = new int[6];
        for (int i = 0; i < 6; i++) {
            String raw = args[2 + i];
            Integer value = parseInt(raw);
            if (value == null) {
                errors.add("Coordinate " + axis(i) + " must be a whole number: " + raw);
                continue;
            }
            String range = checkRange(i % 3, value);
            if (range != null) {
                errors.add(range);
            }
            c[i] = value;
        }

        Parsed<Map<String, String>> flags = parseFlagPairs(Arrays.copyOfRange(args, 8, args.length), isFlag, true);
        errors.addAll(flags.errors());
        Integer priority = null;
        Map<String, String> flagMap = flags.value() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(flags.value());
        String rawPriority = flagMap.remove(PRIORITY);
        if (rawPriority != null) {
            priority = parseInt(rawPriority);
            if (priority == null) {
                errors.add("priority must be a whole number: " + rawPriority);
            }
        }
        if (!errors.isEmpty()) {
            return new Parsed<>(null, List.copyOf(errors));
        }
        return new Parsed<>(new DefineArgs(id, world, Cuboid.of(c[0], c[1], c[2], c[3], c[4], c[5]),
                flagMap, priority), List.of());
    }

    /**
     * Parses {@code flag=value} tokens.
     *
     * @param tokens        the tokens
     * @param isFlag        flag-name predicate
     * @param allowPriority whether {@code priority=N} is accepted as a pseudo-flag
     * @return flag name (lower-case) to raw value, or the errors
     */
    public static Parsed<Map<String, String>> parseFlagPairs(String[] tokens, Predicate<String> isFlag,
                                                             boolean allowPriority) {
        List<String> errors = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq <= 0 || eq == token.length() - 1) {
                errors.add("Flag must be written flag=value: " + token);
                continue;
            }
            String name = token.substring(0, eq).toLowerCase(Locale.ROOT);
            String value = token.substring(eq + 1);
            if (flags.containsKey(name)) {
                errors.add("Flag given twice: " + name);
                continue;
            }
            if (!(allowPriority && PRIORITY.equals(name)) && !isFlag.test(name)) {
                errors.add("Unknown flag: " + name);
                continue;
            }
            flags.put(name, value);
        }
        return new Parsed<>(errors.isEmpty() ? flags : null, List.copyOf(errors));
    }

    /**
     * Parses {@code <name> <world> <flag> <value...|clear>}. The value is every remaining
     * argument joined by spaces, so greeting messages work.
     */
    public static Parsed<FlagArgs> parseFlag(String[] args, Predicate<String> isFlag) {
        if (args.length < 4) {
            return Parsed.error("Usage: /rvnk region flag <name> <world> <flag> <value|clear>");
        }
        List<String> errors = new ArrayList<>();
        String id = normalizeId(args[0]);
        if (id == null) {
            errors.add("Invalid region name '" + args[0] + "'.");
        }
        String flag = args[2].toLowerCase(Locale.ROOT);
        if (!isFlag.test(flag)) {
            errors.add("Unknown flag: " + flag);
        }
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (CLEAR.equalsIgnoreCase(value)) {
            value = null;
        }
        if (!errors.isEmpty()) {
            return new Parsed<>(null, List.copyOf(errors));
        }
        return new Parsed<>(new FlagArgs(id, args[1], flag, value), List.of());
    }

    /**
     * Parses {@code <name> <world>} for {@code remove} and {@code info}.
     *
     * @return {@code [id, world]}
     */
    public static Parsed<String[]> parseNameWorld(String[] args, String verb) {
        if (args.length != 2) {
            return Parsed.error("Usage: /rvnk region " + verb + " <name> <world>");
        }
        String id = normalizeId(args[0]);
        if (id == null) {
            return Parsed.error("Invalid region name '" + args[0] + "'.");
        }
        return new Parsed<>(new String[]{id, args[1]}, List.of());
    }

    /** @return the range error for one coordinate, or null when it is in range */
    static String checkRange(int axis, int value) {
        if (axis == 1) {
            return Math.abs(value) > MAX_Y ? "y out of range (|y| <= " + MAX_Y + "): " + value : null;
        }
        return Math.abs(value) > MAX_XZ
                ? (axis == 0 ? "x" : "z") + " out of range (|" + (axis == 0 ? "x" : "z") + "| <= " + MAX_XZ + "): " + value
                : null;
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String axis(int i) {
        return switch (i) {
            case 0 -> "x1";
            case 1 -> "y1";
            case 2 -> "z1";
            case 3 -> "x2";
            case 4 -> "y2";
            default -> "z2";
        };
    }
}
