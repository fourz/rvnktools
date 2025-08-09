package org.fourz.rvnktools.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnktools.core.RVNKCoreBootstrap;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

/**
 * DEPRECATED: This is a legacy command for backward compatibility.
 * Please use '/teleport worldswap' or '/rvnktools teleport worldswap' instead.
 * This command will be removed in a future version.
 * 
 * @deprecated Use new command framework instead
 * @since 1.0.0
 */
@Deprecated(since = "1.1", forRemoval = true)
public class LegacyWorldSwapCommand implements CommandExecutor {
    
    private final RVNKTools plugin;
    private final RVNKCoreBootstrap coreBootstrap;
    private final LogManager logger;
    
    public LegacyWorldSwapCommand(RVNKTools plugin, RVNKCoreBootstrap coreBootstrap) {
        this.plugin = plugin;
        this.coreBootstrap = coreBootstrap;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Show deprecation warning
        player.sendMessage("§e⚠ This command is deprecated and will be removed in a future version.");
        player.sendMessage("§7Please use '/teleport worldswap' or '/rvnktools teleport worldswap' instead.");
        
        // Check for correct usage
        if (args.length != 1) {
            player.sendMessage("§cUsage: /worldswap <world_name>");
            return true;
        }
        
        String targetWorldName = args[0];
        
        // Validate that the target world exists
        World targetWorld = Bukkit.getWorld(targetWorldName);
        if (targetWorld == null) {
            player.sendMessage("§c✖ World '" + targetWorldName + "' does not exist.");
            return true;
        }
        
        // Check if player is already in the target world
        if (player.getWorld().getName().equals(targetWorldName)) {
            player.sendMessage("§c✖ You are already in world '" + targetWorldName + "'.");
            return true;
        }
        
        try {
            PlayerWorldService playerWorldService = coreBootstrap.getService(PlayerWorldService.class);
            
            // Get player's last known location in the target world
            playerWorldService.getLastKnownLocation(player.getUniqueId(), targetWorldName)
                .thenAccept(locationOpt -> {
                    if (locationOpt.isPresent()) {
                        // Player has been to this world before - teleport to last location
                        PlayerWorldDataDTO worldData = locationOpt.get();
                        Location targetLocation = new Location(
                            targetWorld,
                            worldData.getLastX(),
                            worldData.getLastY(), 
                            worldData.getLastZ(),
                            worldData.getLastYaw(),
                            worldData.getLastPitch()
                        );
                        
                        // Perform teleportation on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(targetLocation);
                            player.sendMessage("§aWelcome back to " + targetWorldName + "! " +
                                             "You have visited this world " + worldData.getVisitCount() + " times.");
                            
                            // Log the successful teleport
                            logger.info("Player " + player.getName() + " teleported to last location in " + 
                                       targetWorldName + " (" + worldData.getLastX() + ", " + 
                                       worldData.getLastY() + ", " + worldData.getLastZ() + ")");
                        });
                        
                    } else {
                        // Player has never been to this world - teleport to spawn
                        Location spawnLocation = targetWorld.getSpawnLocation();
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(spawnLocation);
                            player.sendMessage("§aWelcome to " + targetWorldName + " for the first time! " +
                                             "Teleported to world spawn.");
                            
                            // Log the first-time teleport
                            logger.info("Player " + player.getName() + " teleported to " + targetWorldName + 
                                       " for the first time (spawn location)");
                        });
                    }
                })
                .exceptionally(throwable -> {
                    // Handle errors gracefully
                    player.sendMessage("§c✖ Failed to retrieve location data. Teleporting to world spawn.");
                    logger.error("Failed to get last known location for " + player.getName() + 
                               " in world " + targetWorldName, throwable);
                    
                    // Fallback to spawn location
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.teleport(targetWorld.getSpawnLocation());
                    });
                    
                    return null;
                });
                
        } catch (ServiceException e) {
            player.sendMessage("§c✖ Failed to access world tracking service. Please try again.");
            logger.error("Failed to get PlayerWorldService for worldswap command", e);
        }
        
        return true;
    }
}
