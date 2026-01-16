package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandUpdate extends AnnounceSubCommand {
    
    public AnnounceSubCommandUpdate(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "rvnktools.command.announce.update")) {
            return true;
        }

        if (args.length < 3) {
            messageSender(sender, "&cUsage: /announce update <id> <message>");
            return true;
        }

        String id = args[1];
        Announcement announcement = announceManager.getAnnouncement(id);
        
        if (announcement == null) {
            messageSender(sender, "&cAnnouncement with ID '" + id + "' not found");
            return true;
        }

        // Combine remaining args into message
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }
        String newMessage = message.toString().trim();

        if (announceManager.updateAnnouncement(id, newMessage)) {
            messageSender(sender, "&aAnnouncement updated successfully");
        } else {
            messageSender(sender, "&cFailed to update announcement");
        }

        return true;
    }
}
