package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnkcore.service.npc.NpcKeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Parses and validates an NPC spec file (#2248). Pure: world and material checks are predicates,
 * so tests need no server.
 *
 * <p><b>Strict.</b> An unknown field, a bad value or an unknown world is an error, and a spec with
 * any error is not applied at all. A typo such as {@code lookClose:} would otherwise be ignored and
 * the drift it hides would never show.</p>
 *
 * <p>YAML 1.1 reads a bare {@code on}, {@code off}, {@code yes} or {@code no} as a boolean; every
 * on/off field here accepts both the boolean and the text.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcSpecParser {

    /** Longest NPC name accepted. */
    public static final int MAX_NAME = 64;
    public static final double MAX_XZ = 30_000_000;
    public static final double MAX_Y = 2048;

    static final Set<String> ROOT_FIELDS = Set.of("npcs", "description", "version");
    static final Set<String> NPC_FIELDS = Set.of("name", "world", "pos", "yaw", "pitch", "skin", "lookclose",
            "protected", "pose", "hold", "nameplate", "zone");

    private final Predicate<String> worldExists;
    private final Predicate<String> materialValid;

    /**
     * @param worldExists   true for a world name the server knows (loaded or on disk)
     * @param materialValid true for a holdable material key such as "iron_sword"
     */
    public NpcSpecParser(Predicate<String> worldExists, Predicate<String> materialValid) {
        this.worldExists = worldExists;
        this.materialValid = materialValid;
    }

    /**
     * Parse outcome.
     *
     * @param npcs   the parsed NPCs in file order; empty when there are errors
     * @param errors every error found, each prefixed with its YAML path
     */
    public record Result(List<NpcSpec> npcs, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** Parses spec text. */
    public Result parse(String yamlText) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(yamlText == null ? "" : yamlText);
        } catch (InvalidConfigurationException e) {
            return new Result(List.of(), List.of("YAML syntax: " + firstLine(e.getMessage())));
        }
        List<String> errors = new ArrayList<>();
        for (String root : yaml.getKeys(false)) {
            if (!ROOT_FIELDS.contains(root)) {
                errors.add(root + ": unknown top-level field (known: npcs, description, version)");
            }
        }
        ConfigurationSection npcs = yaml.getConfigurationSection("npcs");
        if (npcs == null || npcs.getKeys(false).isEmpty()) {
            errors.add("npcs: missing or empty - declare at least one NPC under npcs:");
            return new Result(List.of(), errors);
        }

        List<NpcSpec> specs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawKey : npcs.getKeys(false)) {
            String path = "npcs." + rawKey;
            String key = NpcKeys.normalize(rawKey);
            if (key == null || !key.equals(rawKey)) {
                errors.add(path + ": invalid key - use lower-case a-z 0-9 _ -, 1-" + NpcKeys.MAX_LENGTH + " characters");
                continue;
            }
            if (!seen.add(key)) {
                errors.add(path + ": key declared twice");
                continue;
            }
            ConfigurationSection entry = npcs.getConfigurationSection(rawKey);
            if (entry == null) {
                errors.add(path + ": must be a map of fields (name, world, pos, ...)");
                continue;
            }
            NpcSpec spec = parseEntry(key, path, entry, errors);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return errors.isEmpty() ? new Result(List.copyOf(specs), List.of()) : new Result(List.of(), List.copyOf(errors));
    }

    private NpcSpec parseEntry(String key, String path, ConfigurationSection entry, List<String> errors) {
        int before = errors.size();
        for (String field : entry.getKeys(false)) {
            if (!NPC_FIELDS.contains(field)) {
                errors.add(path + "." + field + ": unknown field (known: " + String.join(", ", sorted(NPC_FIELDS)) + ")");
            }
        }

        String name = entry.getString("name");
        if (name == null || stripColors(name).isBlank()) {
            errors.add(path + ".name: required");
        } else if (name.length() > MAX_NAME) {
            errors.add(path + ".name: longer than " + MAX_NAME + " characters");
        }

        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            errors.add(path + ".world: required");
        } else if (!worldExists.test(world)) {
            errors.add(path + ".world: unknown world '" + world + "' (not loaded and no world folder found)");
        }

        double[] pos = parsePos(path, entry.get("pos"), errors);

        Float yaw = null;
        if (entry.contains("yaw")) {
            Double value = number(path + ".yaw", entry.get("yaw"), errors);
            if (value != null) {
                if (value < -360 || value > 360) {
                    errors.add(path + ".yaw: out of range (-360..360): " + value);
                } else {
                    yaw = normalizeYaw(value.floatValue());
                }
            }
        }
        Float pitch = null;
        if (entry.contains("pitch")) {
            Double value = number(path + ".pitch", entry.get("pitch"), errors);
            if (value != null) {
                if (value < -90 || value > 90) {
                    errors.add(path + ".pitch: out of range (-90..90): " + value);
                } else {
                    pitch = value.floatValue();
                }
            }
        }

        String skin = null;
        if (entry.contains("skin")) {
            skin = entry.getString("skin");
            if (!NpcSkins.isValid(skin)) {
                errors.add(path + ".skin: must be a player name (1-16 of A-Z a-z 0-9 _) or an http(s) URL: " + skin);
            }
        }

        Boolean lookClose = entry.contains("lookclose") ? bool(path + ".lookclose", entry.get("lookclose"), errors) : null;
        Boolean protect = entry.contains("protected") ? bool(path + ".protected", entry.get("protected"), errors) : null;

        NpcPose pose = null;
        if (entry.contains("pose")) {
            pose = NpcPose.parse(String.valueOf(entry.get("pose")));
            if (pose == null) {
                errors.add(path + ".pose: must be stand, sit or sneak: " + entry.get("pose"));
            }
        }

        String hold = null;
        if (entry.contains("hold")) {
            hold = normalizeMaterial(String.valueOf(entry.get("hold")));
            if (!"none".equals(hold) && !materialValid.test(hold)) {
                errors.add(path + ".hold: unknown or non-item material '" + entry.get("hold") + "'");
            }
        }

        NpcNameplate nameplate = null;
        if (entry.contains("nameplate")) {
            nameplate = NpcNameplate.parse(entry.get("nameplate"));
            if (nameplate == null) {
                errors.add(path + ".nameplate: must be on, off or hover: " + entry.get("nameplate"));
            }
        }

        NpcZone zone = entry.contains("zone") ? parseZone(path + ".zone", entry.get("zone"), errors) : null;

        if (errors.size() > before || pos == null) {
            return null;
        }
        return new NpcSpec(key, name, world, pos[0], pos[1], pos[2], yaw, pitch, skin, lookClose, protect, pose,
                hold, nameplate, zone);
    }

    private static double[] parsePos(String path, Object raw, List<String> errors) {
        if (raw == null) {
            errors.add(path + ".pos: required, as [x, y, z]");
            return null;
        }
        if (!(raw instanceof List<?> list) || list.size() != 3) {
            errors.add(path + ".pos: must be a list of three numbers [x, y, z]");
            return null;
        }
        double[] pos = new double[3];
        String[] axes = {"x", "y", "z"};
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            Double value = number(path + ".pos." + axes[i], list.get(i), errors);
            if (value == null) {
                ok = false;
                continue;
            }
            double limit = i == 1 ? MAX_Y : MAX_XZ;
            if (Math.abs(value) > limit) {
                errors.add(path + ".pos." + axes[i] + ": out of range (|" + axes[i] + "| <= " + (long) limit + "): " + value);
                ok = false;
            }
            pos[i] = value;
        }
        return ok ? pos : null;
    }

    private static NpcZone parseZone(String path, Object raw, List<String> errors) {
        if (raw instanceof Boolean b) {
            return b ? NpcZone.defaults() : null;
        }
        Map<String, Object> map;
        if (raw instanceof ConfigurationSection section) {
            map = section.getValues(false);
        } else if (raw instanceof Map<?, ?> m) {
            map = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> map.put(String.valueOf(k), v));
        } else {
            errors.add(path + ": must be true, false or { radius: N, height: N }");
            return null;
        }
        for (String field : map.keySet()) {
            if (!field.equals("radius") && !field.equals("height")) {
                errors.add(path + "." + field + ": unknown field (known: radius, height)");
            }
        }
        Integer radius = map.containsKey("radius") ? integer(path + ".radius", map.get("radius"), errors) : NpcZone.DEFAULT_RADIUS;
        Integer height = map.containsKey("height") ? integer(path + ".height", map.get("height"), errors) : NpcZone.DEFAULT_HEIGHT;
        if (radius == null || height == null) {
            return null;
        }
        String range = NpcZone.validate(radius, height);
        if (range != null) {
            errors.add(path + ": " + range);
            return null;
        }
        return new NpcZone(radius, height);
    }

    // ── value helpers (package-visible for the command's argument parsing) ──────

    static Double number(String path, Object raw, List<String> errors) {
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isFinite(d)) {
                return d;
            }
        } else if (raw != null) {
            try {
                double d = Double.parseDouble(raw.toString().trim());
                if (Double.isFinite(d)) {
                    return d;
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        errors.add(path + ": must be a number: " + raw);
        return null;
    }

    private static Integer integer(String path, Object raw, List<String> errors) {
        if (raw instanceof Integer i) {
            return i;
        }
        if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        errors.add(path + ": must be a whole number: " + raw);
        return null;
    }

    /** Parses an on/off value, as YAML boolean or text. */
    public static Boolean parseBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return null;
        }
        return switch (raw.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> Boolean.TRUE;
            case "false", "off", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Boolean bool(String path, Object raw, List<String> errors) {
        Boolean value = parseBool(raw);
        if (value == null) {
            errors.add(path + ": must be true/false or on/off: " + raw);
        }
        return value;
    }

    /** @return a material key: lower-case, "minecraft:" stripped; "air"/"empty" become "none" */
    public static String normalizeMaterial(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return switch (key) {
            case "", "air", "empty", "nothing" -> "none";
            default -> key;
        };
    }

    /** @return yaw folded into (-180, 180] */
    public static float normalizeYaw(float yaw) {
        float y = yaw % 360f;
        if (y > 180f) {
            y -= 360f;
        } else if (y <= -180f) {
            y += 360f;
        }
        return y;
    }

    /** @return the text without &amp;x or section-sign colour codes */
    public static String stripColors(String text) {
        return text == null ? null : text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "unreadable";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        java.util.Collections.sort(list);
        return list;
    }
}
