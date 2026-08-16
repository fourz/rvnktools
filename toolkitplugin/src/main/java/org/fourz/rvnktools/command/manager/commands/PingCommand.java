package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;

public class PingCommand extends BaseCommand {
    private static final DecimalFormat df = new DecimalFormat("#.##");

    public PingCommand(RVNKCore plugin) {
        super(plugin, "ping",
              "Shows server performance information including TPS and memory usage",
              "/ping",
              null); // No permission required
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        double[] tps = readRecentTps();
        if (tps == null) {
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
        // getSystemLoadAverage() is a run-queue load average, not a CPU percentage, and returns a
        // negative value when the platform cannot supply it.
        double loadAverage = osBean.getSystemLoadAverage();
        message.append("§eLoad Average (1m): §f")
                .append(loadAverage < 0 ? "unavailable" : df.format(loadAverage)).append("\n");
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

        logger.debug("Ping command executed by " + sender.getName());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Ping command doesn't need tab completion, it takes no arguments
        return Collections.emptyList();
    }

    /**
     * Read the server's recent TPS averages.
     *
     * <p>RVNKCore compiles against spigot-api, which has no {@code Server#getTPS()}, so the call is
     * made reflectively. Paper declares {@code public double[] getTPS()} on {@code Server} and every
     * Ravenkraft tier runs Paper, so this is the primary path.</p>
     *
     * <p>The previous implementation reached through {@code CraftServer.getServer()} for the NMS
     * {@code recentTps} field. That threw {@code NoSuchFieldException} on Paper 26.2 — the field is
     * not public on the Mojang-mapped server — so the command reported an error every time it was
     * reached. Falling back to it keeps plain Spigot working (#1600).</p>
     *
     * @return the 1m/5m/15m averages, or {@code null} if neither source is available
     */
    private double[] readRecentTps() {
        try {
            Object result = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (result instanceof double[] tps && tps.length >= 3) {
                return tps;
            }
            logger.warning("Server.getTPS() returned an unusable value: " + result);
        } catch (NoSuchMethodException e) {
            logger.debug("Server.getTPS() not available - falling back to the NMS recentTps field");
        } catch (Exception e) {
            logger.warning("Server.getTPS() failed: " + e.getMessage());
        }

        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object tps = server.getClass().getField("recentTps").get(server);
            if (tps instanceof double[] values && values.length >= 3) {
                return values;
            }
        } catch (Exception e) {
            logger.error("Failed to get TPS information from both Server.getTPS() and recentTps", e);
        }
        return null;
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
