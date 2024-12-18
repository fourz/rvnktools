package org.fourz.rvnktools.hatManager;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.rvnktools.RVNKTools;

public class CustomCarvedPumpkinUtils {

    public static void setCustomCarvedPumpkinOnMob(Entity entity, int customModelData, RVNKTools plugin) {
        // Ensure the entity is a LivingEntity
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) entity;

        // Create a Jack-o’-Lantern item
        ItemStack hat = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = hat.getItemMeta();

        // Set custom model data
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            hat.setItemMeta(meta);           
        } 

        // Set the jack-o’-lantern as the helmet
        livingEntity.getEquipment().setHelmet(hat);

        // Prevent the mob from dropping the helmet
        livingEntity.getEquipment().setHelmetDropChance(0.0f);

        // Refresh the entity's equipment
        livingEntity.getEquipment().setHelmet(livingEntity.getEquipment().getHelmet());
    }
}
