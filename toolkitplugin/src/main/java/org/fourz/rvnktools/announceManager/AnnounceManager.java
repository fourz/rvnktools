package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatServiceInterface;
import org.fourz.rvnktools.util.ChatService;

public class AnnounceManager {
    private enum CheckCondition {
        ANNOUNCEMENT_EXISTS,
        SAVE_ANNOUNCEMENT,
        ADD_ANNOUNCEMENT
    }

    private final RVNKTools plugin;
    private final AnnounceConfig announceConfig;
    private final AnnounceScheduler announceScheduler;
    private final ChatServiceInterface chatService;    
    boolean usingPlaceholderAPI;    
    private List<Announcement> announcements;

    public AnnounceManager(RVNKTools plugin) {
        plugin.getLogger().info("Enabling AnnounceManager.");
        this.plugin = plugin;
        this.chatService = new ChatService();
        this.usingPlaceholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        this.announcements = new ArrayList<>();
        this.announceConfig = new AnnounceConfig(plugin, this);
        this.announceConfig.initializeDataStore();
        this.announceScheduler = new AnnounceScheduler(plugin, this);

        // Register commands
        plugin.getCommand("announce").setExecutor(new AnnounceCommand(this, plugin));
        plugin.getCommand("announce").setTabCompleter(new AnnounceTabCompleter(this));

        announceScheduler.scheduleAnnouncements();
    }

    // Add an announcement to the announcements list, used by AnnounceConfig and AnnounceManager.parseAnnouncement()
    public boolean addAnnouncement(Announcement announcement) {
        CheckCondition condition;

        if (announcement == null) {
            plugin.getLogger().warning("Cannot add null announcement");
            return false;
        } else if (announceConfig.getDataStore() != null && !announcement.isImported()) {
            condition = checkAnnounceExist(announcement.getId()) ? 
            CheckCondition.ANNOUNCEMENT_EXISTS : 
            CheckCondition.SAVE_ANNOUNCEMENT;
        } else {
            condition = CheckCondition.ADD_ANNOUNCEMENT;
        }

        switch (condition) {
            case ANNOUNCEMENT_EXISTS:
                plugin.getLogger().warning("Announcement with ID '" + announcement.getId() + "' already exists");
                return false;
            case SAVE_ANNOUNCEMENT:
                announceConfig.getDataStore().saveAnnouncement(announcement);
                setImported(announcement.getId());
                // fall through
            case ADD_ANNOUNCEMENT:
                announcements.add(announcement);
                return true;
            default:
                return false;
        }
    }

    // private setImported method
    private void setImported(String id) {
        for (Announcement a : announcements) {
            if (a.getId().equalsIgnoreCase(id)) {
                a.setImported();
                break;
            }
        }
    }

    // Add an announcement to the announcements list, used by AnnounceCommand
    public boolean addAnnouncement(Player player, String input) {

        // extract id, type, and text from input
        String[] args = input.split(" ", 3);
        
        if (args.length < 3) {
            if (player != null) {
                chatService.sendMessage(player, "Invalid announcement format. Usage: <type> <id> <message>");
            } else {
                plugin.getLogger().warning("Invalid announcement format. Usage: <type> <id> <message>");
            }
            return false;
        }
        
        String type = args[0];
        String id = args[1];
        String text = args[2];

        // Check if announcement already exists
        if (announceConfig.getDataStore() != null) {
            if (checkAnnounceExist(id)) {
                if (player != null) {
                    chatService.sendMessage(player, "An announcement with ID '" + id + "' already exists");
                } else {
                    plugin.getLogger().warning("An announcement with ID '" + id + "' already exists");
                }
                return false;
            }
        }

        if (!validateAnnounceType(type)) {
            plugin.getLogger().warning("Invalid announcement type: " + type);
            return false;
        }

        if (player != null) {
            if (!player.hasPermission("rvnktools.command.announce.add." + type)) {
                player.sendMessage("You do not have permission to add announcements.");
                return false;
            } else {
                // Parse announcement as player. Set owner to player
                player.sendMessage("Announcement added: " + id + " (" + type + ")");
                return announceConfig.parseAnnouncement(id, type, text, player.getName());
            }
        }        
        // Parse announcement as console        

        if (announceConfig.getDataStore() != null) {
            announceConfig.getDataStore().connect();            
            Boolean result = announceConfig.parseAnnouncement(id, type, text);
            announceConfig.getDataStore().disconnect();
            return result;
        } 
        return announceConfig.parseAnnouncement(id, type, text);       
    }

    private boolean checkAnnounceExist(String id) {
        return announceConfig.getDataStore().announcementExists(id);
    }

    public void broadcastAnnouncement(Announcement announcement) {      
        if (announcement == null) {
            plugin.getLogger().warning("Cannot broadcast null announcement");
            return;
        }

        String announcementType = announcement.getType();
        if (announcementType == null || announcementType.isEmpty()) {
            plugin.getLogger().warning("Announcement has null or empty type");
            return;
        }
                
        AnnounceType type = announceConfig.getAnnounceTypes().get(announcementType);        
        if (type == null) {
            plugin.getLogger().warning("Could not find announcement type '" + announcementType + "' in config. Available types: " + 
                String.join(", ", announceConfig.getAnnounceTypes().keySet()));
            return;
        }

        // Only construct message after all null checks have passed
        String prefix = type.getPrefix() != null ? type.getPrefix() : "";
        String suffix = type.getSuffix() != null ? type.getSuffix() : "";
        String message = prefix + announcement.getText() + suffix;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.shouldReceiveAnnouncement(player, announcement)) {
                chatService.sendMessage(player, message, plugin.linkMaker);
                plugin.getLogger().info("Broadcasting announcement to " + player.getName() + ": " + message);
            }
        }
    }

    public void cleanup() {
        // Clear any cached data or temporary collections
        announceScheduler.cleanup();
        
        // Suggest garbage collection for this manager        
        Runtime.getRuntime().gc();
    }

    public boolean deleteAnnouncement(String id) {
        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("Cannot delete announcement with null or empty id");
            return false;
        }

        if (announceConfig.getDataStore() != null) {
            if (!announceConfig.getDataStore().announcementExists(id)) {
                plugin.getLogger().warning("Announcement with ID '" + id + "' does not exist");
                return false;
            }
            announceConfig.getDataStore().deleteAnnouncement(id);
        }

        boolean removed = announcements.removeIf(announcement -> announcement.getId().equalsIgnoreCase(id));
        if (removed) {
            plugin.getLogger().info("Deleted announcement: " + id);
            announceConfig.saveConfig();
            return true;
        }

        plugin.getLogger().warning("Could not find announcement with id: " + id);
        return false;
    }

    public void toggleAnnouncementType(Player player, String type) {
        UUID playerId = player.getUniqueId();
        type = type.toLowerCase();
        if (player.hasPermission("rvnktools.command.announce.toggle." + type)) {
            Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().getOrDefault(playerId, new HashSet<>());

            if (disabledTypes.contains(type)) {
                disabledTypes.remove(type);
                announceConfig.removePlayerDisabledType(playerId, type);
                chatService.sendMessage(player, "Announcements of type '" + type + "' enabled.");
            } else {
                disabledTypes.add(type);
                announceConfig.addPlayerDisabledType(playerId, type);
                chatService.sendMessage(player, "Announcements of type '" + type + "' disabled.");
            }
            announceConfig.getPlayerDisabledTypes().put(playerId, disabledTypes);
        } else {
            chatService.sendMessage(player, "You do not have permission to toggle announcements of this type.");
        }
    }

    public void reloadConfig() {
        announceConfig.reloadConfig();
        announceScheduler.scheduleAnnouncements();
    }

    public void saveConfig() {
        announceConfig.saveConfig();
        //log to console
        plugin.getLogger().info("Saved AnnounceManager configuration.");
    }    

    public void shutdown() {    
        saveConfig();    
        announceScheduler.shutdown();
        savePlayerDisabledTypes();
        announceConfig.shutdown(); // Disconnect the DataStore during shutdown
    }

    public boolean validateAnnounceType(String type) {
        return announceConfig.getAnnounceTypes().containsKey(type);
    }

    public boolean shouldReceiveAnnouncement(Player player, Announcement announcement) {

        //check if player has permission to receive this type of announcement
        if (!player.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())) {
            return false;
        }

        //check if player has permission to receive this announcement
        if (announcement.getPermission() != null && !player.hasPermission(announcement.getPermission())) {
            return false;
        }

        //check if player has disabled this type of announcement
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        if (disabledTypes != null && disabledTypes.contains(announcement.getType().toLowerCase())) {
            return false;
        }

        // default to true
        return true;
    }

    // get disabled types for a player
    public boolean getPlayerDisabledTypes(Player player, String type) {
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        return disabledTypes != null && disabledTypes.contains(type);
    }

    // get disabled types for a player
    public String[] getPlayerDisabledAnnouncementTypes(Player player)  {
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        if (disabledTypes == null) {
            return new String[0];
        }
        return disabledTypes.toArray(new String[0]);
    }

    public Set<String> getAnnounceTypes() {
        return announceConfig.getAnnounceTypes().keySet();
    }

    public Set<String> getAnnouncementIds() {
        Set<String> ids = new HashSet<>();
        for (Announcement announcement : this.announcements) {
            ids.add(announcement.getId());
        }
        return ids;
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public void setAnnouncements(List<Announcement> announcements) {
        this.announcements = announcements;
    }

    public void savePlayerDisabledTypes() {
        announceConfig.savePlayerDisabledTypes();
    }

    public boolean sendAnnouncementNow(Player player, String id) {
        for (Announcement announcement : this.announcements) {
            if (announcement.getId().equalsIgnoreCase(id)) {
                broadcastAnnouncement(announcement);
                return true;
            }
        }
        return false;
    }

    public AnnounceType getAnnounceType(String type) {
        return announceConfig.getAnnounceTypes().get(type);
    }

    public boolean announcementExists(String id) {
        // First check in memory
        for (Announcement announcement : announcements) {
            if (announcement.getId().equalsIgnoreCase(id)) {
                plugin.getLogger().info("Announcement with ID '" + id + "' found in memory");
                return true;
            }
        }

        // Then check in database if available
        if (announceConfig.getDataStore() != null) {
            announceConfig.getDataStore().connect();
            boolean exists = announceConfig.getDataStore().announcementExists(id);
            announceConfig.getDataStore().disconnect();
            plugin.getLogger().info("Announcement with ID '" + id + "' found in database: " + exists);
            return exists;
        }

        return false;
    }

    public void setAnnouncementImported(String id) {
        for (Announcement announcement : announcements) {
            if (announcement.getId().equalsIgnoreCase(id)) {
                announcement.setImported();         
                break;
            }
        }
    }

    public void setAnnouncementsImported() {
        for (Announcement announcement : announcements) {
            announcement.setImported();
        }
    }

    public boolean isAnnouncementImported(String id) {
        for (Announcement announcement : announcements) {
            if (announcement.getId().equalsIgnoreCase(id)) {
                return announcement.isImported();
            }
        }
        return false;
    }
}