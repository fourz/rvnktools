package org.fourz.rvnktools.announceManager.preferences;

public enum PreferenceProperty {
    LOCATION("announcement_location", "chat"),
    SOUND("announcement_sound", "");

    private final String key;
    private final String defaultValue;

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
        for (PreferenceProperty prop : values()) {
            if (prop.key.equals(key)) {
                return prop;
            }
        }
        return null;
    }
}
