package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.Debug;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AnnounceScheduler {

    private static final String CLASS_NAME = "AnnounceScheduler";
    private final Debug debug;
    // constant for random tick multiplier and default values
    private static final double RANDOM_TICK_MULTIPLIER_MIN = 0.9;
    private static final double RANDOM_TICK_MULTIPLIER_MAX = 1.1;
    private static final long DEFAULT_RECURRENCE_TICKS = 4 * 60 * 60 * 20L; // 4 hours in ticks
    private static final long DAILY_RECURRENCE_TICKS = 12 * 60 * 60 * 20L; // 12 hours in ticks
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String ANNUAL_DATE_PATTERN = "\\d{2}-\\d{2}";  // MM-dd pattern

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
            debug.info("Scheduled " + scheduledTasks.size() + " announcements.");
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

        debug.debug("Handling periodic announcement: " + announcement.getId() + " running in " + ticks + " ticks");
        handlePeriodicAnnouncement(announcement, ticks);
    }

    // Handle scheduling of announcements with a specific date and time
    private void handleScheduledAnnouncement(Announcement announcement) {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = announcement.getOriginalDateString(); // You'll need to add this to Announcement class
        
        if (!isDateMatch(now.toLocalDate(), announcement.getDate(), dateStr)) {
            debug.debug("Announcement " + announcement.getId() + " is not scheduled for today");
            return;
        }

        long delayInTicks = 0L;
        if (announcement.getTime() != null) {
            if (now.toLocalTime().isAfter(announcement.getTime())) {
                debug.debug("Skipping announcement " + announcement.getId() + " - time already passed today");
                return;
            }
            delayInTicks = Duration.between(now.toLocalTime(), announcement.getTime()).toMillis() / 50L;
        }

        long recurrence = calculateRecurrence(announcement);
        if ("daily".equalsIgnoreCase(announcement.getRecurrenceString())) {
            scheduleOneTimeAnnouncement(announcement, delayInTicks);
        } else {
            scheduleRecurringAnnouncement(announcement, delayInTicks, recurrence);
        }
    }

    private void scheduleRecurringAnnouncement(Announcement announcement, long initialDelay, long period) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, initialDelay, period);
        scheduledTasks.put(announcement, task);
        debug.debug("Scheduled recurring announcement " + announcement.getId() + 
                   " (delay: " + initialDelay + " ticks, period: " + period + " ticks)");
    }

    private void scheduleOneTimeAnnouncement(Announcement announcement, long delay) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement(announcement);
            }
        }.runTaskLater(plugin, delay);
        scheduledTasks.put(announcement, task);
        debug.debug("Scheduled one-time announcement " + announcement.getId() + 
                   " (delay: " + delay + " ticks)");
    }

    // Helper method to compare only month and day of dates
    private boolean isDateMatch(LocalDate today, LocalDate announcementDate, String originalDateString) {
        if (announcementDate == null) return false;
        
        // Default to exact date comparison if originalDateString is null
        if (originalDateString == null) {
            return today.equals(announcementDate);
        }
        
        // If it's an annual date (MM-dd format)
        if (originalDateString.matches(ANNUAL_DATE_PATTERN)) {
            return today.getMonth() == announcementDate.getMonth() 
                && today.getDayOfMonth() == announcementDate.getDayOfMonth();
        }
        
        // For full date format (yyyy-MM-dd), compare exact dates
        return today.equals(announcementDate);
    }

    // Handle scheduling of periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long unusedTicks) {
        long recurrence = calculateRecurrence(announcement);
        debug.debug("Scheduling periodic announcement " + announcement.getId() + " with recurrence " + recurrence + " ticks");

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement(announcement);
            }
        }.runTaskTimer(plugin, recurrence, recurrence);

        scheduledTasks.put(announcement, task);
    }

    // Apply a random multiplier to the ticks value
    private long applyRandomTicks(long ticks) {
        long randomizedTicks = (long) (ticks * (RANDOM_TICK_MULTIPLIER_MIN + rand.nextDouble() * (RANDOM_TICK_MULTIPLIER_MAX - RANDOM_TICK_MULTIPLIER_MIN)));
        return randomizedTicks;
    }

    private long calculateRecurrence(Announcement announcement) {
        String recurrenceStr = announcement.getRecurrenceString();
        Long recurrence = announcement.getRecurrence();

        // Handle different recurrence scenarios
        if (recurrence != null && recurrence > 0) {
            return applyRandomTicks(recurrence * 20L);
        }
        if ("daily".equalsIgnoreCase(recurrenceStr)) {
            return DAILY_RECURRENCE_TICKS;
        }
        return applyRandomTicks(DEFAULT_RECURRENCE_TICKS);
    }

    // Broadcast the announcement to all players
    public void broadcastAnnouncement(Announcement announcement) {
        this.announceManager.broadcastAnnouncement(announcement);

    }

    // Shut down the scheduler and cancel all tasks
    public void shutdown() {
        debug.info("Shutting down announcement scheduler");
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