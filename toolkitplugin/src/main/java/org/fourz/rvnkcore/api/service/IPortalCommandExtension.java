package org.fourz.rvnkcore.api.service;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Extension point that lets another plugin contribute subcommands and listing output to
 * RVNKCore's {@code /portal} command (#1861).
 *
 * <p><b>Why this exists.</b> RVNKCore owns cross-server portals ({@code [server]} sign on a
 * {@code DIAMOND_BLOCK} frame, targeting another <i>server</i>). RVNKWorlds owns world portals
 * ({@code [portal]} sign on a {@code LAPIS_BLOCK} frame, targeting another <i>portal</i>). Both
 * can exist in the same world, often within a few blocks of each other, and an admin has no single
 * place to ask "what portals are here, of any kind?" — see #1752, closed for exactly this
 * confusion.</p>
 *
 * <p><b>Why it is an SPI and not a command.</b> RVNKWorlds cannot simply declare {@code portal} in
 * its own {@code plugin.yml} and take over. Commands are resolved through
 * {@code plugin.getCommand(name)} and must be declared statically; Bukkit's command map awards the
 * bare label to whichever plugin registers first, and because RVNKWorlds declares
 * {@code depend: [RVNKCore]} the core always enables first. A second declaration would silently
 * yield only {@code rvnkworlds:portal}. So RVNKCore keeps the registration permanently and the
 * override is <i>behavioural</i>: implementations are resolved per invocation, which also means a
 * plugin can be loaded or unloaded mid-session without leaving {@code /portal} broken.</p>
 *
 * <p><b>Direction of travel.</b> RVNKCore never reads the implementor's portal data. The
 * implementor renders its own portals and RVNKCore never handles a foreign type, so there is no
 * shared DTO to keep in sync and no second source of truth. Management of <i>cross-server</i>
 * portals stays in {@code PortalService}, which implementors call directly via
 * {@code RVNKCore.getServiceSafe(PortalService.class)} rather than reimplementing.</p>
 *
 * <p><b>Registration.</b> Register with the RVNKCore {@code ServiceRegistry} on enable and
 * unregister on disable, alongside the plugin's other core services:</p>
 *
 * <pre>{@code
 * serviceRegistry.registerService(IPortalCommandExtension.class, new MyPortalExtension(this));
 * }</pre>
 *
 * <p><b>Forward compatibility.</b> Every optional method has a default so that adding to this
 * interface does not break an implementor compiled against an older RVNKCore. Only
 * {@link #providerName()}, {@link #subcommands()} and
 * {@link #handle(CommandSender, String, String[])} must be implemented. Implementors are still
 * expected to be redeployed alongside RVNKCore when the contract changes meaningfully.</p>
 *
 * @since 1.5.64
 */
public interface IPortalCommandExtension {

    /**
     * Short name of the plugin providing this extension, e.g. {@code "RVNKWorlds"}.
     *
     * <p>Shown by {@code /portal types} and used in diagnostics so an operator can tell which
     * plugin is answering a given subcommand.</p>
     *
     * @return the provider name; never null or blank
     */
    String providerName();

    /**
     * Subcommand names this extension claims, lowercase, without a leading slash.
     *
     * <p>RVNKCore's own subcommands always win a collision — a clash is logged as a startup
     * warning and the claimed name is ignored rather than shadowing core behaviour.</p>
     *
     * @return claimed subcommand names; may be empty, never null
     */
    List<String> subcommands();

    /**
     * Executes one of this extension's claimed subcommands.
     *
     * <p>Only ever called with a name returned by {@link #subcommands()} that did not collide with
     * a core subcommand. The extension is responsible for its own permission checks, argument
     * validation and user messaging, exactly as it would be inside its own command.</p>
     *
     * @param sender     who ran the command; may be the console
     * @param subcommand the claimed subcommand name, already lowercased
     * @param args       arguments following the subcommand
     * @return true when handled (including handled-with-an-error-message); false to make
     *         {@code /portal} fall through to its usage output
     */
    boolean handle(CommandSender sender, String subcommand, String[] args);

    /**
     * Supplies tab completions for one of this extension's claimed subcommands.
     *
     * @param sender     who is completing
     * @param subcommand the claimed subcommand name, already lowercased
     * @param args       arguments following the subcommand; the last element is the partial token
     * @return completions, or an empty list
     */
    default List<String> tabComplete(CommandSender sender, String subcommand, String[] args) {
        return List.of();
    }

    /**
     * Writes this extension's portals into the output of {@code /portal list}.
     *
     * <p>RVNKCore prints the section header (so the {@code WORLD} vs {@code SERVER} labelling stays
     * consistent regardless of implementor) and then calls this to fill in the rows. The returned
     * future lets RVNKCore sequence sections rather than interleaving them — complete it only once
     * the rows have actually been sent, and never block the calling thread waiting for a database.</p>
     *
     * @param sender      who to write to
     * @param worldFilter world name to restrict the listing to, or null for every world
     * @return a future completing when the rows have been sent; never null
     */
    default CompletableFuture<Void> appendListing(CommandSender sender, String worldFilter) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * One-line description of how this extension's portals are recognised in-world — sign header
     * and accepted frame materials.
     *
     * <p>Surfaced by {@code /portal types}. This is the direct answer to the "lapis worlds vs
     * diamond servers" confusion in #1752: an operator can ask which marker belongs to which
     * system instead of inferring it.</p>
     *
     * @return a human-readable description, or null to omit this extension from {@code types}
     */
    default String describe() {
        return null;
    }
}
