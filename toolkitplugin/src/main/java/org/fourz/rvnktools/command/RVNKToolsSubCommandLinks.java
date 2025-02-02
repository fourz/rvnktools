package org.fourz.rvnktools.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;

public class RVNKToolsSubCommandLinks extends RVNKToolsSubCommand {
    
    public RVNKToolsSubCommandLinks(RVNKTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "rvnktools.links")) {
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            messageSender(sender, "&cUsage: /rvnktools links reload");
            return true;
        }

        if (!checkPermission(sender, "rvnktools.links.reload")) {
            return true;
        }

        plugin.linkMaker.reloadConfig();
        messageSender(sender, "&aLinks configuration reloaded successfully!");
        return true;
    }
}
