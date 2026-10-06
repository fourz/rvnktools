package org.fourz.rvnkcore.service.npc.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.api.service.INpcService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * PlaceholderAPI expansion {@code %rvnknpc_*%} (#2213), following RVNKLore's
 * {@code RVNKLorePlaceholderExpansion}.
 *
 * <table>
 *   <caption>Placeholders</caption>
 *   <tr><td>{@code %rvnknpc_last_key%}</td><td>RVNK key of the last NPC the player clicked; "" when none</td></tr>
 *   <tr><td>{@code %rvnknpc_last_name%}</td><td>that NPC's name, no colour codes; "" when none</td></tr>
 *   <tr><td>{@code %rvnknpc_last_ago_seconds%}</td><td>whole seconds since that click; "-1" when none</td></tr>
 * </table>
 *
 * <p>Values come from the in-memory tracker, which is thread-safe, so asynchronous placeholder
 * requests are fine. With no player (Citizens resolves NPC names and holograms with a null player)
 * every placeholder returns its "none" value. Unknown parameters return null so PlaceholderAPI
 * leaves the text as typed.</p>
 *
 * <p>Load this class only when PlaceholderAPI is enabled; it extends a PlaceholderAPI type.</p>
 *
 * @since 1.5.99-alpha
 */
public class RvnkNpcPlaceholderExpansion extends PlaceholderExpansion {

    public static final String IDENTIFIER = "rvnknpc";

    private final String author;
    private final String version;
    private final INpcService service;
    private final LongSupplier clock;

    public RvnkNpcPlaceholderExpansion(String author, String version, INpcService service, LongSupplier clock) {
        this.author = author;
        this.version = version;
        this.service = service;
        this.clock = clock;
    }

    /**
     * Registers the expansion. The signature names no PlaceholderAPI type, so the caller loads
     * without PlaceholderAPI present.
     *
     * @return an action that unregisters it, or null when PlaceholderAPI refused it
     */
    public static Runnable registerFor(Plugin plugin, INpcService service) {
        RvnkNpcPlaceholderExpansion expansion = new RvnkNpcPlaceholderExpansion(
                String.join(", ", plugin.getDescription().getAuthors()),
                plugin.getDescription().getVersion(),
                service,
                System::currentTimeMillis);
        return expansion.register() ? expansion::unregister : null;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true; // keep the expansion across /papi reload
    }

    @Override
    public List<String> getPlaceholders() {
        return List.of("%rvnknpc_last_key%", "%rvnknpc_last_name%", "%rvnknpc_last_ago_seconds%");
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        Optional<NpcInteraction> last = player == null
                ? Optional.empty()
                : service.lastInteraction(player.getUniqueId());

        switch (params.toLowerCase(Locale.ROOT)) {
            case "last_key":
                return last.map(NpcInteraction::key).orElse("");
            case "last_name":
                return last.map(NpcInteraction::displayName).orElse("");
            case "last_ago_seconds":
                return last.map(i -> String.valueOf(Math.max(0L, (clock.getAsLong() - i.atMillis()) / 1000L)))
                        .orElse("-1");
            default:
                return null;
        }
    }
}
