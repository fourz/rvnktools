
package org.fourz.rvnktools.announceManager;

import org.bukkit.entity.Player;

public interface PlayerMessageService {
    void sendMessage(Player player, String message);
    void broadcastMessage(String message);
    String formatMessage(Player player, String message);
}