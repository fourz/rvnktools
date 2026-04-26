
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
            // Console: /announce toggle <type> <player>
            if (args.length < 3) {
                messageSender(sender, "&cUsage: /announce toggle <type> <player>");
                return false;
            }
            String type = args[1];
            Player target = plugin.getServer().getPlayer(args[2]);
            if (target == null) {
                messageSender(sender, "&cPlayer not found or not online: " + args[2]);
                return false;
            }
            if (!announceManager.validateAnnounceType(type)) {
                messageSender(sender, "&cInvalid announcement type: " + type);
                return false;
            }
            announceManager.toggleAnnouncementType(target, type);
            messageSender(sender, "&aToggled " + type + " for " + target.getName());
            return true;
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