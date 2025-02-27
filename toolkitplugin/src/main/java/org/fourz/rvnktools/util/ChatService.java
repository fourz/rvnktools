package org.fourz.rvnktools.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.announceManager.PlayerMessageService;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import net.md_5.bungee.api.ChatMessageType;
import java.util.Map;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;

public class ChatService implements ChatServiceInterface {
    private final boolean usingPlaceholderAPI;

    public ChatService() {
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(formatMessage(player, message));
    }

    public void sendMessage(Player player, String message, LinkMaker linkMaker) {
        TextComponent textComponent = ChatFormat.parse(message, linkMaker);
        player.spigot().sendMessage(textComponent);
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

    @Override
    public void sendTitle(Player player, String message) {
        player.sendTitle(ChatFormat.parseTitle(message), "", 10, 100, 20);
    }

    @Override
    public void sendActionBar(Player player, String message) {        
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ChatFormat.parseActionBar(message));
    }

    @Override
    public void sendMessage(Player player, String message, Map<String, String> placeholders) {
        String formattedMessage = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formattedMessage = formattedMessage.replace(entry.getKey(), entry.getValue());
        }
        sendMessage(player, formattedMessage);
    }

    public String parseTitle(String message) {
        return ChatFormat.parseTitle(formatMessage(null, message));
    }

    public net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String message) {
        return ChatFormat.parseActionBar(formatMessage(null, message));
    }
}