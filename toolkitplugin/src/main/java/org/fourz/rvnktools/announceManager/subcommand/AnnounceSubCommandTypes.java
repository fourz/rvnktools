
package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandTypes extends AnnounceSubCommand {
    
    public AnnounceSubCommandTypes(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        messagePlayer(player, "&6=== Announcement Type Access ===");
        messagePlayer(player, "");
        
        for (String type : announceManager.getAnnounceTypes()) {
            String permission = "rvnktools.announce.type." + type.toLowerCase();
            if (player.hasPermission(permission)) {
                messagePlayer(player, " &a✔ &f" + type);
            } else {
                messagePlayer(player, " &c✘ &7" + type);
            }
        }
        
        messagePlayer(player, "");
        messagePlayer(player, "&7✔ = You have access to this type");
        messagePlayer(player, "&7✘ = You don't have access to this type");
        messagePlayer(player, "&7Use &5/announce list [type]&7 to see all of a given type.");
        return true;
    }
}