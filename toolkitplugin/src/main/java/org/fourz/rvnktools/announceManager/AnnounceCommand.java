package org.fourz.rvnktools.announceManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.subcommand.*;

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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        if (!sender.hasPermission("rvnktools.command.announce")) {
            sender.sendMessage("You do not have permission to use this command");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            sendUsage(player);
            return false;
        }

        String subcommand = args[0].toLowerCase();
        AnnounceSubCommand handler = subcommands.get(subcommand);
        
        if (handler != null) {
            return handler.execute(player, args);
        } else {
            sendUsage(player);
            return false;
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage("Usage:");
        player.sendMessage("/announce toggle <type> - Toggle announcement type");
        player.sendMessage("/announce list - List all announcements");
        player.sendMessage("/announce add <message> - Add new announcement");
        player.sendMessage("/announce remove <id> - Remove an announcement");
        player.sendMessage("/announce reload - Reload announcements configuration");
    }
}

