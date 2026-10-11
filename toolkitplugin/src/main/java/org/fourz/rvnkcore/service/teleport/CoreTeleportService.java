package org.fourz.rvnkcore.service.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

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
 * Every teleport runs on the main thread, and its future completes with the boolean
 * {@code Player.teleport} returned, after the teleport ran (#2279).
 *
 * @since 1.3.0
 */
public class CoreTeleportService implements ITeleportService {

    private final Plugin plugin;
    private final LogManager logger;
    /** Runs a task on the server main thread; the Bukkit scheduler in production. */
    private final Executor mainThread;

    public CoreTeleportService(Plugin plugin) {
        this(plugin, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * @param mainThread runs a task on the server main thread. Tests pass a direct or queued
     *                   executor; production uses the Bukkit scheduler.
     */
    CoreTeleportService(Plugin plugin, Executor mainThread) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.mainThread = mainThread;
    }

    /**
     * Runs {@code teleport} on the main thread and completes with the value it returns: the
     * result of {@code Player.teleport}, so a teleport another plugin cancels completes
     * {@code false}. The future never completes before the teleport has run (#2279).
     * An exception, or a scheduler that refuses the task, completes {@code false}.
     */
    private CompletableFuture<Boolean> onMainThread(String what, BooleanSupplier teleport) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            mainThread.execute(() -> {
                boolean landed;
                try {
                    landed = teleport.getAsBoolean();
                } catch (Exception e) {
                    logger.warning("Error teleporting " + what + ": " + e.getMessage());
                    landed = false;
                }
                result.complete(landed);
            });
        } catch (Exception e) {
            logger.warning("Could not schedule teleport " + what + ": " + e.getMessage());
            result.complete(false);
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> teleportToPlayer(Player player, Player target) {
        return onMainThread("player to player", () ->
                player.isOnline() && target.isOnline() && player.teleport(target.getLocation()));
    }

    @Override
    public CompletableFuture<Boolean> teleportToCoordinates(Player player, double x, double y, double z) {
        return onMainThread("player to coordinates", () ->
                player.isOnline() && player.teleport(new Location(player.getWorld(), x, y, z)));
    }

    @Override
    public CompletableFuture<Boolean> teleportPlayerToPlayer(CommandSender commander, Player playerToTeleport, Player target) {
        return onMainThread("player to player", () -> {
            if (commander instanceof Player && !hasPermission(commander, ITeleportService.PERM_TP_OTHERS)) {
                return false;
            }
            return playerToTeleport.isOnline() && target.isOnline()
                    && playerToTeleport.teleport(target.getLocation());
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
