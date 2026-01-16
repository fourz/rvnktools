package org.fourz.rvnktools.command.manager.commands;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Collections;

/**
 * World swap subcommand that allows players to teleport between worlds
 * while preserving their locations in each world using RVNKCore tracking.
 */
public class WorldSwapSubCommand extends BaseSubCommand {
    
    private final MultiverseCore multiverseCore;
    private final RVNKCore rvnkCore;
    private static final String DEFAULT_EVENT_WORLD = "event";
    
    public WorldSwapSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "worldswap",
              "Teleport between worlds while preserving locations",
              "/rvnktools teleport worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);

        this.rvnkCore = plugin;
        
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
        
        // Check if player is already in the target world
        if (currentWorld.equals(targetWorld)) {
            sender.sendMessage("§c✖ You are already in world '" + targetWorld + "'.");
            return true;
        }
        
        try {
            PlayerWorldService playerWorldService = rvnkCore.getService(PlayerWorldService.class);
            
            // Get player's last known location in the target world
            playerWorldService.getLastKnownLocation(playerId, targetWorld)
                .thenAccept(locationOpt -> {
                    if (locationOpt.isPresent()) {
                        // Player has been to this world before - teleport to last location
                        PlayerWorldDataDTO worldData = locationOpt.get();
                        World bukkitWorld = Bukkit.getWorld(targetWorld);
                        
                        if (bukkitWorld == null) {
                            sender.sendMessage("§c✖ Failed to find world '" + targetWorld + "'.");
                            return;
                        }
                        
                        Location targetLocation = new Location(
                            bukkitWorld,
                            worldData.getLastX(),
                            worldData.getLastY(), 
                            worldData.getLastZ(),
                            worldData.getLastYaw(),
                            worldData.getLastPitch()
                        );
                        
                        // Perform teleportation on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(targetLocation);
                            sender.sendMessage("§a✓ Welcome back to " + targetWorld + "! " +
                                             "You have visited this world " + worldData.getVisitCount() + " times.");
                            
                            // Log the successful teleport
                            logger.info(player.getName() + " used worldswap to teleport to last location in " + 
                                       targetWorld + " (" + worldData.getLastX() + ", " + 
                                       worldData.getLastY() + ", " + worldData.getLastZ() + ")");
                        });
                        
                    } else {
                        // Player has never been to this world - teleport to spawn
                        Location spawnLocation = mvWorld.getSpawnLocation();
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(spawnLocation);
                            sender.sendMessage("§a✓ Welcome to " + targetWorld + " for the first time! " +
                                             "Teleported to world spawn.");
                            
                            // Log the first-time teleport
                            logger.info(player.getName() + " used worldswap to teleport to " + targetWorld + 
                                       " for the first time (spawn location)");
                        });
                    }
                })
                .exceptionally(throwable -> {
                    // Handle errors gracefully - fallback to spawn
                    sender.sendMessage("§c✖ Failed to retrieve location data. Teleporting to world spawn.");
                    logger.error("Failed to get last known location for " + player.getName() + 
                               " in world " + targetWorld, throwable);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.teleport(mvWorld.getSpawnLocation());
                    });
                    
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage("§c✖ Failed to access world tracking service. Please try again.");
            logger.error("Failed to get PlayerWorldService for worldswap command", e);
        }
        
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
}
