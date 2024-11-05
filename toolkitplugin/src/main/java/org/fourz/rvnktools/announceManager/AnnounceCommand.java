package org.fourz.rvnktools.announceManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class AnnounceCommand implements CommandExecutor {

    private final AnnounceManager announcementManager;

    public AnnounceCommand(AnnounceManager announcementManager) {
        this.announcementManager = announcementManager;
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
                //return listAnnouncements(player);
                break;            
            case "add":
                //return addAnnouncement(player, args);
                break;            
            case "remove":
                //return removeAnnouncement(player, args);
                break;            
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
}
