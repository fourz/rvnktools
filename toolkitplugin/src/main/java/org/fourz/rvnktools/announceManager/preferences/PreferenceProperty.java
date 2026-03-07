package org.fourz.rvnktools.announceManager.preferences;

import java.util.HashMap;
import java.util.Map;

public enum PreferenceProperty {
    LOCATION("location", "chat"),
    SOUND("sound", "none");

    private final String key;
    private final String defaultValue;
    private static final Map<String, PreferenceProperty> BY_KEY = new HashMap<>();

    static {
        for (PreferenceProperty prop : values()) {
            BY_KEY.put(prop.key, prop);
        }
    }

    PreferenceProperty(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public static PreferenceProperty fromKey(String key) {
        if (key == null) return null;
        return BY_KEY.get(key.toLowerCase());
    }

    public static boolean isValidKey(String key) {
        return key != null && BY_KEY.containsKey(key.toLowerCase());
    }

    public boolean isValidValue(String value) {
        if (value == null) return false;
        
        switch (this) {
            case LOCATION:
                return value.matches("(?i)^(chat|title|action-bar|none)$");
            case SOUND:
                if (value.equalsIgnoreCase("none")) return true;
                try {
                    org.bukkit.Sound.valueOf(value.toUpperCase());
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            default:
                return true;
        }
    }
}
