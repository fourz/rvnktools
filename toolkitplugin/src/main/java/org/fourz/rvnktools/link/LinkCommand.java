package org.fourz.rvnktools.link;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
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

    private static final String DEFAULT_CALLBACK_URL = "http://localhost:3000/auth/callback";
    private static final List<String> SUBCOMMANDS = List.of("login", "matrix", "discord");

    private final AuthTokenStore authTokenStore;
    private final String callbackUrl;

    public LinkCommand(RVNKCore plugin, AuthTokenStore authTokenStore) {
        super(plugin, "link",
                "Link your account to external services",
                "/link <login|matrix|discord>",
                "rvnktools.link");
        this.authTokenStore = authTokenStore;
        // config.yml: auth.callback-url (defaults to localhost for dev)
        String configured = plugin.getConfig().getString("auth.callback-url", "");
        this.callbackUrl = (configured == null || configured.isBlank()) ? DEFAULT_CALLBACK_URL : configured;
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
        // Tab-complete player names for console "link login <player>"
        if (args.length == 2 && args[0].equalsIgnoreCase("login") && !(sender instanceof Player)) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
