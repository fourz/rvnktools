package org.fourz.rvnktools.command.manager.commands;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Collections;

/**
 * World swap subcommand that allows players to teleport between worlds
 * while preserving their locations in each world.
 */
public class WorldSwapSubCommand extends BaseSubCommand {
    
    private final MultiverseCore multiverseCore;
    private final HashMap<UUID, HashMap<String, Location>> playerLocations;
    private static final String DEFAULT_EVENT_WORLD = "event";
    
    public WorldSwapSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "worldswap", 
              "Teleport between worlds while preserving locations", 
              "/rvnktools teleport worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);
        
        this.playerLocations = new HashMap<>();
        
        Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin == null || !mvPlugin.isEnabled()) {
            logger.warning("Multiverse-Core not found or not enabled! World swap features will not work.");
            multiverseCore = null;
        } else {
            multiverseCore = (MultiverseCore) mvPlugin;
            logger.info("WorldSwap integrated with Multiverse-Core");
        }
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender; // Already validated as player-only
        
        if (multiverseCore == null) {
            sender.sendMessage("§c✖ Multiverse-Core is not available. World swap feature is disabled.");
            return true;
        }
        
        String currentWorld = player.getWorld().getName();
        String targetWorld = args.length > 0 ? args[0] : DEFAULT_EVENT_WORLD;
        UUID playerId = player.getUniqueId();
        
        // Get the MVWorldManager to verify worlds
        MVWorldManager worldManager = multiverseCore.getMVWorldManager();
        
        // If the specified target world doesn't exist, show an error
        if (!worldManager.isMVWorld(targetWorld)) {
            sender.sendMessage("§c✖ World '" + targetWorld + "' does not exist!");
            return true;
        }
        
        // Check Multiverse access permission
        MultiverseWorld mvWorld = worldManager.getMVWorld(targetWorld);
        if (!multiverseCore.getMVPerms().canEnterWorld(player, mvWorld)) {
            sender.sendMessage("§c✖ You don't have permission to enter that world!");
            return true;
        }
        
        // Initialize player's location map if it doesn't exist
        playerLocations.computeIfAbsent(playerId, _ -> new HashMap<>());
        
        // Store current location in the map
        playerLocations.get(playerId).put(currentWorld, player.getLocation());
        
        // Determine where to teleport
        Location targetLocation;
        
        // Check if we have a stored location for the target world
        if (playerLocations.get(playerId).containsKey(targetWorld)) {
            targetLocation = playerLocations.get(playerId).get(targetWorld);
            sender.sendMessage("§6⚙ Returning to your previous location in " + targetWorld + "...");
        } else {
            targetLocation = mvWorld.getSpawnLocation();
            sender.sendMessage("§6⚙ Teleporting to " + targetWorld + " spawn...");
        }
        
        // Perform the teleport
        player.teleport(targetLocation);
        sender.sendMessage("§a✓ Successfully teleported to " + targetWorld);
        logger.info(player.getName() + " used worldswap to teleport from " + currentWorld + " to " + targetWorld);
        
        return true;
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1 && multiverseCore != null) {
            String partial = args[0].toLowerCase();
            List<String> worlds = new ArrayList<>();
            
            for (MultiverseWorld world : multiverseCore.getMVWorldManager().getMVWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase().startsWith(partial)) {
                    // Only suggest worlds the player can access
                    if (sender instanceof Player && 
                        multiverseCore.getMVPerms().canEnterWorld((Player) sender, world)) {
                        worlds.add(worldName);
                    }
                }
            }
            return worlds;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get stored locations for debugging or administrative purposes.
     * 
     * @return Map of player locations by world
     */
    public HashMap<UUID, HashMap<String, Location>> getPlayerLocations() {
        return playerLocations;
    }
}
