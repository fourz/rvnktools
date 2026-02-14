package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.concurrent.CompletableFuture;

/**
 * Core implementation of teleport service.
 * Provides base teleportation functionality that can be extended by other plugins.
 *
 * This service handles:
 * - Player-to-player teleportation
 * - Coordinate-based teleportation
 * - Relative coordinate calculation
 * - Permission verification
 *
 * @since 1.3.0
 */
public class CoreTeleportService implements ITeleportService {

    private final Plugin plugin;
    private final LogManager logger;

    public CoreTeleportService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
    }

    @Override
    public CompletableFuture<Boolean> teleportToPlayer(Player player, Player target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!player.isOnline()) {
                    return false;
                }

                if (!target.isOnline()) {
                    return false;
                }

                // Perform teleport on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(target.getLocation());
                });

                return true;
            } catch (Exception e) {
                logger.warning("Error teleporting player to player: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> teleportToCoordinates(Player player, double x, double y, double z) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!player.isOnline()) {
                    return false;
                }

                Location targetLocation = new Location(player.getWorld(), x, y, z);

                // Perform teleport on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(targetLocation);
                });

                return true;
            } catch (Exception e) {
                logger.warning("Error teleporting player to coordinates: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> teleportPlayerToPlayer(CommandSender commander, Player playerToTeleport, Player target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Permission check
                if (commander instanceof Player && !hasPermission(commander, "rvnktools.command.tp.others")) {
                    return false;
                }

                if (!playerToTeleport.isOnline() || !target.isOnline()) {
                    return false;
                }

                // Perform teleport on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    playerToTeleport.teleport(target.getLocation());
                });

                return true;
            } catch (Exception e) {
                logger.warning("Error teleporting player to player: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Location calculateRelativeCoordinates(Player player, String x, String y, String z) throws NumberFormatException {
        Location currentLoc = player.getLocation();

        double finalX = calculateCoordinate(currentLoc.getX(), x);
        double finalY = calculateCoordinate(currentLoc.getY(), y);
        double finalZ = calculateCoordinate(currentLoc.getZ(), z);

        return new Location(player.getWorld(), finalX, finalY, finalZ);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) {
            return sender.hasPermission(permission);
        }
        // Console always has permissions
        return true;
    }

    @Override
    public String getServiceVersion() {
        return "1.0.0-core";
    }

    /**
     * Calculate a single coordinate value, supporting relative (~) notation.
     *
     * @param current Current coordinate value
     * @param input Input string (can be ~, ~5, or absolute number)
     * @return Calculated coordinate value
     * @throws NumberFormatException if input cannot be parsed
     */
    private double calculateCoordinate(double current, String input) throws NumberFormatException {
        if (input.startsWith("~")) {
            // Relative coordinate
            if (input.equals("~")) {
                return current;
            }

            String offset = input.substring(1);
            if (offset.isEmpty()) {
                return current;
            }

            double offsetValue = Double.parseDouble(offset);
            return current + offsetValue;
        } else {
            // Absolute coordinate
            return Double.parseDouble(input);
        }
    }
}
