package org.fourz.rvnkcore.api.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Enforces the network-wide ban flag at login (#1814).
 *
 * <h2>The gap this closes</h2>
 *
 * <p>Minecraft's own ban list is a per-server file ({@code banned-players.json}). Banning someone on
 * one server has never stopped them joining another, so a player removed from nations could simply
 * hop to Event through the portal or {@code /server} and carry on.</p>
 *
 * <p>{@code rvnk_players.banned} already existed but was only ever <i>read</i> by the auth
 * controller, i.e. it blocked website logins and nothing else. Since #1812 that column lives in the
 * cluster roster, so it is now genuinely network-wide — this listener is what finally acts on it.</p>
 *
 * <h2>Fail-open, deliberately</h2>
 *
 * <p>If the lookup fails or times out, the login is <b>allowed</b>. A database blip must not make
 * the whole network unjoinable. The cost of that choice is bounded and asymmetric: a banned player
 * might slip in during an outage — and is still subject to the local vanilla ban list, which is
 * unaffected by any of this — whereas failing closed would lock out every legitimate player for the
 * duration. Availability wins over a brief enforcement gap.</p>
 *
 * <p>Runs on {@link AsyncPlayerPreLoginEvent}, which is already off the main thread, so the query
 * costs a login handshake rather than a server tick. The wait is bounded so a hung connection
 * degrades to fail-open rather than stalling the handshake indefinitely.</p>
 *
 * @since 1.5.58
 */
public class ClusterBanListener implements Listener {

    /** Upper bound on the roster lookup before the login is allowed through. */
    private static final long LOOKUP_TIMEOUT_MS = 3000L;

    private final ServiceRegistry registry;
    private final LogManager logger;

    public ClusterBanListener(ServiceRegistry registry, LogManager logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // Something earlier already rejected this login; don't override its reason.
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        try {
            PlayerService playerService = registry.getService(PlayerService.class);
            if (playerService == null) {
                return;
            }

            Optional<PlayerDTO> player = playerService.getPlayer(event.getUniqueId())
                    .get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Unknown UUID means a first-time join — nothing to enforce.
            if (player.isEmpty() || !player.get().isBanned()) {
                return;
            }

            logger.info("Refused login for " + event.getName()
                    + " — banned on the network roster (#1814)");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "You are banned from the Ravenkraft network.");

        } catch (Exception e) {
            // Fail open — see the class javadoc. Logged at WARNING because a persistent failure
            // means bans silently stop being enforced, which should not pass unnoticed.
            logger.warning("Network ban check failed for " + event.getName()
                    + " — allowing login (fail-open): " + e.getMessage());
        }
    }
}
