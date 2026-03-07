package org.fourz.rvnktools.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.UUID;

public class MickyHatPlaceListener implements Listener {

    // Store the last interaction time for each player
    private final HashMap<UUID, Long> lastInteraction = new HashMap<>();
    private static final long COOLDOWN_TIME = 2000; // Cooldown in milliseconds (2 seconds)

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure it's a block placement action
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();

            // Check if the item is a carved pumpkin
            if (item != null && item.getType() == Material.CARVED_PUMPKIN) {
                ItemMeta meta = item.getItemMeta();

                // Check if the item has custom model data
                if (meta != null && meta.hasCustomModelData()) {

                    UUID playerId = event.getPlayer().getUniqueId();
                    long currentTime = System.currentTimeMillis();                    

                    // Check if the player is on cooldown
                    if (lastInteraction.containsKey(playerId) &&
                        (currentTime - lastInteraction.get(playerId)) < COOLDOWN_TIME) {
                        event.setCancelled(true);
                        return;
                    }

                    // Update the last interaction time
                    lastInteraction.put(playerId, currentTime);

                    // Cancel the interaction before the block is placed
                    event.setCancelled(true);

                    // Sync the player's inventory to correct the client-side state
                    event.getPlayer().updateInventory();

                    // Force a resync of the player's position
                    event.getPlayer().teleport(event.getPlayer().getLocation());

                    // Send message to player
                    event.getPlayer().sendMessage("Mickyhats cannot be placed place as a block.");
                }
            }
        }
    }
}
