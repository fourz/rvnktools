package org.fourz.rvnktools.listener;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Random;

import org.fourz.rvnkcore.util.ChatFormat;

import me.clip.placeholderapi.PlaceholderAPI;

public class JoinListener implements Listener {
    private JavaPlugin plugin;
    private List<String> messages;
    private List<String> newPlayerMessages;
    private int chance;
    
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
        chance = config.getInt("chance", 100); // Default chance is 100% if not specified
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Random random = new Random();
        Player player = event.getPlayer();

        if (player.hasPermission("rvnktools.welcome")) {     

            if (player.hasPlayedBefore()) {

                if (random.nextInt(100) < chance) {
                    String message = messages.get(random.nextInt(messages.size()));
                    messagePlayer(player, message);
                }

            } else {
                                
                String message = newPlayerMessages.get(random.nextInt(newPlayerMessages.size()));
                messagePlayer(player, message);
            }
        }
    }

    public void messagePlayer (Player player, String message) {
        //if placeholderAPI is enabled
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {

            //set any placeholders in the message
            message = PlaceholderAPI.setPlaceholders(player, message);
        } 
        
        //use ChatFormat to colorize the message and replace {player} with the player name
        message = ChatFormat.colorize(message.replace("{player}", player.getName())); 

        //send the message to the player
        player.sendMessage(message);        

    }
}