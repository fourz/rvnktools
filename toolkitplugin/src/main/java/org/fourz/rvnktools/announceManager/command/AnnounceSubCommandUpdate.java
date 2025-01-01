package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandUpdate extends AnnounceSubCommand {
    
    public AnnounceSubCommandUpdate(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!checkPermission(player, "rvnktools.command.announce.update")) {
            return true;
        }

        if (args.length < 3) {
            messagePlayer(player, "&cUsage: /announce update <id> <message>");
            return true;
        }

        String id = args[1];
        Announcement announcement = announceManager.getAnnouncement(id);
        
        if (announcement == null) {
            messagePlayer(player, "&cAnnouncement with ID '" + id + "' not found");
            return true;
        }

        // Combine remaining args into message
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }
        String newMessage = message.toString().trim();

        if (announceManager.updateAnnouncement(id, newMessage)) {
            messagePlayer(player, "&aAnnouncement updated successfully");
        } else {
            messagePlayer(player, "&cFailed to update announcement");
        }

        return true;
    }
}
