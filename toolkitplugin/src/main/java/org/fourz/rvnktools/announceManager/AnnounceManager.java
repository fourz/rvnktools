package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatServiceInterface;
import org.fourz.rvnktools.util.ChatService;

public class AnnounceManager {
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
        this.announceScheduler = new AnnounceScheduler(plugin, this);

        // Register commands
        plugin.getCommand("announce").setExecutor(new AnnounceCommand(this, plugin));
        plugin.getCommand("announce").setTabCompleter(new AnnounceTabCompleter(this));

        // Schedule announcements
        plugin.getLogger().info("Scheduling announcements...");
        announceScheduler.scheduleAnnouncements();
    }

    // Add an announcement to the announcements list, used by AnnounceConfig
    public boolean addAnnouncement(Announcement announcement) {        
        if (announcement == null) {
            plugin.getLogger().warning("Cannot add null announcement");
            return false;
        }
        announcements.add(announcement);

        // Log the announcement
        plugin.getLogger().info("Added announcement: " + announcement.getId() + " (" + announcement.getType() + ")");
        return true;
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
        return announceConfig.parseAnnouncement(id, type, text);
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

    public void toggleAnnouncementType(Player player, String type) {
        UUID playerId = player.getUniqueId();
        type = type.toLowerCase();
        if (player.hasPermission("rvnktools.command.announce.toggle." + type)) {
            Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().getOrDefault(playerId, new HashSet<>());

            if (disabledTypes.contains(type)) {
                disabledTypes.remove(type);
                chatService.sendMessage(player, "Announcements of type '" + type + "' enabled.");
            } else {
                disabledTypes.add(type);
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

    public void cleanup() {
        // Clear any cached data or temporary collections
        announceScheduler.cleanup();
        
        // Suggest garbage collection for this manager        
        Runtime.getRuntime().gc();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } finally {
            super.finalize();
        }
    }
}