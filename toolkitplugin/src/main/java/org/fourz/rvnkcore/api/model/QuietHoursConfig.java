package org.fourz.rvnkcore.api.model;

import java.time.LocalTime;

/**
 * Configuration for per-plugin quiet hours.
 *
 * When quiet hours are enabled, notifications are suppressed between
 * startHour and endHour. Supports spans across midnight (e.g., 22:00 to 08:00).
 *
 * @since 1.5.0
 */
public class QuietHoursConfig {

    public static final QuietHoursConfig DISABLED = new QuietHoursConfig(-1, -1);

    private final int startHour;
    private final int endHour;

    public QuietHoursConfig(int startHour, int endHour) {
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }

    /**
     * Returns true if quiet hours are configured (not disabled).
     */
    public boolean isEnabled() {
        return startHour >= 0 && startHour <= 23 && endHour >= 0 && endHour <= 23;
    }

    /**
     * Returns true if the current time falls within quiet hours.
     * Handles midnight-spanning ranges (e.g., 22:00 to 08:00).
     */
    public boolean isInQuietHours() {
        if (!isEnabled()) return false;
        int currentHour = LocalTime.now().getHour();
        if (startHour < endHour) {
            return currentHour >= startHour && currentHour < endHour;
        } else {
            // Spans midnight (e.g., 22 to 8)
            return currentHour >= startHour || currentHour < endHour;
        }
    }

    @Override
    public String toString() {
        if (!isEnabled()) return "QuietHoursConfig{DISABLED}";
        return "QuietHoursConfig{" + startHour + ":00-" + endHour + ":00}";
    }
}
