
package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;

public class AnnounceSubCommandList extends AnnounceSubCommand {
    
    public AnnounceSubCommandList(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("all")) {
                if (!player.hasPermission("rvnktools.announce.type.*")) {
                    messagePlayer(player, "&cYou don't have permission to list all announcements");
                    return false;
                }
                listAll(player);
                return true;
            } else if (args[1].equalsIgnoreCase("types")) {
                listTypes(player);
                return true;
            } else {
                return listByType(player, args[1]);
            }
        }
        listAccessible(player);
        return true;
    }

    private void listAll(Player player) {
        messagePlayer(player, "&6All announcements:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            messagePlayer(player, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getMessage());
        }
    }

    private void listAccessible(Player player) {
        messagePlayer(player, "&6Available announcements:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            if (player.hasPermission("rvnktools.announce.type.*") || 
                player.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())) {
                messagePlayer(player, " &7- &3" + announcement.getId() + " &7(&a" + announcement.getType() + "&7) - &f" + announcement.getMessage());
            }
        }
    }

    private boolean listByType(Player player, String type) {
        if (!player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
            messagePlayer(player, "&cYou don't have permission to view announcements of type: " + type);
            return false;
        }
        if (!announceManager.validateAnnounceType(type)) {
            messagePlayer(player, "&cInvalid announcement type: " + type);
            return false;
        }
        
        messagePlayer(player, "&6Announcements for type &a" + type + "&6:");
        for (Announcement announcement : announceManager.getAnnouncements()) {
            if (announcement.getType().equalsIgnoreCase(type)) {
                messagePlayer(player, " &7- &3" + announcement.getId() + " &7- &f" + announcement.getMessage());
            }
        }
        return true;
    }

    private void listTypes(Player player) {
        for (String type : announceManager.getAnnounceTypes()) {
            if (player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                messagePlayer(player, " &7- &f" + type);
            }
        }
    }
}