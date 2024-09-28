package org.fourz.rvnktools.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EventsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("events")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage("§a ---- §cEvent Info §fPage I §a----");
                player.sendMessage("§e Events Are scheduled every other weekend");
                player.sendMessage("§e Starting at 7:00PM CST.");
                player.sendMessage("§e Event Details will be posted on discord in advance.");
                player.sendMessage("§e ");
                player.sendMessage("§e https://discord.gg/5PvjD44Ba5");
            } else {
                sender.sendMessage("This command can only be used by players.");
            }
            return true;
        }
        return false;
    }
}