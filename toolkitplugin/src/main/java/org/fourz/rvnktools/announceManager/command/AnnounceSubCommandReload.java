package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandReload extends AnnounceSubCommand {
    public AnnounceSubCommandReload(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            if (!sender.hasPermission("rvnktools.command.announce.reload")) {
                messageSender(sender, "You do not have permission to reload announcements");
                return true;
            }
        }        

        announceManager.saveConfig();
        announceManager.reloadConfig();
        messageSender(sender, "Announcements configuration reloaded successfully");
        return true;
    }

    @Override
    public boolean execute(Player player, String[] args) {
        return execute((CommandSender)player, args);
    }
}