
package org.fourz.rvnktools.util;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.linkMaker.LinkMaker;

public interface ChatServiceInterface {
    void sendMessage(Player player, String message);
    void sendMessage(Player player, String message, LinkMaker linkMaker);
    void broadcastMessage(String message);
    String formatMessage(Player player, String message);    
}