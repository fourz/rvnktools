package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.preferences.AnnouncePreferences;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.fourz.rvnkcore.util.log.LogManager;

public class AnnounceConfig {
    private static final String CLASS_NAME = "AnnounceConfig";
    private static Level logLevel = Level.INFO;

    public static Level getLogLevel() {
        return logLevel;
    }

    private final LogManager logger;
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private Map<String, AnnounceType> announceTypes;
    boolean usingPlaceholderAPI;
    private final AnnounceManager announceManager;
    private List<Announcement> ymlAnnouncements;
    private Map<String, AnnounceType> ymlTypes;
    private AnnouncePreferences preferences;
    private AnnounceMotd announceMotd;
    private boolean doMotd;
    private boolean doMotdScheduleBroadcast;

    public AnnounceConfig(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;

        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);

        config = YamlConfiguration.loadConfiguration(configFile);
        logLevel = LogManager.parseLevel(config.getString("debug.level", "INFO"));
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);

        if (loadConfig()) logger.info("Configuration loaded successfully");

        this.preferences = new AnnouncePreferences(plugin);
        this.announceMotd = new AnnounceMotd(plugin, this);
    }

    /**
     * Loads or creates the YAML configuration file.
     * Parses seed data (types + announcements) and MOTD config.
     */
    public boolean loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            logger.info("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        this.doMotd = config.getBoolean("motd.enable", true);
        this.doMotdScheduleBroadcast = config.getBoolean("motd.schedule-broadcast", false);
        logger.debug("MOTD config - enabled: " + doMotd + ", schedule-broadcast: " + doMotdScheduleBroadcast);

        this.ymlAnnouncements = loadAnnouncementsFromYAML();
        this.ymlTypes = loadTypesFromYAML();

        logger.info(String.format("Loaded configuration: %d announcements, %d types from YAML seed",
            ymlAnnouncements.size(), ymlTypes.size()));

        return !ymlAnnouncements.isEmpty() || !ymlTypes.isEmpty();
    }

    /**
     * Initializes MOTD announcements after announcements are loaded.
     * Called by AnnounceManager after DB load is complete.
     */
    public void initializeMotd(List<Announcement> motdAnnouncements) {
        if (doMotd && announceMotd != null) {
            announceMotd.setMotd(motdAnnouncements);
        } else {
            logger.info("MOTD system is disabled in config");
        }
    }

    // ========== YAML Seed Parsing (inlined from YAMLManager) ==========

    private List<Announcement> loadAnnouncementsFromYAML() {
        List<Announcement> announcements = new ArrayList<>();
        if (!configFile.exists()) {
            logger.warning("Config file does not exist: " + configFile.getName());
            return announcements;
        }

        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
        Set<String> usedIds = new HashSet<>();

        for (Map<?, ?> map : announcementMaps) {
            try {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null && announcement.getId() != null) {
                    if (usedIds.contains(announcement.getId().toLowerCase())) {
                        logger.warning("Duplicate announcement ID found: " + announcement.getId());
                        continue;
                    }
                    usedIds.add(announcement.getId().toLowerCase());
                    announcements.add(announcement);
                }
            } catch (Exception e) {
                logger.warning("Error parsing announcement: " + e.getMessage());
            }
        }
        return announcements;
    }

    private Map<String, AnnounceType> loadTypesFromYAML() {
        Map<String, AnnounceType> types = new HashMap<>();
        if (!configFile.exists()) {
            logger.warning("Config file not found: " + configFile.getName());
            return types;
        }

        List<Map<?, ?>> typesMaps = config.getMapList("announce_types");
        for (Map<?, ?> map : typesMaps) {
            AnnounceType announceType = parseAnnounceType(map);
            if (announceType != null) {
                types.put(announceType.getId(), announceType);
            }
        }
        return types;
    }

    private Announcement parseAnnouncement(Map<?, ?> map) {
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
        announcement.setOwner((String) map.get("owner"));
        announcement.setPermission((String) map.get("permission"));

        setDateAndTime(announcement, (String) map.get("date"), (String) map.get("time"));

        return announcement;
    }

    AnnounceType parseAnnounceType(Map<?, ?> map) {
        String id = (String) map.get("id");
        String prefix = (String) map.get("prefix");
        String suffix = (String) map.get("suffix");
        Double listingFee = map.get("list_fee") == null ? null :
            (map.get("list_fee") instanceof Integer ?
                ((Integer) map.get("list_fee")).doubleValue() : (Double) map.get("list_fee"));
        String permission = (String) map.get("permission");

        AnnounceType announceType = new AnnounceType();
        announceType.setId(id);
        announceType.setPrefix(prefix);
        announceType.setSuffix(suffix);
        if (listingFee != null) {
            announceType.setListingFee(listingFee);
        }
        announceType.setPermission(permission);
        String displayContext = (String) map.get("display_context");
        if (displayContext != null) {
            announceType.setDisplayContext(displayContext);
        }
        return announceType;
    }

    public boolean validateAnnouncement(String id, String text, String type) {
        if (id == null || id.isEmpty()) {
            logger.warning("Invalid announcement: missing ID");
            return false;
        }
        if (text == null || text.isEmpty()) {
            logger.warning("Invalid announcement: missing text for ID " + id);
            return false;
        }
        if (type == null || type.isEmpty()) {
            logger.warning("Invalid announcement: missing type for ID " + id);
            return false;
        }
        if (text.contains("%") && !usingPlaceholderAPI) {
            logger.warning("PlaceholderAPI not found, unable to parse placeholders in announcement: " + id);
            return false;
        }
        return true;
    }

    private Long parseRecurrence(String recStr) {
        try {
            if (recStr.equalsIgnoreCase("none")) return null;
            if (recStr.equalsIgnoreCase("daily")) return 86400L;

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
            logger.warning("Invalid recurrence format: " + recStr);
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
            logger.warning("Error parsing date/time: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String dateStr) {
        try {
            if (dateStr.matches("\\d{2}-\\d{2}")) {
                return LocalDate.parse(LocalDate.now().getYear() + "-" + dateStr,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse date: " + dateStr);
            return null;
        }
    }

    // ========== Announcement creation helpers ==========

    public boolean parseAnnouncement(String id, String type, String text, String playerName) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setMessage(text);
        announcement.setOwner(playerName);
        return announceManager.addAnnouncement(announcement);
    }

    public boolean parseAnnouncement(String id, String type, String text) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setMessage(text);
        return announceManager.addAnnouncement(announcement);
    }

    // ========== Preferences delegation ==========

    public void savePlayerDisabledTypes() {
        preferences.savePreferences();
    }

    public Map<UUID, Set<String>> getPlayerDisabledTypes() {
        return preferences.getAllDisabledTypes();
    }

    public void addPlayerDisabledType(UUID playerId, String type) {
        preferences.addDisabledType(playerId, type);
    }

    public void removePlayerDisabledType(UUID playerId, String type) {
        preferences.removeDisabledType(playerId, type);
    }

    public String getPreference(UUID playerId, String property) {
        return preferences.getPreference(playerId, property);
    }

    public Map<String, String> getAllPreferences(UUID playerId) {
        return preferences.getAllPreferences(playerId);
    }

    public void setPreference(UUID playerId, String property, String value) {
        preferences.setPreference(playerId, property, value);
    }

    public void deletePreference(UUID playerId, String property) {
        preferences.deletePreference(playerId, property);
    }

    public CompletableFuture<String> getPreferenceAsync(UUID playerId, String property) {
        return preferences.getPreferenceAsync(playerId, property);
    }

    public CompletableFuture<Void> setPreferenceAsync(UUID playerId, String property, String value) {
        return preferences.setPreferenceAsync(playerId, property, value);
    }

    // ========== Config & type accessors ==========

    public void reloadConfig() {
        loadConfig();
    }

    public Map<String, AnnounceType> getAnnounceTypes() {
        return announceTypes;
    }

    public Map<String, AnnounceType> getYmlTypes() {
        return ymlTypes;
    }

    public List<Announcement> getYmlAnnouncements() {
        return ymlAnnouncements;
    }

    public void setAnnounceTypes(Map<String, AnnounceType> types) {
        this.announceTypes = types;
    }

    public AnnounceMotd getAnnounceMotd() {
        return announceMotd;
    }

    public boolean isMotdEnabled() {
        return doMotd;
    }

    public boolean isMotdScheduleBroadcast() {
        return doMotdScheduleBroadcast;
    }

    public void shutdown() {
        savePlayerDisabledTypes();
    }
}
