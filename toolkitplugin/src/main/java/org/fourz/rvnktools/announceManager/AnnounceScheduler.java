package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.Debug;
import java.util.logging.Level;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnnounceScheduler {

    private static final String CLASS_NAME = "AnnounceScheduler";
    private final Debug debug;
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
        String CLASS_NAME = "AnnounceScheduler";
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.debug = new Debug(plugin, CLASS_NAME, AnnounceConfig.getLogLevel()) {};
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
            debug.log("Scheduled " + scheduledTasks.size() + " announcements.");
        }
    }

    // Schedule a single announcement
    private void scheduleAnnouncement(Announcement announcement) {

        // Check if the announcement has an expiration date and if it has past, skip it
        if (announcement.getExpiration() != null) {
            if (LocalDateTime.now().isAfter(announcement.getExpiration())) {
                debug.debug("Skipping expired announcement: " + announcement.getId());
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
            debug.debug("Handling scheduled announcement: " + announcement.getId());
            handleScheduledAnnouncement(announcement);
            return;
        }

        debug.debug("Handling periodic announcement: " + announcement.getId() + " with ticks: " + ticks);
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
                    debug.debug("Scheduled announcement " + announcement.getId() + " will run in " + delay + " ticks");
                } else {
                    // Time has already passed today, do not schedule
                    debug.debug("Skipping scheduled announcement " + announcement.getId() + " as time has passed");
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
                debug.debug("Scheduled announcement " + announcement.getId() + " task created");
            } else {
                // Schedule the announcement to run once after the calculated delay
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        broadcastAnnouncement(announcement);
                    }
                }.runTaskLater(plugin, delay);
                scheduledTasks.put(announcement, task);
                debug.debug("Scheduled announcement " + announcement.getId() + " task created");
            }
        } else {
            debug.debug("Announcement " + announcement.getId() + " is not scheduled for today");
        }
    }

    // Handle scheduling of periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long ticks) {
        // if ticks is set to RECURRENCE_UNSET (-1), use the default recurrence of 3 hours
        if (ticks == RECURRENCE_UNSET) {
            ticks = DEFAULT_RECURRENCE_TICKS;
            debug.debug("Using default recurrence for " + announcement.getId() + ": " + ticks + " ticks");
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                debug.debug("Broadcasting periodic announcement: " + announcement.getId());
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, ticks, ticks);

        scheduledTasks.put(announcement, task);
    }

    // Apply a random multiplier to the ticks value
    private long applyRandomTicks(long ticks) {
        long randomizedTicks = (long) (ticks * (RANDOM_TICK_MULTIPLIER_MIN + rand.nextDouble() * (RANDOM_TICK_MULTIPLIER_MAX - RANDOM_TICK_MULTIPLIER_MIN)));
        return randomizedTicks;
    }

    // Broadcast the announcement to all players
    public void broadcastAnnouncement(Announcement announcement) {
        this.announceManager.broadcastAnnouncement(announcement);

    }

    // Shut down the scheduler and cancel all tasks
    public void shutdown() {
        debug.log("Shutting down announcement scheduler");
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
            debug.debug("Cancelled " + scheduledTasks.size() + " scheduled tasks");
        }
    }

    // Cancel all tasks and clear the scheduled tasks map
    public void cleanup() {
        debug.debug("Cleaning up announcement scheduler");
        // Cancel all tasks and clear the map
        shutdown();
        scheduledTasks.clear();
    }

}