package org.fourz.rvnktools.command.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;

import java.util.Collections;
import java.util.List;

public class BroadcastCommand extends BaseCommand {

    public BroadcastCommand(RVNKCore plugin) {
        super(plugin, "broadcast",
              "Broadcasts a message to all players",
              "/broadcast <message>",
              "rvnktools.command.broadcast");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: " + getUsage());
            return false;  // Return false to show usage in plugin.yml
        }

        // Join the args into a single message
        String message = String.join(" ", args);

        // Format with ChatColor (not color codes) and broadcast
        String formattedMessage = ChatColor.GOLD + "[Broadcast] " + ChatColor.RESET + message;
        Bukkit.broadcastMessage(formattedMessage);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
