package org.fourz.rvnkcore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.transfer.TransferService;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for native cross-server player transfer.
 *
 * <p>Usage: {@code /server transfer &lt;target&gt; [confirm]}. Player-only (the native transfer
 * packet needs a client). When {@code transfer.confirm} is enabled and no {@code confirm} arg is
 * supplied, a confirmation prompt is shown — spelling out that this is a server change, not an
 * inventory move (movement only, per the MVP scope).</p>
 *
 * <p>Decision + dispatch live in {@link TransferService}; this command handles argument parsing,
 * the console guard, the confirm gate and tab-completion.</p>
 *
 * @since 1.5.23
 */
public class ServerTransferCommand extends BaseCommand {

    private static final String SUB_TRANSFER = "transfer";

    public ServerTransferCommand(RVNKCore plugin) {
        super(plugin, "server",
              "Cross-server transfer commands",
              "/server transfer <target> [confirm]",
              "rvnkcore.transfer");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase(SUB_TRANSFER)) {
            sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE + getUsage());
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        TransferService service = plugin.getService(TransferService.class);
        if (service == null) {
            player.sendMessage(ChatColor.RED + "Transfer service is not available.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: " + ChatColor.WHITE + getUsage());
            List<String> targets = service.getConfig().getTargetNames();
            if (!targets.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Targets: " + String.join(", ", targets));
            }
            return true;
        }

        String targetName = args[1];
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");

        // Confirmation gate — only for a resolvable target, so bad names fail fast with a clear message.
        if (service.getConfig().isConfirmRequired() && !confirmed
                && service.getConfig().resolveTarget(targetName) != null) {
            player.sendMessage(ChatColor.YELLOW + "You are about to transfer to '"
                    + ChatColor.WHITE + targetName.toLowerCase() + ChatColor.YELLOW + "'.");
            player.sendMessage(ChatColor.GRAY + "This is a server change - movement only. "
                    + "Your items, economy and quest progress do NOT travel with you.");
            player.sendMessage(ChatColor.YELLOW + "Run " + ChatColor.WHITE + "/server transfer "
                    + targetName.toLowerCase() + " confirm" + ChatColor.YELLOW + " to proceed.");
            return true;
        }

        TransferService.TransferResult result = service.transfer(player, targetName);
        ChatColor color = result.isSuccess() ? ChatColor.AQUA : ChatColor.RED;
        player.sendMessage(color + result.message());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Collections.singletonList(SUB_TRANSFER).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase(SUB_TRANSFER)) {
            TransferService service = plugin.getService(TransferService.class);
            if (service == null) {
                return Collections.emptyList();
            }
            String partial = args[1].toLowerCase();
            return service.getConfig().getTargetNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase(SUB_TRANSFER)) {
            return Collections.singletonList("confirm").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
