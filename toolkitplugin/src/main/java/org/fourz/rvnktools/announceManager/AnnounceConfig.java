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
import java.util.logging.Level;
import org.fourz.rvnktools.util.Debug;
import java.util.stream.Collectors;

public class AnnounceConfig {
    private static final String CLASS_NAME = "AnnounceConfig";
    private static Level logLevel = Level.INFO; // Default level
    
    public static Level getLogLevel() {
        return logLevel;
    }
    private enum ConfigOperation {        
        DB_NO_UPDATE,
        DB_MERGE_YML,        
        DB_IMPORT,
        YML_ONLY,  
        YML_FALLBACK,
        DB_REBUILD_YML
    }

    private class ConfigDebug extends Debug {
        public ConfigDebug(JavaPlugin plugin, String className) {
            super(plugin, className, logLevel);
        }
    }

    private final ConfigDebug debug;
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

        this.plugin = plugin;
        this.announceManager = announceManager;
        
        // Initialize core components
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        
        // Load config and set log level, default to INFO.
        config = YamlConfiguration.loadConfiguration(configFile);       
        logLevel = Debug.getLevel(config.getString("debug.level", "INFO")); 
        this.debug = new ConfigDebug(plugin, CLASS_NAME);
        
        //Load the configuration
        if (loadConfig()) debug.log(Level.INFO, "Configuration loaded successfully");
        
        // Detect and initialize data storage
        this.configOperation = detectConfigState();
        debug.log("AnnounceConfig Operation: " + configOperation);
    }

    private ConfigOperation detectConfigState() {
        debug.log("Detecting config state...");

        // check if storage type is set to yml or flatfile
        if (storageType.equalsIgnoreCase("flatfile") || storageType.equalsIgnoreCase("yml")) {
            debug.log("YAML storage explicitly configured");
            return ConfigOperation.YML_ONLY;
        }

        // check for database configurations
        if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("sqlite")) {
            initializeDataStoreConnection();
            
            if (dataStore != null) {
                try {
                    debug.debug("Testing database connectivity"); // Moved to debug level
                    dataStore.connect();
                                        
                    try {
                        boolean dbEmpty = dataStore.isEmpty();
                        debug.debug("Database empty check result: " + dbEmpty);
                        
                        if (dbEmpty) {
                            debug.log("Empty database detected - will import from YAML");
                            return ConfigOperation.DB_IMPORT;
                        }
                        
                        if (dbMatchesYml()) {
                            debug.debug("Database matches YAML configuration"); // Moved to debug level
                            return ConfigOperation.DB_NO_UPDATE;
                        } else {
                            debug.log("Database differs from YAML - will merge");
                            return ConfigOperation.DB_MERGE_YML;
                        }
                        
                    } catch (Exception e) {
                        debug.error("Database access test failed", e);
                        debug.log("Falling back to YAML storage");
                        return ConfigOperation.YML_FALLBACK;
                    }
                    
                } catch (Exception e) {
                    debug.error("Database connectivity test failed", e);
                    debug.log("Falling back to YAML storage");
                    return ConfigOperation.YML_FALLBACK;
                }
            }
        }
        
        debug.log(Level.WARNING, "No database configuration found - using YAML storage");
        return ConfigOperation.YML_ONLY;
    }

    public void initializeDataStore() {
        debug.log("AnnounceConfig using " + storageType + " storage");

        // Initialize DataStore if not already done
        if (dataStore == null) {
            initializeDataStoreConnection();
        }
        
        // Add memory check
        debug.debug("Memory state before initialization - YML: " + 
            (ymlAnnouncements != null ? ymlAnnouncements.size() : 0) + " announcements");

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
                case YML_FALLBACK:
                case YML_ONLY:
                    debug.log("Using existing announcements from local configuration");
                case DB_NO_UPDATE:                
                    announceManager.setAnnouncements(ymlAnnouncements);
                    announceTypes = ymlTypes;                    
                    break;

                case DB_IMPORT:
                    announceTypes = ymlTypes;
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                    if (isChanged) {
                        announceManager.setAnnouncements(dataStore.loadAnnouncements());
                        announceManager.setAnnouncementsImported();
                    }
                    break;

                case DB_REBUILD_YML:
                    List<Announcement> announcements = loadDataFromDatabase();
                    announceManager.setAnnouncements(announcements);
                    isChanged = true;
                    break;

                case DB_MERGE_YML:
                    List<Announcement> announcements_ = loadDataFromDatabase();
                    announceManager.setAnnouncements(announcements_);
                    announceManager.setAnnouncementsImported();
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                    break;
            }

        } catch (Exception e) {
            debug.error("Failed to initialize database: " + e.getMessage(), e);
            e.printStackTrace();
            // Fallback to YAML data on database error
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
        }
    }

    private boolean dbMatchesYml() {
        String ymlHash = generateIdsHash(ymlAnnouncements.stream()
            .map(a -> a.getId().toLowerCase())
            .collect(Collectors.toSet()));
        
        if (dataStore instanceof MySQLDataConnector) {
            MySQLDataConnector mysqlStore = (MySQLDataConnector) dataStore;
            String dbHash = mysqlStore.calculateDatabaseHash();
            
            debug.debug("Configuration hash comparison - YAML=" + ymlHash + " DB=" + dbHash); // Moved to debug level
            
            if (ymlHash != null && dbHash != null && ymlHash.equals(dbHash)) {
                debug.debug("Loading announcements from local configuration"); // Moved to debug level
                dataStore.disconnect();
                return true;
            } else {
                debug.log("Configuration mismatch - will synchronize with database");
            }
        }
        return false;
    }

    private String generateIdsHash(Set<String> ids) {
        if (ids.isEmpty()) return null;
        
        StringBuilder hashBuilder = new StringBuilder();
        debug.debug("Processing " + ids.size() + " announcements for hash generation");
        
        ids.stream()
            .sorted()
            .forEach(id -> hashBuilder.append(id).append("\n"));
            
        String hashStr = hashBuilder.toString();
        return org.apache.commons.codec.digest.DigestUtils.md5Hex(hashStr);
    }

    // Loads or creates the YAML configuration file
    public boolean loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            debug.log("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        storageType = config.getString("storage.type", "yml").toLowerCase();
        
        // Consolidate loading messages into a single INFO message
        this.ymlAnnouncements = loadDataFromYAML();
        this.ymlTypes = loadTypesFromYAML();
        
        debug.log(String.format("Loaded configuration: %d announcements, %d types, storage: %s", 
            ymlAnnouncements.size(), ymlTypes.size(), storageType));

        return !ymlAnnouncements.isEmpty() || !ymlTypes.isEmpty();
    }

    private boolean importYML(List<Announcement> ymlAnnouncements, Map<String, AnnounceType> ymlTypes) {
        boolean needsSaving = false;

        List<Announcement> existingAnnouncements = dataStore.loadAnnouncements();
        List<AnnounceType> existingTypes = dataStore.loadAnnounceTypes();

        Set<String> existingAnnouncementIds = existingAnnouncements.stream()
            .map(a -> a.getId().toLowerCase())
            .collect(Collectors.toSet());

        Set<String> existingTypeIds = new HashSet<>();
        for (AnnounceType type : existingTypes) {
            existingTypeIds.add(type.getId().toLowerCase());
        }

        // Import types first
        for (AnnounceType type : ymlTypes.values()) {
            if (!existingTypeIds.contains(type.getId().toLowerCase())) {
                try {                    
                    dataStore.saveAnnounceType(type);                    
                    debug.log("Imported announce type: " + type.getId());                    
                } catch (Exception e) {
                    debug.log(Level.WARNING, "Error saving announce type: " + type.getId());
                    e.printStackTrace();
                }
            }
        }

        // Track successfully imported announcements
        Set<String> successfulImports = new HashSet<>();
        
        // Process announcements that need importing
        for (Announcement announcement : ymlAnnouncements) {
            if (!existingAnnouncementIds.contains(announcement.getId().toLowerCase())) {
                try {
                    dataStore.saveAnnouncement(announcement); 
                    announcement.setImported(); // Mark as imported immediately
                    successfulImports.add(announcement.getId().toLowerCase());
                    announceManager.getAnnouncements().add(announcement); // Add to memory
                    debug.log("Imported announcement: " + announcement.getId());
                } catch (Exception e) {
                    debug.log(Level.WARNING, "Error importing announcement " + announcement.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }            
        }

        // Update the local announcements list after import
        if (!successfulImports.isEmpty()) {
            announceManager.setAnnouncements(dataStore.loadAnnouncements());
            debug.log("Updated in-memory announcements after import");
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

    // Updated YAML loading with better error handling
    private List<Announcement> loadDataFromYAML() {
        List<Announcement> announcements = new ArrayList<>();
        if (!configFile.exists()) {
            debug.log(Level.WARNING, "Config file does not exist: " + configFile.getName());
            return announcements;
        }

        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
        Set<String> usedIds = new HashSet<>();
        
        for (Map<?, ?> map : announcementMaps) {
            try {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null && announcement.getId() != null) {
                    if (usedIds.contains(announcement.getId().toLowerCase())) {
                        debug.log(Level.WARNING, "Duplicate announcement ID found: " + announcement.getId());
                        continue;
                    }
                    usedIds.add(announcement.getId().toLowerCase());
                    announcements.add(announcement);
                }
            } catch (Exception e) {
                debug.log(Level.WARNING, "Error parsing announcement: " + e.getMessage());
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
            debug.log(Level.WARNING, "Config file not found: " + configFile.getName());
        }
        return types;
    }

    // Creates appropriate DataStore instance based on configuration settings
    private void initializeDataStoreConnection() {
        debug.log("Initializing data store connection with type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            String host = config.getString("storage.mysql.host", "");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "");
            String username = config.getString("storage.mysql.username", "");
            String password = config.getString("storage.mysql.password", "");
            boolean useSSL = config.getBoolean("storage.mysql.useSSL", false);
            
            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                debug.log(Level.SEVERE, "Invalid MySQL configuration - missing required fields");
                dataStore = null;
                return;
            }
            
            debug.debug("Configuring MySQL connection for " + host + ":" + port); // Moved to debug level
            dataStore = new MySQLDataConnector(plugin, host, port, database, username, password, useSSL);
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            String databasePath = config.getString("storage.sqlite.database", "announcements.db");
            dataStore = new SQLiteDataConnector(plugin, databasePath);
        } else {
            dataStore = null;
        }
        
        if (dataStore == null) {
            debug.log(Level.WARNING, "Using YAML storage - no valid database configuration");
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

        // Convert list to map
        List<Announcement> announcementList = new ArrayList<>();
        List<Announcement> dbAnnouncements = dataStore.loadAnnouncements();
        for (Announcement announcement : dbAnnouncements) {
            announcement.setImported(); // Mark as imported when loading from DB
            announcementList.add(announcement);
        }
        return announcementList;
    }

    // Creates a new announcement from player input with owner information
    public boolean parseAnnouncement(String id, String type, String text, String playerName) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setMessage(text);
        announcement.setOwner(playerName);
        return announceManager.addAnnouncement(announcement);
    }

    // Creates a new announcement from console input without owner information
    public boolean parseAnnouncement(String id, String type, String text) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setMessage(text);        
        return announceManager.addAnnouncement(announcement);
    }

    // Converts a YAML map into an Announcement object
    private Announcement parseAnnouncement(Map<?, ?> map) {
        String id = (String) map.get("id");
        String text = (String) map.get("text");
        String type = (String) map.get("type");
        Object recurrence = map.get("recurrence");
        Long recurrenceSeconds = null;
        String originalRecurrence = null;
        
        if (recurrence != null) {
            originalRecurrence = recurrence.toString();
            if (recurrence instanceof Long) {
                recurrenceSeconds = (Long) recurrence;
            } else if (recurrence instanceof String) {
                String recStr = (String) recurrence;
                try {
                    if (recStr.equalsIgnoreCase("none")) {
                        recurrenceSeconds = null;
                    } else if (recStr.equalsIgnoreCase("daily")) {
                        recurrenceSeconds = 86400L;
                    } else if (recStr.endsWith("s")) {
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
                    debug.log(Level.WARNING, "Invalid recurrence format for announcement " + id + ": " + recStr);
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
            debug.log(Level.WARNING, "PlaceholderAPI not found, unable to parse placeholders in announcement: " + id);
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
        announcement.setMessage(text);
        announcement.setType(type);
        announcement.setRecurrence(recurrenceSeconds);
        announcement.setRecurrenceString(originalRecurrence); // Store original string
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
        List<Map<String, Object>> announcementMaps = new ArrayList<>();

        // Get a snapshot of announcements to avoid concurrent modification
        Collection<Announcement> currentAnnouncements = new ArrayList<>(announceManager.getAnnouncements());
        debug.log("Saving " + currentAnnouncements.size() + " announcements to file");
        int importedCount = 0;

        for (Announcement announcement : currentAnnouncements) {
            try {
                // Validate required fields
                if (announcement.getId() == null || announcement.getType() == null || announcement.getMessage() == null) {
                    debug.log(Level.WARNING, "Skipping invalid announcement: " + announcement.getId());
                    continue;
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", announcement.getId());
                map.put("text", announcement.getMessage());
                map.put("type", announcement.getType());

                // Handle recurrence value
                Long recurrence = announcement.getRecurrence();
                if (recurrence != null) {
                    // If we have the original string format, use that
                    if (announcement.getRecurrenceString() != null) {
                        map.put("recurrence", announcement.getRecurrenceString());
                    } else {
                        // Convert seconds back to a readable format
                        if (recurrence % 86400 == 0) {
                            map.put("recurrence", (recurrence / 86400) + "d");
                        } else if (recurrence % 3600 == 0) {
                            map.put("recurrence", (recurrence / 3600) + "h");
                        } else if (recurrence % 60 == 0) {
                            map.put("recurrence", (recurrence / 60) + "m");
                        } else {
                            map.put("recurrence", recurrence + "s");
                        }
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
                
                // Ensure imported status is correctly tracked
                boolean isImported = announcement.isImported();
                map.put("imported", isImported);
                if (isImported) importedCount++;
                
                announcementMaps.add(map);
            } catch (Exception e) {
                debug.error("Error saving announcement " + announcement.getId() + ": " + e.getMessage(), e);
            }
        }

        debug.log("Saving " + announcementMaps.size() + " announcements (" + importedCount + " imported)");

        try {
            config.set("announcements", announcementMaps);
            config.save(configFile);
            debug.log("Configuration saved successfully");
        } catch (IOException e) {
            debug.error("Failed to save announcements: " + e.getMessage(), e);
            e.printStackTrace();
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
            saveConfig(); // Save any pending changes
            dataStore.disconnect();
        }
    }
}