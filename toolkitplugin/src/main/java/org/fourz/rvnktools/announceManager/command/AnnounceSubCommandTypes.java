package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandTypes extends AnnounceSubCommand {
    
    public AnnounceSubCommandTypes(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        messageSender(sender, "&6=== Announcement Type Access ===");
        messageSender(sender, "");
        
        for (String type : announceManager.getAnnounceTypes()) {
            String permission = "rvnktools.announce.type." + type.toLowerCase();
            if (sender.hasPermission(permission)) {
                messageSender(sender, " &a✔ &f" + type);
            } else {
                messageSender(sender, " &c✘ &7" + type);
            }
        }
        
        messageSender(sender, "");
        messageSender(sender, "&7✔ = Access granted for this type");
        messageSender(sender, "&7✘ = Access denied for this type");
        messageSender(sender, "&7Use &5/announce list [type]&7 to see all of a given type.");
        return true;
    }
}