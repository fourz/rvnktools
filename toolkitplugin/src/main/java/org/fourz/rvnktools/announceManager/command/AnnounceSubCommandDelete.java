
package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandDelete extends AnnounceSubCommand {
    
    public AnnounceSubCommandDelete(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("rvnktools.command.announce.delete")) {
            messagePlayer(player, "&cYou don't have permission to delete announcements");
            return false;
        }

        if (args.length < 2) {
            messagePlayer(player, "&cAnnouncement ID cannot be empty");
            return false;
        }

        String id = args[1];
        if (announceManager.deleteAnnouncement(id)) {
            messagePlayer(player, "&aAnnouncement deleted successfully");
            return true;
        } else {
            messagePlayer(player, "&cFailed to delete announcement: " + id);
            return false;
        }
    }
}