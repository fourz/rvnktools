package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import me.clip.placeholderapi.PlaceholderAPI;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatFormat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class AnnounceScheduler {

    // constant for random tick multiplier and default values
    private static final double RANDOM_TICK_MULTIPLIER_MIN = 0.9;
    private static final double RANDOM_TICK_MULTIPLIER_MAX = 1.1;
    private static final long DEFAULT_RANDOM_RANGE = 144000 * 2;
    private static final long DEFAULT_DELAY = 72000;

    private final RVNKTools plugin;
    private final AnnounceManager announceManager;
    private Map<Announcement, BukkitTask> scheduledTasks;
    private boolean usingPlaceholderAPI;
    private final Random rand = new Random();

    // initialize the scheduler with the plugin and announce manager
    public AnnounceScheduler(RVNKTools plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    // schedules all announcements
    public void scheduleAnnouncements() {
        // Cancel existing tasks if any
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
        scheduledTasks = new HashMap<>();

        // Schedule each announcement
        for (Announcement announcement : announceManager.getAnnouncements()) {
            scheduleAnnouncement(announcement);
        }
    }

    // schedule a single announcement based on its type and recurrence
    private void scheduleAnnouncement(Announcement announcement) {
        long ticks = convertRecurrenceToTicks(announcement.getRecurrence());

        logInfo("Announcement id: " + announcement.getId());
        logInfo("Ticks value: " + ticks);

        if (ticks > 0) {
            ticks = applyRandomTicks(ticks);
        }

        if ("scheduled".equalsIgnoreCase(announcement.getType()) && announcement.getDate() != null && announcement.getTime() != null) {
            handleScheduledAnnouncement(announcement, ticks);
            return;
        }

        handlePeriodicAnnouncement(announcement, ticks);
    }

    // handle scheduling of announcements with a specific date and time
    private void handleScheduledAnnouncement(Announcement announcement, long ticks) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime announcementDateTime = LocalDateTime.of(announcement.getDate(), announcement.getTime());

        // adjust the date if the announcement time has passed
        if (now.isAfter(announcementDateTime)) {
            if ("annual".equalsIgnoreCase(announcement.getRecurrence())) {
                announcementDateTime = announcementDateTime.plusYears(1);
            } else {
                return;
            }
        }

        long delay = Duration.between(now, announcementDateTime).toMillis() / 50L;

        // schedule the announcement
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement(announcement);
                if ("annual".equalsIgnoreCase(announcement.getRecurrence())) {
                    announcement.setDate(announcement.getDate().plusYears(1));
                    scheduleAnnouncement(announcement);
                }
            }
        }.runTaskLater(plugin, delay);

        scheduledTasks.put(announcement, task);
    }

    // handle scheduling of periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long ticks) {
        if (ticks == -1) {
            ticks = rand.nextLong(DEFAULT_RANDOM_RANGE) + DEFAULT_DELAY;
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                logInfo("Broadcasting announcement: " + announcement.getId());
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, ticks, ticks);

        scheduledTasks.put(announcement, task);
        logInfo("Scheduled announcement '" + announcement.getId() + "' with delay " + ticks + " ticks.");
    }

    // applies a random multiplier to the ticks value
    private long applyRandomTicks(long ticks) {
        return (long) (ticks * (RANDOM_TICK_MULTIPLIER_MIN + rand.nextDouble() * (RANDOM_TICK_MULTIPLIER_MAX - RANDOM_TICK_MULTIPLIER_MIN)));
    }

    // log an informational message
    private void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    // parse the recurrence string to ticks
    private long convertRecurrenceToTicks(String recurrence) {
        if (recurrence == null) {
            return -1;
        }
        recurrence = recurrence.toLowerCase();
        if (recurrence.equals("annual")) {
            return -1;
        }
        long ticks = 0;
        try {
            if (recurrence.endsWith("h")) {
                int hours = Integer.parseInt(recurrence.substring(0, recurrence.length() - 1));
                ticks = hours * 60L * 60L * 20L;
            } else if (recurrence.endsWith("m")) {
                int minutes = Integer.parseInt(recurrence.substring(0, recurrence.length() - 1));
                ticks = minutes * 60L * 20L;
            } else if (recurrence.endsWith("s")) {
                int seconds = Integer.parseInt(recurrence.substring(0, recurrence.length() - 1));
                ticks = seconds * 20L;
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return ticks;
    }

    // broadcast the announcement to all players
    public void broadcastAnnouncement(Announcement announcement) {
        this.announceManager.broadcastAnnouncement(announcement);

    }

    // shut down the scheduler and cancels all tasks
    public void shutdown() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
    }
}
