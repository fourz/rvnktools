
package org.fourz.rvnktools.util;

import org.bukkit.entity.Player;

public interface ChatServiceInterface {
    void sendMessage(Player player, String message);
    void broadcastMessage(String message);
    String formatMessage(Player player, String message);
}