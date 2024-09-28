package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnnouncementManager {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private List<String> announcements;
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
        announcements = config.getStringList("announcements");
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
            defaultConfig.set("announcements", Arrays.asList("Welcome to Ravenkraft!", "Remember to follow the rules!"));
            defaultConfig.set("interval", 3000);
            defaultConfig.set("random-interval", true);
            defaultConfig.set("min-interval", 2400);
            defaultConfig.set("max-interval", 6000);
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
        if (announcements.isEmpty()) return;
        String announcement = announcements.get(new Random().nextInt(announcements.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!disabledPlayers.contains(player.getUniqueId())) {
                player.sendMessage(announcement);
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
