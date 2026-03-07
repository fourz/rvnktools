package org.fourz.rvnktools.announceManager;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;

public class AnnounceScheduler {
    // constant for random tick multiplier and default values
    private static final double RANDOM_TICK_MULTIPLIER_MIN = 0.9;
    private static final double RANDOM_TICK_MULTIPLIER_MAX = 1.1;
    private static final long DEFAULT_RECURRENCE_TICKS = 4 * 60 * 60 * 20L; // 4 hours in ticks
    private static final long DAILY_RECURRENCE_TICKS = 12 * 60 * 60 * 20L; // 12 hours in ticks
    private static final String ANNUAL_DATE_PATTERN = "\\d{2}-\\d{2}";  // MM-dd pattern

    private final LogManager logger;
    private final JavaPlugin plugin;
    private final AnnounceManager announceManager;
    private Map<Announcement, BukkitTask> scheduledTasks = new ConcurrentHashMap<>();    
    private final Random rand = new Random();

    // constants for improved readability in conditional statements
    private static final int RECURRENCE_UNSET = -1; 

    // Initialize the scheduler
    public AnnounceScheduler(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.logger = LogManager.getInstance(plugin, getClass());
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
            logger.info("Scheduled " + scheduledTasks.size() + " announcements.");
        }
    }

    // Schedule a single announcement
    public void scheduleAnnouncement(Announcement announcement) {
        // Skip MOTD announcements if broadcasting is disabled
        if ("motd".equalsIgnoreCase(announcement.getType()) && 
            !announceManager.getConfig().getAnnounceMotd().shouldScheduleBroadcast()) {
            logger.debug("Skipping MOTD announcement schedule: " + announcement.getId() + " (broadcasting disabled)");
            return;
        }

        // Check if the announcement has an expiration date and if it has past, skip it
        if (announcement.getExpiration() != null) {
            if (LocalDateTime.now().isAfter(announcement.getExpiration())) {
                logger.debug("Skipping expired announcement: " + announcement.getId());
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
            logger.debug("Handling scheduled announcement: " + announcement.getId());
            handleScheduledAnnouncement(announcement);
            return;
        }

        logger.debug("Handling periodic announcement: " + announcement.getId() + " running in " + ticks + " ticks");
        handlePeriodicAnnouncement(announcement, ticks);
    }

    // Handle scheduling of announcements with a specific date and time
    private void handleScheduledAnnouncement(Announcement announcement) {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = announcement.getOriginalDateString(); // You'll need to add this to Announcement class
        
        if (!isDateMatch(now.toLocalDate(), announcement.getDate(), dateStr)) {
            logger.debug("Announcement " + announcement.getId() + " is not scheduled for today");
            return;
        }

        long delayInTicks = 0L;
        if (announcement.getTime() != null) {
            if (now.toLocalTime().isAfter(announcement.getTime())) {
                logger.debug("Skipping announcement " + announcement.getId() + " - time already passed today");
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
        logger.debug("Scheduled recurring announcement " + announcement.getId() + " (delay: " + initialDelay + 
            " ticks, period: " + period + " ticks)");
    }

    private void scheduleOneTimeAnnouncement(Announcement announcement, long delay) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement(announcement);
            }
        }.runTaskLater(plugin, delay);
        scheduledTasks.put(announcement, task);
        logger.debug("Scheduled one-time announcement " + announcement.getId() + " (delay: " + delay + " ticks)");
    }

    // Helper method to compare only month and day of dates
    private boolean isDateMatch(LocalDate today, LocalDate announcementDate, String originalDateString) {
        if (announcementDate == null) return false;
        
        // Default to exact date comparison if originalDateString is null
        if (originalDateString == null || !originalDateString.matches(ANNUAL_DATE_PATTERN)) {
            return today.equals(announcementDate);
        }

        // For annual dates (MM-dd pattern), compare only month and day
        return today.getMonthValue() == announcementDate.getMonthValue() && 
               today.getDayOfMonth() == announcementDate.getDayOfMonth();
    }

    // Helper method to handle periodic announcements
    private void handlePeriodicAnnouncement(Announcement announcement, long ticks) {
        if (ticks <= 0) {
            long defaultTicks = DEFAULT_RECURRENCE_TICKS;
            if ("daily".equalsIgnoreCase(announcement.getRecurrenceString())) {
                defaultTicks = DAILY_RECURRENCE_TICKS;
            }
            defaultTicks = applyRandomTicks(defaultTicks);
            scheduleRecurringAnnouncement(announcement, defaultTicks, defaultTicks);
            logger.debug("Using default recurrence for announcement " + announcement.getId() + ": " + defaultTicks + " ticks");
        } else {
            scheduleRecurringAnnouncement(announcement, ticks, ticks);
        }
    }

    // Helper method to calculate recurrence
    private long calculateRecurrence(Announcement announcement) {
        if ("daily".equalsIgnoreCase(announcement.getRecurrenceString())) {
            return DAILY_RECURRENCE_TICKS;
        }
        
        Long recurrence = announcement.getRecurrence();
        if (recurrence != null && recurrence > 0) {
            return recurrence * 20L; // Convert seconds to ticks
        }
        
        return DEFAULT_RECURRENCE_TICKS;
    }

    // Apply random variation to ticks to prevent all announcements running at once
    private long applyRandomTicks(long ticks) {
        double multiplier = RANDOM_TICK_MULTIPLIER_MIN + (rand.nextDouble() * 
                          (RANDOM_TICK_MULTIPLIER_MAX - RANDOM_TICK_MULTIPLIER_MIN));
        return Math.round(ticks * multiplier);
    }

    private void broadcastAnnouncement(Announcement announcement) {
        announceManager.broadcastAnnouncement(announcement);
    }

    // Clean up any scheduled tasks
    public void cleanup() {
        for (BukkitTask task : scheduledTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        scheduledTasks.clear();
        logger.debug("Cleaned up scheduled announcement tasks");
    }

    // Shut down the scheduler and cancel all tasks
    public void shutdown() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
            logger.debug("Cancelled " + scheduledTasks.size() + " scheduled tasks");
        }
    }

    public void unscheduleAnnouncement(Announcement announcement) {
        BukkitTask task = scheduledTasks.remove(announcement);
        if (task != null) {
            task.cancel();
            logger.debug("Unscheduled announcement: " + announcement.getId());
        }
    }
}