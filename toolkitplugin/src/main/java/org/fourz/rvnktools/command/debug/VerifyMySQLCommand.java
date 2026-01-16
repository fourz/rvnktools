package org.fourz.rvnktools.command.debug;

import org.fourz.rvnkcore.integration.MySQLIntegrationVerification;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.util.ChatFormat;
import org.bukkit.command.CommandSender;

/**
 * Command to verify MySQL integration status across all RVNKCore components.
 */
public class VerifyMySQLCommand extends BaseCommand {

    public VerifyMySQLCommand(RVNKCore plugin) {
        super(plugin, "verifymysql",
              "Verify MySQL integration status",
              "/verifymysql",
              "rvnktools.admin.verify");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Starting MySQL integration verification..."));

        try {
            MySQLIntegrationVerification verification = new MySQLIntegrationVerification(plugin);
            MySQLIntegrationVerification.IntegrationStatus status = verification.verifyIntegration();

            String report = status.generateReport();

            // Send report to sender (split by lines to avoid message limits)
            String[] lines = report.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    if (line.startsWith("Overall Status:")) {
                        sender.sendMessage(ChatFormat.colorize("&a✓ " + line));
                    } else if (line.contains("✅")) {
                        sender.sendMessage(ChatFormat.colorize("&a" + line));
                    } else if (line.contains("❌")) {
                        sender.sendMessage(ChatFormat.colorize("&c" + line));
                    } else if (line.contains(":") && !line.startsWith("  ")) {
                        sender.sendMessage(ChatFormat.colorize("&e" + line));
                    } else {
                        sender.sendMessage(ChatFormat.colorize("&7" + line));
                    }
                }
            }

        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Error during verification: " + e.getMessage()));
            logger.error("MySQL verification failed", e);
        }

        return true;
    }
}
