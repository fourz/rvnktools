package org.fourz.rvnktools.command;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;

public class DiscordCommand implements CommandExecutor {
    private LinkMaker linkMaker;

    public DiscordCommand(RVNKTools plugin) {
        linkMaker = plugin.linkMaker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("discord")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                TextComponent constructedMessage = 
                    ChatFormat.parse("&oJoin our {discord-link}!", linkMaker);

                // Send the combined message
                player.spigot().sendMessage(constructedMessage);
            } else {
                sender.sendMessage("This command can only be used by players.");
            }
            return true;
        }
        return false;
    }
}