package org.fourz.rvnktools.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

public class BroadcastCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private static final String BROADCAST_PERMISSION = "rvnktools.command.broadcast"; // Permission node

    public BroadcastCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the sender has permission
        if (!sender.hasPermission(BROADCAST_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Check if there is at least one argument (the message to broadcast)
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /broadcast <message>");
            return false;
        }

        // Join the args into a single message
        String message = String.join(" ", args);
        
        // Format the message with colors and broadcast it to all players
        String formattedMessage = ChatColor.GOLD + "[Broadcast] " + ChatColor.RESET + message;
        Bukkit.broadcastMessage(formattedMessage);

        return true;
    }
}
