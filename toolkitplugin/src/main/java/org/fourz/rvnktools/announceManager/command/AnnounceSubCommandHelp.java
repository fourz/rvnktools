package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;

public class AnnounceSubCommandHelp extends AnnounceSubCommand {

    public AnnounceSubCommandHelp(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String topic = args.length > 1 ? args[1] : null;
        return handleHelpCommand(sender, topic);
    }

    private boolean handleHelpCommand(CommandSender sender, String topic) {
        if (topic == null) {
            return showGeneralHelp(sender);
        }

        switch (topic.toLowerCase()) {
            case "status":
                return showStatusHelp(sender);
            case "toggle":
                return showToggleHelp(sender);
            case "list":
                return showListHelp(sender);
            case "add":
                return showAddHelp(sender);
            case "delete":
                return showDeleteHelp(sender);
            case "now":
                return showNowHelp(sender);
            case "set":
                return showSetHelp(sender);
            case "help":
                messageSender(sender, "&cAre you ok?");
                return true;
            default:
                messageSender(sender, "&cUnknown help topic: " + topic);
                return false;
        }
    }

    private boolean showGeneralHelp(CommandSender sender) {
        messageSender(sender, "&6=== Announcement Help ===");
        messageSender(sender, "");
        messageSender(sender, " &f/announce status &8- &7View announcement system status");
        messageSender(sender, " &f/announce toggle <type> &8- &7Enable/disable announcement types (player only)");
        messageSender(sender, " &f/announce list &8- &7List all announcements");
        messageSender(sender, " &f/announce list <type> &8- &7List all announcements by type");
        messageSender(sender, " &f/announce types &8- &7List available announcement types");

        if (sender.hasPermission("rvnktools.command.announce.add")) {
            messageSender(sender, " &f/announce add <type> <id> <message> &8- &7Create new announcement");
        }
        if (sender.hasPermission("rvnktools.command.announce.set")) {
            messageSender(sender, " &f/announce set <id> <property> <value> &8- &7Modify announcement properties");
        }
        if (sender.hasPermission("rvnktools.command.announce.delete")) {
            messageSender(sender, " &f/announce delete <id> &8- &7Remove an announcement");
        }
        if (sender.hasPermission("rvnktools.command.announce.now")) {
            messageSender(sender, " &f/announce now <id> &8- &7Trigger immediate broadcast");
        }

        messageSender(sender, "");
        messageSender(sender, "&7For detailed help: &f/announce help <command>");
        return true;
    }

    private boolean showStatusHelp(CommandSender sender) {
        messageSender(sender, "&6Help for &f/announce status");
        messageSender(sender, "&7Shows announcement system status and type preferences");
        messageSender(sender, "&7Usage: &f/announce status");
        return true;
    }

    private boolean showToggleHelp(CommandSender sender) {
        messageSender(sender, "&6Help for &f/announce toggle");
        messageSender(sender, "&7Toggle specific announcement types on/off");
        messageSender(sender, "&7Usage: &f/announce toggle <type>");
        return true;
    }

    private boolean showListHelp(CommandSender sender) {
        messageSender(sender, "&6Help for &f/announce list");
        messageSender(sender, "&7List all announcements or filter by type");
        messageSender(sender, "&7Usage: &f/announce list [type|all]");
        return true;
    }

    private boolean showAddHelp(CommandSender sender) {
        if (!sender.hasPermission("rvnktools.command.announce.add")) {
            return true;
        }
        messageSender(sender, "&6Help for &f/announce add");
        messageSender(sender, "&7Add a new announcement to the system");
        messageSender(sender, "&7Usage: &f/announce add <type> <id> <message>");
        return true;
    }

    private boolean showDeleteHelp(CommandSender sender) {
        if (!sender.hasPermission("rvnktools.command.announce.delete")) {
            return true;
        }
        messageSender(sender, "&6Help for &f/announce delete");
        messageSender(sender, "&7Remove an announcement from the system");
        messageSender(sender, "&7Usage: &f/announce delete <id>");
        return true;
    }

    private boolean showNowHelp(CommandSender sender) {
        if (!sender.hasPermission("rvnktools.command.announce.now")) {
            return true;
        }
        messageSender(sender, "&6Help for &f/announce now");
        messageSender(sender, "&7Immediately broadcast an announcement");
        messageSender(sender, "&7Usage: &f/announce now <id>");
        return true;
    }

    private boolean showSetHelp(CommandSender sender) {
        if (!sender.hasPermission("rvnktools.command.announce.set")) {
            return true;
        }
        messageSender(sender, "&6Help for &f/announce set");
        messageSender(sender, "&7Modify properties of an existing announcement");
        messageSender(sender, "&7Usage: &f/announce set <id> <property> <value>");
        messageSender(sender, "");
        messageSender(sender, "&7Properties:");
        messageSender(sender, "&8- &frecurrence &7(daily, none, 90m, 2h)");
        messageSender(sender, "&8- &fdate &7(YYYY-MM-DD)");
        messageSender(sender, "&8- &ftype &7(announcement type)");
        messageSender(sender, "&8- &fpermission &7(permission node or 'none')");
        messageSender(sender, "&8- &fmessage &7(announcement message)");
        return true;
    }
}
