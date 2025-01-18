package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;

public class AnnounceSubCommandList extends AnnounceSubCommand {
    
    public AnnounceSubCommandList(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("all")) {
                if (!sender.hasPermission("rvnktools.announce.type.*")) {
                    messageSender(sender, "&cYou don't have permission to list all announcements");
                    return false;
                }
                listAll(sender);
                return true;
            } else if (args[1].equalsIgnoreCase("types")) {
                listTypes(sender);
                return true;
            } else {
                return listByType(sender, args[1]);
            }
        }
        listAccessible(sender);
        return true;
    }

    private void listAll(CommandSender sender) {
        messageSender(sender, "&6All announcements:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            messageSender(sender, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getMessage());
        }
    }

    private void listAccessible(CommandSender sender) {
        messageSender(sender, "&6Available announcements:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            if (sender.hasPermission("rvnktools.announce.type.*") || 
                sender.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())) {
                messageSender(sender, " &7- &3" + announcement.getId() + " &7(&a" + announcement.getType() + "&7) - &f" + announcement.getMessage());
            }
        }
    }

    private boolean listByType(CommandSender sender, String type) {
        if (!sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
            messageSender(sender, "&cYou don't have permission to view announcements of type: " + type);
            return false;
        }
        if (!announceManager.validateAnnounceType(type)) {
            messageSender(sender, "&cInvalid announcement type: " + type);
            return false;
        }
        
        messageSender(sender, "&6Announcements for type &a" + type + "&6:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            if (announcement.getType().equalsIgnoreCase(type)) {
                messageSender(sender, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getMessage());
            }
        }
        return true;
    }

    private void listTypes(CommandSender sender) {
        for (String type : announceManager.getAnnounceTypes()) {
            if (sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                messageSender(sender, " &7- &f" + type);
            }
        }
    }
}