package org.fourz.rvnkcore.api.service;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for teleportation operations.
 * Provides layered architecture for core teleport functionality with extensibility for plugins.
 *
 * This service defines the contract for teleport operations that can be extended by RVNKWorlds
 * to add world-specific features like world swap, inventory isolation, and gamemode persistence.
 *
 * @since 1.3.0
 */
public interface ITeleportService {

    /**
     * Teleport a player to another player's location.
     *
     * Examples:
     * - /tp wizardofire (teleport sender to player)
     * - Sender must be a player
     *
     * @param player Player to teleport (sender)
     * @param target Target player location
     * @return CompletableFuture indicating success/failure
     */
    CompletableFuture<Boolean> teleportToPlayer(Player player, Player target);

    /**
     * Teleport a player to specific coordinates.
     *
     * Examples:
     * - /tp 0 64 0 (absolute coordinates)
     * - /tp ~ ~5 ~ (relative coordinates)
     *
     * @param player Player to teleport
     * @param x X coordinate (world of player is used)
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture indicating success/failure
     */
    CompletableFuture<Boolean> teleportToCoordinates(Player player, double x, double y, double z);

    /**
     * Teleport a player to another player's location (admin command).
     *
     * Examples:
     * - /tp wizardofire testplayer (teleport wizardofire to testplayer)
     * - /tp here wizardofire (teleport wizardofire to sender)
     *
     * @param commander Command sender (requires permission check)
     * @param playerToTeleport Player to teleport
     * @param target Target player location
     * @return CompletableFuture indicating success/failure
     */
    CompletableFuture<Boolean> teleportPlayerToPlayer(CommandSender commander, Player playerToTeleport, Player target);

    /**
     * Calculate relative coordinates for a player.
     *
     * Supports tildes (~) for relative positioning:
     * - ~ = current coordinate
     * - ~5 = current + 5
     * - ~-5 = current - 5
     * - 100 = absolute coordinate
     *
     * @param player Reference player for current position
     * @param x X coordinate (can be ~, ~5, or absolute number)
     * @param y Y coordinate (can be ~, ~5, or absolute number)
     * @param z Z coordinate (can be ~, ~5, or absolute number)
     * @return Calculated location in player's current world
     * @throws NumberFormatException if coordinates cannot be parsed
     */
    Location calculateRelativeCoordinates(Player player, String x, String y, String z) throws NumberFormatException;

    /**
     * Check if sender has specific teleport permission.
     *
     * @param sender Command sender
     * @param permission Permission node to check
     * @return true if sender has permission or is console
     */
    boolean hasPermission(CommandSender sender, String permission);

    /**
     * Get service version for debugging
     *
     * @return Version string (e.g., "1.0.0-core" or "1.0.0-world")
     */
    String getServiceVersion();
}
