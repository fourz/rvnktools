package org.fourz.rvnktools.link;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.permission.LuckPermsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Subcommand dispatcher for {@code /link <login|matrix|discord>}.
 * MVP implements {@code login} — generates a one-time magic link URL
 * for web portal authentication.
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
            case "login" -> handleLogin(sender);
            case "matrix" -> sender.sendMessage(ChatFormat.colorize("&7[Link] &eMatrix linking is coming soon!"));
            case "discord" -> sender.sendMessage(ChatFormat.colorize("&7[Link] &eDiscord linking is coming soon!"));
            default -> sendUnknownSubCommandMessage(sender, subCommand);
        }

        return true;
    }

    private void handleLogin(CommandSender sender) {
        if (!validatePlayer(sender)) {
            return;
        }

        Player player = (Player) sender;

        // Rate limit check
        if (authTokenStore.isRateLimited(player)) {
            sender.sendMessage(ChatFormat.colorize(
                    "&7[Link] &cPlease wait before generating another login link."));
            return;
        }

        // Resolve player groups from LuckPerms
        List<String> groups = resolveGroups(player);

        // Generate token and build URL
        String token = authTokenStore.generateToken(player, groups);
        String url = callbackUrl + "?token=" + token;

        // Send clickable link to player
        sender.sendMessage(ChatFormat.colorize("&7[Link] &aClick the link below to log in to the web portal:"));

        TextComponent linkComponent = new TextComponent(ChatFormat.colorize("&b&n" + url));
        linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(linkComponent);

        sender.sendMessage(ChatFormat.colorize("&7This link expires in &f15 minutes &7and can only be used once."));
    }

    /**
     * Resolves the player's permission groups via LuckPerms.
     * Returns a list with at least "default" if LuckPerms is unavailable.
     */
    private List<String> resolveGroups(Player player) {
        try {
            LuckPerms lp = LuckPermsManager.getLuckPerms();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            String primaryGroup = user.getPrimaryGroup();

            List<String> groups = new ArrayList<>();
            groups.add(primaryGroup);

            // Add inherited groups (excluding the primary to avoid duplicates)
            user.getInheritedGroups(user.getQueryOptions()).stream()
                    .map(g -> g.getName())
                    .filter(name -> !name.equals(primaryGroup))
                    .forEach(groups::add);

            return groups;
        } catch (Exception e) {
            logger.warning("Failed to resolve LuckPerms groups for " + player.getName() + ": " + e.getMessage());
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
        return Collections.emptyList();
    }
}
