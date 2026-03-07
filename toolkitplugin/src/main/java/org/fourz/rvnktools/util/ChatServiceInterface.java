package org.fourz.rvnktools.util;

import java.util.Map;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.linkMaker.LinkMaker;

/**
 * @deprecated Use {@link org.fourz.rvnkcore.util.chat.ChatServiceInterface} instead.
 * This interface is kept for backwards compatibility only.
 */
@Deprecated
public interface ChatServiceInterface {
    void sendMessage(Player player, String message);
    void sendMessage(Player player, String message, LinkMaker linkMaker);
    void sendMessage(Player player, String message, Map<String, String> linkMap);

    void broadcastMessage(String message);
    String formatMessage(Player player, String message);    

    void sendTitle(Player player, String message);

    void sendActionBar(Player player, String message);

    net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String message);
    
    String parseTitle(String message);
}