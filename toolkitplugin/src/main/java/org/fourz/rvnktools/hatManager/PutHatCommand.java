package org.fourz.rvnktools.hatManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.RVNKTools;

public class PutHatCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public PutHatCommand(RVNKTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Usage: /puthat <customModelData>");
            return true;
        }

        int customModelData;
        try {
            customModelData = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The custom model data must be a number.");
            return true;
        }

        Player player = (Player) sender;
        Entity nearestEntity = getNearestVillager(player);

        if (nearestEntity == null || !(nearestEntity instanceof LivingEntity)) {
            player.sendMessage("No nearby mob found.");
            return true;
        }

        CustomCarvedPumpkinUtils.setCustomCarvedPumpkinOnMob(nearestEntity, customModelData, (RVNKTools) plugin);

        player.sendMessage("MickyHat applied to " + ((LivingEntity) nearestEntity).getName() + ".");
        return true;
    }

    private Entity getNearestEntity(Player player) {
        double closestDistance = Double.MAX_VALUE;
        Entity closestEntity = null;

        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof LivingEntity) {
                double distance = entity.getLocation().distance(player.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }

    private Entity getNearestVillager(Player player) {
        double closestDistance = Double.MAX_VALUE;
        Entity closestEntity = null;

        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Villager) {
                double distance = entity.getLocation().distance(player.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }
}
