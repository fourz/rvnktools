package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
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
    public boolean execute(CommandSender sender, String[] args) {
        // Check permissions
        if (!sender.hasPermission("rvnktools.command.announce.add")) {
            messageSender(sender, "&cYou don't have permission to add announcements");
            return false;
        }

        // Validate argument count
        if (args.length < 3) {
            messageSender(sender, "&cUsage: /announce add <type> <id> <message>");
            return false;
        }

        String type = args[1];
        String id = args[2];

        // Check if announcement already exists
        if (announceManager.announcementExists(id)) {
            messageSender(sender, "&cAn announcement with ID '" + id + "' already exists");
            return false;
        }
        
        // Get announcement type config
        AnnounceType announceType = announceManager.getAnnounceType(type);
        if (announceType == null) {
            messageSender(sender, "&cInvalid announcement type: " + type);
            return false;
        }

        // Check if type has a listing fee
        // Handle listing fee if sender is a player
        if (announceType.getListingFee() != null && sender instanceof Player) {
            Player player = (Player) sender;
            Double fee = announceType.getListingFee();
            String typeName = announceType.getId();            
            String feeDesc = CurrencyFormatter.format(fee, plugin);

            // Check if player has enough money
            if (!plugin.getEconomy().has(player, fee)) {                
                typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                messageSender(sender, "&c" + typeName + " announcements costs " + feeDesc + " &cto list.");
                return true;
            }
            
            // Charge the player and notify
            plugin.getEconomy().withdrawPlayer(player, fee);
            messageSender(sender, "&aYou have been debited " + feeDesc + " &afor this " + typeName + " listing.");
            
            // Output current balance to player
            String balanceDesc = CurrencyFormatter.format(plugin.getEconomy().getBalance(player), plugin);
            messageSender(sender, "&aYour balance is now " + balanceDesc);
        }

        // Join remaining args as message and add announcement
        String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        return announceManager.addAnnouncement(sender, type + " " + id + " " + message);
    }
}