
package org.fourz.rvnktools.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.announceManager.PlayerMessageService;
import me.clip.placeholderapi.PlaceholderAPI;

public class ChatService implements ChatServiceInterface {
    private final boolean usingPlaceholderAPI;

    public ChatService() {
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
