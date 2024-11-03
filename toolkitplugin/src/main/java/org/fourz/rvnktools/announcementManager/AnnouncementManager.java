package org.fourz.rvnktools.announcementManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.linkMaker.LinkMaker;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import me.clip.placeholderapi.PlaceholderAPI;

public class AnnouncementManager {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private List<Announcement> announcements;
    private Map<Announcement, BukkitTask> scheduledTasks;
    private Map<UUID, Set<String>> playerDisabledTypes;
    boolean usingPlaceholderAPI;

    public AnnouncementManager(RVNKTools plugin) {
        plugin.getLogger().info("Enabling AnnouncementManager.");
        this.plugin = plugin;
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        this.playerDisabledTypes = new HashMap<>();
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml"); // Initialize configFile
        
        loadConfig();
        loadPlayerDisabledTypes(); 

        plugin.getLogger().info("Scheduling announcements...");
        scheduleAnnouncements();
    }

    public void loadConfig() {
        // Define the path to the announcements.yml file within the plugin's data folder
        File configFile = new File(plugin.getDataFolder(), "announcements.yml");

        // Check if the announcements.yml file exists
        if (!this.configFile.exists()) {
            // If the file does not exist, save the default resource from the plugin's jar
            plugin.saveResource("announcements.yml", false);
            // createDefaultConfig();
            // Notify the console that a new config file has been created
            plugin.getLogger().info("Created default announcements.yml");
        }

        // Load the configuration from the announcements.yml file
        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Initialize the list to hold Announcement objects
        announcements = new ArrayList<>();

        // Retrieve the list of maps from the "announcements" section of the config
        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");

        // Iterate through each map in the list
        for (Map<?, ?> map : announcementMaps) {
            // Parse the map into an Announcement object
            Announcement announcement = parseAnnouncement(map);

            // If the announcement is successfully parsed, add it to the list
            if (announcement != null) {
                announcements.add(announcement);
            }
        }
    }

    private Announcement parseAnnouncement(Map<?, ?> map) {
        String id = (String) map.get("id");
        String text = (String) map.get("text");
        String type = (String) map.get("type");
        String recurrence = (String) map.get("recurrence");
        String owner = (String) map.get("owner");
        String permission = (String) map.get("permission");
        String dateStr = (String) map.get("date");
        String timeStr = (String) map.get("time");
        Integer cost = null;
        if (map.containsKey("cost")) {
            cost = (Integer) map.get("cost");
        }
        
        //if text contains a placeholder and placeholderAPI is not found, return null
        if (text.contains("%") && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI not found, unable to parse placeholders in announcement: " + id);
            return null;
        }

        LocalDate date = null;
        LocalTime time = null;
        try {
            if (dateStr != null) {
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                date = LocalDate.parse(dateStr, dateFormatter);
            }
            if (timeStr != null) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmm");
                time = LocalTime.parse(timeStr, timeFormatter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setText(text);
        announcement.setType(type);
        announcement.setRecurrence(recurrence);
        announcement.setOwner(owner);
        announcement.setPermission(permission);
        announcement.setDate(date);
        announcement.setTime(time);
        announcement.setCost(cost);

        return announcement;
    }

    private boolean loadPlayerDisabledTypes() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "playerDisabledTypes.yml");
        if (!configFile.exists()) {
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        for (String key : config.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            boolean value = config.getBoolean(key);
            if (value) {
                playerDisabledTypes.put(playerId, new HashSet<>());
            }
        }

        return true;
    }

    public void savePlayerDisabledTypes() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "playerDisabledTypes.yml");
        YamlConfiguration config = new YamlConfiguration();

        // build the config file from the playerDisabledTypes map
        for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
            String key = entry.getKey().toString();
            boolean value = !entry.getValue().isEmpty();
            config.set(key, value);
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerDisabledTypes to file: " + e.getMessage());
        }
    }

    private void scheduleAnnouncements() {
        if (scheduledTasks != null) {
            // Cancel existing tasks
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
        scheduledTasks = new HashMap<>();

        for (Announcement announcement : announcements) {
            scheduleAnnouncement(announcement);
        }
    }

    private void scheduleAnnouncement(Announcement announcement) {        
        long ticks = parseRecurrenceToTicks(announcement.getRecurrence());                
        Random rand = new Random();

        //output announcement id to console
        plugin.getLogger().info("Announcement id: " + announcement.getId());

        //output ticks value to console
        plugin.getLogger().info("Ticks value: " + ticks);

        if (ticks > 0) {
            //generate random long ranging from 90% to 110% of ticks
            //ticks = (long) (rand.nextLong(ticks / 5) + ticks * 0.9); 
            ticks = (long) (ticks * (0.9 + rand.nextDouble() * 0.2)); // between 90% and 110%

        } 

        if (announcement.getType().equalsIgnoreCase("scheduled") && announcement.getDate() != null && announcement.getTime() != null) {
            // Calculate delay until the specified date and time
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime announcementDateTime = LocalDateTime.of(announcement.getDate(), announcement.getTime());

            if (now.isAfter(announcementDateTime)) {
                // If the time has passed, and recurrence is "annual", schedule for next year
                if ("annual".equalsIgnoreCase(announcement.getRecurrence())) {
                    announcementDateTime = announcementDateTime.plusYears(1);
                } else {
                    return; // Time has passed, and no recurrence, so don't schedule
                }
            }

            long delay = Duration.between(now, announcementDateTime).toMillis() / 50L; // Convert milliseconds to ticks

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    broadcastAnnouncement(announcement);
                    if ("annual".equalsIgnoreCase(announcement.getRecurrence())) {
                        // Reschedule for next year
                        announcement.setDate(announcement.getDate().plusYears(1));
                        scheduleAnnouncement(announcement);
                    }
                }
            }.runTaskLater(plugin, delay);

            scheduledTasks.put(announcement, task);
        } else {
            //if no recurrence is set, generate random ticks for next 1 to 3 hours
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

            //
            scheduledTasks.put(announcement, task);

            // output the scheduled announcement to the console with the delay in ticks
            plugin.getLogger().info("Scheduled announcement '" + announcement.getId() + "' with delay " + ticks + " ticks.");
        }
    }

    private long parseRecurrenceToTicks(String recurrence) {
        if (recurrence == null) {
            return -1;
        }
        recurrence = recurrence.toLowerCase();
        if (recurrence.equals("annual")) {
            return -1; // Special handling
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

    private void broadcastAnnouncement(Announcement announcement) {
        String message = announcement.getText();
        //message = replacePlaceholders(message);

        //add integration for LinkMaker here      


        //if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {        

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldReceiveAnnouncement(player, announcement)) {
                message = ChatFormat.colorize(message); 
                if (this.usingPlaceholderAPI) {
                    message = PlaceholderAPI.setPlaceholders(player, message);
                }
                player.sendMessage(message);
            }
        }
    }

    private boolean shouldReceiveAnnouncement(Player player, Announcement announcement) {
        if (announcement.getPermission() != null && !player.hasPermission(announcement.getPermission())) {
            return false;
        }

        // Types that can't be turned off
        if (announcement.getType().matches("(?i)^(server|scheduled)$")) {
            return true;
        }

        // Check if player has disabled this type
        Set<String> disabledTypes = playerDisabledTypes.get(player.getUniqueId());
        if (disabledTypes != null && disabledTypes.contains(announcement.getType().toLowerCase())) {
            return false;
        }

        return true;
    }

    public void toggleAnnouncementType(Player player, String type) {
        UUID playerId = player.getUniqueId();
        if (player.hasPermission("rvnktools.command.announcement.toggle." + type)) {
            type = type.toLowerCase();
            Set<String> disabledTypes = playerDisabledTypes.getOrDefault(playerId, new HashSet<>());

            if (disabledTypes.contains(type)) {
                disabledTypes.remove(type);
                player.sendMessage("Announcements of type '" + type + "' enabled.");
            } else {
                disabledTypes.add(type);
                player.sendMessage("Announcements of type '" + type + "' disabled.");
            }
            playerDisabledTypes.put(playerId, disabledTypes);
        } else {
            player.sendMessage("You do not have permission to toggle announcements.");
        }
    }

    public void reloadConfig() {
        loadConfig();
        scheduleAnnouncements();
    }

    public void shutdown() {
        if (scheduledTasks != null) {
            for (BukkitTask task : scheduledTasks.values()) {
                task.cancel();
            }
        }
    }

    private void createDefaultConfig() {
        configFile.getParentFile().mkdirs();
        try {
            configFile.createNewFile();
            YamlConfiguration defaultConfig = new YamlConfiguration();
            List<Map<String, Object>> defaultAnnouncements = new ArrayList<>();

            Map<String, Object> announcement1 = new HashMap<>();
            announcement1.put("id", "welcome");
            announcement1.put("text", "Welcome to the server!");
            announcement1.put("type", "server");
            announcement1.put("recurrence", "1h");

            Map<String, Object> announcement2 = new HashMap<>();
            announcement2.put("id", "help");
            announcement2.put("text", "&aUse &d/help &afor a command guide");
            announcement2.put("type", "guide");
            announcement2.put("recurrence", "1h");

            defaultAnnouncements.add(announcement1);
            defaultAnnouncements.add(announcement2);

            defaultConfig.set("announcements", defaultAnnouncements);
            defaultConfig.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}