package org.fourz.rvnktools.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.framework.BaseCommand;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

/**
 * TPS Command using the new command framework.
 * Shows server performance information including TPS, memory usage, and system info.
 */
public class TPSCommand extends BaseCommand {

    private static final DecimalFormat df = new DecimalFormat("#.##");

    public TPSCommand(RVNKTools plugin) {
        super(plugin, "tps", 
              "Shows server performance information including TPS and memory usage", 
              "/tps",
              null); // No permission required
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Get TPS information
        double[] tps = new double[3];
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            tps = (double[]) server.getClass().getField("recentTps").get(server);
        } catch (Exception e) {
            logger.error("Failed to get TPS information", e);
            sender.sendMessage("§cError retrieving TPS information.");
            return true;
        }

        // Get system information
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        // Build the message
        StringBuilder message = new StringBuilder("§a---- §6Server Information §a----\n");
        message.append("§eTPS (1m, 5m, 15m): §f").append(formatTPS(tps[0])).append(", ")
                .append(formatTPS(tps[1])).append(", ").append(formatTPS(tps[2])).append("\n");
        message.append("§eOnline Players: §f").append(Bukkit.getOnlinePlayers().size()).append("/")
                .append(Bukkit.getMaxPlayers()).append("\n");
        message.append("§eMemory Usage: §f").append(formatMemory(runtime.totalMemory() - runtime.freeMemory()))
                .append("/").append(formatMemory(runtime.maxMemory())).append("\n");
        message.append("§eCPU Usage: §f").append(df.format(osBean.getSystemLoadAverage())).append("%\n");
        message.append("§eServer Version: §f").append(Bukkit.getVersion()).append("\n");
        message.append("§eJava Version: §f").append(System.getProperty("java.version")).append("\n");
        message.append("§eOperating System: §f").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version"));

        // Send the message
        if (sender instanceof Player) {
            sender.sendMessage(message.toString());
        } else {
            // Strip color codes for console
            sender.sendMessage(message.toString().replaceAll("§[0-9a-fk-or]", ""));
        }

        logger.debug("TPS command executed by " + sender.getName());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // TPS command doesn't need tab completion, it takes no arguments
        return Collections.emptyList();
    }

    /**
     * Format TPS value with appropriate colors.
     * Green for good (>18), yellow for okay (>16), red for poor.
     */
    private String formatTPS(double tps) {
        return (tps > 18.0 ? "§a" : tps > 16.0 ? "§e" : "§c") + df.format(Math.min(tps, 20.0));
    }

    /**
     * Format memory usage in MB.
     */
    private String formatMemory(long bytes) {
        long megabytes = bytes / (1024 * 1024);
        return df.format(megabytes) + " MB";
    }
}
