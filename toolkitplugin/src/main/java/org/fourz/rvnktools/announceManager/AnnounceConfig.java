package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStore;
import org.fourz.rvnktools.announceManager.data.DataStoreManager;
import org.fourz.rvnktools.announceManager.data.YAMLManager;
import org.fourz.rvnktools.announceManager.preferences.AnnouncePreferences;

import java.io.File;
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
    private final DataStoreManager dataManager;
    private final YAMLManager yamlManager;
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
        
        // Initialize managers first
        this.dataManager = new DataStoreManager(plugin, config);
        this.yamlManager = new YAMLManager(plugin);
        
        // Load config after managers are initialized
        if (loadConfig()) debug.info("Configuration loaded successfully");
        
        this.configOperation = detectConfigState();
        debug.info("AnnounceConfig Operation: " + configOperation);
    }

    private ConfigOperation detectConfigState() {
        debug.info("Detecting config state...");

        // Update storage type checks to use dataManager
        if (dataManager.getStorageType().equals("flatfile") || 
            dataManager.getStorageType().equals("yml")) {
            debug.info("YAML storage explicitly configured");
            return ConfigOperation.YML_ONLY;
        }

        if (dataManager.isUsingDatabase()) {
            try {
                debug.debug("Testing database connectivity");
                
                if (dataManager.isEmpty()) {
                    debug.info("Empty database detected - will import from YAML");
                    return ConfigOperation.DB_IMPORT;
                }
                
                if (dbMatchesYml()) {
                    return ConfigOperation.DB_NO_UPDATE;
                } else {
                    debug.info("Database differs from YAML - will merge");
                    return ConfigOperation.DB_MERGE_YML;
                }
                
            } catch (Exception e) {
                debug.error("Database access test failed", e);
                return ConfigOperation.YML_FALLBACK;
            }
        }
        
        debug.warning("No database configuration found - using YAML storage");
        return ConfigOperation.YML_ONLY;
    }

    public void initializeDataStore() {
        debug.info("AnnounceConfig using " + dataManager.getStorageType() + " storage");

        // Add memory check
        debug.debug("Memory state before initialization - YML: " + 
            (ymlAnnouncements != null ? ymlAnnouncements.size() : 0) + " announcements");

        // Initialize preferences after dataStore is initialized
        this.preferences = new AnnouncePreferences(plugin, dataManager);

        // If not using database storage, use YAML data directly
        if (!dataManager.isUsingDatabase()) {
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
            return;
        }

        boolean isChanged = false;

        try {
            switch (configOperation) {
                case YML_FALLBACK:
                case YML_ONLY:
                    debug.info("Using existing announcements from local configuration");
                case DB_NO_UPDATE:                
                    announceManager.setAnnouncements(ymlAnnouncements);
                    announceTypes = ymlTypes;                    
                    break;

                case DB_IMPORT:
                    announceTypes = ymlTypes;
                    isChanged = importYML(ymlAnnouncements, ymlTypes);
                    if (isChanged) {
                        announceManager.setAnnouncements(dataManager.getDataStore().loadAnnouncements());
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
        if (!dataManager.isInitialized()) return false;
        
        String ymlHash = generateIdsHash(ymlAnnouncements.stream()
            .map(a -> a.getId().toLowerCase())
            .collect(Collectors.toSet()));
        
        String dbHash = dataManager.calculateDatabaseHash();
        debug.debug("Configuration hash comparison - YAML=" + ymlHash + " DB=" + dbHash);
        
        return ymlHash != null && dbHash != null && ymlHash.equals(dbHash);
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
            debug.info("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Update storage type initialization
        dataManager.setStorageType(config.getString("storage.type", "yml").toLowerCase());
        
        // Consolidate loading messages into a single INFO message
        this.ymlAnnouncements = loadDataFromYAML();
        this.ymlTypes = loadTypesFromYAML();
        
        debug.info(String.format("Loaded configuration: %d announcements, %d types, storage: %s", 
            ymlAnnouncements.size(), ymlTypes.size(), dataManager.getStorageType()));

        return !ymlAnnouncements.isEmpty() || !ymlTypes.isEmpty();
    }

    private boolean importYML(List<Announcement> ymlAnnouncements, Map<String, AnnounceType> ymlTypes) {
        boolean needsSaving = false;

        List<Announcement> existingAnnouncements = dataManager.getDataStore().loadAnnouncements();
        List<AnnounceType> existingTypes = dataManager.getDataStore().loadAnnounceTypes();

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
                    dataManager.getDataStore().saveAnnounceType(type);                    
                    debug.info("Imported announce type: " + type.getId());                    
                } catch (Exception e) {
                    debug.warning("Error saving announce type: " + type.getId());
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
                    dataManager.getDataStore().saveAnnouncement(announcement); 
                    announcement.setImported(); // Mark as imported immediately
                    successfulImports.add(announcement.getId().toLowerCase());
                    announceManager.getAnnouncements().add(announcement); // Add to memory
                    debug.info("Imported announcement: " + announcement.getId());
                } catch (Exception e) {
                    debug.warning("Error importing announcement " + announcement.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }            
        }

        // Update the local announcements list after import
        if (!successfulImports.isEmpty()) {
            announceManager.setAnnouncements(dataManager.getDataStore().loadAnnouncements());
            debug.info("Updated in-memory announcements after import");
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
        return yamlManager.loadAnnouncements();
    }

    // Reads and parses announcement types from YAML configuration
    private Map<String, AnnounceType> loadTypesFromYAML() {
        Map<String, AnnounceType> types = new HashMap<>();
        if (configFile.exists()) {
            List<Map<?, ?>> typesMaps = config.getMapList("announce_types");
            for (Map<?, ?> map : typesMaps) {
                AnnounceType announceType = yamlManager.parseAnnounceType(map);
                if (announceType != null) {
                    types.put(announceType.getId(), announceType);
                }
            }
        } else {
            debug.warning("Config file not found: " + configFile.getName());
        }
        return types;
    }

    // Retrieves announcement data from database and stores it in memory
    private List<Announcement> loadDataFromDatabase() {
        // Load announce types from database

        List<AnnounceType> dbTypes = dataManager.getDataStore().loadAnnounceTypes();
        announceTypes = new HashMap<>();
        for (AnnounceType type : dbTypes) {
            announceTypes.put(type.getId(), type);
        }

        // Convert list to map
        List<Announcement> announcementList = new ArrayList<>();
        List<Announcement> dbAnnouncements = dataManager.getDataStore().loadAnnouncements();
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
        yamlManager.saveAnnouncements(announceManager.getAnnouncements());
    }

    // Add delegate method for validation
    public boolean validateAnnouncement(String id, String text, String type) {
        return yamlManager.validateAnnouncement(id, text, type);
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
        return dataManager.getDataStore();
    }

    public void addPlayerDisabledType(UUID playerId, String type) {
        preferences.addDisabledType(playerId, type);
    }

    public void removePlayerDisabledType(UUID playerId, String type) {
        preferences.removeDisabledType(playerId, type);
    }

    // Add a shutdown method to disconnect the DataStore
    public void shutdown() {
        saveConfig(); // Save any pending changes
        dataManager.shutdown();
    }

    public boolean isDataStoreAvailable() {
        return dataManager != null && dataManager.isUsingDatabase();
    }

    /**
     * Gets a specific preference value for a player
     * @param playerId The UUID of the player
     * @param property The preference property key
     * @return The preference value, or default value if not set
     */
    public String getPreference(UUID playerId, String property) {
        return preferences.getPreference(playerId, property);
    }

    /**
     * Gets all preferences for a player
     * @param playerId The UUID of the player
     * @return Map of preference properties and their values
     */
    public Map<String, String> getAllPreferences(UUID playerId) {
        if (dataManager != null && dataManager.isInitialized()) {
            return dataManager.getPreferences(playerId);
        }
        return new HashMap<>();
    }

    /**
     * Sets a specific preference value for a player
     * @param playerId The UUID of the player
     * @param property The preference property key
     * @param value The preference value to set
     */
    public void setPreference(UUID playerId, String property, String value) {
        preferences.setPreference(playerId, property, value);
    }
}