
package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandReload extends AnnounceSubCommand {
    public AnnounceSubCommandReload(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("rvnktools.command.announce.reload")) {
            messagePlayer(player, "You do not have permission to reload announcements");
            return true;
        }

        announceManager.saveConfig();
        announceManager.reloadConfig();
        messagePlayer(player, "Announcements configuration reloaded successfully");
        return true;
    }
}