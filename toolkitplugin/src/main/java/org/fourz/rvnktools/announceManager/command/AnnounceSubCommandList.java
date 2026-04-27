package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import java.util.ArrayList;
import java.util.List;

public class AnnounceSubCommandList extends AnnounceSubCommand {
    
    public AnnounceSubCommandList(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("all")) {
                if (!sender.hasPermission("rvnktools.announce.type.*")) {
                    messageSender(sender, "&cYou don't have permission to list all announcements");
                    return false;
                }
                listAll(sender);
                return true;
            } else if (args[1].equalsIgnoreCase("types")) {
                listTypes(sender);
                return true;
            } else {
                return listByType(sender, args[1]);
            }
        }
        listAccessible(sender);
        return true;
    }

    private void listAll(CommandSender sender) {
        List<Announcement> all = announceManager.getAnnouncements();
        messageSender(sender, "&6All announcements &7(" + all.size() + "):");
        for (Announcement a : all) {
            messageSender(sender, " &7- &3" + a.getId() + " &7(&a" + a.getType() + "&7)" + scheduleSuffix(a) + " &7- &f" + a.getMessage());
        }
    }

    private void listAccessible(CommandSender sender) {
        List<Announcement> all = announceManager.getAnnouncements();
        int count = 0;
        List<String> lines = new ArrayList<>();
        for (Announcement a : all) {
            if (sender.hasPermission("rvnktools.announce.type.*") ||
                sender.hasPermission("rvnktools.announce.type." + a.getType().toLowerCase())) {
                lines.add(" &7- &3" + a.getId() + " &7(&a" + a.getType() + "&7)" + scheduleSuffix(a) + " &7- &f" + a.getMessage());
                count++;
            }
        }
        messageSender(sender, "&6Available announcements &7(" + count + "):");
        for (String line : lines) messageSender(sender, line);
    }

    private boolean listByType(CommandSender sender, String type) {
        if (!sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
            messageSender(sender, "&cYou don't have permission to view announcements of type: " + type);
            return false;
        }
        if (!announceManager.validateAnnounceType(type)) {
            messageSender(sender, "&cInvalid announcement type: " + type);
            return false;
        }

        List<String> lines = new ArrayList<>();
        for (Announcement a : announceManager.getAnnouncements()) {
            if (a.getType().equalsIgnoreCase(type)) {
                lines.add(" &7- &3" + a.getId() + scheduleSuffix(a) + " &7- &f" + a.getMessage());
            }
        }
        messageSender(sender, "&6Announcements for type &a" + type + "&6 &7(" + lines.size() + "):");
        for (String line : lines) messageSender(sender, line);
        return true;
    }

    private String scheduleSuffix(Announcement a) {
        if (a.getDate() == null) return "";
        String orig = a.getOriginalDateString();
        if (orig != null && orig.matches("\\d{2}-\\d{2}")) {
            return " &e[annual:" + orig + "]";
        }
        return " &e[date:" + a.getDate() + "]";
    }

    private void listTypes(CommandSender sender) {
        for (String type : announceManager.getAnnounceTypes()) {
            if (sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                messageSender(sender, " &7- &f" + type);
            }
        }
    }
}