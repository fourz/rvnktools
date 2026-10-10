package org.fourz.rvnkcore.service.npc.harness;

import java.util.Locale;

/**
 * Name-plate visibility (#2248), stored by Citizens as the {@code nameplate-visible} metadata with
 * the string values "true", "false" or "hover".
 *
 * @since 1.5.100-alpha
 */
public enum NpcNameplate {
    ON("true"), OFF("false"), HOVER("hover");

    private final String citizensValue;

    NpcNameplate(String citizensValue) {
        this.citizensValue = citizensValue;
    }

    /** @return the value Citizens stores */
    public String citizensValue() {
        return citizensValue;
    }

    /** @return the spec spelling: on, off or hover */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a spec or command value. YAML 1.1 reads a bare {@code on}/{@code off} as a boolean, so
     * Boolean input is accepted as well as text.
     *
     * @return the mode, or null when the value is not one
     */
    public static NpcNameplate parse(Object raw) {
        if (raw instanceof Boolean b) {
            return b ? ON : OFF;
        }
        if (raw == null) {
            return null;
        }
        return switch (raw.toString().trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "show" -> ON;
            case "off", "false", "no", "hide" -> OFF;
            case "hover" -> HOVER;
            default -> null;
        };
    }

    /** @return the mode for a stored Citizens value; ON when unset, as Citizens defaults */
    public static NpcNameplate fromCitizens(Object stored) {
        NpcNameplate parsed = parse(stored);
        return parsed == null ? ON : parsed;
    }
}
