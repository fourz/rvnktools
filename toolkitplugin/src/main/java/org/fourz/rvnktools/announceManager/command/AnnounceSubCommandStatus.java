package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnounceSubCommandStatus extends AnnounceSubCommand {

    public AnnounceSubCommandStatus(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            return showPlayerStatus((Player) sender);
        }
        return showSystemStatus(sender);
    }

    private boolean showPlayerStatus(Player player) {
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

    private boolean showSystemStatus(CommandSender sender) {
        List<Announcement> announcements = announceManager.getAnnouncements();
        java.util.Set<String> types = announceManager.getAnnounceTypes();

        // Count by type
        Map<String, Integer> typeCounts = new HashMap<>();
        for (Announcement a : announcements) {
            String type = a.getType() != null ? a.getType() : "unknown";
            typeCounts.merge(type, 1, Integer::sum);
        }

        messageSender(sender, "&6=== Announcement System Status ===");
        messageSender(sender, "");
        messageSender(sender, " &7Total announcements: &f" + announcements.size());
        messageSender(sender, " &7Registered types: &f" + types.size());
        messageSender(sender, "");

        if (typeCounts.isEmpty()) {
            messageSender(sender, " &7No announcements found");
        } else {
            messageSender(sender, " &7By type:");
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                messageSender(sender, "  &a" + entry.getKey() + "&7: &f" + entry.getValue());
            }
        }

        messageSender(sender, "");
        messageSender(sender, " &7Available types:");
        for (String type : types) {
            messageSender(sender, "  &7- &f" + type);
        }
        return true;
    }
}
