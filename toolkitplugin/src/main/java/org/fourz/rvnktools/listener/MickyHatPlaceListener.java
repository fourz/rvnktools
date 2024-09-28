package org.fourz.rvnktools.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.block.Action;

public class MickyHatPlaceListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure it's a block placement action
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();

            if (item != null && item.getType() == Material.CARVED_PUMPKIN) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasCustomModelData()) {
                    int customModelData = meta.getCustomModelData();

                    // Cancel block placement if custom model data is greater than 0
                    if (customModelData > 0) {
                        // Cancel the interaction before the block is placed
                        event.setCancelled(true);

                        // Optionally log or notify the player
                        event.getPlayer().sendMessage("no");

                        // Sync the player's inventory to correct the client-side state
                        event.getPlayer().updateInventory();
                    }
                }
            }
        }
    }
}
