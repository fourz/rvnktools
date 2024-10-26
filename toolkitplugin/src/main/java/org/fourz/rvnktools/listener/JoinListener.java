package org.fourz.rvnktools.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.fourz.rvnktools.util.ChatFormat;

public class JoinListener implements Listener {
    private JavaPlugin plugin;
    private List<String> messages;
    private List<String> newPlayerMessages;
    private int interval;
    
    public JoinListener(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "joinmessages.yml");
        if (!file.exists()) {
            plugin.saveResource("joinmessages.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        messages = config.getStringList("messages");
        newPlayerMessages = config.getStringList("new_player_messages");
        interval = config.getInt("chance", 40); // Default chance is 40%
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Random random = new Random();
        Player player = event.getPlayer();

        if (player.hasPlayedBefore()) {
            if (random.nextInt(100) < interval && player.hasPermission("rvnktools.welcome")) {
                String message = messages.get(random.nextInt(messages.size()));
                player.sendMessage(ChatFormat.colorize(message.replace("{player}", player.getName())));
            }
        } else {
            if (player.hasPermission("rvnktools.welcome.newplayer")) {
                String message = newPlayerMessages.get(random.nextInt(newPlayerMessages.size()));
                player.sendMessage(ChatFormat.colorize(message.replace("{player}", player.getName())));
            }
        }
    }
}