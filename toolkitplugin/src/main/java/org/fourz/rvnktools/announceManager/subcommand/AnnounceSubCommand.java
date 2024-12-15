package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.util.ChatFormat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;

public abstract class AnnounceSubCommand {
    protected final AnnounceManager announceManager;
    protected final RVNKTools plugin;

    public AnnounceSubCommand(AnnounceManager announceManager, RVNKTools plugin) {
        this.announceManager = announceManager;
        this.plugin = plugin;
    }

    public abstract boolean execute(Player player, String[] args);

    protected void messagePlayer(Player player, String message) {
        if (message == null) {
            player.sendMessage("");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        
        TextComponent constructedMessage = ChatFormat.parse(message, plugin.linkMaker);
        player.spigot().sendMessage(constructedMessage);
    }

    protected boolean checkPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            messagePlayer(player, "&cYou don't have permission to do this");
            return false;
        }
        return true;
    }
}