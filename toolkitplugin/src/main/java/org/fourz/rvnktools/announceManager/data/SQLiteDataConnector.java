package org.fourz.rvnktools.announceManager.data;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.Announcement;
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
    private final String databasePath;
    private Connection connection;
    private final JavaPlugin plugin;
    private boolean empty = false;

    public SQLiteDataConnector(JavaPlugin plugin, String databasePath) {
        this.plugin = plugin;
        this.databasePath = new File(plugin.getDataFolder(), databasePath).getAbsolutePath();
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
                    plugin.getLogger().info("SQLite database file created at: " + dbFile.getAbsolutePath());
                }

                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databasePath);
                this.connection.setAutoCommit(true);
                
                // Initialize tables immediately after connection
                initializeTables();
                plugin.getLogger().info("Successfully connected to SQLite database");
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
            e.printStackTrace();
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
                plugin.getLogger().severe("Error closing SQLite connection: " + e.getMessage());
                e.printStackTrace();
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
                pstmt.setString(2, announcement.getText());
                pstmt.setString(3, announcement.getType());
                pstmt.setObject(4, announcement.getRecurrence()); // Changed to handle Long
                pstmt.setString(5, announcement.getOwner());
                pstmt.setString(6, announcement.getPermission());
                pstmt.setString(7, announcement.getDate() != null ? announcement.getDate().toString() : null);
                pstmt.setString(8, announcement.getTime() != null ? announcement.getTime().toString() : null);
                pstmt.setString(9, announcement.getExpiration() != null ? announcement.getExpiration().toString() : null);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAnnouncement(String id) {
        try {
            ensureConnected();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        String sql = "DELETE FROM announcements WHERE id = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
                    announcement.setText(rs.getString("text"));
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
        } catch (SQLException e) {
            e.printStackTrace();
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
        String sql = "INSERT INTO announce_types (id, prefix, suffix, permission, listing_fee) VALUES (?, ?, ?, ?, ?)";
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
        String sql = "SELECT * FROM announce_types";
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
            
            // Check if announcements table exists
            tables = metaData.getTables(null, null, "announcements", null);
            if (!tables.next()) {
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
                empty = true;
            }
            
            // Check if announce_types table exists
            tables = metaData.getTables(null, null, "announce_types", null);
            if (!tables.next()) {
                Statement stmt = connection.createStatement();
                String createAnnounceTypesTable = "CREATE TABLE announce_types (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "prefix VARCHAR(128)," + 
                    "suffix VARCHAR(128)," +
                    "permission VARCHAR(128)," +
                    "listing_fee DOUBLE" +
                    ")";
                stmt.executeUpdate(createAnnounceTypesTable);
            }
            
            // Check if announce_disabledtypes table exists
            tables = metaData.getTables(null, null, "announce_disabledtypes", null);
            if (!tables.next()) {
                Statement stmt = connection.createStatement();
                String createAnnounceDisabledTypesTable = "CREATE TABLE announce_disabledtypes (" +
                    "player_id VARCHAR(36)," +
                    "type VARCHAR(64)," +
                    "PRIMARY KEY (player_id, type)" +
                    ")";
                stmt.executeUpdate(createAnnounceDisabledTypesTable);
            }
            
            // Check if announce_prefs table exists
            tables = metaData.getTables(null, null, "announce_prefs", null);
            if (!tables.next()) {
                Statement stmt = connection.createStatement();
                String createAnnouncePrefsTable = "CREATE TABLE announce_prefs (" +
                    "player_id VARCHAR(36) PRIMARY KEY," +
                    "text VARCHAR(512)" +
                    ")";
                stmt.executeUpdate(createAnnouncePrefsTable);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        return empty;
    }

    @Override
    public void savePlayerDisabledType(UUID playerId, String type) {
        try {
            ensureConnected();
            String sql = "INSERT OR IGNORE INTO announce_disabledtypes (player_id, type) VALUES (?, ?)";
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
            String sql = "DELETE FROM announce_disabledtypes WHERE player_id = ? AND type = ?";
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
            String sql = "SELECT type FROM announce_disabledtypes WHERE player_id = ?";
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
            String sql = "SELECT player_id, type FROM announce_disabledtypes";
            try (Statement stmt = connection.createStatement();
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
    public void savePlayerPreferences(UUID playerId, String preferences) {
        try {
            ensureConnected();
            String sql = "INSERT OR REPLACE INTO announce_prefs (player_id, text) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, preferences);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPlayerPreferences(UUID playerId) {
        String preferences = null;
        try {
            ensureConnected();
            String sql = "SELECT text FROM announce_prefs WHERE player_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        preferences = rs.getString("text");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return preferences;
    }
}