package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;

public class TPSCommand implements CommandExecutor {

    private static final DecimalFormat df = new DecimalFormat("#.##");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tps")) {
            double[] tps = new double[3];
            try {
                Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                tps = (double[]) server.getClass().getField("recentTps").get(server);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Runtime runtime = Runtime.getRuntime();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

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

            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(message.toString());
            } else {
                sender.sendMessage(message.toString().replaceAll("§[0-9a-fk-or]", ""));
            }

            return true;
        }
        return false;
    }

    private String formatTPS(double tps) {
        return (tps > 18.0 ? "§a" : tps > 16.0 ? "§e" : "§c") + df.format(Math.min(tps, 20.0));
    }

    private String formatMemory(long bytes) {
        long megabytes = bytes / (1024 * 1024);
        return df.format(megabytes) + " MB";
    }
}
