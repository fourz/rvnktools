
package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import java.util.Arrays;

public class AnnounceSubCommandStatus extends AnnounceSubCommand {
    
    public AnnounceSubCommandStatus(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageSender(sender, "&cThis command can only be used by players");
            return true;
        }
        // Cast sender to Player
        Player player = (Player) sender;        
        messageSender(player, "&6=== Enabled Announcement Types ===");
        messageSender(player, "");
        
        String[] playerDisabledTypes = announceManager.getPlayerDisabledAnnouncementTypes(player);
        for (String type : announceManager.getAnnounceTypes()) {
            if (player.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                boolean isEnabled = !Arrays.asList(playerDisabledTypes).contains(type);
                String symbol = isEnabled ? "✔" : "✘";
                String color = isEnabled ? "&a" : "&c";
                messageSender(player, " " + color + symbol + " &f" + type);
            }
        }
       
        messageSender(player, "");
        messageSender(player, "&7✔ = Enabled for you");
        messageSender(player, "&7✘ = Disabled by you");
        messageSender(player, "&7Use &5/announce toggle [type]&7 to change settings");
        return true;
    }
}