
package org.fourz.rvnktools.announceManager;

import org.bukkit.entity.Player;

public class AnnounceCommandHelp {
    private final AnnounceCommand command;

    public AnnounceCommandHelp(AnnounceCommand command) {
        this.command = command;
    }

    public boolean handleHelpCommand(Player player, String topic) {
        if (topic == null) {
            return showGeneralHelp(player);
        }

        switch (topic.toLowerCase()) {
            case "status":
                return showStatusHelp(player);
            case "toggle":
                return showToggleHelp(player);
            case "list":
                return showListHelp(player);
            case "add":
                return showAddHelp(player);
            case "delete":
                return showDeleteHelp(player);
            case "now":
                return showNowHelp(player);
            case "help":
                command.messagePlayer(player, "&cAre you ok?");
                return true;
            default:
                command.messagePlayer(player, "&cUnknown help topic: " + topic);
                return false;
        }
    }

    private boolean showGeneralHelp(Player player) {
        command.messagePlayer(player, "&6=== Announcement Help ===");
        command.messagePlayer(player, "");
        command.messagePlayer(player, " &f/announce status &8- &7View your enabled announcements types");
        command.messagePlayer(player, " &f/announce toggle <type> &8- &7Enable/disable announcement types");
        command.messagePlayer(player, " &f/announce list &8- &7List all announcements");
        command.messagePlayer(player, " &f/announce list <type> &8- &7List all announcements by type");
        
        if (player.hasPermission("rvnktools.command.announce.add")) {
            command.messagePlayer(player, " &f/announce add &8- &7Create new announcements");
        }
        if (player.hasPermission("rvnktools.command.announce.delete")) {
            command.messagePlayer(player, " &f/announce delete &8- &7Remove announcements");
        }
        if (player.hasPermission("rvnktools.command.announce.now")) {
            command.messagePlayer(player, " &f/announce now &8- &7Trigger immediate broadcast");
        }

        command.messagePlayer(player, "");
        command.messagePlayer(player, "&7For detailed help: &f/announce help <command>");
        command.messagePlayer(player, "&8Example: &7/announce help toggle");
        return true;
    }

    private boolean showStatusHelp(Player player) {
        command.messagePlayer(player, "&6Help for &f/announce status");
        command.messagePlayer(player, "&7Shows your current announcement type preferences");
        command.messagePlayer(player, "&7Usage: &f/announce status");
        return true;
    }

    private boolean showToggleHelp(Player player) {
        command.messagePlayer(player, "&6Help for &f/announce toggle");
        command.messagePlayer(player, "&7Toggle specific announcement types on/off");
        command.messagePlayer(player, "&7Usage: &f/announce toggle <type>");
        return true;
    }

    private boolean showListHelp(Player player) {
        command.messagePlayer(player, "&6Help for &f/announce list");
        command.messagePlayer(player, "&7List all announcements or filter by type");
        command.messagePlayer(player, "&7Usage: &f/announce list [type|all]");
        return true;
    }

    private boolean showAddHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.add")) {
            return true;
        }
        command.messagePlayer(player, "&6Help for &f/announce add");
        command.messagePlayer(player, "&7Add a new announcement to the system");
        command.messagePlayer(player, "&7Usage: &f/announce add <message>");
        return true;
    }

    private boolean showDeleteHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.delete")) {
            return true;
        }
        command.messagePlayer(player, "&6Help for &f/announce delete");
        command.messagePlayer(player, "&7Remove an announcement from the system");
        command.messagePlayer(player, "&7Usage: &f/announce delete <id>");
        return true;
    }

    private boolean showNowHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.now")) {
            return true;
        }
        command.messagePlayer(player, "&6Help for &f/announce now");
        command.messagePlayer(player, "&7Immediately broadcast an announcement");
        command.messagePlayer(player, "&7Usage: &f/announce now <id>");
        return true;
    }
}