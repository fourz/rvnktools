package org.fourz.rvnkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.sightings.SightingService;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@code /rvnk entitlement <grant|revoke|list> <player|uuid> [list] [by]} (#2351).
 *
 * <p>Entitled lists, such as {@code mc-dev}, are game state. A quest grants one with an RVNKQuests
 * {@code COMMAND} reward, for example {@code rvnk entitlement grant {player} mc-dev quest:dev_key}.
 * When the player is online, a grant or a revoke records a sighting at once. Console-safe; permission
 * {@code rvnkcore.entitlement.admin}.</p>
 */
public class EntitlementSubCommand extends BaseSubCommand {

    static final String PERM_ADMIN = "rvnkcore.entitlement.admin";
    private static final List<String> VERBS = List.of("grant", "revoke", "list");

    public EntitlementSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "entitlement",
                "Grant, revoke or list a player's entitled lists (mc-dev)",
                "/rvnk entitlement <grant|revoke|list> <player|uuid> [list] [by]",
                null, false);
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/rvnk entitlement grant wizardofire mc-dev quest:dev_key",
                "/rvnk entitlement revoke wizardofire mc-dev",
                "/rvnk entitlement list wizardofire");
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        if (args.length < 2 || !VERBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            sendErrorMessage(sender, getUsage());
            return true;
        }
        SightingService svc = RVNKCore.getServiceSafe(SightingService.class);
        if (svc == null) {
            sendErrorMessage(sender, "Entitlements are not available: the database is not ready");
            return true;
        }
        String verb = args[0].toLowerCase(Locale.ROOT);
        Target target = resolve(args[1]);
        if (target == null) {
            sendErrorMessage(sender, "Unknown player: " + args[1] + " (use a name seen on this server, or a UUID)");
            return true;
        }
        try {
            switch (verb) {
                case "list" -> reply(sender, svc.lists(target.uuid()).thenApply(lists ->
                        target.name() + " has " + (lists.isEmpty() ? "no lists" : String.join(", ", lists))));
                case "grant", "revoke" -> {
                    if (args.length < 3) {
                        sendErrorMessage(sender, getUsage());
                        return true;
                    }
                    String list = SightingService.normaliseList(args[2]);
                    String by = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                            : (sender instanceof Player p ? "player:" + p.getName() : "console");
                    var change = verb.equals("grant")
                            ? svc.grant(target.uuid(), target.name(), list, by, target.address())
                            : svc.revoke(target.uuid(), target.name(), list, target.address());
                    reply(sender, change.thenApply(c -> target.name() + ": " + list + " "
                            + (verb.equals("grant") ? (c.changed() ? "granted" : "already granted")
                                                    : (c.changed() ? "revoked" : "was not granted"))
                            + (c.sighted() ? " (sighting recorded)" : "")
                            + ". Lists now: " + (c.lists().isEmpty() ? "none" : String.join(", ", c.lists()))));
                }
                default -> sendErrorMessage(sender, getUsage());
            }
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, e.getMessage());
        } catch (RejectedExecutionException e) {
            sendErrorMessage(sender, "Entitlement service busy; try again");
        }
        return true;
    }

    private record Target(UUID uuid, String name, InetAddress address) { }

    /** Online player, then a UUID, then a player this server has seen. Never a web lookup. */
    private static Target resolve(String arg) {
        Player online = Bukkit.getPlayerExact(arg);
        if (online == null) {
            try {
                online = Bukkit.getPlayer(UUID.fromString(arg));
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
        }
        if (online != null) {
            InetSocketAddress a = online.getAddress();
            return new Target(online.getUniqueId(), online.getName(), a == null ? null : a.getAddress());
        }
        try {
            UUID id = UUID.fromString(arg);
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            return new Target(id, op.getName() != null ? op.getName() : arg, null);
        } catch (IllegalArgumentException ignored) {
            // a name
        }
        // Players this server has seen; Bukkit.getOfflinePlayer(String) could block on a web lookup.
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (arg.equalsIgnoreCase(op.getName())) return new Target(op.getUniqueId(), op.getName(), null);
        }
        return null;
    }

    private void reply(CommandSender sender, CompletableFuture<String> message) {
        message.whenComplete((text, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                sendErrorMessage(sender, "Entitlement change failed: " + rootMessage(ex));
            } else {
                sendSuccessMessage(sender, text);
            }
        }));
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return VERBS;
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && !args[0].equalsIgnoreCase("list")) return List.of("mc-dev");
        return Collections.emptyList();
    }
}
