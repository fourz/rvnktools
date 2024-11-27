
package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import java.util.Arrays;

public class AnnounceSubCommandStatus extends AnnounceSubCommand {
    
    public AnnounceSubCommandStatus(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        messagePlayer(player, "&6=== Enabled Announcement Types ===");
        messagePlayer(player, "");
        
        String[] playerDisabledTypes = announceManager.getPlayerDisabledAnnouncementTypes(player);
        for (String type : announceManager.getAnnounceTypes()) {
            if (player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                boolean isEnabled = !Arrays.asList(playerDisabledTypes).contains(type);
                String symbol = isEnabled ? "✔" : "✘";
                String color = isEnabled ? "&a" : "&c";
                messagePlayer(player, " " + color + symbol + " &f" + type);
            }
        }
        
        messagePlayer(player, "");
        messagePlayer(player, "&7✔ = Enabled for you");
        messagePlayer(player, "&7✘ = Disabled by you");
        messagePlayer(player, "&7Use &5/announce toggle [type]&7 to change settings");
        return true;
    }
}