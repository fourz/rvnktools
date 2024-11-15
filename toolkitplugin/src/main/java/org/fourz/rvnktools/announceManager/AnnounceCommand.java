package org.fourz.rvnktools.announceManager;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;

public class AnnounceCommand implements CommandExecutor {

    private final AnnounceManager announcementManager;
    private final RVNKTools plugin;
    private final AnnounceCommandHelp helpHandler;

    public AnnounceCommand(AnnounceManager announcementManager, RVNKTools plugin) {
        this.announcementManager = announcementManager;
        this.plugin = plugin;
        this.helpHandler = new AnnounceCommandHelp(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        //check if the player has the required permission
        if (!sender.hasPermission("rvnktools.command.announce")) {
            sender.sendMessage("You do not have permission to use this command");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            sendUsage(player);
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                return handleHelpCommand(player, args.length > 1 ? args[1] : null);
            case "types":
                return showAnnouncementTypes(player);
            case "toggle":
                return toggleAnnouncementType(player, args.length > 1 ? args[1] : null);
            case "list":
                return listAnnouncements(player, args);
            case "add":
                return addAnnouncement(player, args);
            case "delete":
                //return removeAnnouncement(player, args);
                break;
            case "now":
                return sendAnnouncementNow(player, args.length > 1 ? args[1] : null);
            case "status":
                return showPlayerAnnouncementTypes(player);
            default:
                sendUsage(player);
                return false;
        }
        
        return true;
    }

    private boolean addAnnouncement(Player player, String[] args) {
        if (!player.hasPermission("rvnktools.command.announce.add")) {
            messagePlayer(player, "&cYou don't have permission to add announcements");
            return false;
        }

        if (args.length < 2) {
            player.sendMessage("Announcement message cannot be empty");
            return false;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                
        return announcementManager.addAnnouncement(player, message);
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
        messagePlayer(player, "&6=== Enabled Announcement Types ===");
        messagePlayer(player, "");
        
        String[] playerDisabledTypes = announcementManager.getPlayerDisabledAnnouncementTypes(player);
        for (String type : announcementManager.getAnnounceTypes()) {
            if (player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                boolean isEnabled = !Arrays.asList(playerDisabledTypes).contains(type);
                String symbol = isEnabled ? "✔" : "✘";
                String color = isEnabled ? "&a" : "&c";
                messagePlayer(player, " " + color + symbol + " &f" + type);
            }
        }
        
        messagePlayer(player, "");
        messagePlayer(player, "&7✔ = Enabled for you");
        messagePlayer(player, "&7✘ = Disabled by you");
        messagePlayer(player, "&7Use &5/announce toggle [type]&7 to change settings");
        return true;
    }    

    public void messagePlayer (Player player, String message) {

        //if message is null, return a vertical space to avoid using unnecessary parsing methods
        if (message == null) {
            player.sendMessage("");
            return;
        }   

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
                if (!player.hasPermission("rvnktools.announce.type.*")) {
                    messagePlayer(player, "&cYou don't have permission to list all announcements");
                    return false;
                    
                }
                listAnnouncements(player);
                return true;
            } else if (args[1].equalsIgnoreCase("types")) {
                showAnnouncementTypes(player);
                return true;                  
            } else {
                String type = args[1];
                if (!player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                    messagePlayer(player, "&cYou don't have permission to view announcements of type: " + type);
                    return false;
                }
                if (announcementManager.validateAnnounceType(type)) {
                    listAnnouncements(player, type);
                    return true;
                } else {
                    messagePlayer(player, "&cInvalid announcement type: " + type);
                    return false;
                }
            }
        }
        // Default to showing only announcements the player has permission to see
        listAccessibleAnnouncements(player);
        return true;
    }

    private void listAnnouncements(Player player, String type) {
        if (!player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
            return;
        }
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
            if (player.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())) {
                messagePlayer(player, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getText());
            }
        }
    }

    private void listAccessibleAnnouncements(Player player) {
        messagePlayer(player, "&6Available announcements:");
        for (Announcement announcement : announcementManager.getAnnouncements()) {            
            if ((player.hasPermission("rvnktools.announce.type.*")) 
                || ((player.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())))) {
                messagePlayer(player, " &7- &3" + announcement.getId() + " &7(&a" + announcement.getType() + "&7) - &f" + announcement.getText());
            }
        }
    }

    private boolean handleHelpCommand(Player player, String topic) {
        return helpHandler.handleHelpCommand(player, topic);
    }

    private boolean showAnnouncementTypes(Player player) {
        messagePlayer(player, "&6=== Announcement Type Access ===");
        messagePlayer(player, "");
        for (String type : announcementManager.getAnnounceTypes()) {
            String permission = "rvnktools.announce.type." + type.toLowerCase();
            if (player.hasPermission(permission)) {
                messagePlayer(player, " &a✔ &f" + type);
            } else {
                messagePlayer(player, " &c✘ &7" + type);
            }
        }
        
        messagePlayer(player, "");
        messagePlayer(player, "&7✔ = You have access to this type");
        messagePlayer(player, "&7✘ = You don't have access to this type");
        messagePlayer(player, "&7Use &5/announce list [type]&7 to see all of a given type.");
        return true;
    }
}

