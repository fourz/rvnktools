package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;

public class PutHatCommand extends BaseCommand {

    public PutHatCommand(RVNKCore plugin) {
        super(plugin, "puthat",
              "Applies a custom Jack-o-Lantern to the nearest mob.",
              "/puthat <customModelData>",
              "rvnktools.command.puthat");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) {
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            sender.sendMessage("Usage: " + getUsage());            return true;
        }

        int customModelData;
        try {
            customModelData = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("CustomModelData must be a number.");
            return true;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        LivingEntity nearestMob = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity == player) continue;
            double distance = entity.getLocation().distance(playerLoc);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestMob = entity;
            }
        }

        if (nearestMob == null || nearestDistance > 10) {
            sender.sendMessage("§cNo mob found nearby (within 10 blocks).");
            return true;
        }

        ItemStack hat = new ItemStack(Material.JACK_O_LANTERN);
        hat.getItemMeta().setCustomModelData(customModelData);
        nearestMob.getEquipment().setHelmet(hat);

        sender.sendMessage("§aApplied custom hat to " + nearestMob.getType().name() + "!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
