
package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandToggle extends AnnounceSubCommand {
    
    public AnnounceSubCommandToggle(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            messagePlayer(player, "&cAnnouncement type cannot be empty");
            return false;
        }

        String type = args[1];
        if (announceManager.validateAnnounceType(type)) {
            announceManager.toggleAnnouncementType(player, type);
            return true;
        } else {
            messagePlayer(player, "&cInvalid announcement type: " + type);
            return false;
        }
    }
}