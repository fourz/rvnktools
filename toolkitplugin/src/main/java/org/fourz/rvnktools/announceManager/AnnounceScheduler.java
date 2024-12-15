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
    private static final long DEFAULT_RECURRENCE_TICKS = 3 * 60 * 60 * 20L; // 3 hours in ticks

    private final RVNKTools plugin;
    private final AnnounceManager announceManager;
    private Map<Announcement, BukkitTask> scheduledTasks = new ConcurrentHashMap<>();    
    private boolean usingPlaceholderAPI;
    private final Random rand = new Random();

    // constants for improved readability in conditional statements
    private static final int RECURRENCE_UNSET = -1; 

    // Initialize the scheduler
    public AnnounceScheduler(RVNKTools plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    // Schedule all announcements
    public void scheduleAnnouncements() {
        // Cancel existing tasks if any
        cleanup();

        // Schedule each announcement
        for (Announcement announcement : announceManager.getAnnouncements()) {
            scheduleAnnouncement(announcement);
        }
        
        // Only log the final count
        if (scheduledTasks.size() > 0) {
            logInfo("Scheduled " + scheduledTasks.size() + " announcements.");
        }
    }

    // Schedule a single announcement
    private void scheduleAnnouncement(Announcement announcement) {

        // Check if the announcement has an expiration date and if it has past, skip it
        if (announcement.getExpiration() != null) {
            if (LocalDateTime.now().isAfter(announcement.getExpiration())) {
                return;
            }
        }

        // Safely handle null recurrence by defaulting to RECURRENCE_UNSET (-1)
        Long recurrence = announcement.getRecurrence();
        long ticks = recurrence != null ? recurrence * 20L : RECURRENCE_UNSET;

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

    // Handle scheduling of announcements with a specific date and time
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

    // Handle scheduling of periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long ticks) {
        // if ticks is set to RECURRENCE_UNSET (-1), use the default recurrence of 3 hours
        if (ticks == RECURRENCE_UNSET) {
            ticks = DEFAULT_RECURRENCE_TICKS;
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                logInfo("Broadcasting announcement: " + announcement.getId());
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, ticks, ticks);

        scheduledTasks.put(announcement, task);
    }

    // Apply a random multiplier to the ticks value
    private long applyRandomTicks(long ticks) {
        return (long) (ticks * (RANDOM_TICK_MULTIPLIER_MIN + rand.nextDouble() * (RANDOM_TICK_MULTIPLIER_MAX - RANDOM_TICK_MULTIPLIER_MIN)));
    }

    // Log an informational message
    private void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    // Broadcast the announcement to all players
    public void broadcastAnnouncement(Announcement announcement) {
        this.announceManager.broadcastAnnouncement(announcement);

    }

    // Shut down the scheduler and cancel all tasks
    public void shutdown() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
    }

    // Cancel all tasks and clear the scheduled tasks map
    public void cleanup() {
        // Cancel all tasks and clear the map
        shutdown();
        scheduledTasks.clear();
    }

}