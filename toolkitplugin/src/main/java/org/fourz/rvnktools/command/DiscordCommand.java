package org.fourz.rvnktools.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DiscordCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("discord")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Create the main message component
                TextComponent message = new TextComponent("Click here");
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        "https://discord.com/channels/452900386794111006/698630242801156106"));

                // Add additional text
                TextComponent additionalText = new TextComponent(" to join our Discord!");

                // Combine the components
                message.addExtra(additionalText);

                // Send the combined message
                player.spigot().sendMessage(message);
            } else {
                sender.sendMessage("This command can only be used by players.");
            }
            return true;
        }
        return false;
    }
}