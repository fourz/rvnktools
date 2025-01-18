package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandNow extends AnnounceSubCommand {
    
    public AnnounceSubCommandNow(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rvnktools.command.announce.now")) {
            messageSender(sender, "&cYou don't have permission to trigger announcements");
            return false;
        }

        if (args.length < 2) {
            messageSender(sender, "&cAnnouncement ID cannot be empty");
            return false;
        }

        String id = args[1];
        if (announceManager.sendAnnouncementNow(sender, id)) {
            return true;
        } else {
            messageSender(sender, "&cInvalid announcement ID: " + id);
            return false;
        }
    }
}