package org.fourz.rvnkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PortalDTO;
import org.fourz.rvnkcore.api.service.IPortalCommandExtension;
import org.fourz.rvnkcore.service.portal.PortalService;
import org.fourz.rvnkcore.service.transfer.TransferService;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Administration command for cross-server portals, and the single cross-system view of every
 * portal on the server (#1859).
 *
 * <p>Before this existed, portals could only be managed by standing next to them: creation and
 * removal were driven entirely by the registration sign, so a portal whose sign had been destroyed
 * was both invisible and impossible to remove. {@code PortalRepository.listAll()} was implemented
 * and unreachable.</p>
 *
 * <p><b>Ownership.</b> RVNKCore owns this command outright. RVNKWorlds cannot take it over by
 * declaring {@code portal} in its own {@code plugin.yml} — Bukkit awards the bare label to
 * whichever plugin registers first and {@code depend: [RVNKCore]} guarantees that is the core, so
 * a second declaration would silently yield only {@code rvnkworlds:portal}. Instead, other plugins
 * contribute through {@link IPortalCommandExtension}, resolved <b>per invocation</b> so a plugin
 * can be loaded or unloaded mid-session without leaving this command broken.</p>
 *
 * <p>Console is supported on every subcommand except {@code tp}, which needs a player to move.</p>
 *
 * @since 1.5.64
 */
public class PortalCommand extends BaseCommand {

    private static final List<String> CORE_SUBCOMMANDS =
            List.of("list", "info", "delete", "tp", "repair", "reload", "types");

    /** Providers already checked for subcommand collisions, so the warning is logged once each. */
    private final java.util.Set<String> warnedProviders = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PortalCommand(RVNKCore plugin) {
        super(plugin, "portal",
                "Cross-server portal administration",
                "/portal <list|info|delete|tp|repair|reload|types> [args]",
                "rvnkcore.portal.admin");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = tail(args);

        switch (sub) {
            case "list":
                return handleList(sender, rest);
            case "info":
                return handleInfo(sender, rest);
            case "delete":
                return handleDelete(sender, rest);
            case "tp":
                return handleTp(sender, rest);
            case "repair":
                return handleRepair(sender, rest);
            case "reload":
                return handleReload(sender);
            case "types":
                return handleTypes(sender);
            default:
                // Not a core subcommand — offer it to the extension before failing.
                IPortalCommandExtension extension = extension();
                if (extension != null && claims(extension, sub)) {
                    return extension.handle(sender, sub, rest);
                }
                showUsage(sender);
                return true;
        }
    }

    // ── Core subcommands ────────────────────────────────────────────────────────

    /**
     * Lists portals. Cross-server portals come from {@link PortalService}'s in-memory index (safe
     * on the main thread, and still correct during a database outage); world portals are appended
     * by the extension when one is registered.
     */
    private boolean handleList(CommandSender sender, String[] args) {
        PortalService service = portalService();
        if (service == null) {
            sender.sendMessage(ChatColor.RED + "Portal service is not available.");
            return true;
        }

        String worldFilter = null;
        String typeFilter = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--type") && i + 1 < args.length) {
                typeFilter = args[++i].toLowerCase(Locale.ROOT);
            } else if (!args[i].startsWith("--")) {
                worldFilter = args[i];
            }
        }

        boolean wantServer = typeFilter == null || typeFilter.equals("server");
        boolean wantWorld = typeFilter == null || typeFilter.equals("world");
        if (typeFilter != null && !wantServer && !wantWorld) {
            sender.sendMessage(ChatColor.RED + "Unknown type '" + typeFilter + "' (expected: server, world).");
            return true;
        }

        if (wantServer) {
            List<PortalDTO> portals = service.listPortalsInWorld(worldFilter);
            sender.sendMessage(ChatColor.GOLD + "Cross-server portals" + ChatColor.GRAY
                    + " (" + portals.size() + (worldFilter != null ? " in " + worldFilter : "") + ")");
            if (portals.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "  none");
            }
            for (PortalDTO portal : portals) {
                PortalService.Verification state = service.verify(portal.getPortalId());
                sender.sendMessage("  " + ChatColor.DARK_GRAY + "SERVER " + ChatColor.WHITE
                        + shortId(portal.getPortalId()) + ChatColor.GRAY + "  "
                        + portal.getWorld() + " " + portal.getX() + "," + portal.getY() + "," + portal.getZ()
                        + ChatColor.WHITE + "  -> " + portal.getTargetServer()
                        + "  " + colourFor(state) + state.name().toLowerCase(Locale.ROOT));
            }
        }

        if (wantWorld) {
            IPortalCommandExtension extension = extension();
            // "no extension" and "no world portals" are different facts. Rendering them the same way
            // is how an operator concludes there are no world portals when the plugin simply is not
            // loaded — the #1752 confusion, one level up.
            if (extension == null) {
                sender.sendMessage(ChatColor.GOLD + "World portals" + ChatColor.GRAY
                        + " — unavailable: no portal provider is loaded (RVNKWorlds absent).");
            } else {
                sender.sendMessage(ChatColor.GOLD + "World portals" + ChatColor.GRAY
                        + " (via " + extension.providerName() + ")");
                // Consume the future rather than dropping it: a provider that fails mid-listing
                // would otherwise print a header and then nothing, with no trace anywhere.
                java.util.concurrent.CompletableFuture<Void> listing =
                        extension.appendListing(sender, worldFilter);
                if (listing != null) {
                    listing.whenComplete((v, ex) -> {
                        if (ex != null) {
                            logger.warning("Portal extension '" + extension.providerName()
                                    + "' failed while listing world portals: " + ex);
                            sender.sendMessage(ChatColor.RED
                                    + "  (world portal listing failed — see console)");
                        }
                    });
                }
            }
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        PortalService service = portalService();
        if (service == null || args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /portal info <id>");
            return true;
        }

        Optional<PortalDTO> found = service.resolvePortal(args[0]);
        if (found.isEmpty()) {
            // Could be a world-portal id; say so rather than asserting it does not exist.
            IPortalCommandExtension extension = extension();
            sender.sendMessage(ChatColor.RED + "No cross-server portal matches '" + args[0]
                    + "' (unknown id, or an ambiguous prefix).");
            if (extension != null) {
                sender.sendMessage(ChatColor.GRAY + "  It may be a world portal — try "
                        + ChatColor.WHITE + "/world portal info " + args[0]);
            }
            return true;
        }

        PortalDTO portal = found.get();
        PortalService.Verification state = service.verify(portal.getPortalId());
        sender.sendMessage(ChatColor.GOLD + "Portal " + ChatColor.WHITE + portal.getPortalId());
        sender.sendMessage(ChatColor.GRAY + "  type       " + ChatColor.WHITE + "SERVER (cross-server)");
        sender.sendMessage(ChatColor.GRAY + "  location   " + ChatColor.WHITE + portal.getWorld()
                + " " + portal.getX() + "," + portal.getY() + "," + portal.getZ());
        sender.sendMessage(ChatColor.GRAY + "  target     " + ChatColor.WHITE + portal.getTargetServer());
        sender.sendMessage(ChatColor.GRAY + "  blocks     " + ChatColor.WHITE
                + (portal.getPortalBlocks() == null ? 0 : portal.getPortalBlocks().size()));
        sender.sendMessage(ChatColor.GRAY + "  owner      " + ChatColor.WHITE
                + (portal.getOwnerUuid() != null ? portal.getOwnerUuid() : "unknown"));
        sender.sendMessage(ChatColor.GRAY + "  state      " + colourFor(state) + state.name().toLowerCase(Locale.ROOT));
        if (state == PortalService.Verification.UNVERIFIED) {
            sender.sendMessage(ChatColor.GRAY + "             world or chunk not loaded — not checked, not broken");
        } else if (state == PortalService.Verification.ORPHANED) {
            sender.sendMessage(ChatColor.GRAY + "             trigger block or stamped sign missing; "
                    + "repair with /portal repair, or remove with /portal delete");
        }
        return true;
    }

    /**
     * Removes a portal by id. This is the path that makes an orphaned row recoverable at all — the
     * only other caller of the delete is the sign-break handler, which cannot fire when the sign is
     * already gone.
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        PortalService service = portalService();
        if (service == null || args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /portal delete <id>");
            return true;
        }
        if (!sender.hasPermission("rvnkcore.portal.delete")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to remove portals.");
            return true;
        }

        Optional<PortalDTO> found = service.resolvePortal(args[0]);
        if (found.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No cross-server portal matches '" + args[0]
                    + "' (unknown id, or an ambiguous prefix).");
            return true;
        }

        PortalDTO portal = found.get();
        String id = portal.getPortalId();
        boolean removed = service.deletePortalById(id);
        if (removed) {
            sender.sendMessage(ChatColor.GREEN + "Portal removed: " + ChatColor.WHITE + shortId(id)
                    + ChatColor.GRAY + " (" + portal.getWorld() + " " + portal.getX() + ","
                    + portal.getY() + "," + portal.getZ() + " -> " + portal.getTargetServer() + ")");
            logger.info("Portal " + id + " deleted via /portal by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Portal " + shortId(id) + " could not be removed.");
        }
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        PortalService service = portalService();
        if (service == null || args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /portal tp <id>");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This subcommand must be run by a player.");
            return true;
        }

        Optional<PortalDTO> found = service.resolvePortal(args[0]);
        if (found.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No cross-server portal matches '" + args[0] + "'.");
            return true;
        }

        PortalDTO portal = found.get();
        World world = Bukkit.getWorld(portal.getWorld());
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + portal.getWorld() + "' is not loaded.");
            return true;
        }
        // +1 on Y so the player lands on the anchor rather than inside it.
        player.teleport(new Location(world, portal.getX() + 0.5, portal.getY() + 1, portal.getZ() + 0.5));
        player.sendMessage(ChatColor.GREEN + "Teleported to portal " + ChatColor.WHITE + shortId(portal.getPortalId()));
        return true;
    }

    private boolean handleRepair(CommandSender sender, String[] args) {
        PortalService service = portalService();
        if (service == null || args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /portal repair <id>");
            return true;
        }

        Optional<PortalDTO> found = service.resolvePortal(args[0]);
        if (found.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No cross-server portal matches '" + args[0] + "'.");
            return true;
        }

        PortalService.PortalResult result =
                service.repairSign(found.get().getPortalId(), plugin.getService(TransferService.class));
        sender.sendMessage((result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        PortalService service = portalService();
        if (service == null) {
            sender.sendMessage(ChatColor.RED + "Portal service is not available.");
            return true;
        }
        // loadIndex() hits the database, so keep it off the main thread; the index itself is on
        // concurrent maps. The reply hops back to main rather than messaging from the async thread.
        sender.sendMessage(ChatColor.GRAY + "Reloading portal index...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            service.loadIndex();
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ChatColor.GREEN + "Portal index reloaded: "
                            + ChatColor.WHITE + service.getPortalCount() + ChatColor.GREEN + " portal(s)."));
        });
        return true;
    }

    /**
     * Reports how each portal system is recognised in-world. This is the direct answer to #1752 —
     * "lapis worlds vs diamond servers" — so an operator can look it up instead of inferring it.
     */
    private boolean handleTypes(CommandSender sender) {
        PortalService service = portalService();
        sender.sendMessage(ChatColor.GOLD + "Portal systems on this server");
        if (service != null) {
            sender.sendMessage(ChatColor.WHITE + "  SERVER " + ChatColor.GRAY + "(RVNKCore) — "
                    + service.getConfig().getSignHeader() + " sign on a "
                    + service.getConfig().getTriggerBlock() + " frame; sends you to another server. "
                    + "Manage with /portal.");
        }
        IPortalCommandExtension extension = extension();
        if (extension == null) {
            sender.sendMessage(ChatColor.WHITE + "  WORLD  " + ChatColor.GRAY
                    + "— no provider loaded (RVNKWorlds absent).");
        } else {
            String described = extension.describe();
            sender.sendMessage(ChatColor.WHITE + "  WORLD  " + ChatColor.GRAY + "(" + extension.providerName()
                    + ") — " + (described != null ? described : "no description provided"));
        }
        return true;
    }

    // ── Tab completion ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        IPortalCommandExtension extension = extension();

        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (String sub : CORE_SUBCOMMANDS) {
                if (sub.startsWith(partial)) out.add(sub);
            }
            if (extension != null) {
                for (String sub : extension.subcommands()) {
                    if (sub != null && sub.toLowerCase(Locale.ROOT).startsWith(partial)
                            && !CORE_SUBCOMMANDS.contains(sub.toLowerCase(Locale.ROOT))) {
                        out.add(sub);
                    }
                }
            }
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = tail(args);

        if (extension != null && !CORE_SUBCOMMANDS.contains(sub) && claims(extension, sub)) {
            List<String> fromExtension = extension.tabComplete(sender, sub, rest);
            return fromExtension != null ? fromExtension : out;
        }

        PortalService service = portalService();
        if (service == null) return out;

        if (args.length == 2 && List.of("info", "delete", "tp", "repair").contains(sub)) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (PortalDTO portal : service.listPortals()) {
                String id = shortId(portal.getPortalId());
                if (id.toLowerCase(Locale.ROOT).startsWith(partial)) out.add(id);
            }
        } else if (args.length == 2 && sub.equals("list")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(world.getName());
            }
        }
        return out;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE + getUsage());
        sender.sendMessage(ChatColor.GRAY + "  list [world] [--type server|world]  " + ChatColor.DARK_GRAY + "every portal");
        sender.sendMessage(ChatColor.GRAY + "  info <id>                           " + ChatColor.DARK_GRAY + "details + world state");
        sender.sendMessage(ChatColor.GRAY + "  delete <id>                         " + ChatColor.DARK_GRAY + "remove, even with no sign");
        sender.sendMessage(ChatColor.GRAY + "  tp <id>                             " + ChatColor.DARK_GRAY + "teleport to it");
        sender.sendMessage(ChatColor.GRAY + "  repair <id>                         " + ChatColor.DARK_GRAY + "rewrite + re-stamp its sign");
        sender.sendMessage(ChatColor.GRAY + "  reload                              " + ChatColor.DARK_GRAY + "reload the index from the DB");
        sender.sendMessage(ChatColor.GRAY + "  types                               " + ChatColor.DARK_GRAY + "which sign belongs to which system");

        IPortalCommandExtension extension = extension();
        if (extension != null && !extension.subcommands().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  via " + extension.providerName() + ": "
                    + ChatColor.DARK_GRAY + String.join(", ", extension.subcommands()));
        }
    }

    /**
     * Resolves the portal-command extension. Deliberately looked up on every call rather than
     * cached, so a provider plugin loading or unloading mid-session is picked up immediately and a
     * stale reference can never outlive it.
     */
    private IPortalCommandExtension extension() {
        return RVNKCore.getServiceSafe(IPortalCommandExtension.class);
    }

    private PortalService portalService() {
        return RVNKCore.getServiceSafe(PortalService.class);
    }

    /**
     * Core subcommands always win a name clash; an extension cannot shadow built-in behaviour.
     *
     * <p>A clash is warned about once per provider rather than silently ignored, so an author who
     * claims a reserved name finds out instead of wondering why their subcommand never fires.</p>
     */
    private boolean claims(IPortalCommandExtension extension, String sub) {
        warnOnCollisionsOnce(extension);
        if (CORE_SUBCOMMANDS.contains(sub)) {
            return false;
        }
        List<String> claimed = extension.subcommands();
        if (claimed == null) return false;
        for (String candidate : claimed) {
            if (candidate != null && candidate.equalsIgnoreCase(sub)) return true;
        }
        return false;
    }

    /**
     * Logs, once per provider, any subcommand names an extension claims that collide with a core
     * subcommand. The SPI contract promises this warning; without it a shadowed name fails silently.
     */
    private void warnOnCollisionsOnce(IPortalCommandExtension extension) {
        String provider = extension.providerName();
        if (provider == null || !warnedProviders.add(provider)) {
            return;
        }
        List<String> claimed = extension.subcommands();
        if (claimed == null) return;
        List<String> clashes = new ArrayList<>();
        for (String candidate : claimed) {
            if (candidate != null && CORE_SUBCOMMANDS.contains(candidate.toLowerCase(Locale.ROOT))) {
                clashes.add(candidate);
            }
        }
        if (!clashes.isEmpty()) {
            logger.warning("Portal extension '" + provider + "' claims subcommand(s) reserved by core: "
                    + String.join(", ", clashes) + " — core wins, these will not be dispatched.");
        }
    }

    private ChatColor colourFor(PortalService.Verification state) {
        return switch (state) {
            case VERIFIED -> ChatColor.GREEN;
            case UNVERIFIED -> ChatColor.YELLOW;
            case ORPHANED -> ChatColor.RED;
            case UNKNOWN -> ChatColor.DARK_GRAY;
        };
    }

    private String shortId(String portalId) {
        if (portalId == null) return "";
        return portalId.length() >= 8 ? portalId.substring(0, 8) : portalId;
    }

    private String[] tail(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }
}
