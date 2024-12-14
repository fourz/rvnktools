package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnktools.RVNKTools;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnnounceScheduler {

    // constant for random tick multiplier and default values
    private static final double RANDOM_TICK_MULTIPLIER_MIN = 0.9;
    private static final double RANDOM_TICK_MULTIPLIER_MAX = 1.1;
    private static final long DEFAULT_RANDOM_RANGE = 144000 * 2;
    private static final long DEFAULT_DELAY = 72000;

    private final RVNKTools plugin;
    private final AnnounceManager announceManager;
    private Map<Announcement, BukkitTask> scheduledTasks = new ConcurrentHashMap<>();    
    private boolean usingPlaceholderAPI;
    private final Random rand = new Random();

    // constants for improved readability in conditional statements
    private static final int RECURRENCE_UNSET = -1;
    private static final int TIME_SET__BEFORE_TIME = 0;
    private static final int TIME_SET__TICKS_POSITIVE = 1;
    private static final int TIME_SET__DATE_EQUAL = 3;
    private static final int TIME_NULL__DATE_EQUAL = 4;    

    // initialize the scheduler 
    public AnnounceScheduler(RVNKTools plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        // Comment out the scheduleSaveConfig() method to prevent periodic saving
        // private void scheduleSaveConfig() {
        //     // Schedule saveConfig to run every 20 minutes (24000 ticks)
        //     // new BukkitRunnable() {
        //     //     @Override
        //     //     public void run() {
        //     //         saveConfig();
        //     //     }
        //     // }.runTaskTimer(plugin, 24000L, 24000L);
        // }
    }

    // schedules all announcements
    public void scheduleAnnouncements() {
        // Cancel existing tasks if any
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
        scheduledTasks = new ConcurrentHashMap<>();

        // Schedule each announcement
        for (Announcement announcement : announceManager.getAnnouncements()) {
            scheduleAnnouncement(announcement);
        }
        
        // Only log the final count
        if (scheduledTasks.size() > 0) {
            logInfo("Scheduled " + scheduledTasks.size() + " announcements.");
        }
    }

    // schedule a single announcement given an announcement object
    private void scheduleAnnouncement(Announcement announcement) {

        // Check if the announcement has an expiration date and if it has past, skip it
        if (announcement.getExpiration() != null) {
            if (LocalDateTime.now().isAfter(announcement.getExpiration())) {
                return;
            }
        }

        //calculate the ticks based on the recurrence
        long ticks = announcement.getRecurrence() * 20L;


        if (ticks > 0) {
            ticks = applyRandomTicks(ticks);
        }

        // if the type is 'scheduled' and the date is set, handle it as a scheduled announcement
        if ("scheduled".equalsIgnoreCase(announcement.getType()) && announcement.getDate() != null) {
            handleScheduledAnnouncement(announcement);
            return;
        }

        handlePeriodicAnnouncement(announcement, ticks);
    }

    // handle scheduling of announcements with a specific date and time
    private void handleScheduledAnnouncement(Announcement announcement) {
        LocalDateTime now = LocalDateTime.now();

        // Check if the announcement date is today
        if (now.toLocalDate().isEqual(announcement.getDate())) {
            long delay = 0L;

            // If time is specified
            if (announcement.getTime() != null) {
                if (now.toLocalTime().isBefore(announcement.getTime())) {
                    // Calculate delay until the specified time
                    delay = Duration.between(now.toLocalTime(), announcement.getTime()).toMillis() / 50L;
                } else {
                    // Time has already passed today, do not schedule
                    return;
                }
            }

            // If recurrence is set, schedule periodically on the scheduled day
            long ticks = announcement.getRecurrence() * 20L;
            if (ticks > 0) {
                ticks = applyRandomTicks(ticks);
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        broadcastAnnouncement(announcement);
                    }
                }.runTaskTimer(plugin, delay, ticks);
                scheduledTasks.put(announcement, task);
            } else {
                // Schedule the announcement to run once after the calculated delay
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        broadcastAnnouncement(announcement);
                    }
                }.runTaskLater(plugin, delay);
                scheduledTasks.put(announcement, task);
            }
        }
    }

    // handle scheduling of periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long ticks) {

        // if ticks is set to RECURRENCE_UNSET (-1), the recurrence was not set, calculate an interval that will only run once before midnight
        // using factor of 0.7, if the server starts at midnight, the announcement will run at 4:48 PM
        // using factor of 0.7, if the server starts at 5am, the announcement will run at 6:18 PM
        if (ticks == RECURRENCE_UNSET) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
            long ticks_until_midnight = Duration.between(now, midnight).toMillis() / 50L;
            ticks = (long) (ticks_until_midnight * 0.7);
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                logInfo("Broadcasting announcement: " + announcement.getId());
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, ticks, ticks);

        scheduledTasks.put(announcement, task);
        //logInfo("Scheduled announcement '" + announcement.getId() + "' with delay " + ticks + " ticks.");
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
            return RECURRENCE_UNSET;
        }
        recurrence = recurrence.toLowerCase();
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

    public void cleanup() {
        // Cancel all tasks and clear the map
        shutdown();
        scheduledTasks.clear();
    }

}