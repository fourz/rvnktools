package org.fourz.rvnktools.commands;

import java.util.Arrays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.AnnouncementManager;

public class AnnouncementCommand implements CommandExecutor {
  private final AnnouncementManager announcementManager;

  public AnnouncementCommand(AnnouncementManager announcementManager) {
    this.announcementManager = announcementManager;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage("This command can only be used by players.");
      return true;
    }

    Player player = (Player) sender;

    if (args.length < 1) {
      player.sendMessage("Usage: /announcement -add <message> or /announcement -remove");
      return true;
    }

    String flag = args[0];
    String message = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";

    switch (flag) {
      case "-add":
        announcementManager.addAnnouncement(player, message);
        break;
      case "-remove":
        announcementManager.removeAnnouncement(player);
        break;
      default:
        player.sendMessage("Invalid flag. Use -add or -remove.");
        break;
    }

    return true;
  }
}