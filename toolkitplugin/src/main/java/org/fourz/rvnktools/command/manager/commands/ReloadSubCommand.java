package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for reloading plugin configuration.
 * Provides functionality to reload all plugin configurations and reinitialize services.
 */
public class ReloadSubCommand extends BaseSubCommand {

    public ReloadSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "reload",
              "Reload plugin configuration",
              "/rvnktools reload",
              "rvnktools.admin.reload", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§6[*] Reloading RVNKTools configuration...");
        logger.info("Plugin reload initiated by " + sender.getName());

        try {
            // Reload main config
            plugin.reloadConfig();

            // Reload individual components
            if (plugin.getLinkMaker() != null) {
                plugin.getLinkMaker().reloadConfig();
            }

            if (plugin.getCycleCommands() != null) {
                plugin.getCycleCommands().loadConfig();
            }

            // TODO: Add other component reloads as needed

            sender.sendMessage("§a[+] RVNKTools configuration reloaded successfully!");
            logger.info("Plugin configuration reloaded successfully by " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage("§c[!] Failed to reload configuration. Check console for errors.");
            logger.error("Failed to reload plugin configuration", e);
        }

        return true;
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
