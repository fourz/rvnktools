
package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandToggle extends AnnounceSubCommand {
    
    public AnnounceSubCommandToggle(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        
        if (!(sender instanceof Player)) { 
            messageSender(sender, "&cYou must be a player to toggle announcement types");
            return false;
        }
        Player player = (Player) sender;
        
        if (args.length < 2) {
            messageSender(player, "&cAnnouncement type cannot be empty");
            return false;
        }

        String type = args[1];
        if (announceManager.validateAnnounceType(type)) {
            announceManager.toggleAnnouncementType(player, type);
            return true;
        } else {
            messageSender(player, "&cInvalid announcement type: " + type);
            return false;
        }
    }
}