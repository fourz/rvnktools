package org.fourz.rvnktools.announceManager.data;

// Add new import
import org.fourz.rvnktools.util.Debug;
import org.fourz.rvnktools.announceManager.AnnounceConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.preferences.PreferenceProperty;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.io.File;
import java.io.IOException;

public class SQLiteDataConnector implements DataStore {
    private static final String CLASS_NAME = "SQLiteDataConnector";
    private final String databasePath;
    private Connection connection;
    private final JavaPlugin plugin;
    private boolean empty = false;
    private boolean tablesInitialized = false;
    private final Debug debug;  // Add Debug field

    public SQLiteDataConnector(JavaPlugin plugin, String databasePath) {
        this.plugin = plugin;
        this.databasePath = new File(plugin.getDataFolder(), databasePath).getAbsolutePath();
        this.debug = new Debug(plugin, CLASS_NAME, AnnounceConfig.getLogLevel()) {};
    }

    @Override
    public void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                File dbFile = new File(this.databasePath);
                File parentDir = dbFile.getParentFile();
                
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                if (!dbFile.exists()) {
                    dbFile.createNewFile();
                    debug.debug("SQLite database file created at: " + dbFile.getAbsolutePath());
                }

                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databasePath);
                this.connection.setAutoCommit(true);
                
                initializeTables();
                debug.debug("Successfully connected to SQLite database");
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            debug.error("Failed to connect to SQLite database: " + e.getMessage(), e);
            this.connection = null;
        }
    }

    @Override
    public void disconnect() {
        if (this.connection != null) {
            try {
                this.connection.close();
                this.connection = null;
            } catch (SQLException e) {
                debug.error("Error closing SQLite connection: " + e.getMessage(), e);
            }
        }
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // Add to the top of each database operation method:
    private void ensureConnected() throws SQLException {
        if (!isConnected()) {
            connect();
        }
        if (!isConnected()) {
            throw new SQLException("Not connected to database");
        }
    }

    @Override
    public void saveAnnouncement(Announcement announcement) {
        String sql = "INSERT INTO announcements (id, text, type, recurrence, owner, permission, date, time, expiration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            ensureConnected();
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, announcement.getId());
                pstmt.setString(2, announcement.getMessage());
                pstmt.setString(3, announcement.getType());
                pstmt.setObject(4, announcement.getRecurrence()); // Changed to handle Long
                pstmt.setString(5, announcement.getOwner());
                pstmt.setString(6, announcement.getPermission());
                pstmt.setString(7, announcement.getDate() != null ? announcement.getDate().toString() : null);
                pstmt.setString(8, announcement.getTime() != null ? announcement.getTime().toString() : null);
                pstmt.setString(9, announcement.getExpiration() != null ? announcement.getExpiration().toString() : null);
                pstmt.executeUpdate();
                debug.debug("Saved announcement with ID: " + announcement.getId());
            }
        } catch (SQLException e) {
            debug.error("Error saving announcement: " + announcement.getId(), e);
        }
    }

    @Override
    public void deleteAnnouncement(String id) {
        try {
            ensureConnected();
            String sql = "DELETE FROM announcements WHERE id = ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, id);
                int rows = pstmt.executeUpdate();
                debug.debug("Deleted announcement with ID: " + id + " (" + rows + " rows affected)");
            }
        } catch (SQLException e) {
            debug.error("Error deleting announcement: " + id, e);
        }
    }

    @Override
    public List<Announcement> loadAnnouncements() {
        Map<String, Announcement> announcements = new HashMap<>();
        String sql = "SELECT * FROM announcements";
        try {
            ensureConnected();
            try (Statement stmt = this.connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Announcement announcement = new Announcement();
                    announcement.setId(rs.getString("id"));
                    announcement.setMessage(rs.getString("text"));
                    announcement.setType(rs.getString("type"));
                    announcement.setRecurrence(rs.getObject("recurrence") != null ? rs.getLong("recurrence") : null);
                    announcement.setOwner(rs.getString("owner"));
                    announcement.setPermission(rs.getString("permission"));
                    announcement.setDate(rs.getString("date") != null ? LocalDate.parse(rs.getString("date")) : null);
                    announcement.setTime(rs.getString("time") != null ? LocalTime.parse(rs.getString("time")) : null);
                    announcement.setExpiration(rs.getString("expiration") != null ? LocalDateTime.parse(rs.getString("expiration")) : null);
                    announcements.put(announcement.getId(), announcement);
                }
            }
            debug.debug("Loaded " + announcements.size() + " announcements from database");
        } catch (SQLException e) {
            debug.error("Error loading announcements", e);
        }
        return new ArrayList<>(announcements.values());
    }

    @Override
    public void saveAnnounceType(AnnounceType announceType) {
        try {
            ensureConnected();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        String sql = "INSERT INTO types (id, prefix, suffix, permission, listing_fee) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, announceType.getId());
            pstmt.setString(2, announceType.getPrefix());
            pstmt.setString(3, announceType.getSuffix());
            pstmt.setString(4, announceType.getPermission());
            pstmt.setDouble(5, announceType.getListingFee() != null ? announceType.getListingFee() : 0.0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<AnnounceType> loadAnnounceTypes() {
        try {
            ensureConnected();
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        List<AnnounceType> announceTypes = new ArrayList<>();
        String sql = "SELECT * FROM types";
        try (Statement stmt = this.connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                AnnounceType announceType = new AnnounceType();
                announceType.setId(rs.getString("id"));
                announceType.setPrefix(rs.getString("prefix"));
                announceType.setSuffix(rs.getString("suffix"));
                announceType.setPermission(rs.getString("permission"));
                announceType.setListingFee(rs.getDouble("listing_fee"));
                announceTypes.add(announceType);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announceTypes;
    }

    @Override
    public void initializeTables() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables;
            
            debug.debug("Starting table initialization check");
            
            // Check if announcements table exists
            tables = metaData.getTables(null, null, "announcements", null);
            if (!tables.next()) {
                debug.debug("Creating announcements table");
                Statement stmt = connection.createStatement();
                String createAnnouncementsTable = "CREATE TABLE announcements (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "text TEXT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "recurrence BIGINT," + // Changed from VARCHAR to BIGINT
                    "owner VARCHAR(64)," +
                    "permission VARCHAR(128)," +
                    "date DATE," +
                    "time TIME," +
                    "expiration DATETIME" +
                    ")";
                stmt.executeUpdate(createAnnouncementsTable);
                debug.debug("Announcements table created successfully");
                empty = true;
            }
            
            // Check if types table exists
            tables = metaData.getTables(null, null, "types", null);
            if (!tables.next()) {
                debug.debug("Creating types table");
                Statement stmt = connection.createStatement();
                String createAnnounceTypesTable = "CREATE TABLE types (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "prefix VARCHAR(128)," + 
                    "suffix VARCHAR(128)," +
                    "permission VARCHAR(128)," +
                    "listing_fee DOUBLE" +
                    ")";
                stmt.executeUpdate(createAnnounceTypesTable);
                debug.debug("types table created successfully");
            }
            
            // Check if disabledtypes table exists
            tables = metaData.getTables(null, null, "disabledtypes", null);
            if (!tables.next()) {
                debug.debug("Creating disabledtypes table");
                Statement stmt = connection.createStatement();
                String createAnnounceDisabledTypesTable = "CREATE TABLE disabledtypes (" +
                    "player_id VARCHAR(36)," +
                    "type VARCHAR(64)," +
                    "PRIMARY KEY (player_id, type)" +
                    ")";
                stmt.executeUpdate(createAnnounceDisabledTypesTable);
                debug.debug("disabledtypes table created successfully");
            }
            
            // Check if announce_prefs table exists
            tables = metaData.getTables(null, null, "preferences", null);
            if (!tables.next()) {
                debug.debug("Creating preferences table");
                Statement stmt = connection.createStatement();
                String createAnnouncePrefsTable = "CREATE TABLE " + "preferences (" +
                    "player_id VARCHAR(36) PRIMARY KEY," +
                    "text VARCHAR(512)" +
                    ")";
                stmt.executeUpdate(createAnnouncePrefsTable);
                debug.debug("preferences table created successfully");
            }
            
            debug.debug("Table initialization complete");
            
        } catch (SQLException e) {
            debug.error("Error initializing database tables", e);
        }
    }

    public boolean announcementExists(String id) {
        try {
            ensureConnected();
            String sql = "SELECT COUNT(*) FROM announcements WHERE id = ?";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        return count > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        debug.debug("Checking if database is empty");
        return empty;
    }

    @Override
    public void savePlayerDisabledType(UUID playerId, String type) {
        try {
            ensureConnected();
            String sql = "INSERT OR IGNORE INTO disabledtypes (player_id, type) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removePlayerDisabledType(UUID playerId, String type) {
        try {
            ensureConnected();
            String sql = "DELETE FROM disabledtypes WHERE player_id = ? AND type = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Set<String> getPlayerDisabledTypes(UUID playerId) {
        Set<String> types = new HashSet<>();
        try {
            ensureConnected();
            String sql = "SELECT type FROM disabledtypes WHERE player_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        types.add(rs.getString("type"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return types;
    }

    @Override
    public Map<UUID, Set<String>> getAllPlayerDisabledTypes() {
        Map<UUID, Set<String>> allTypes = new HashMap<>();
        try {
            ensureConnected();
            String sql = "SELECT player_id, type FROM disabledtypes";
            try (Statement stmt = this.connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    String type = rs.getString("type");
                    allTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allTypes;
    }

    @Override
    @Deprecated
    public void savePlayerPreferences(UUID playerId, String preferences) {
        debug.warning("Using deprecated savePlayerPreferences method - update to use setPlayerPreference");
        // Store as a single 'legacy' property to maintain backwards compatibility
        setPlayerPreference(playerId, "legacy", preferences);
    }

    @Override
    public void setPlayerPreference(UUID playerId, String property, String value) {
        try {
            ensureConnected();
            String sql = "INSERT OR REPLACE INTO preferences (player_id, property, value) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, property);
                pstmt.setString(3, value);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            debug.error("Error setting player preference", e);
        }
    }

    @Override
    public String getPlayerPreference(UUID playerId, String property) {
        try {
            ensureConnected();
            String sql = "SELECT value FROM preferences WHERE player_id = ? AND property = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, property);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            debug.error("Error getting player preference", e);
        }
        return PreferenceProperty.fromKey(property).getDefaultValue();
    }

    @Override
    public Map<String, String> getPlayerPreferences(UUID playerId) {
        Map<String, String> preferences = new HashMap<>();
        try {
            ensureConnected();
            String sql = "SELECT property, value FROM preferences WHERE player_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    preferences.put(rs.getString("property"), rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            debug.error("Error getting player preferences", e);
        }
        return preferences;
    }

    @Override
    public void deletePlayerPreference(UUID playerId, String property) {
        try {
            ensureConnected();
            String sql = "DELETE FROM preferences WHERE player_id = ? AND property = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, property);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            debug.error("Error deleting player preference", e);
        }
    }

    @Override
    public boolean areTablesInitialized() {
        return tablesInitialized;
    }

    @Override
    public void setTablesInitialized(boolean initialized) {
        this.tablesInitialized = initialized;
    }

    public String calculateDatabaseHash() {
        List<String> ids = new ArrayList<>();
        try {
            ensureConnected();
            String sql = "SELECT LOWER(id) FROM announcements ORDER BY LOWER(id)";
            try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }

            if (ids.isEmpty()) {
                return null;
            }

            StringBuilder hashStr = new StringBuilder();
            for (String id : ids) {
                hashStr.append(id).append('\n');
            }

            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(hashStr.toString().getBytes());
                StringBuilder hexString = new StringBuilder();
                for (byte b : digest) {
                    hexString.append(String.format("%02x", b));
                }
                return hexString.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                debug.error("MD5 algorithm not available: " + e.getMessage(), e);
                return null;
            }

        } catch (SQLException e) {
            debug.error("Error calculating database hash: " + e.getMessage(), e);
            return null;
        }
    }

    public Set<String> getExistingAnnouncementIds() {
        Set<String> ids = new HashSet<>();
        try {
            ensureConnected();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT LOWER(id) FROM announcements")) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
            debug.debug("Retrieved " + ids.size() + " announcement IDs from database");
        } catch (SQLException e) {
            debug.error("Error retrieving announcement IDs: " + e.getMessage(), e);
        }
        return ids;
    }
}