package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandDelete extends AnnounceSubCommand {
    
    public AnnounceSubCommandDelete(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rvnktools.command.announce.delete")) {
            messageSender(sender, "&cYou don't have permission to delete announcements");
            return false;
        }

        if (args.length < 2) {
            messageSender(sender, "&cAnnouncement ID cannot be empty");
            return false;
        }

        String id = args[1];
        if (announceManager.deleteAnnouncement(id)) {
            messageSender(sender, "&aAnnouncement deleted successfully");
            return true;
        } else {
            messageSender(sender, "&cFailed to delete announcement: " + id);
            return false;
        }
    }
}