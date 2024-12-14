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
import org.fourz.rvnktools.util.Debug;
import org.fourz.rvnktools.util.Debug.LogLevel;
import java.util.stream.Collectors;

public class AnnounceConfig {
    private static final String CLASS_NAME = "AnnounceConfig";
    private enum ConfigOperation {
        IMPORT_YML_TO_NEW_DB,
        UPDATE_MISSING_YML_FROM_DB,
        MERGE_YML_INTO_DB,
        FALLBACK_TO_YML,
        NO_UPDATE
    }

    private class ConfigDebug extends Debug {
        public ConfigDebug(JavaPlugin plugin, String className, LogLevel level) {
            super(plugin, className, level);
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
        // Store dependencies
        this.plugin = plugin;
        this.announceManager = announceManager;
        
        // Initialize core components
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        
        // Initialize debug with default level
        this.debug = new ConfigDebug(plugin, CLASS_NAME, Debug.LogLevel.INFO);
        
        // Load config and update debug level
        if (loadConfig()) {
            String logLevel = config.getString("debug.level", "INFO");
            debug.setLogLevel(Debug.LogLevel.fromString(logLevel));
        }
        
        // Rest of initialization
        this.configOperation = detectConfigState();
        debug.log("AnnounceConfig Operation: " + configOperation);
    }

    private ConfigOperation detectConfigState() {
        debug.log(LogLevel.CONFIG, "Detecting config state...");

        // Initialize database connection first 
        if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("sqlite")) {
            initializeDataStoreConnection();
            
            if (dataStore != null) {
                try {
                    debug.log(LogLevel.CONFIG, "Testing database connectivity and state...");
                    dataStore.connect();
                    
                    // Wait briefly for connection to stabilize
                    Thread.sleep(100);
                    
                    // Verify database access
                    boolean dbEmpty;
                    try {
                        dbEmpty = dataStore.isEmpty();
                        debug.log(LogLevel.FINE, "Database empty check result: " + dbEmpty);
                        
                        // Test data access
                        List<Announcement> testLoad = dataStore.loadAnnouncements();
                        List<AnnounceType> testTypes = dataStore.loadAnnounceTypes();
                        
                        debug.log(LogLevel.FINE, "Database access test - Announcements: " + testLoad.size() + 
                                              ", Types: " + testTypes.size());
                        
                        if (dbEmpty || testLoad.isEmpty() && testTypes.isEmpty()) {
                            debug.log(LogLevel.CONFIG, "Empty database detected - will import from YAML");
                            return ConfigOperation.IMPORT_YML_TO_NEW_DB;
                        }
                        
                        if (!testLoad.isEmpty() || !testTypes.isEmpty()) {
                            if (ymlAnnouncements.isEmpty() && ymlTypes.isEmpty()) {
                                debug.log(LogLevel.CONFIG, "Using existing database content");
                                return ConfigOperation.NO_UPDATE;
                            }
                            debug.log(LogLevel.CONFIG, "Will merge YAML content into database");
                            return ConfigOperation.MERGE_YML_INTO_DB;
                        }
                    } catch (Exception e) {
                        debug.error("Database access test failed: " + e.getMessage(), e);
                        throw e;
                    }
                    
                } catch (Exception e) {
                    debug.error("Database connectivity test failed", e);
                    e.printStackTrace();
                    return ConfigOperation.FALLBACK_TO_YML;
                }
            }
        }
        
        debug.log(LogLevel.CONFIG, "No valid database configuration - using YAML storage");
        return ConfigOperation.FALLBACK_TO_YML;
    }

    public void initializeDataStore() {
        debug.log(LogLevel.INFO, "AnnounceConfig using " + storageType + " storage");

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
                debug.log(LogLevel.INFO, "Saved announcements after import changes");
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
            Integer ymlHash = generateConfigHash(ymlAnnouncements, ymlTypes);
            Integer dbHash = generateConfigHash(dataStore.loadAnnouncements(), dataStore.loadAnnounceTypes());
            
            if (ymlHash.equals(dbHash)) {
                debug.log(LogLevel.INFO, "Database and YAML configurations match (Hash: " + ymlHash + ")");
                dataStore.disconnect();
                return true;
            }
            return false;
    }

    // Generates a hash of the configuration data for comparison
    private Integer generateConfigHash(List<Announcement> announcements, Collection<AnnounceType> types) {
        return generateConfigHash(
            announcements.stream()
                .collect(Collectors.toMap(Announcement::getId, a -> a)),
            types
        );
    }

    // Overloaded method to generate hash of configuration data
    private Integer generateConfigHash(List<Announcement> announcements, Map<String, AnnounceType> types) {
        return generateConfigHash(
            announcements.stream()
                .collect(Collectors.toMap(Announcement::getId, a -> a)),
            types.values()
        );
    }

    // Updated hash generation for Map structure
    private Integer generateConfigHash(Map<String, Announcement> announcements, Collection<AnnounceType> types) {
        StringBuilder hashBuilder = new StringBuilder();

        // Process announcements
        announcements.values().stream()
            .sorted(Comparator.comparing(a -> a.getId().toLowerCase()))
            .forEach(a -> hashBuilder.append(String.format("A|%s|%s|%s|%s\n",
                a.getId().toLowerCase(),
                a.getType().toLowerCase(),
                a.getText(),
                Optional.ofNullable(a.getPermission()).orElse(""))));

        // Process types
        types.stream()
            .sorted(Comparator.comparing(t -> t.getId().toLowerCase()))
            .forEach(t -> hashBuilder.append(String.format("T|%s|%s|%s|%s|%s\n",
                t.getId().toLowerCase(),
                Optional.ofNullable(t.getPrefix()).orElse(""),
                Optional.ofNullable(t.getSuffix()).orElse(""),
                Optional.ofNullable(t.getPermission()).orElse(""),
                Optional.ofNullable(t.getListingFee()).map(Object::toString).orElse(""))));

        return hashBuilder.toString().hashCode();
    }

    // Loads or creates the YAML configuration file
    public boolean loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            debug.log(LogLevel.INFO, "Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);        
        storageType = config.getString("storage.type", "yml");

        // Load data from YAML
        this.ymlAnnouncements = loadDataFromYAML();
        this.ymlTypes = loadTypesFromYAML();

        // return success if data is loaded
        return !ymlAnnouncements.isEmpty() && !ymlTypes.isEmpty();
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
                    debug.log(LogLevel.INFO, "Imported announce type: " + type.getId());                    
                } catch (Exception e) {
                    debug.log(LogLevel.WARNING, "Error saving announce type: " + type.getId());
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
                    debug.log(LogLevel.INFO, "Imported announcement: " + announcement.getId());
                } catch (Exception e) {
                    debug.log(LogLevel.WARNING, "Error importing announcement " + announcement.getId() + ": " + e.getMessage());
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

    // Updated import method for Map structure
    private boolean importYML(Map<String, Announcement> ymlAnnouncements, Map<String, AnnounceType> ymlTypes) {
        boolean needsSaving = false;
        Set<String> importedIds = new HashSet<>();

        // Import types first with validation
        for (AnnounceType type : ymlTypes.values()) {
            try {
                if (type.getId() == null) {
                    debug.log(LogLevel.WARNING, "Skipping announce type with null ID");
                    continue;
                }
                
                if (!dataStore.loadAnnounceTypes().stream()
                        .anyMatch(t -> t.getId().equalsIgnoreCase(type.getId()))) {
                    dataStore.saveAnnounceType(type);
                    debug.log(LogLevel.INFO, "Imported announce type: " + type.getId());
                }
            } catch (Exception e) {
                debug.log(LogLevel.WARNING, "Failed to import announce type " + type.getId() + ": " + e.getMessage());
            }
        }

        // Import announcements with validation
        for (Announcement announcement : ymlAnnouncements.values()) {
            try {
                if (validateAnnouncement(announcement) && 
                    !dataStore.announcementExists(announcement.getId())) {
                    
                    announceManager.addAnnouncement(announcement);
                    importedIds.add(announcement.getId().toLowerCase());
                    needsSaving = true;
                }
            } catch (Exception e) {
                debug.log(LogLevel.WARNING, "Failed to import announcement " + announcement.getId() + ": " + e.getMessage());
            }
        }

        // Mark successful imports
        if (!importedIds.isEmpty()) {
            ymlAnnouncements.values().stream()
                .filter(a -> importedIds.contains(a.getId().toLowerCase()))
                .forEach(Announcement::setImported);
        }

        return needsSaving;
    }

    // Updated YAML loading with better error handling
    private List<Announcement> loadDataFromYAML() {
        List<Announcement> announcements = new ArrayList<>();
        if (!configFile.exists()) {
            debug.log(LogLevel.WARNING, "Config file does not exist: " + configFile.getName());
            return announcements;
        }

        List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
        Set<String> usedIds = new HashSet<>();
        
        for (Map<?, ?> map : announcementMaps) {
            try {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null && announcement.getId() != null) {
                    if (usedIds.contains(announcement.getId().toLowerCase())) {
                        debug.log(LogLevel.WARNING, "Duplicate announcement ID found: " + announcement.getId());
                        continue;
                    }
                    usedIds.add(announcement.getId().toLowerCase());
                    announcements.add(announcement);
                }
            } catch (Exception e) {
                debug.log(LogLevel.WARNING, "Error parsing announcement: " + e.getMessage());
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
            debug.log(LogLevel.WARNING, "Config file not found: " + configFile.getName());
        }
        return types;
    }

    // Creates appropriate DataStore instance based on configuration settings
    private void initializeDataStoreConnection() {
        debug.log(LogLevel.INFO, "Initializing data store connection with type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            String host = config.getString("storage.mysql.host", "");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "");
            String username = config.getString("storage.mysql.username", "");
            String password = config.getString("storage.mysql.password", "");
            boolean useSSL = config.getBoolean("storage.mysql.useSSL", false);
            
            debug.log(LogLevel.INFO, "MySQL configuration - Host: " + host + ", Port: " + port + 
                ", Database: " + database + ", Username: " + username + 
                ", SSL: " + useSSL);
            
            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                debug.log(LogLevel.SEVERE, "Invalid MySQL configuration - missing required fields");
                dataStore = null;
                return;
            }
            
            dataStore = new MySQLDataConnector(plugin, host, port, database, username, password, useSSL);
            debug.log(LogLevel.INFO, "MySQL connector created successfully");
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            String databasePath = config.getString("storage.sqlite.database", "announcements.db");
            dataStore = new SQLiteDataConnector(plugin, databasePath);
        } else {
            dataStore = null;
        }
        
        if (dataStore == null) {
            debug.log(LogLevel.WARNING, "Failed to initialize data store - unknown or invalid storage type");
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
            announcementList.add(announcement);
        }
        return announcementList;
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
                    debug.log(LogLevel.WARNING, "Invalid recurrence format for announcement " + id + ": " + recStr);
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
            debug.log(LogLevel.WARNING, "PlaceholderAPI not found, unable to parse placeholders in announcement: " + id);
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

    // New validation method
    private boolean validateAnnouncement(Announcement announcement) {
        if (announcement == null) {
            debug.log(LogLevel.WARNING, "Null announcement found during validation");
            return false;
        }

        if (announcement.getId() == null || announcement.getId().trim().isEmpty()) {
            debug.log(LogLevel.WARNING, "Invalid announcement: missing ID");
            return false;
        }

        if (announcement.getText() == null || announcement.getText().trim().isEmpty()) {
            debug.log(LogLevel.WARNING, "Invalid announcement: missing text for ID " + announcement.getId());
            return false;
        }

        if (announcement.getType() == null || announcement.getType().trim().isEmpty()) {
            debug.log(LogLevel.WARNING, "Invalid announcement: missing type for ID " + announcement.getId());
            return false;
        }

        return true;
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
        debug.log(LogLevel.INFO, "Saving " + currentAnnouncements.size() + " announcements to file");

        for (Announcement announcement : currentAnnouncements) {
            try {
                // Validate required fields
                if (announcement.getId() == null || announcement.getType() == null || announcement.getText() == null) {
                    debug.log(LogLevel.WARNING, "Skipping invalid announcement: " + announcement.getId());
                    continue;
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", announcement.getId());
                map.put("text", announcement.getText());
                map.put("type", announcement.getType());

                // Optional fields
                Optional.ofNullable(announcement.getRecurrence()).ifPresent(r -> map.put("recurrence", r));
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
                debug.error("Error saving announcement " + announcement.getId() + ": " + e.getMessage(), e);
            }
        }

        if (announcementMaps.isEmpty()) {
            debug.log(LogLevel.WARNING, "No valid announcements to save");
            return;
        }

        try {
            config.set("announcements", announcementMaps);
            config.save(configFile);
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
            dataStore.disconnect();
        }
    }
}