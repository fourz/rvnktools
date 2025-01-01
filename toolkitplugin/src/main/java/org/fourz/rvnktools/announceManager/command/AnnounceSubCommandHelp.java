package org.fourz.rvnktools.announceManager.command;

import org.bukkit.entity.Player;
import org.checkerframework.checker.units.qual.m;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandHelp extends AnnounceSubCommand {
    
    public AnnounceSubCommandHelp(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        String topic = args.length > 1 ? args[1] : null;
        return handleHelpCommand(player, topic);
    }

    private boolean handleHelpCommand(Player player, String topic) {
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
            case "set":
                return showSetHelp(player);
            case "help":
                messagePlayer(player, "&cAre you ok?");
                return true;
            default:
                messagePlayer(player, "&cUnknown help topic: " + topic);
                return false;
        }
    }

    private boolean showGeneralHelp(Player player) {
        messagePlayer(player, "&6=== Announcement Help ===");
        messagePlayer(player, "");
        messagePlayer(player, " &f/announce status &8- &7View your enabled announcements types");
        messagePlayer(player, " &f/announce toggle <type> &8- &7Enable/disable announcement types");
        messagePlayer(player, " &f/announce list &8- &7List all announcements");
        messagePlayer(player, " &f/announce list <type> &8- &7List all announcements by type");
        
        if (player.hasPermission("rvnktools.command.announce.add")) {
            messagePlayer(player, " &f/announce add &8- &7Create new announcements");
        }
        if (player.hasPermission("rvnktools.command.announce.set")) {
            messagePlayer(player, " &f/announce set &8- &7Modify announcement properties");
        }
        if (player.hasPermission("rvnktools.command.announce.delete")) {
            messagePlayer(player, " &f/announce delete &8- &7Remove announcements");
        }
        if (player.hasPermission("rvnktools.command.announce.now")) {
            messagePlayer(player, " &f/announce now &8- &7Trigger immediate broadcast");
        }

        messagePlayer(player, "");
        messagePlayer(player, "&7For detailed help: &f/announce help <command>");
        messagePlayer(player, "&8Example: &7/announce help toggle");
        return true;
    }

    private boolean showStatusHelp(Player player) {
        messagePlayer(player, "&6Help for &f/announce status");
        messagePlayer(player, "&7Shows your current announcement type preferences");
        messagePlayer(player, "&7Usage: &f/announce status");
        return true;
    }

    private boolean showToggleHelp(Player player) {
        messagePlayer(player, "&6Help for &f/announce toggle");
        messagePlayer(player, "&7Toggle specific announcement types on/off");
        messagePlayer(player, "&7Usage: &f/announce toggle <type>");
        return true;
    }

    private boolean showListHelp(Player player) {
        messagePlayer(player, "&6Help for &f/announce list");
        messagePlayer(player, "&7List all announcements or filter by type");
        messagePlayer(player, "&7Usage: &f/announce list [type|all]");
        return true;
    }

    private boolean showAddHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.add")) {
            return true;
        }
        messagePlayer(player, "&6Help for &f/announce add");
        messagePlayer(player, "&7Add a new announcement to the system");
        messagePlayer(player, "&7Usage: &f/announce add <message>");
        return true;
    }

    private boolean showDeleteHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.delete")) {
            return true;
        }
        messagePlayer(player, "&6Help for &f/announce delete");
        messagePlayer(player, "&7Remove an announcement from the system");
        messagePlayer(player, "&7Usage: &f/announce delete <id>");
        return true;
    }

    private boolean showNowHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.now")) {
            return true;
        }
        messagePlayer(player, "&6Help for &f/announce now");
        messagePlayer(player, "&7Immediately broadcast an announcement");
        messagePlayer(player, "&7Usage: &f/announce now <id>");
        return true;
    }

    private boolean showSetHelp(Player player) {
        if (!player.hasPermission("rvnktools.command.announce.set")) {
            return true;
        }
        messagePlayer(player, "&6Help for &f/announce set");
        messagePlayer(player, "&7Modify properties of an existing announcement");
        messagePlayer(player, "&7Usage: &f/announce set <id> <property> <value>");
        messagePlayer(player, "");
        messagePlayer(player, "&7Properties:");
        messagePlayer(player, "&8- &frecurrence &7(daily, none, 90m, 2h)");
        messagePlayer(player, "&8- &fdate &7(YYYY-MM-DD)");
        messagePlayer(player, "&8- &ftype &7(announcement type)");
        messagePlayer(player, "&8- &fpermission &7(permission node or 'none')");
        messagePlayer(player, "&8- &fmessage &7(announcement message)");
        messagePlayer(player, "");
        messagePlayer(player, "Examples:");
        messagePlayer(player, "&8- &f/announce set ad_woodnthings recurrence daily");
        messagePlayer(player, "&8- &f/announce set xmas date 2020-12-25");
        messagePlayer(player, "&8- &f/announce set events permission none");   

        return true;
    }
}