package org.fourz.rvnktools.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.ChatFormat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;

public abstract class RVNKToolsSubCommand {
    protected final RVNKTools plugin;

    public RVNKToolsSubCommand(RVNKTools plugin) {
        this.plugin = plugin;
    }

    public abstract boolean execute(CommandSender sender, String[] args);

    public boolean execute(Player player, String[] args) {
        return execute((CommandSender)player, args);
    }

    protected void messageSender(CommandSender sender, String message) {
        if (message == null) {
            sender.sendMessage("");
            return;
        }

        if (sender instanceof Player && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            message = PlaceholderAPI.setPlaceholders((Player) sender, message);
        }
        
        TextComponent constructedMessage = ChatFormat.parse(message, plugin.linkMaker);
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(constructedMessage);
        } else {
            sender.sendMessage(constructedMessage.toLegacyText());
        }
    }

    protected boolean checkPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            if (!sender.hasPermission(permission)) {
                messageSender(sender, "&cYou don't have permission to do this");
                return false;
            }
        }         
        return true;
    }
}
