package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.util.CurrencyFormatter;
import java.util.Arrays;


public class AnnounceSubCommandAdd extends AnnounceSubCommand {
    
    public AnnounceSubCommandAdd(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("rvnktools.command.announce.add")) {
            messagePlayer(player, "&cYou don't have permission to add announcements");
            return false;
        }

        if (args.length < 3) {
            messagePlayer(player, "&cUsage: /announce add <type> <id> <message>");
            return false;
        }

        String type = args[1];
        String id = args[2];
        
        // Get announcement type config
        AnnounceType announceType = announceManager.getAnnounceType(type);
        if (announceType == null) {
            messagePlayer(player, "&cInvalid announcement type: " + type);
            return false;
        }

        // Check if type has a listing fee
        
        if (announceType.getListingFee() != null) {            
            Double fee = announceType.getListingFee();
            String typeName = announceType.getId();            
            String feeDesc = CurrencyFormatter.format(fee, plugin);

            // Check if player has enough money
            if (!plugin.getEconomy().has(player, fee)) {                

                typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                messagePlayer(player, "&c" + typeName + " announcements costs " + feeDesc + " &cto list.");
                return true;
            }
            
            // Charge the player
            plugin.getEconomy().withdrawPlayer(player, fee);
            messagePlayer(player, "&aYou have been debited " + feeDesc + " &afor this " + typeName + " listing.");

            // output current balance to player            
            String balanceDesc = CurrencyFormatter.format(plugin.getEconomy().getBalance(player), plugin);
            messagePlayer(player, "&aYour balance is now " + balanceDesc);
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        return announceManager.addAnnouncement(player, type + " " + id + " " + message);
    }
}