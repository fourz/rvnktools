package org.fourz.rvnktools.announcementManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.fourz.rvnktools.RVNKTools;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnnouncementManager {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private List<Announcement> announcements;
    private int interval;
    private boolean randomInterval;
    private int minInterval;
    private int maxInterval;
    private Set<UUID> disabledPlayers;
    private BukkitRunnable announcementTask;

    public AnnouncementManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.disabledPlayers = new HashSet<>();
        loadConfig();
        startAnnouncementTask();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        List<Map<?, ?>> rawAnnouncements = config.getMapList("announcements");
        announcements = new ArrayList<>();
        for (Map<?, ?> rawAnnouncement : rawAnnouncements) {
            Announcement announcement = new Announcement();
            announcement.setId((String) rawAnnouncement.get("id"));
            announcement.setText((String) rawAnnouncement.get("text"));
            announcement.setType((String) rawAnnouncement.get("type"));
            announcement.setRecurrence((String) rawAnnouncement.get("recurrence"));
            announcement.setOwner((String) rawAnnouncement.get("owner"));
            announcement.setPermission((String) rawAnnouncement.get("permission"));
            announcement.setCost((Integer) rawAnnouncement.get("cost"));
            announcement.setDate((String) rawAnnouncement.get("date"));
            announcement.setTime((String) rawAnnouncement.get("time"));
            announcements.add(announcement);
        }
        interval = config.getInt("interval", 300);


        randomInterval = config.getBoolean("random-interval", false);
        minInterval = config.getInt("min-interval", 180);
        maxInterval = config.getInt("max-interval", 600);
    }

    private void createDefaultConfig() {
        configFile.getParentFile().mkdirs();
        try {
            configFile.createNewFile();
            YamlConfiguration defaultConfig = new YamlConfiguration();
            defaultConfig.set("announcements",
                    Arrays.asList("Welcome to the server!", "Remember to follow the rules!"));
            defaultConfig.set("interval", 300);
            defaultConfig.set("random-interval", false);
            defaultConfig.set("min-interval", 180);
            defaultConfig.set("max-interval", 600);
            defaultConfig.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAnnouncementTask() {
        if (announcementTask != null) {
            announcementTask.cancel();
        }

        announcementTask = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastAnnouncement();
                if (randomInterval) {
                    long nextInterval = (long) (Math.random() * (maxInterval - minInterval + 1) + minInterval) * 20L;
                    this.cancel();
                    announcementTask = this;
                    this.runTaskLater(plugin, nextInterval);
                }
            }
        };

        if (randomInterval) {
            long initialDelay = (long) (Math.random() * (maxInterval - minInterval + 1) + minInterval) * 20L;
            announcementTask.runTaskLater(plugin, initialDelay);
        } else {
            announcementTask.runTaskTimer(plugin, interval * 20L, interval * 20L);
        }
    }

    private void broadcastAnnouncement() {
        if (announcements.isEmpty())
            return;
        Announcement announcement = announcements.get(new Random().nextInt(announcements.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!disabledPlayers.contains(player.getUniqueId())) {
                player.sendMessage(announcement.getText());
            }
        }
    }

    public void toggleAnnouncements(Player player) {
        UUID playerId = player.getUniqueId();
        if (disabledPlayers.contains(playerId)) {
            disabledPlayers.remove(playerId);
            player.sendMessage("Announcements enabled.");
        } else {
            disabledPlayers.add(playerId);
            player.sendMessage("Announcements disabled.");
        }
    }

    public void reloadConfig() {
        loadConfig();
        startAnnouncementTask();
    }

    public void shutdown() {
        if (announcementTask != null) {
            announcementTask.cancel();
        }
    }
}

class Announcement {
    private String id;
    private String text;
    private String type;
    private String recurrence;
    private String owner;
    private String permission;
    private int cost;
    private String date;
    private String time;

    // Getters and setters for all fields
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}