package org.fourz.rvnktools.announceManager.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DataStoreManager {
    private static final String CLASS_NAME = "DataStoreManager";
    private final LogManager logger;
    private final JavaPlugin plugin;
    private DataStore dataStore;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final Map<String, Announcement> announcementCache = new ConcurrentHashMap<>();
    private final Map<String, AnnounceType> announceTypeCache = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private String storageType;

    public DataStoreManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        initializeDataStore(config);
    }

    private void initializeDataStore(FileConfiguration config) {
        this.storageType = config.getString("storage.type", "yml").toLowerCase();

        if (storageType.equalsIgnoreCase("mysql")) {
            initializeMySQLStore(config);
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            initializeSQLiteStore(config);
        }

        if (dataStore != null) {
            try {
                connectAndInitialize();
                initialized = true;
            } catch (Exception e) {
                logger.error("Failed to initialize data store", e);
                dataStore = null;
            }
        }
    }

    private void initializeMySQLStore(FileConfiguration config) {
        String host = config.getString("storage.mysql.host", "");
        int port = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "");
        String username = config.getString("storage.mysql.username", "");
        String password = config.getString("storage.mysql.password", "");
        boolean useSSL = config.getBoolean("storage.mysql.useSSL", false);
        String tablePrefix = config.getString("storage.mysql.tablePrefix", "");

        if (!host.isEmpty() && !database.isEmpty() && !username.isEmpty()) {
            dataStore = new MySQLDataConnector(plugin, host, port, database, username, password, useSSL, tablePrefix);
        }
    }

    private void initializeSQLiteStore(FileConfiguration config) {
        String databasePath = config.getString("storage.sqlite.database", "announcements.db");
        dataStore = new SQLiteDataConnector(plugin, databasePath);
    }

    private void connectAndInitialize() {
        if (!connectionLock.tryLock()) {
            logger.debug("Connection operation already in progress");
            return;
        }

        try {
            dataStore.connect();
            if (!dataStore.areTablesInitialized()) {
                dataStore.initializeTables();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    public boolean isInitialized() {
        return initialized && dataStore != null;
    }

    /**
     * Gets all preferences for a player
     * @param playerId The UUID of the player
     * @return Map of preference properties and values, or empty map if not found
     */
    public Map<String, String> getPreferences(UUID playerId) {
        if (!isInitialized()) return new HashMap<>();

        try {
            connectAndInitialize();
            Map<String, String> prefs = dataStore.getPlayerPreferences(playerId);
            // Ensure we return an empty map rather than null
            return prefs != null ? prefs : new HashMap<>();
        } catch (Exception e) {
            logger.error("Failed to get player preferences", e);
            return new HashMap<>();
        }
    }

    // CRUD Operations for Announcements
    public boolean saveAnnouncement(Announcement announcement) {
        if (!isInitialized() || announcement == null) return false;

        try {
            connectAndInitialize();
            dataStore.saveAnnouncement(announcement);
            announcementCache.put(announcement.getId().toLowerCase(), announcement);
            return true;
        } catch (Exception e) {
            logger.error("Failed to save announcement: " + announcement.getId(), e);
            return false;
        }
    }

    public boolean deleteAnnouncement(String id) {
        if (!isInitialized() || id == null) return false;

        try {
            connectAndInitialize();
            dataStore.deleteAnnouncement(id);
            announcementCache.remove(id.toLowerCase());
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete announcement: " + id, e);
            return false;
        }
    }

    public List<Announcement> loadAnnouncements() {
        if (!isInitialized()) return new ArrayList<>();

        try {
            connectAndInitialize();
            List<Announcement> announcements = dataStore.loadAnnouncements();
            announcementCache.clear();
            announcements.forEach(a -> announcementCache.put(a.getId().toLowerCase(), a));
            return announcements;
        } catch (Exception e) {
            logger.error("Failed to load announcements", e);
            return new ArrayList<>(announcementCache.values());
        }
    }

    public boolean announcementExists(String id) {
        if (!isInitialized() || id == null) return false;

        String lowerId = id.toLowerCase();
        if (announcementCache.containsKey(lowerId)) {
            return true;
        }

        try {
            connectAndInitialize();
            return dataStore.announcementExists(id);
        } catch (Exception e) {
            logger.error("Failed to check announcement existence: " + id, e);
            return false;
        }
    }

    // CRUD Operations for AnnounceTypes
    public boolean saveAnnounceType(AnnounceType announceType) {
        if (!isInitialized() || announceType == null) return false;

        try {
            connectAndInitialize();
            dataStore.saveAnnounceType(announceType);
            announceTypeCache.put(announceType.getId().toLowerCase(), announceType);
            return true;
        } catch (Exception e) {
            logger.error("Failed to save announce type: " + announceType.getId(), e);
            return false;
        }
    }

    public List<AnnounceType> loadAnnounceTypes() {
        if (!isInitialized()) return new ArrayList<>();

        try {
            connectAndInitialize();
            List<AnnounceType> types = dataStore.loadAnnounceTypes();
            announceTypeCache.clear();
            types.forEach(t -> announceTypeCache.put(t.getId().toLowerCase(), t));
            return types;
        } catch (Exception e) {
            logger.error("Failed to load announce types", e);
            return new ArrayList<>(announceTypeCache.values());
        }
    }

    // Player Preferences Management
    public void savePlayerDisabledType(UUID playerId, String type) {
        if (!isInitialized()) return;

        try {
            connectAndInitialize();
            dataStore.savePlayerDisabledType(playerId, type);
        } catch (Exception e) {
            logger.error("Failed to save player disabled type", e);
        }
    }

    public void removePlayerDisabledType(UUID playerId, String type) {
        if (!isInitialized()) return;

        try {
            connectAndInitialize();
            dataStore.removePlayerDisabledType(playerId, type);
        } catch (Exception e) {
            logger.error("Failed to remove player disabled type", e);
        }
    }

    public Set<String> getPlayerDisabledTypes(UUID playerId) {
        if (!isInitialized()) return new HashSet<>();

        try {
            connectAndInitialize();
            return dataStore.getPlayerDisabledTypes(playerId);
        } catch (Exception e) {
            logger.error("Failed to get player disabled types", e);
            return new HashSet<>();
        }
    }

    public Map<UUID, Set<String>> getAllPlayerDisabledTypes() {
        if (!isInitialized()) return new HashMap<>();

        try {
            connectAndInitialize();
            return dataStore.getAllPlayerDisabledTypes();
        } catch (Exception e) {
            logger.error("Failed to get all player disabled types", e);
            return new HashMap<>();
        }
    }

    public void savePlayerPreferences(UUID playerId, String preferences) {
        if (!isInitialized()) return;

        try {
            connectAndInitialize();
            dataStore.savePlayerPreferences(playerId, preferences);
        } catch (Exception e) {
            logger.error("Failed to save player preferences", e);
        }
    }

    public Map<String, String> getPlayerPreferences(UUID playerId) {
        if (!isInitialized()) return new HashMap<>();

        try {
            connectAndInitialize();
            Map<String, String> prefs = dataStore.getPlayerPreferences(playerId);
            // Ensure we return an empty map rather than null
            return prefs != null ? prefs : new HashMap<>();
        } catch (Exception e) {
            logger.error("Failed to get player preferences", e);
            return new HashMap<>();
        }
    }

    /**
     * Gets a specific player preference
     * @param playerId The UUID of the player
     * @param property The preference property key
     * @return The preference value, or null if not found
     */
    public String getPlayerPreference(UUID playerId, String property) {
        if (!isInitialized()) return null;

        try {
            connectAndInitialize();
            return dataStore.getPlayerPreference(playerId, property);
        } catch (Exception e) {
            logger.error("Failed to get player preference", e);
            return null;
        }
    }

    /**
     * Sets a specific player preference
     * @param playerId The UUID of the player
     * @param property The preference property key
     * @param value The preference value to set
     */
    public void setPlayerPreference(UUID playerId, String property, String value) {
        if (!isInitialized()) return;

        try {
            connectAndInitialize();
            dataStore.setPlayerPreference(playerId, property, value);
        } catch (Exception e) {
            logger.error("Failed to set player preference", e);
        }
    }

    public void shutdown() {
        if (dataStore != null) {
            try {
                dataStore.disconnect();
                announcementCache.clear();
                announceTypeCache.clear();
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public void setDataStore(DataStore dataStore) {
        this.dataStore = dataStore;
        this.initialized = (dataStore != null);
    }

    public boolean isEmpty() {
        if (!isInitialized()) return true;

        try {
            connectAndInitialize();
            return dataStore.isEmpty();
        } catch (Exception e) {
            logger.error("Failed to check if data store is empty", e);
            return true;
        }
    }

    public String calculateDatabaseHash() {
        if (!isInitialized() || !(dataStore instanceof MySQLDataConnector)) {
            return null;
        }
        return ((MySQLDataConnector)dataStore).calculateDatabaseHash();
    }

    public String getDatabaseType() {
        if (dataStore instanceof MySQLDataConnector) {
            return "mysql";
        } else if (dataStore instanceof SQLiteDataConnector) {
            return "sqlite";
        }
        return "none";
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String type) {
        this.storageType = type.toLowerCase();
    }

    public boolean isUsingDatabase() {
        return isInitialized() && (storageType.equals("mysql") || storageType.equals("sqlite"));
    }
}
