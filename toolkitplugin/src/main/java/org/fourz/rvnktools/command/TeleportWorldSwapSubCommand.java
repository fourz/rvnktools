package org.fourz.rvnktools.command;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.RVNKTools;

import java.util.HashMap;
import java.util.UUID;

public class TeleportWorldSwapSubCommand extends RVNKToolsSubCommand {

    private final MultiverseCore multiverseCore;
    private final HashMap<UUID, HashMap<String, Location>> playerLocations = new HashMap<>();
    private static final String DEFAULT_EVENT_WORLD = "event";

    public TeleportWorldSwapSubCommand(RVNKTools plugin) {
        super(plugin);
        Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin == null || !mvPlugin.isEnabled()) {
            plugin.getLogger().severe("Multiverse-Core not found or not enabled! World swap features will not work.");
            multiverseCore = null;
        } else {
            multiverseCore = (MultiverseCore) mvPlugin;
        }
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageSender(sender, "&cThis command can only be used by players.");
            return true;
        }

        if (multiverseCore == null) {
            messageSender(sender, "&cMultiverse-Core is not available. World swap features are disabled.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        String currentWorld = player.getWorld().getName();
        String targetWorld = DEFAULT_EVENT_WORLD;

        // Check if a specific world was specified
        if (args.length > 0) {
            targetWorld = args[0];
        }

        // Get the MVWorldManager to verify worlds
        MVWorldManager worldManager = multiverseCore.getMVWorldManager();
        
        // If the specified target world doesn't exist, show an error
        if (!worldManager.isMVWorld(targetWorld)) {
            messageSender(player, "&cThe world '" + targetWorld + "' does not exist.");
            return true;
        }

        // Check Multiverse access permission
        if (!multiverseCore.getMVPerms().canEnterWorld(player, worldManager.getMVWorld(targetWorld))) {
            messageSender(player, "&cYou don't have permission to enter " + targetWorld);
            return true;
        }

        // Initialize player's location map if it doesn't exist
        if (!playerLocations.containsKey(playerId)) {
            playerLocations.put(playerId, new HashMap<>());
        }
        
        // Store current location in the map
        playerLocations.get(playerId).put(currentWorld, player.getLocation());
        
        // Determine where to teleport
        Location targetLocation;
        
        // Check if we have a stored location for the target world
        if (playerLocations.get(playerId).containsKey(targetWorld)) {
            targetLocation = playerLocations.get(playerId).get(targetWorld);
        } else {
            // No stored location, use the world's spawn point
            MultiverseWorld mvWorld = worldManager.getMVWorld(targetWorld);
            targetLocation = mvWorld.getSpawnLocation();
        }
        
        // Perform the teleport
        player.teleport(targetLocation);
        messageSender(player, "&aTeleported to " + targetWorld);
        
        return true;
    }

    /**
     * Method used for WorldSwap command to get stored locations
     */
    public HashMap<UUID, HashMap<String, Location>> getPlayerLocations() {
        return playerLocations;
    }
}
