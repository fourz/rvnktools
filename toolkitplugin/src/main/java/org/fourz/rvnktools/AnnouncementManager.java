package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnnouncementManager implements Listener {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private List<String> announcements;
    private List<String> loginAnnouncements;
    private int interval;
    private boolean randomInterval;
    private int minInterval;
    private int maxInterval;
    private Set<UUID> disabledPlayers;
    private BukkitRunnable announcementTask;
    private Map<UUID, List<String>> userAnnouncements;
    private Set<UUID> playersShownLoginAnnouncements;

    public AnnouncementManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.disabledPlayers = new HashSet<>();
        this.userAnnouncements = new HashMap<>();
        this.playersShownLoginAnnouncements = new HashSet<>();
        loadConfig();
        startAnnouncementTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        announcements = config.getStringList("announcements");
        loginAnnouncements = config.getStringList("login-announcements");
        interval = config.getInt("interval", 300);
        randomInterval = config.getBoolean("random-interval", false);
        minInterval = config.getInt("min-interval", 180);
        maxInterval = config.getInt("max-interval", 600);

        // Load user-specific announcements
        if (config.contains("user-announcements")) {
            for (String key : config.getConfigurationSection("user-announcements").getKeys(false)) {
                UUID userId = UUID.fromString(key);
                List<String> userAnns = config.getStringList("user-announcements." + key);
                userAnnouncements.put(userId, new ArrayList<>(userAnns));
            }
        }
    }

    private void createDefaultConfig() {
        config.set("announcements", Arrays.asList("Don't forget to vote!", "Join our Discord server!"));
        config.set("login-announcements", Arrays.asList("Welcome to the server!", "Remember to follow the rules!"));
        config.set("interval", 300);
        config.set("random-interval", false);
        config.set("min-interval", 180);
        config.set("max-interval", 600);
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!playersShownLoginAnnouncements.contains(playerId)) {
            for (String announcement : loginAnnouncements) {
                player.sendMessage(announcement);
            }
            playersShownLoginAnnouncements.add(playerId);
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
        if (announcements.isEmpty() && userAnnouncements.isEmpty())
            return;
        List<String> allAnnouncements = new ArrayList<>(announcements);
        for (List<String> userAnns : userAnnouncements.values()) {
            allAnnouncements.addAll(userAnns);
        }
        String announcement = allAnnouncements.get(new Random().nextInt(allAnnouncements.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!disabledPlayers.contains(player.getUniqueId())) {
                player.sendMessage(announcement);
            }
        }
    }

    public void addAnnouncement(Player player, String announcement) {
        UUID playerId = player.getUniqueId();
        List<String> userAnns = new ArrayList<>();
        userAnns.add(announcement);
        userAnnouncements.put(playerId, userAnns);
        saveUserAnnouncements();
        player.sendMessage("Announcement set.");
    }

    public void removeAnnouncement(Player player) {
        UUID playerId = player.getUniqueId();
        if (userAnnouncements.remove(playerId) != null) {
            saveUserAnnouncements();
            player.sendMessage("Announcement removed.");
        } else {
            player.sendMessage("No announcement found to remove.");
        }
    }

    private void saveUserAnnouncements() {
        for (Map.Entry<UUID, List<String>> entry : userAnnouncements.entrySet()) {
            config.set("user-announcements." + entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
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
