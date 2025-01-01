package org.fourz.rvnktools.announceManager.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.Debug;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class YAMLManager {
    private static final String CLASS_NAME = "YAMLManager";
    private final Debug debug;
    private final JavaPlugin plugin;
    private final File configFile;
    private final File preferencesFile;
    private FileConfiguration config;
    private boolean usingPlaceholderAPI;

    public YAMLManager(JavaPlugin plugin) {
        this.plugin = plugin;
        String CLASS_NAME = "YAMLManager";
        this.debug = new YAMLManagerDebug(plugin, CLASS_NAME);
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.preferencesFile = new File(plugin.getDataFolder(), "announcePreferences.yml");
        this.usingPlaceholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        loadConfig();
    }

    private class YAMLManagerDebug extends Debug {
        public YAMLManagerDebug(JavaPlugin plugin, String className) {
            super(plugin, CLASS_NAME, Level.FINE);
        }
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            debug.info("Created default announcements.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public List<Announcement> loadAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();
        if (!configFile.exists()) {
            debug.warning("Config file does not exist: " + configFile.getName());
            return announcements;
        }

        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
        Set<String> usedIds = new HashSet<>();

        for (Map<?, ?> map : announcementMaps) {
            try {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null && announcement.getId() != null) {
                    if (usedIds.contains(announcement.getId().toLowerCase())) {
                        debug.warning("Duplicate announcement ID found: " + announcement.getId());
                        continue;
                    }
                    usedIds.add(announcement.getId().toLowerCase());
                    announcements.add(announcement);
                }
            } catch (Exception e) {
                debug.warning("Error parsing announcement: " + e.getMessage());
            }
        }
        return announcements;
    }

    public Map<String, AnnounceType> loadAnnounceTypes() {
        Map<String, AnnounceType> types = new HashMap<>();
        if (configFile.exists()) {
            List<Map<?, ?>> typesMaps = config.getMapList("announce_types");
            for (Map<?, ?> map : typesMaps) {
                AnnounceType announceType = parseAnnounceType(map);
                if (announceType != null) {
                    types.put(announceType.getId(), announceType);
                }
            }
        }
        return types;
    }

    public void saveAnnouncements(Collection<Announcement> announcements) {
        List<Map<String, Object>> announcementMaps = new ArrayList<>();
        debug.info("Saving " + announcements.size() + " announcements to file");

        for (Announcement announcement : announcements) {
            try {
                if (announcement.getId() == null || announcement.getType() == null || announcement.getMessage() == null) {
                    debug.warning("Skipping invalid announcement: " + announcement.getId());
                    continue;
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", announcement.getId());
                map.put("text", announcement.getMessage());
                map.put("type", announcement.getType());

                // Handle recurrence value
                Long recurrence = announcement.getRecurrence();
                if (recurrence != null) {
                    if (announcement.getRecurrenceString() != null) {
                        map.put("recurrence", announcement.getRecurrenceString());
                    } else {
                        map.put("recurrence", formatRecurrence(recurrence));
                    }
                }

                Optional.ofNullable(announcement.getOwner()).ifPresent(o -> map.put("owner", o));
                Optional.ofNullable(announcement.getPermission()).ifPresent(p -> map.put("permission", p));

                if (announcement.getDate() != null) {
                    map.put("date", announcement.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                }
                if (announcement.getTime() != null) {
                    map.put("time", announcement.getTime().format(DateTimeFormatter.ofPattern("HHmm")));
                }

                map.put("imported", announcement.isImported());
                announcementMaps.add(map);
            } catch (Exception e) {
                debug.error("Error saving announcement " + announcement.getId(), e);
            }
        }

        try {
            config.set("announcements", announcementMaps);
            config.save(configFile);
            debug.info("Configuration saved successfully");
        } catch (IOException e) {
            debug.error("Failed to save announcements", e);
        }
    }

    public void savePreferences(Map<UUID, Set<String>> playerDisabledTypes, Map<UUID, String> preferences) {
        YamlConfiguration prefsConfig = new YamlConfiguration();
        
        for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
            String key = entry.getKey().toString();
            prefsConfig.set(key + ".disabled_types", new ArrayList<>(entry.getValue()));
            
            String prefs = preferences.get(entry.getKey());
            if (prefs != null) {
                prefsConfig.set(key + ".preferences", prefs);
            }
        }

        try {
            prefsConfig.save(preferencesFile);
        } catch (IOException e) {
            debug.error("Failed to save preferences to YAML", e);
        }
    }

    public Map<UUID, Set<String>> loadPlayerDisabledTypes() {
        Map<UUID, Set<String>> disabledTypes = new HashMap<>();
        if (!preferencesFile.exists()) return disabledTypes;

        YamlConfiguration prefsConfig = YamlConfiguration.loadConfiguration(preferencesFile);
        for (String key : prefsConfig.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                List<String> typesList = prefsConfig.getStringList(key + ".disabled_types");
                disabledTypes.put(playerId, new HashSet<>(typesList));
            } catch (IllegalArgumentException e) {
                debug.warning("Invalid UUID in preferences file: " + key);
            }
        }
        return disabledTypes;
    }

    public Map<UUID, String> loadPlayerPreferences() {
        Map<UUID, String> preferences = new HashMap<>();
        if (!preferencesFile.exists()) return preferences;

        YamlConfiguration prefsConfig = YamlConfiguration.loadConfiguration(preferencesFile);
        for (String key : prefsConfig.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String prefs = prefsConfig.getString(key + ".preferences");
                if (prefs != null) {
                    preferences.put(playerId, prefs);
                }
            } catch (IllegalArgumentException e) {
                debug.warning("Invalid UUID in preferences file: " + key);
            }
        }
        return preferences;
    }

    private String formatRecurrence(Long seconds) {
        if (seconds % 86400 == 0) return (seconds / 86400) + "d";
        if (seconds % 3600 == 0) return (seconds / 3600) + "h";
        if (seconds % 60 == 0) return (seconds / 60) + "m";
        return seconds + "s";
    }

    public Announcement parseAnnouncement(Map<?, ?> map) {
        String id = (String) map.get("id");
        String text = (String) map.get("text");
        String type = (String) map.get("type");
        
        if (!validateAnnouncement(id, text, type)) {
            return null;
        }

        Object recurrence = map.get("recurrence");
        Long recurrenceSeconds = null;
        String originalRecurrence = null;
        
        if (recurrence != null) {
            originalRecurrence = recurrence.toString();
            recurrenceSeconds = parseRecurrence(originalRecurrence);
        }
        
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setMessage(text);
        announcement.setType(type);
        announcement.setRecurrence(recurrenceSeconds);
        announcement.setRecurrenceString(originalRecurrence);
        
        // Set optional fields
        announcement.setOwner((String) map.get("owner"));
        announcement.setPermission((String) map.get("permission"));
        
        // Parse date/time if present
        setDateAndTime(announcement, (String) map.get("date"), (String) map.get("time"));
        
        // Set imported flag
        if (map.containsKey("imported")) {
            //announcement.setImported((Boolean) map.get("imported"));
        }

        return announcement;
    }

    private Long parseRecurrence(String recStr) {
        try {
            if (recStr.equalsIgnoreCase("none")) {
                return null;
            }
            if (recStr.equalsIgnoreCase("daily")) {
                return 86400L;
            }
            
            char unit = recStr.charAt(recStr.length() - 1);
            String number = recStr.substring(0, recStr.length() - 1);
            
            switch (unit) {
                case 's': return Long.parseLong(number);
                case 'm': return Long.parseLong(number) * 60;
                case 'h': return Long.parseLong(number) * 3600;
                case 'd': return Long.parseLong(number) * 86400;
                default: return Long.parseLong(recStr);
            }
        } catch (NumberFormatException e) {
            debug.warning("Invalid recurrence format: " + recStr);
            return null;
        }
    }

    private void setDateAndTime(Announcement announcement, String dateStr, String timeStr) {
        try {
            if (dateStr != null) {
                announcement.setDate(parseDate(dateStr));
                announcement.setOriginalDateString(dateStr);
            }
            if (timeStr != null) {
                announcement.setTime(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HHmm")));
            }
        } catch (Exception e) {
            debug.warning("Error parsing date/time: " + e.getMessage());
        }
    }

    public boolean validateAnnouncement(String id, String text, String type) {
        if (id == null || id.isEmpty()) {
            debug.warning("Invalid announcement: missing ID");
            return false;
        }
        if (text == null || text.isEmpty()) {
            debug.warning("Invalid announcement: missing text for ID " + id);
            return false;
        }
        if (type == null || type.isEmpty()) {
            debug.warning("Invalid announcement: missing type for ID " + id);
            return false;
        }
        
        // Check PlaceholderAPI requirement
        if (text.contains("%") && !usingPlaceholderAPI) {
            debug.warning("PlaceholderAPI not found, unable to parse placeholders in announcement: " + id);
            return false;
        }
        
        return true;
    }

    // Add getter/setter for announcements
    public void setAnnouncements(Collection<Announcement> announcements) {
        List<Map<String, Object>> announcementMaps = new ArrayList<>();
        for (Announcement announcement : announcements) {
            if (validateAnnouncement(announcement.getId(), 
                                   announcement.getMessage(), 
                                   announcement.getType())) {
                announcementMaps.add(convertAnnouncementToMap(announcement));
            }
        }
        config.set("announcements", announcementMaps);
        saveConfig();
    }

    private Map<String, Object> convertAnnouncementToMap(Announcement announcement) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", announcement.getId());
        map.put("text", announcement.getMessage());
        map.put("type", announcement.getType());
        
        if (announcement.getRecurrence() != null) {
            map.put("recurrence", announcement.getRecurrenceString() != null ? 
                   announcement.getRecurrenceString() : 
                   formatRecurrence(announcement.getRecurrence()));
        }
        
        Optional.ofNullable(announcement.getOwner()).ifPresent(o -> map.put("owner", o));
        Optional.ofNullable(announcement.getPermission()).ifPresent(p -> map.put("permission", p));
        
        if (announcement.getDate() != null) {
            map.put("date", announcement.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (announcement.getTime() != null) {
            map.put("time", announcement.getTime().format(DateTimeFormatter.ofPattern("HHmm")));
        }
        
        map.put("imported", announcement.isImported());
        return map;
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            debug.error("Failed to save configuration", e);
        }
    }

    // Converts a YAML map into an AnnounceType object
    public AnnounceType parseAnnounceType(Map<?, ?> map) {
        String id = (String) map.get("id");
        String prefix = (String) map.get("prefix");
        String suffix = (String) map.get("suffix");
        Double listingFee = map.get("list_fee") == null ? null : (map.get("list_fee") instanceof Integer ? ((Integer) map.get("list_fee")).doubleValue() : (Double) map.get("list_fee"));
        String permission = (String) map.get("permission");        

        AnnounceType announceType = new AnnounceType();
        announceType.setId(id);
        announceType.setPrefix(prefix);
        announceType.setSuffix(suffix);
        if (listingFee != null) {
            announceType.setListingFee(listingFee);
        }
        announceType.setPermission(permission);
        return announceType;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            if (dateStr.matches("\\d{2}-\\d{2}")) { // MM-dd format
                // Parse as current year
                return LocalDate.parse(LocalDate.now().getYear() + "-" + dateStr, 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                // Parse as full date
                return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception e) {
            debug.warning("Failed to parse date: " + dateStr);
            return null;
        }
    }


    public FileConfiguration getConfig() {
        return config;
    }
}
