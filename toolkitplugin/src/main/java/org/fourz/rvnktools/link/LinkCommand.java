package org.fourz.rvnktools.link;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.auth.AuthTokenStore.TokenKind;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.permission.LuckPermsGroupResolver;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand dispatcher for {@code /link <login|matrix|discord>}.
 * MVP implements {@code login} — generates a one-time magic link URL
 * for web portal authentication.
 *
 * <p>Console usage: {@code link login <player>} — generates a token for
 * the named player (offline or online) and prints the URL to console.</p>
 *
 * @since 1.5.0
 */
public class LinkCommand extends BaseCommand {

    private static final List<String> SUBCOMMANDS = List.of("login", "invite", "matrix", "discord");
    private static final long DEFAULT_LOGIN_TTL_MINUTES = 15L;
    private static final long DEFAULT_INVITE_TTL_MINUTES = 120L;   // 2h
    private static final long DEFAULT_INVITE_TTL_MAX_MINUTES = 1440L; // 24h hard cap

    private final AuthTokenStore authTokenStore;
    private final String callbackUrl;

    public LinkCommand(RVNKCore plugin, AuthTokenStore authTokenStore) {
        super(plugin, "link",
                "Link your account to external services",
                "/link <login|matrix|discord>",
                "rvnktools.link");
        this.authTokenStore = authTokenStore;
        // config.yml: auth.callback-url takes precedence; if blank, derive from webui.host + webui.port
        String configured = plugin.getConfig().getString("auth.callback-url", "");
        if (configured == null || configured.isBlank()) {
            String webHost = plugin.getConfig().getString("webui.host", "localhost");
            int webPort    = plugin.getConfig().getInt("webui.port", 3000);
            this.callbackUrl = "http://" + webHost + ":" + webPort + "/auth/callback";
        } else {
            this.callbackUrl = configured;
        }
        logger.info("Link command initialized — callback URL: " + this.callbackUrl);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "login" -> handleLogin(sender, args);
            case "invite" -> handleInvite(sender, args);
            case "matrix" -> sender.sendMessage(ChatFormat.colorize("&7[Link] &eMatrix linking is coming soon!"));
            case "discord" -> sender.sendMessage(ChatFormat.colorize("&7[Link] &eDiscord linking is coming soon!"));
            default -> sendUnknownSubCommandMessage(sender, subCommand);
        }

        return true;
    }

    private void handleLogin(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            // Player usage: /link login (no args needed)
            handlePlayerLogin(sender, player);
        } else {
            // Console usage: /link login <player>
            if (args.length < 2) {
                sender.sendMessage(ChatFormat.colorize(
                        "&7[Link] &cConsole usage: /link login <player>"));
                return;
            }
            handleConsoleLogin(sender, args[1]);
        }
    }

    private void handlePlayerLogin(CommandSender sender, Player player) {
        if (authTokenStore.isRateLimited(player)) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &cPlease wait before generating another login link."));
            return;
        }

        List<String> groups = resolveGroups(player);
        String token = authTokenStore.generateToken(player, groups);
        String url = callbackUrl + "?token=" + token;

        sender.sendMessage(ChatFormat.colorize("&7[Link] &aClick the link below to log in to the web portal:"));
        TextComponent linkComponent = new TextComponent(ChatFormat.colorize("&b&n" + url));
        linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(linkComponent);
        sender.sendMessage(ChatFormat.colorize("&7This link expires in &f15 minutes &7and can only be used once."));
    }

    @SuppressWarnings("deprecation")
    private void handleConsoleLogin(CommandSender sender, String playerName) {
        // Resolve player UUID — check online first, then offline cache
        Player online = Bukkit.getPlayerExact(playerName);
        UUID uuid;
        String resolvedName;

        if (online != null) {
            uuid = online.getUniqueId();
            resolvedName = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(ChatFormat.colorize(
                        "&7[Link] &cPlayer '" + playerName + "' not found."));
                return;
            }
            uuid = offline.getUniqueId();
            resolvedName = offline.getName() != null ? offline.getName() : playerName;
        }

        if (authTokenStore.isRateLimited(uuid)) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &cPlayer is rate-limited. Wait before generating another token."));
            return;
        }

        List<String> groups = resolveGroupsByUuid(uuid, resolvedName);
        String token = authTokenStore.generateToken(uuid, resolvedName, groups);
        String url = callbackUrl + "?token=" + token;

        sender.sendMessage(ChatFormat.colorize(
                "&7[Link] &aGenerated login link for &f" + resolvedName + "&a:"));
        sender.sendMessage(url);
        sender.sendMessage(ChatFormat.colorize(
                "&7Groups: &f" + String.join(", ", groups)));
        sender.sendMessage(ChatFormat.colorize(
                "&7This link expires in &f15 minutes &7and can only be used once."));
    }

    /**
     * {@code /link invite <player> [hours]} — admin-only, pre-bound shareable URL.
     *
     * Generates an INVITE token whose session grants are captured from the
     * TARGET's UUID/name/groups at generation time. Any browser clicking the
     * printed URL will be signed in as that target. Admin shares the URL
     * out-of-band (DM, email, screenshot).
     *
     * <p>Permissions:
     * <ul>
     *   <li>{@code rvnktools.link.invite} — required to use the command at all
     *       (self-invite).</li>
     *   <li>{@code rvnktools.link.invite.other} — additionally required when the
     *       target is a different player than the issuer.</li>
     * </ul>
     * Console bypasses both (no associated user identity).
     * </p>
     *
     * <p>TTL: {@code link.invite-ttl-minutes} (default 120), capped at
     * {@code link.invite-ttl-max-minutes} (default 1440 = 24h).</p>
     */
    @SuppressWarnings("deprecation")
    private void handleInvite(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("rvnktools.link.invite")) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &cYou don't have permission to issue invite links."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &cUsage: /link invite <player> [hours]"));
            return;
        }

        String targetName = args[1];

        // TTL resolution: config default + optional per-command override, capped.
        long defaultTtlMinutes = plugin.getConfig().getLong(
                "link.invite-ttl-minutes", DEFAULT_INVITE_TTL_MINUTES);
        long maxTtlMinutes = plugin.getConfig().getLong(
                "link.invite-ttl-max-minutes", DEFAULT_INVITE_TTL_MAX_MINUTES);
        long requestedMinutes = defaultTtlMinutes;
        boolean wasCapped = false;

        if (args.length >= 3) {
            try {
                long hours = Long.parseLong(args[2]);
                if (hours <= 0) {
                    sender.sendMessage(ChatFormat.colorize(
                            "&7[Link] &cHours must be positive."));
                    return;
                }
                requestedMinutes = hours * 60L;
            } catch (NumberFormatException nfe) {
                sender.sendMessage(ChatFormat.colorize(
                        "&7[Link] &cHours must be a whole number."));
                return;
            }
        }
        long ttlMinutes = Math.min(requestedMinutes, maxTtlMinutes);
        if (ttlMinutes < requestedMinutes) {
            wasCapped = true;
        }

        // Target resolution — online first, then offline player cache.
        Player online = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        String resolvedName;

        if (online != null) {
            uuid = online.getUniqueId();
            resolvedName = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(ChatFormat.colorize(
                        "&7[Link] &cPlayer '" + targetName + "' not found (never joined this server)."));
                return;
            }
            uuid = offline.getUniqueId();
            resolvedName = offline.getName() != null ? offline.getName() : targetName;
        }

        // Second-gate: targeting another player requires the .other permission.
        // Console always bypasses (no associated user identity to compare against).
        if (sender instanceof Player issuer && !issuer.getUniqueId().equals(uuid)) {
            if (!issuer.hasPermission("rvnktools.link.invite.other")) {
                sender.sendMessage(ChatFormat.colorize(
                        "&7[Link] &cYou don't have permission to issue invites for other players."));
                return;
            }
        }

        List<String> groups = online != null
                ? resolveGroups(online)
                : resolveGroupsByUuid(uuid, resolvedName);

        String token = authTokenStore.generateToken(
                uuid, resolvedName, groups, TokenKind.INVITE, ttlMinutes * 60L);
        String url = callbackUrl + "?token=" + token;

        String issuerName = (sender instanceof Player p) ? p.getName() : "console";
        logger.info("Invite issued — invitee=" + resolvedName + " targetUuid=" + uuid
                + " ttlMinutes=" + ttlMinutes + " issuer=" + issuerName);

        sender.sendMessage(ChatFormat.colorize(
                "&7[Link] &aInvite link for &f" + resolvedName + "&a:"));

        if (sender instanceof Player issuer) {
            TextComponent linkComponent = new TextComponent(ChatFormat.colorize("&b&n" + url));
            linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            issuer.spigot().sendMessage(linkComponent);
        } else {
            sender.sendMessage(url);
        }

        sender.sendMessage(ChatFormat.colorize(
                "&7Expires in &f" + ttlMinutes + " minutes&7. One-time use. Share only with &f" + resolvedName + "&7."));
        if (wasCapped) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &eTTL capped to server maximum (" + maxTtlMinutes + " min)."));
        }
    }

    /**
     * Resolves the player's permission groups via LuckPerms (online player).
     */
    private List<String> resolveGroups(Player player) {
        try {
            return LuckPermsGroupResolver.resolveGroups(player).allGroups();
        } catch (Exception e) {
            logger.warning("Failed to resolve LuckPerms groups for " + player.getName() + ": " + e.getMessage());
            return List.of("default");
        }
    }

    /**
     * Resolves permission groups by UUID via LuckPerms UserManager.
     * Works for both online and offline players.
     */
    private List<String> resolveGroupsByUuid(UUID uuid, String playerName) {
        try {
            return LuckPermsGroupResolver.resolveGroupsAsync(uuid).join().allGroups();
        } catch (Exception e) {
            logger.warning("Failed to resolve LuckPerms groups for " + playerName + ": " + e.getMessage());
            return List.of("default");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        // Tab-complete player names for: console "link login <player>" and
        // any "link invite <player>" caller (admin or console).
        if (args.length == 2
                && (args[0].equalsIgnoreCase("invite")
                    || (args[0].equalsIgnoreCase("login") && !(sender instanceof Player)))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
