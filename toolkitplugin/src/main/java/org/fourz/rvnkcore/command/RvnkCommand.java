package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;

/**
 * {@code /rvnk} — umbrella for RVNK network tools. Holds the {@code npc} subcommand of the NPC
 * bridge (#2213) and harness (#2248), the {@code region} subcommand (#2248), {@code entitlement}
 * (#2351) and {@code kit} (#2415); other tools can register further subcommands.
 *
 * <p>The command itself needs no permission; each verb checks its own {@code rvnkcore.*} node.</p>
 *
 * @since 1.5.99-alpha
 */
public class RvnkCommand extends BaseCommand {

    public RvnkCommand(RVNKCore plugin) {
        super(plugin, "rvnk",
                "RVNK network tools",
                "/rvnk <npc|region|entitlement|kit> <verb> [args]",
                null);
        registerSubCommand("npc", new NpcSubCommand(plugin, this));
        registerSubCommand("region", new RegionSubCommand(plugin, this));
        registerSubCommand("entitlement", new EntitlementSubCommand(plugin, this));
        registerSubCommand("kit", new KitSubCommand(plugin, this));
    }
}
