package org.fourz.rvnktools.announceManager;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;


import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.linkMaker.LinkMaker;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;

public class AnnounceCommand implements CommandExecutor {

    private final AnnounceManager announcementManager;
    private final RVNKTools plugin;

    public AnnounceCommand(AnnounceManager announcementManager, RVNKTools plugin) {
        this.announcementManager = announcementManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            sendUsage(player);
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                return toggleAnnouncementType(player, args[1]);
            case "list":
                return listAnnouncements(player, args);
            case "add":
                //return addAnnouncement(player, args);
                break;            
            case "remove":
                //return removeAnnouncement(player, args);
                break;
            case "now":
                return sendAnnouncementNow(player, args[1]);
            case "types":
                return showPlayerAnnouncementTypes(player);
            default:
                sendUsage(player);
                return false;
        }
        
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage("Usage:");
        player.sendMessage("/announce toggle <type> - Toggle announcement type");
        player.sendMessage("/announce list - List all announcements");
        player.sendMessage("/announce add <message> - Add new announcement");
        player.sendMessage("/announce remove <id> - Remove an announcement");
    }

    private boolean toggleAnnouncementType(Player player, String type) {
        if (type == null) {
            player.sendMessage("Announcement type cannot be empty");
            return false;            
        }   
        if (announcementManager.validateAnnounceType(type)) {
            announcementManager.toggleAnnouncementType(player, type);
            return true;
        } else {
            player.sendMessage("Invalid announcement type: " + type);
            return false;
        } 
    }

    private boolean sendAnnouncementNow(Player player, String id) {
        if (id == null) {
            player.sendMessage("Announcement ID cannot be empty");
            return false;
        }
        if (announcementManager.sendAnnouncementNow(player, id)) {
            return true;
        } else {
            player.sendMessage("Invalid announcement ID: " + id);
            return false;
        }

    }    
    
    private boolean showDisabledAnnouncementTypes(Player player, String[] args) {
        // Implementation for showing disabled announcement types
        messagePlayer(player, "&aDisabled announcement types for you:");
        for (String type : announcementManager.getPlayerDisabledAnnouncementTypes(player)) {
            messagePlayer(player, " &7- &a" + type);
        }
        return true;
    }

    private boolean showPlayerAnnouncementTypes(Player player) {
        messagePlayer(player, "&6Available announcement types:");
        String[] playerDisabledTypes = announcementManager.getPlayerDisabledAnnouncementTypes(player);
        for (String type : announcementManager.getAnnounceTypes()) {
            boolean isEnabled = !Arrays.asList(playerDisabledTypes).contains(type);
            String color = isEnabled ? "&a" : "&c";
            messagePlayer(player, " &7- " + color + type + " &7(" + (isEnabled ? "enabled" : "disabled") + ")");            
        }
        messagePlayer(player, "&7Use &5/announce toggle [type]&7 to toggle announcement types.");
        return true;
    }    

    public void messagePlayer (Player player, String message) {
        //if placeholderAPI is enabled
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {

            //set any placeholders in the message
            message = PlaceholderAPI.setPlaceholders(player, message);
        } 
        
        //use ChatFormat to colorize the message and replace linkMaker placeholders
        TextComponent constructedMessage = ChatFormat.parse(message, plugin.linkMaker);

        //send the message to the player
        player.spigot().sendMessage(constructedMessage);        
    }

    public boolean listAnnouncements(Player player, String[] args) {

        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("all")) {
                listAnnouncements(player);
                return true;
            } else {
                //
                String type = args[1];
                if (announcementManager.validateAnnounceType(type)) {
                    listAnnouncements(player, type);
                    return true;
                } else {
                    player.sendMessage("Invalid announcement type: " + type);
                return false;
                }
            } 
        }
        listAnnouncements(player);
        return true;

    }
    private void listAnnouncements(Player player, String type) {
        messagePlayer(player, "&6Announcements for type &a" + type + "&6:");
        for (Announcement announcement : announcementManager.getAnnouncements()) {
            if (announcement.getType().equalsIgnoreCase(type)) {
                messagePlayer(player, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getText());

            }
        }
    }

    private void listAnnouncements(Player player) {
        messagePlayer(player, "&6All announcements:");
        for (Announcement announcement : announcementManager.getAnnouncements()) {
            messagePlayer(player, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getText());
        }
    }
}

