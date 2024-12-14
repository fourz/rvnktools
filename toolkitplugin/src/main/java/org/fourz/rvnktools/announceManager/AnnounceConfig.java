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
    private enum ConfigOperation {
        IMPORT_YML_TO_NEW_DB,
        UPDATE_MISSING_YML_FROM_DB,
        MERGE_YML_INTO_DB,
        FALLBACK_TO_YML,
        NO_UPDATE
    }

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private Map<String, AnnounceType> announceTypes;
    boolean usingPlaceholderAPI;
    private final AnnounceManager announceManager;
    private DataStore dataStore;
    private String storageType;
    private ConfigOperation configOperation;
    private List<Announcement> ymlAnnouncements;
    private Map<String, AnnounceType> ymlTypes;
    private AnnouncePreferences preferences;

    // initializes the configuration and data storage settings
    public AnnounceConfig(JavaPlugin plugin, AnnounceManager announceManager) {
        // Store dependencies
        this.plugin = plugin;
        this.announceManager = announceManager;
        
        // Initialize core components
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        
        // Load configuration and determine operation mode
        boolean configLoaded = loadConfig();
        this.configOperation = configLoaded ? detectConfigState() : ConfigOperation.FALLBACK_TO_YML;
        
        // Log the determined configuration operation
        plugin.getLogger().info("AnnounceConfig Operation: " + configOperation);
    }

    private ConfigOperation detectConfigState() {
        plugin.getLogger().info("Detecting config state...");
        
        initializeDataStoreConnection();
        
        if (dataStore == null) {
            plugin.getLogger().warning("Database connection failed - dataStore is null");
            return ConfigOperation.FALLBACK_TO_YML;
        }

        // Connect to database and check if it is empty
        try {
            plugin.getLogger().info("Attempting database connection...");
            dataStore.connect();
            plugin.getLogger().info("Database connection successful");
            
            boolean dbEmpty = dataStore.isEmpty();
            plugin.getLogger().info("Database empty check result: " + dbEmpty);
            
            // Check if database and YAML configurations match
            if (!dbEmpty && dbMatchesYml()) {
                plugin.getLogger().info("Database matches YAML content");
                return ConfigOperation.NO_UPDATE;
            }
            
            dataStore.disconnect();

            if (dbEmpty) {
                plugin.getLogger().info("Empty database detected - will import YAML");
                return ConfigOperation.IMPORT_YML_TO_NEW_DB;

            } else if (!dbEmpty && (ymlAnnouncements.isEmpty() || ymlTypes.isEmpty())) {
                return ConfigOperation.UPDATE_MISSING_YML_FROM_DB;

            } else if (!dbEmpty && !ymlAnnouncements.isEmpty() && !ymlTypes.isEmpty()) {
                //output a warning message
                plugin.getLogger().warning("Database and YAML configurations do not match. Merging YAML data into database.");

                return ConfigOperation.MERGE_YML_INTO_DB;
            }

            //output a warning message
            plugin.getLogger().warning("Unable to determine configuration state. Using YAML configurations.");
            return ConfigOperation.FALLBACK_TO_YML;
        } catch (Exception e) {
            plugin.getLogger().severe("Database connection/check failed: " + e.getMessage());
            e.printStackTrace();
            return ConfigOperation.FALLBACK_TO_YML;
        }
    }
    
    private boolean dbMatchesYml() {
            Integer ymlHash = generateConfigHash(ymlAnnouncements, ymlTypes);
            Integer dbHash = generateConfigHash(dataStore.loadAnnouncements(), dataStore.loadAnnounceTypes());
            
            if (ymlHash.equals(dbHash)) {
                plugin.getLogger().info("Database and YAML configurations match (Hash: " + ymlHash + ")");
                dataStore.disconnect();
                return true;
            }
            return false;
    }

    // Generates a hash of the configuration data for comparison
    private Integer generateConfigHash(List<Announcement> announcements, Collection<AnnounceType> types) {
        List<String> elements = new ArrayList<>();
        
        // Add sorted announcement entries
        for (Announcement a : announcements) {
            elements.add(String.format("%s:%s:%s:%s",
                a.getId().toLowerCase(),
                a.getType().toLowerCase(),
                a.getText(),
                a.getPermission() != null ? a.getPermission() : ""));
        }
        
        // Add sorted type entries
        for (AnnounceType t : types) {
            elements.add(String.format("%s:%s:%s:%s:%s",
                t.getId().toLowerCase(),
                t.getPrefix() != null ? t.getPrefix() : "",
                t.getSuffix() != null ? t.getSuffix() : "",
                t.getPermission() != null ? t.getPermission() : "",
                t.getListingFee() != null ? t.getListingFee().toString() : ""));
        }
        
        // Sort elements for consistent ordering
        Collections.sort(elements);

        //generate hash of concatenated elements into a string
        return String.join("|", elements).hashCode();
    }

    // Overloaded method to generate hash of configuration data
    private Integer generateConfigHash(List<Announcement> announcements, Map<String, AnnounceType> types) {
        return generateConfigHash(announcements, types.values());
    }

    // Loads or creates the YAML configuration file
    public boolean loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            plugin.getLogger().info("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);        
        storageType = config.getString("storage.type", "yml");

        // Load data from YAML
        this.ymlAnnouncements = loadDataFromYAML();
        this.ymlTypes = loadTypesFromYAML();

        // return success if data is loaded
        return !ymlAnnouncements.isEmpty() && !ymlTypes.isEmpty();
    }

    // Sets up data storage and handles initial data migration from YAML to database if needed
    public void initializeDataStore() {
        plugin.getLogger().info("AnnounceConfig using " + storageType + " storage");

        // Initialize DataStore if not already done
        if (dataStore == null) {
            initializeDataStoreConnection();
        }

        // Connect to the database once during initialization
        if (dataStore != null) {
            dataStore.connect();
        }

        // Initialize preferences after dataStore is initialized
        this.preferences = new AnnouncePreferences(plugin, dataStore);
        preferences.loadPreferences();

        // If not using database storage, use YAML data directly
        if (dataStore == null) {
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
            return;
        }

        boolean isChanged = false;

        try {
            switch (configOperation) {

                case NO_UPDATE:
                case FALLBACK_TO_YML:
                    announceManager.setAnnouncements(ymlAnnouncements);
                    announceTypes = ymlTypes;
                    break;

                case IMPORT_YML_TO_NEW_DB:
                    announceTypes = ymlTypes;
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                    if (isChanged) {
                        announceManager.setAnnouncements(dataStore.loadAnnouncements());
                        announceManager.setAnnouncementsImported();
                    }
                    break;

                case UPDATE_MISSING_YML_FROM_DB:
                    List<Announcement> announcements = loadDataFromDatabase();
                    announceManager.setAnnouncements(announcements);
                    isChanged = true;
                    break;

                case MERGE_YML_INTO_DB:
                    List<Announcement> announcements_ = loadDataFromDatabase();
                    announceManager.setAnnouncements(announcements_);
                    announceManager.setAnnouncementsImported();
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                    break;
            }

            if (isChanged) {
                saveConfig();
                plugin.getLogger().info("Saved announcements after import changes");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            // Fallback to YAML data on database error
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
        }
    }

    private boolean importYML(List<Announcement> ymlAnnouncements, Map<String, AnnounceType> ymlTypes) {
        boolean needsSaving = false;

        List<Announcement> existingAnnouncements = dataStore.loadAnnouncements();
        List<AnnounceType> existingTypes = dataStore.loadAnnounceTypes();

        Set<String> existingAnnouncementIds = new HashSet<>();
        for (Announcement announcement : existingAnnouncements) {
            existingAnnouncementIds.add(announcement.getId().toLowerCase());
        }

        Set<String> existingTypeIds = new HashSet<>();
        for (AnnounceType type : existingTypes) {
            existingTypeIds.add(type.getId().toLowerCase());
        }

        // Import types first
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

        // Track successfully imported announcements
        Set<String> successfulImports = new HashSet<>();
        
        // Process announcements that need importing
        for (Announcement announcement : ymlAnnouncements) {
            boolean exists = existingAnnouncementIds.contains(announcement.getId().toLowerCase());
            // Remove isImported() check since we want to import all announcements from YML
            if (!exists) {
                try {
                    announceManager.addAnnouncement(announcement);
                    successfulImports.add(announcement.getId().toLowerCase());              
                    plugin.getLogger().info("Imported announcement: " + announcement.getId());
                } catch (Exception e) {
                    plugin.getLogger().warning("Error importing announcement " + announcement.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }            
        }

        // Only mark announcements that were successfully imported
        if (!successfulImports.isEmpty()) {
            for (Announcement announcement : ymlAnnouncements) {
                if (successfulImports.contains(announcement.getId().toLowerCase())) {                   
                    announcement.setImported(); // Set the imported flag on ymlAnnouncements
                    needsSaving = true;
                }
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
        } else {
            plugin.getLogger().warning("Config file not found: " + configFile.getName());
        }
        return types;
    }

    // Creates appropriate DataStore instance based on configuration settings
    private void initializeDataStoreConnection() {
        plugin.getLogger().info("Initializing data store connection with type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            String host = config.getString("storage.mysql.host", "");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "");
            String username = config.getString("storage.mysql.username", "");
            String password = config.getString("storage.mysql.password", "");
            boolean useSSL = config.getBoolean("storage.mysql.useSSL", false);
            
            plugin.getLogger().info("MySQL configuration - Host: " + host + ", Port: " + port + 
                ", Database: " + database + ", Username: " + username + 
                ", SSL: " + useSSL);
            
            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                plugin.getLogger().severe("Invalid MySQL configuration - missing required fields");
                dataStore = null;
                return;
            }
            
            dataStore = new MySQLDataConnector(host, port, database, username, password, useSSL);
            plugin.getLogger().info("MySQL connector created successfully");
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            String databasePath = config.getString("storage.sqlite.database", "announcements.db");
            dataStore = new SQLiteDataConnector(plugin, databasePath);
        } else {
            dataStore = null;
        }
        
        if (dataStore == null) {
            plugin.getLogger().warning("Failed to initialize data store - unknown or invalid storage type");
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
        Object recurrence = map.get("recurrence");
        Long recurrenceSeconds = null;
        
        if (recurrence != null) {
            if (recurrence instanceof Long) {
                recurrenceSeconds = (Long) recurrence;
            } else if (recurrence instanceof String) {
                // Convert string format to seconds
                String recStr = (String) recurrence;
                try {
                    if (recStr.endsWith("s")) {
                        recurrenceSeconds = Long.parseLong(recStr.substring(0, recStr.length() - 1));
                    } else if (recStr.endsWith("m")) {
                        recurrenceSeconds = Long.parseLong(recStr.substring(0, recStr.length() - 1)) * 60;
                    } else if (recStr.endsWith("h")) {
                        recurrenceSeconds = Long.parseLong(recStr.substring(0, recStr.length() - 1)) * 3600;
                    } else if (recStr.endsWith("d")) {
                        recurrenceSeconds = Long.parseLong(recStr.substring(0, recStr.length() - 1)) * 86400;
                    } else {
                        recurrenceSeconds = Long.parseLong(recStr);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid recurrence format for announcement " + id + ": " + recStr);
                }
            }
        }
        
        String owner = (String) map.get("owner");
        String permission = (String) map.get("permission");
        String dateStr = (String) map.get("date");
        String timeStr = (String) map.get("time");
        // Default imported to false if not present in YAML
        boolean imported = map.containsKey("imported") && (Boolean) map.get("imported");

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
        announcement.setRecurrence(recurrenceSeconds);
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

    // Persists player preferences for disabled announcement types
    public void savePlayerDisabledTypes() {
        preferences.savePreferences();
    }

    // Refreshes configuration and player preferences from disk
    public void reloadConfig() {
        loadConfig();
        //loadPlayerDisabledTypes();
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
        return preferences.getAllDisabledTypes();
    }

    // Returns the map of available announcement types
    public Map<String, AnnounceType> getAnnounceTypes() {
        return announceTypes;
    }
    
    // Returns the current data storage implementation
    public DataStore getDataStore() {
        return dataStore;
    }

    public void addPlayerDisabledType(UUID playerId, String type) {
        preferences.addDisabledType(playerId, type);
    }

    public void removePlayerDisabledType(UUID playerId, String type) {
        preferences.removeDisabledType(playerId, type);
    }

    // Add a shutdown method to disconnect the DataStore
    public void shutdown() {
        if (dataStore != null) {
            dataStore.disconnect();
        }
    }
}