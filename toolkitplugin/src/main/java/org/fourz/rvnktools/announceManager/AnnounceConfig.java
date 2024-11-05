package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AnnounceConfig {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private List<Announcement> announcements;
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<String, AnnounceType> announceTypes;
    boolean usingPlaceholderAPI;

    public AnnounceConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.playerDisabledTypes = new HashMap<>();

        loadConfig();
        loadPlayerDisabledTypes();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            plugin.getLogger().info("Created default announcements.yml");
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        announcements = new ArrayList<>();
        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
        for (Map<?, ?> map : announcementMaps) {
            Announcement announcement = parseAnnouncement(map);
            if (announcement != null) {
                announcements.add(announcement);
            }
        }

        announceTypes = new HashMap<>();
        List<Map<?, ?>> announceTypeMaps = config.getMapList("announce_types");
        for (Map<?, ?> map : announceTypeMaps) {
            AnnounceType announceType = parseAnnounceType(map);
            if (announceType != null) {
                announceTypes.put(announceType.getId(), announceType);
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

        if (text.contains("%") && !usingPlaceholderAPI) {
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

    private AnnounceType parseAnnounceType(Map<?, ?> map) {
        String id = (String) map.get("id");
        String color = (String) map.get("color");
        String prefix = (String) map.get("prefix");
        String suffix = (String) map.get("suffix");
        String permission = (String) map.get("permission");

        AnnounceType announceType = new AnnounceType();
        announceType.setId(id);
        announceType.setColor(color);
        announceType.setPrefix(prefix);
        announceType.setSuffix(suffix);
        announceType.setPermission(permission);

        return announceType;
    }

    public boolean loadPlayerDisabledTypes() {
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
            List<String> disabledTypesList = config.getStringList(key);
            Set<String> disabledTypesSet = new HashSet<>(disabledTypesList);
            playerDisabledTypes.put(playerId, disabledTypesSet);
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

        for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
            String key = entry.getKey().toString();
            List<String> disabledTypesList = new ArrayList<>(entry.getValue());
            config.set(key, disabledTypesList);
        }

        try {
            // Save the config to file
            config.save(configFile);
            plugin.getLogger().info("Saved playerDisabledTypes to file");

        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerDisabledTypes to file: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
        loadPlayerDisabledTypes();
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public Map<UUID, Set<String>> getPlayerDisabledTypes() {
        return playerDisabledTypes;
    }

    public Map<String, AnnounceType> getAnnounceTypes() {
        return announceTypes;
    }
}
