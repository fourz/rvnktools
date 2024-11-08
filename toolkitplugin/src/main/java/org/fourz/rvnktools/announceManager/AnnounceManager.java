package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatFormat;

import me.clip.placeholderapi.PlaceholderAPI;

public class AnnounceManager {
    private final RVNKTools plugin;
    private final AnnounceConfig announceConfig;
    private final AnnounceScheduler announceScheduler;
    boolean usingPlaceholderAPI;
    

    public AnnounceManager(RVNKTools plugin) {
        plugin.getLogger().info("Enabling AnnounceManager.");
        this.plugin = plugin;
        this.usingPlaceholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        this.announceConfig = new AnnounceConfig(plugin);
        this.announceScheduler = new AnnounceScheduler(plugin, this);

        // Register commands
        plugin.getCommand("announce").setExecutor(new AnnounceCommand(this, plugin));
        plugin.getCommand("announce").setTabCompleter(new AnnounceTabCompleter(this));

        // Schedule announcements
        plugin.getLogger().info("Scheduling announcements...");
        announceScheduler.scheduleAnnouncements();
    }

    public void broadcastAnnouncement(Announcement announcement) {      
                
        AnnounceType type = announceConfig.getAnnounceTypes().get(announcement.getType());        
        String prefix = type.getPrefix();
        String suffix = type.getSuffix();                
        String message = announcement.getText();

        message = prefix + message + suffix;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.shouldReceiveAnnouncement(player, announcement)) {
                String formattedMessage = ChatFormat.colorize(message);
                if (usingPlaceholderAPI) {
                    formattedMessage = PlaceholderAPI.setPlaceholders(player, formattedMessage);
                }
                player.sendMessage(formattedMessage);
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
                player.sendMessage("Announcements of type '" + type + "' enabled.");
            } else {
                disabledTypes.add(type);
                player.sendMessage("Announcements of type '" + type + "' disabled.");
            }
            announceConfig.getPlayerDisabledTypes().put(playerId, disabledTypes);
        } else {
            player.sendMessage("You do not have permission to toggle announcements.");
        }
    }

    public void reloadConfig() {
        announceConfig.reloadConfig();
        announceScheduler.scheduleAnnouncements();
    }

    public void shutdown() {
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

    public boolean getPlayerDisabledTypes(Player player, String type) {
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        return disabledTypes != null && disabledTypes.contains(type);
    }

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
        for (Announcement announcement : announceConfig.getAnnouncements()) {
            ids.add(announcement.getId());
        }
        return ids;
    }

    public List<Announcement> getAnnouncements() {
        return announceConfig.getAnnouncements();
    }

    public void savePlayerDisabledTypes() {
        announceConfig.savePlayerDisabledTypes();
    }

    public boolean sendAnnouncementNow(Player player, String id) {
        for (Announcement announcement : announceConfig.getAnnouncements()) {
            if (announcement.getId().equalsIgnoreCase(id)) {
                broadcastAnnouncement(announcement);
                return true;
            }
        }
        return false;
    }
}
