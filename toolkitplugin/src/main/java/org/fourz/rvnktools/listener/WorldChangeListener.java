package org.fourz.rvnktools.listener;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Random;

import org.fourz.rvnktools.util.ChatFormat;
import me.clip.placeholderapi.PlaceholderAPI;

public class WorldChangeListener implements Listener {
    private JavaPlugin plugin;
    private List<String> messages;
    private int chance;
    
    public WorldChangeListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

/*     @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
    } */
}
