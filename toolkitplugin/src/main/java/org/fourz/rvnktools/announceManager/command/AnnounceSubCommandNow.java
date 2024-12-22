
package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandNow extends AnnounceSubCommand {
    
    public AnnounceSubCommandNow(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("rvnktools.command.announce.now")) {
            messagePlayer(player, "&cYou don't have permission to trigger announcements");
            return false;
        }

        if (args.length < 2) {
            messagePlayer(player, "&cAnnouncement ID cannot be empty");
            return false;
        }

        String id = args[1];
        if (announceManager.sendAnnouncementNow(player, id)) {
            return true;
        } else {
            messagePlayer(player, "&cInvalid announcement ID: " + id);
            return false;
        }
    }
}