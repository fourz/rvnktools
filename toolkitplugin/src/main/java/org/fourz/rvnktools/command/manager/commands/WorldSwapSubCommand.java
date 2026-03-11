package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerWorldDataDTO;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

/**
 * World swap subcommand that allows players to teleport between worlds
 * while preserving their locations in each world using RVNKCore tracking.
 *
 * Uses RVNKWorlds IWorldService for world management (via ServiceRegistry).
 * Falls back to graceful error if RVNKWorlds is not available.
 */
public class WorldSwapSubCommand extends BaseSubCommand {

    private Object worldService; // IWorldService (accessed via reflection to avoid hard dependency)
    private final RVNKCore rvnkCore;
    private static final String DEFAULT_EVENT_WORLD = "event";
    private boolean worldServiceAvailable = false;

    public WorldSwapSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "worldswap",
              "Teleport between worlds while preserving locations",
              "/rvnktools teleport worldswap [world]",
              "rvnktools.command.teleport.worldswap", true);

        this.rvnkCore = plugin;
        // IWorldService lookup deferred to first use (RVNKWorlds registers after RVNKCore)
    }

    /**
     * Lazily resolves IWorldService from ServiceRegistry on first use.
     * Deferred because RVNKWorlds registers its services after RVNKCore enables.
     */
    private void ensureWorldService() {
        if (worldServiceAvailable) return;
        try {
            Class<?> iWorldServiceClass = Class.forName("org.fourz.RVNKWorlds.service.IWorldService");
            ServiceRegistry registry = rvnkCore.getServiceRegistry();
            this.worldService = registry.getService(iWorldServiceClass);
            this.worldServiceAvailable = true;
            logger.debug("WorldSwap integrated with RVNKWorlds IWorldService");
        } catch (ClassNotFoundException e) {
            // RVNKWorlds not in classpath — fallback to Bukkit
        } catch (Exception e) {
            logger.debug("IWorldService not yet available: " + e.getMessage());
        }
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        ensureWorldService();
        Player player = (Player) sender; // Already validated as player-only

        String currentWorld = player.getWorld().getName();
        String targetWorld = args.length > 0 ? args[0] : DEFAULT_EVENT_WORLD;
        UUID playerId = player.getUniqueId();

        // Check if target world exists - try IWorldService first, fallback to Bukkit
        boolean worldExists;
        if (worldServiceAvailable) {
            try {
                // Reflection: worldService.isWorldActive(targetWorld)
                java.lang.reflect.Method isActiveMethod = worldService.getClass().getMethod("isWorldActive", String.class);
                worldExists = (boolean) isActiveMethod.invoke(worldService, targetWorld);
            } catch (Exception e) {
                logger.warning("Error calling IWorldService.isWorldActive(): " + e.getMessage());
                worldExists = Bukkit.getWorld(targetWorld) != null;
            }
        } else {
            worldExists = Bukkit.getWorld(targetWorld) != null;
        }

        if (!worldExists) {
            sender.sendMessage("§c✖ World '" + targetWorld + "' does not exist or is not active!");
            return true;
        }

        // Check RVNKWorlds access permission using standard permission nodes
        String accessPerm = "rvnkworlds.world.access." + targetWorld.toLowerCase();
        if (!player.hasPermission(accessPerm) && !player.hasPermission("rvnkworlds.world.access.*")) {
            sender.sendMessage("§c✖ You don't have permission to access world '" + targetWorld + "'!");
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
                        World world = Bukkit.getWorld(targetWorld);
                        if (world == null) {
                            sender.sendMessage("§c✖ Failed to load world '" + targetWorld + "'.");
                            return;
                        }
                        Location spawnLocation = world.getSpawnLocation();

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
                        World world = Bukkit.getWorld(targetWorld);
                        if (world != null) {
                            player.teleport(world.getSpawnLocation());
                        } else {
                            player.sendMessage("§c✖ Failed to load world spawn location.");
                        }
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
        ensureWorldService();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> worlds = new ArrayList<>();

            // Get worlds from IWorldService if available, otherwise use Bukkit
            Map<String, World> activeWorlds = null;
            if (worldServiceAvailable) {
                try {
                    // Reflection: worldService.getActiveWorlds()
                    java.lang.reflect.Method getActiveMethod = worldService.getClass().getMethod("getActiveWorlds");
                    activeWorlds = (Map<String, World>) getActiveMethod.invoke(worldService);
                } catch (Exception e) {
                    logger.warning("Error calling IWorldService.getActiveWorlds(): " + e.getMessage());
                    activeWorlds = null;
                }
            }

            // Fallback to Bukkit worlds if IWorldService not available
            if (activeWorlds == null) {
                activeWorlds = new java.util.HashMap<>();
                for (World world : Bukkit.getWorlds()) {
                    activeWorlds.put(world.getName(), world);
                }
            }

            for (String worldName : activeWorlds.keySet()) {
                if (worldName.toLowerCase().startsWith(partial)) {
                    // Only suggest worlds the player can access
                    String accessPerm = "rvnkworlds.world.access." + worldName.toLowerCase();
                    if (!(sender instanceof Player) ||
                        ((Player) sender).hasPermission(accessPerm) ||
                        ((Player) sender).hasPermission("rvnkworlds.world.access.*")) {
                        worlds.add(worldName);
                    }
                }
            }
            return worlds;
        }
        return Collections.emptyList();
    }
}
