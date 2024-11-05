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
import java.time.LocalTime;
import java.util.*;

public class AnnounceScheduler {
    private final RVNKTools plugin;
    private final AnnounceManager announceManager;
    private Map<Announcement, BukkitTask> scheduledTasks;
    private boolean usingPlaceholderAPI;

    public AnnounceScheduler(RVNKTools plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public void scheduleAnnouncements() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
        scheduledTasks = new HashMap<>();

        for (Announcement announcement : announceManager.getAnnouncements()) {
            scheduleAnnouncement(announcement);
        }
    }

    private void scheduleAnnouncement(Announcement announcement) {
        long ticks = parseRecurrenceToTicks(announcement.getRecurrence());
        Random rand = new Random();

        plugin.getLogger().info("Announcement id: " + announcement.getId());
        plugin.getLogger().info("Ticks value: " + ticks);

        if (ticks > 0) {
            ticks = (long) (ticks * (0.9 + rand.nextDouble() * 0.2));
        }

        if (announcement.getType().equalsIgnoreCase("scheduled") && announcement.getDate() != null && announcement.getTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime announcementDateTime = LocalDateTime.of(announcement.getDate(), announcement.getTime());

            if (now.isAfter(announcementDateTime)) {
                if ("annual".equalsIgnoreCase(announcement.getRecurrence())) {
                    announcementDateTime = announcementDateTime.plusYears(1);
                } else {
                    return;
                }
            }

            long delay = Duration.between(now, announcementDateTime).toMillis() / 50L;

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
        } else {
            if (ticks == -1) {
                ticks = (long) (rand.nextLong(144000 * 2) + 72000);
            }
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getLogger().info("Broadcasting announcement: " + announcement.getId());
                    broadcastAnnouncement(announcement);
                }
            }.runTaskTimer(plugin, ticks, ticks);

            scheduledTasks.put(announcement, task);
            plugin.getLogger().info("Scheduled announcement '" + announcement.getId() + "' with delay " + ticks + " ticks.");
        }
    }

    private long parseRecurrenceToTicks(String recurrence) {
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

    public void broadcastAnnouncement(Announcement announcement) {
        String message = announcement.getText();
        message = "&5" + announcement.getType() + "&6: &f" + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (announceManager.shouldReceiveAnnouncement(player, announcement)) {
                String formattedMessage = ChatFormat.colorize(message);
                if (usingPlaceholderAPI) {
                    formattedMessage = PlaceholderAPI.setPlaceholders(player, formattedMessage);
                }
                player.sendMessage(formattedMessage);
            }
        }
    }

    public void shutdown() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
    }
}
