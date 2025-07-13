package org.fourz.rvnktools.command.manager.commands;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.util.ChatFormat;

import java.util.Collections;
import java.util.List;

public class DiscordCommand extends BaseCommand {

    public DiscordCommand(RVNKTools plugin) {
        super(plugin, "discord",
              "Provides information about the server's discord",
              "/discord",
              null); // No permission required
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;
        TextComponent message = ChatFormat.parse("&oJoin our {discord-link}!", plugin.linkMaker);
        player.spigot().sendMessage(message);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
