
package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.util.ChatFormat;
import me.clip.placeholderapi.PlaceholderAPI;

public class DefaultPlayerMessageService implements PlayerMessageService {
    private final boolean usingPlaceholderAPI;

    public DefaultPlayerMessageService() {
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public void sendMessage(Player player, String message) {
        player.sendMessage(formatMessage(player, message));
    }

    @Override
    public void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendMessage(player, message);
        }
    }

    @Override
    public String formatMessage(Player player, String message) {
        String formattedMessage = ChatFormat.colorize(message);
        if (usingPlaceholderAPI && player != null) {
            formattedMessage = PlaceholderAPI.setPlaceholders(player, formattedMessage);
        }
        return formattedMessage;
    }
}