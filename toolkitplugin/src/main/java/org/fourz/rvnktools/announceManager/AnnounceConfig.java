package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStore;
import org.fourz.rvnktools.announceManager.data.MySQLDataConnector;
import org.fourz.rvnktools.announceManager.data.SQLiteDataConnector;

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
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<String, AnnounceType> announceTypes;
    boolean usingPlaceholderAPI;
    private final AnnounceManager announceManager;
    private DataStore dataStore;
    private String storageType;

    // Constructor to initialize the announcement configuration and load data
    public AnnounceConfig(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.playerDisabledTypes = new HashMap<>();

        loadConfig();        
        loadPlayerDisabledTypes();
    }

    // Sets up data storage and handles initial data migration from YAML to database if needed
    public void initializeDataStore() {
        plugin.getLogger().info("Using " + storageType + " storage");

        // Load data from YAML files
        List<Announcement> ymlAnnouncements = loadDataFromYAML();
        Map<String, AnnounceType> ymlTypes = loadTypesFromYAML();
        announceTypes = new HashMap<>();

        // Initialize DataStore based on storage type
        initializeDataStoreConnection();

        boolean isChanged = false;

        if (dataStore != null) {
            dataStore.connect();
            try {
                if (dataStore.isEmpty()) {
                    // if database is empty, populate it with YAML data
                    plugin.getLogger().info("Database is empty");                    
                    announceManager.setAnnouncements(ymlAnnouncements);
                    plugin.getLogger().info("Loading " + ymlAnnouncements.size() + " announcements from file");
                    announceTypes = ymlTypes;
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                } else {
                    plugin.getLogger().info("Database is not empty");

                    //if YAML data is empty, load data from the database into memory first
                    if (ymlAnnouncements.isEmpty() || ymlTypes.isEmpty()) {                        
                        
                        plugin.getLogger().warning("YAML data is empty. Skipping import");
                        // Load data from the database into memory first                        
                        List<Announcement> announcements = loadDataFromDatabase();
                        announceManager.setAnnouncements(announcements);
                        isChanged = true;

                    //if YML data is not empty, perform import
                    } else {                        
                        List<Announcement> announcements = loadDataFromDatabase();                    
                        // Load data from the database into memory first
                        announceManager.setAnnouncements(announcements);
                        announceManager.setAnnouncementsImported();

                        // Perform YML imports after database data is loaded
                        isChanged = importYML(ymlAnnouncements, ymlTypes);
                    }                    

                }
                if (isChanged) {
                    saveConfig();
                    plugin.getLogger().info("Saved announcements after import changes");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
                e.printStackTrace();

                // Fallback to YAML data if database fails
                announceManager.setAnnouncements(ymlAnnouncements);
                announceTypes = ymlTypes;
            } finally {
                dataStore.disconnect();
            }
        } else {
            // If using YAML storage, set the data directly
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
        }
    }

    private boolean importYML(List<Announcement> ymlAnnouncements, Map<String, AnnounceType> ymlTypes) {
        boolean needsSaving = false;
        
        // Load existing data for comparison
        List<Announcement> existingAnnouncements = dataStore.loadAnnouncements();
        List<AnnounceType> existingTypes = dataStore.loadAnnounceTypes();

        // Create lookup sets
        Set<String> existingAnnouncementIds = new HashSet<>();
        for (Announcement announcement : existingAnnouncements) {
            existingAnnouncementIds.add(announcement.getId().toLowerCase());
        }

        Set<String> existingTypeIds = new HashSet<>();
        for (AnnounceType type : existingTypes) {
            existingTypeIds.add(type.getId().toLowerCase());
        }

        // Import announce types
        for (AnnounceType type : ymlTypes.values()) {
            if (!existingTypeIds.contains(type.getId().toLowerCase())) {
                try {                    
                    dataStore.saveAnnounceType(type);
                    plugin.getLogger().info("Imported announce type: " + type.getId());                    
                } catch (Exception e) {
                    plugin.getLogger().warning("Error saving announce type: " + type.getId());
                    e.printStackTrace();
                }
            }
        }

        // Import announcements
        for (Announcement ymlAnnouncement : ymlAnnouncements) {
            boolean exists = existingAnnouncementIds.contains(ymlAnnouncement.getId().toLowerCase());
            
            // Only try to import if not already imported
            if (!exists && !ymlAnnouncement.isImported()) {                
                // output message to console
                try {
                    //dataStore.saveAnnouncement(ymlAnnouncement);    
                    announceManager.addAnnouncement(ymlAnnouncement);            
                    announceManager.setAnnouncementImported(ymlAnnouncement.getId());
                    needsSaving = true;                    
                    plugin.getLogger().info("Imported announcement: " + ymlAnnouncement.getId() + announceManager.isAnnouncementImported(ymlAnnouncement.getId()));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error importing announcement " + ymlAnnouncement.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } 
            // Mark as imported if exists but not already marked
            else if (exists && !ymlAnnouncement.isImported()) {
                announceManager.setAnnouncementImported(ymlAnnouncement.getId());
                needsSaving = true;
                plugin.getLogger().info("Marked existing announcement as imported: " + ymlAnnouncement.getId());
            }
        }

        return needsSaving;
    }

    // Reads and parses announcement data from YAML configuration
    private List<Announcement> loadDataFromYAML() {
        List<Announcement> announcements = new ArrayList<>();
        if (configFile.exists()) {
            List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
            for (Map<?, ?> map : announcementMaps) {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null) {
                    announcements.add(announcement);
                }
            }
        }
        return announcements;
    }

    // Reads and parses announcement types from YAML configuration
    private Map<String, AnnounceType> loadTypesFromYAML() {
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

    // Creates appropriate DataStore instance based on configuration settings
    private void initializeDataStoreConnection() {
        if (storageType.equalsIgnoreCase("mysql")) {
            String host = config.getString("storage.mysql.host");
            int port = config.getInt("storage.mysql.port");
            String database = config.getString("storage.mysql.database");
            String username = config.getString("storage.mysql.username");
            String password = config.getString("storage.mysql.password");
            boolean useSSL = config.getBoolean("storage.mysql.useSSL", false);
            dataStore = new MySQLDataConnector(host, port, database, username, password, useSSL);
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            String databasePath = config.getString("storage.sqlite.database", "announcements.db");
            dataStore = new SQLiteDataConnector(plugin, databasePath);
        } else {
            dataStore = null;
        }
    }

    // Retrieves announcement data from database and stores it in memory
    private List<Announcement> loadDataFromDatabase() {
        // Load announce types from database
        List<AnnounceType> dbTypes = dataStore.loadAnnounceTypes();
        announceTypes = new HashMap<>();
        for (AnnounceType type : dbTypes) {
            announceTypes.put(type.getId(), type);
        }

        // Load announcements from database
        return dataStore.loadAnnouncements();
        //announceManager.setAnnouncements(dbAnnouncements);
    }

    // Loads or creates the YAML configuration file
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            plugin.getLogger().info("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);        
        storageType = config.getString("storage.type", "yml");
    }

    // Parses an announcement object, adding to local memory and data store
    public boolean parseAnnouncement(Announcement announcement) {        
        return announceManager.addAnnouncement(announcement);
    }

    // Creates a new announcement from player input with owner information
    public boolean parseAnnouncement(String id, String type, String text, String playerName) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setText(text);
        announcement.setOwner(playerName);
        return announceManager.addAnnouncement(announcement);
    }

    // Creates a new announcement from console input without owner information
    public boolean parseAnnouncement(String id, String type, String text) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setText(text);        
        return announceManager.addAnnouncement(announcement);
    }

    // Converts a YAML map into an Announcement object
    private Announcement parseAnnouncement(Map<?, ?> map) {
        String id = (String) map.get("id");
        String text = (String) map.get("text");
        String type = (String) map.get("type");
        String recurrence = (String) map.get("recurrence");
        String owner = (String) map.get("owner");
        String permission = (String) map.get("permission");
        String dateStr = (String) map.get("date");
        String timeStr = (String) map.get("time");
        boolean imported = map.containsKey("imported") ? (Boolean) map.get("imported") : false;

        // Check if PlaceholderAPI is used and available
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
        if (imported) announcement.setImported();

        return announcement;
    }

    // Converts a YAML map into an AnnounceType object
    private AnnounceType parseAnnounceType(Map<?, ?> map) {
        String id = (String) map.get("id");
        String prefix = (String) map.get("prefix");
        String suffix = (String) map.get("suffix");
        Double listingFee = map.get("list_fee") == null ? null : (map.get("list_fee") instanceof Integer ? ((Integer) map.get("list_fee")).doubleValue() : (Double) map.get("list_fee"));
        String permission = (String) map.get("permission");
        boolean imported = map.containsKey("imported") ? (Boolean) map.get("imported") : false;

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

    // Loads player preferences for disabled announcement types
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

    // Persists player preferences for disabled announcement types
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

    // Refreshes configuration and player preferences from disk
    public void reloadConfig() {
        loadConfig();
        loadPlayerDisabledTypes();
    }

    // Persists announcements and announcement types to YAML configuration
    public void saveConfig() {
        // Create a list to hold the announcements data
        List<Map<String, Object>> announcementMaps = new ArrayList<>();

        plugin.getLogger().info("Saving " + announceManager.getAnnouncements().size() + " announcements to file");

        for (Announcement announcement : announceManager.getAnnouncements()) {
            // Ensure required fields are not null
            if (announcement.getId() == null || announcement.getType() == null || announcement.getText() == null) {
                plugin.getLogger().warning("Skipping announcement due to missing required fields: " + announcement.getId());
                continue;
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", announcement.getId());
            map.put("text", announcement.getText());
            map.put("type", announcement.getType());

            // Add optional fields only if they are not null
            if (announcement.getRecurrence() != null) {
                map.put("recurrence", announcement.getRecurrence());
            }
            if (announcement.getOwner() != null) {
                map.put("owner", announcement.getOwner());
            }
            if (announcement.getPermission() != null) {
                map.put("permission", announcement.getPermission());
            }
            if (announcement.getDate() != null) {
                map.put("date", announcement.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
            if (announcement.getTime() != null) {
                map.put("time", announcement.getTime().format(DateTimeFormatter.ofPattern("HHmm")));
            }
            
            // Add the imported flag
            map.put("imported", announcement.isImported());

            announcementMaps.add(map);
        }

        // Check if announcementMaps is empty
        if (announcementMaps.isEmpty()) {
            plugin.getLogger().warning("Announcements list is empty. Not saving announcements to config file.");
            return; // Do not save empty announcements
        }

        // Set the 'announcements' section in the config
        config.set("announcements", announcementMaps);
        
        // Create a list to hold the announce types data
        List<Map<String, Object>> announceTypeMaps = new ArrayList<>();
        
        for (AnnounceType type : announceTypes.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", type.getId());
            map.put("prefix", type.getPrefix());
            map.put("suffix", type.getSuffix());
            if (type.getListingFee() != null) {
                map.put("list_fee", type.getListingFee());
            }
            map.put("permission", type.getPermission());            
            announceTypeMaps.add(map);
        }

        config.set("announce_types", announceTypeMaps);

        try {
            config.save(configFile);            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save announcements to file: " + e.getMessage());
        }
    }

    // Returns the map of player-disabled announcement types
    public Map<UUID, Set<String>> getPlayerDisabledTypes() {
        return playerDisabledTypes;
    }

    // Returns the map of available announcement types
    public Map<String, AnnounceType> getAnnounceTypes() {
        return announceTypes;
    }
    
    // Returns the current data storage implementation
    public DataStore getDataStore() {
        return dataStore;
    }
}