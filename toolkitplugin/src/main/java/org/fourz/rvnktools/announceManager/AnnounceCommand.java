package org.fourz.rvnktools.announceManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.command.*;

import java.util.HashMap;
import java.util.Map;

public class AnnounceCommand implements CommandExecutor {
    private final Map<String, AnnounceSubCommand> subcommands = new HashMap<>();
    private final AnnounceManager announceManager;
    private final RVNKTools plugin;

    public AnnounceCommand(AnnounceManager announceManager, RVNKTools plugin) {
        this.announceManager = announceManager;
        this.plugin = plugin;
        
        // Register subcommands
        subcommands.put("help", new AnnounceSubCommandHelp(announceManager, plugin));
        subcommands.put("types", new AnnounceSubCommandTypes(announceManager, plugin));
        subcommands.put("toggle", new AnnounceSubCommandToggle(announceManager, plugin));
        subcommands.put("list", new AnnounceSubCommandList(announceManager, plugin));
        subcommands.put("add", new AnnounceSubCommandAdd(announceManager, plugin));
        subcommands.put("delete", new AnnounceSubCommandDelete(announceManager, plugin));
        subcommands.put("now", new AnnounceSubCommandNow(announceManager, plugin));
        subcommands.put("status", new AnnounceSubCommandStatus(announceManager, plugin));
        subcommands.put("reload", new AnnounceSubCommandReload(announceManager, plugin));
        subcommands.put("pref", new AnnounceSubCommandPrefs(announceManager, plugin));
        subcommands.put("preference", new AnnounceSubCommandPrefs(announceManager, plugin));
        subcommands.put("set", new AnnounceSubCommandSet(announceManager, plugin));
        subcommands.put("update", new AnnounceSubCommandUpdate(announceManager, plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            if (!sender.hasPermission("rvnktools.command.announce")) {
                sender.sendMessage("You do not have permission to use this command");
                return true;
            }
        }

        if (args.length < 1) {
            sendUsage(sender);            
            return false;
        }

        String subcommand = args[0].toLowerCase();
        AnnounceSubCommand handler = subcommands.get(subcommand);        
        
        if (handler != null) {            
            return handler.execute(sender, args);
        } else {
            if (sender instanceof Player) {
                sendUsage((Player) sender);
            }
            return false;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage:");
        sender.sendMessage("/announce toggle <type> - Toggle announcement type");
        sender.sendMessage("/announce list - List all announcements");
        sender.sendMessage("/announce add <message> - Add new announcement");
        sender.sendMessage("/announce delete <id> - Remove an announcement");
        sender.sendMessage("/announce reload - Reload announcements configuration");
        sender.sendMessage("/announce update <id> <message> - Update an announcement");
        sender.sendMessage("/announce now <id> - Send announcement immediately");
        sender.sendMessage("/announce pref <type> - Set your announcement preferences");
        sender.sendMessage("/announce status - View announcement system status");
        sender.sendMessage("/announce reload - Reload announcements configuration");
    }
}

