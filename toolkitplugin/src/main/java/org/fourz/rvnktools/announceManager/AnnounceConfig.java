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
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // initialize the AnnounceConfig object
    public AnnounceConfig(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        this.configFile = new File(plugin.getDataFolder(), "announcements.yml");
        this.playerDisabledTypes = new HashMap<>();

        loadConfig();
        initializeDataStore();
        loadPlayerDisabledTypes();
    }

    private void initializeDataStore() {
        plugin.getLogger().info("Using " + storageType + " storage");
        
        // Load data from YAML first
        List<Announcement> ymlAnnouncements = new ArrayList<>();
        Map<String, AnnounceType> ymlTypes = new HashMap<>();
        
        if (configFile.exists()) {
            List<Map<?, ?>> announcementMaps = config.getMapList("announcements");
            List<Map<?, ?>> typesMaps = config.getMapList("announce_types");
            
            // Parse announcements from YAML
            for (Map<?, ?> map : announcementMaps) {
                Announcement announcement = parseAnnouncement(map);
                if (announcement != null) {
                    ymlAnnouncements.add(announcement);
                }
            }
            
            // Parse types from YAML
            for (Map<?, ?> map : typesMaps) {
                AnnounceType announceType = parseAnnounceType(map);
                if (announceType != null) {
                    ymlTypes.put(announceType.getId(), announceType);
                }
            }
        }

        // Initialize appropriate data store
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
            // If using yml, set the loaded data directly
            announceManager.setAnnouncements(ymlAnnouncements);
            announceTypes = ymlTypes;
            return;
        }

        // Initialize database and populate if needed
        if (dataStore != null) {
            dataStore.connect();
            
            try {
                // pull existing data from database
                List<Announcement> existingAnnouncements = dataStore.loadAnnouncements();
                List<AnnounceType> existingTypes = dataStore.loadAnnounceTypes();

                // if types are empty, populate from yml
                if (existingTypes.isEmpty()) {
                    plugin.getLogger().info("No announce types found in database - populating from yml...");
                    
                    // Use the yml data for the local lookup map
                    announceTypes = ymlTypes;

                    // Populate database with data from the yml
                    for (AnnounceType type : ymlTypes.values()) {
                        try {
                            if (!type.isImported()) {
                                dataStore.saveAnnounceType(type);
                                setImportedType(type.getId());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error saving announce type: " + type.getId());
                            e.printStackTrace();
                        }
                    }

                    plugin.getLogger().info("Database populated with " + ymlTypes.size() + " announce types");
                } else {

                    // Create the local map for lookup from existing types
                    announceTypes = existingTypes.stream().collect(Collectors.toMap(AnnounceType::getId, Function.identity()));

                    // Check for types that are not imported
                    List<AnnounceType> unimportedTypes = new ArrayList<>();
                    for (AnnounceType type : ymlTypes.values()) {
                        if (existingTypes.contains(type)) {
                            unimportedTypes.add(type);
                        } else {
                            //this fails because announceTypes is not initialized
                            setImportedType(type.getId());
                        }
                    }

                    if (!unimportedTypes.isEmpty()) {
                        plugin.getLogger().info("Found " + unimportedTypes.size() + " unimported announce types in database - importing from yml...");
                        // Import unimported types from yml
                        for (AnnounceType type : unimportedTypes) {
                            try {                                
                                dataStore.saveAnnounceType(type);                                
                                // Update the local lookup map
                                announceTypes.put(type.getId(), type);
                                setImportedType(type.getId());

                            } catch (Exception e) {
                                plugin.getLogger().warning("Error saving announce type: " + type.getId());
                                e.printStackTrace();
                            }
                        }

                        plugin.getLogger().info("Imported " + unimportedTypes.size() + " announce types to database");
                    } else {
                        plugin.getLogger().info("All announce types are imported to database");
                    }
                }


                if (existingAnnouncements.isEmpty()) {
                    // Use the yml data for the local lookup map
                    plugin.getLogger().info("No announcements found in database - populating from yml...");                    
                    for (Announcement announcement : ymlAnnouncements) {
                        dataStore.saveAnnouncement(announcement);
                        announcement.setImported(true);                        
                    }
                    plugin.getLogger().info("Database populated with " + ymlAnnouncements.size() + " announcements");

                } else {
                    // Check for announcements that are not imported
                    List<Announcement> unimportedAnnouncements = new ArrayList<>();
                    for (Announcement announcement : existingAnnouncements) {
                        if (!announcement.isImported()) {
                            unimportedAnnouncements.add(announcement);
                        } else {
                            announceManager.setImportedAnnouncement(announcement.getId());
                        }
                    }

                    if (!unimportedAnnouncements.isEmpty()) {
                        plugin.getLogger().info("Found " + unimportedAnnouncements.size() + " unimported announcements in database - importing from yml...");
                        
                        for (Announcement announcement : unimportedAnnouncements) {
                            if (!announcement.isImported()) {
                                dataStore.saveAnnouncement(announcement);
                                announceManager.setImportedAnnouncement(announcement.getId());
                            }
                        }
                        
                        plugin.getLogger().info("Imported " + unimportedAnnouncements.size() + " announcements to database");

                    } else {
                        plugin.getLogger().info("All announcements are imported to database");
                    }
                }

                // Load the data from database
                announceManager.setAnnouncements(dataStore.loadAnnouncements());
                announceTypes = new HashMap<>();
                for (AnnounceType type : dataStore.loadAnnounceTypes()) {
                    announceTypes.put(type.getId(), type);
                }

                // Save the updated imported flags back to config
                saveConfig();

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
                e.printStackTrace();
                
                // Fallback to yml data if database fails
                announceManager.setAnnouncements(ymlAnnouncements);
                announceTypes = ymlTypes;
            } finally {
                dataStore.disconnect();
            }
        }
    }

    // load the configuration from the YAML file
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("announcements.yml", false);
            plugin.getLogger().info("Created default announcements.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);        
        storageType = config.getString("storage.type", "yml");
    }

    // parse an announcement input by a player
    public boolean parseAnnouncement(String id, String type, String text, String playerName) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setText(text);
        announcement.setOwner(playerName);
        return announceManager.addAnnouncement(announcement);
    }

    // parse an announcement from console input
    public boolean parseAnnouncement(String id, String type, String text) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setType(type);
        announcement.setText(text);        
        return announceManager.addAnnouncement(announcement);
    }

    // parse an announcement from a map in the YAML config file
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
        announcement.setImported(imported);

        return announcement;
    }

    // parse an announce type from a map
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
        announceType.setImported(imported);

        return announceType;
    }

    // load player disabled types from a YAML file
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

    // save player disabled types to a YAML file
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

    // reload the configuration and player disabled types
    public void reloadConfig() {
        loadConfig();
        loadPlayerDisabledTypes();
    }

    // save the configuration to the YAML file
    public void saveConfig() {
        // Create a list to hold the announcements data
        List<Map<String, Object>> announcementMaps = new ArrayList<>();

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
            map.put("imported", type.isImported());
            
            announceTypeMaps.add(map);
        }

        config.set("announce_types", announceTypeMaps);

        try {
            config.save(configFile);            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save announcements to file: " + e.getMessage());
        }
    }

    // Getter for player disabled types
    public Map<UUID, Set<String>> getPlayerDisabledTypes() {
        return playerDisabledTypes;
    }

    // Getter for announce types
    public Map<String, AnnounceType> getAnnounceTypes() {
        return announceTypes;
    }
    
    /**
     * Gets the current DataStore instance being used for database operations.
     * Returns null if using yml storage.
     * 
     * @return The DataStore instance, or null if using yml storage
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    private void setImportedType(String typeId) {
        AnnounceType type = announceTypes.get(typeId);
        if (type != null) {
            type.setImported(true);
            announceTypes.put(typeId, type);
        }
    }
    private boolean isImportedType(String typeId) {
        return announceTypes.get(typeId).isImported();        
    }
}