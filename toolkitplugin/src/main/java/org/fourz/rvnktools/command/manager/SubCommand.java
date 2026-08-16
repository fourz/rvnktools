package org.fourz.rvnktools.command.manager;

import org.bukkit.command.CommandSender;
import java.util.List;

/**
 * Interface for subcommands within the RVNKTools command framework.
 * Subcommands are commands that are executed as part of a parent command.
 */
public interface SubCommand {
    
    /**
     * Execute the subcommand logic.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments (excluding the parent command and subcommand name)
     * @return true if the subcommand was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Provide tab completion for the subcommand.
     * 
     * @param sender The command sender
     * @param args Subcommand arguments being completed
     * @return List of possible completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
    
    /**
     * Get the subcommand name.
     * 
     * @return The subcommand name
     */
    String getName();
    
    /**
     * Get the subcommand description.
     * 
     * @return The subcommand description
     */
    String getDescription();
    
    /**
     * Get the subcommand usage string.
     * 
     * @return The usage string
     */
    String getUsage();
    
    /**
     * Get the permission required to use this subcommand.
     * 
     * @return The permission string, or null if no permission is required
     */
    String getPermission();
    
    /**
     * Check if the sender has permission to use this subcommand.
     * 
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Check if this subcommand is restricted to players only.
     * 
     * @return true if only players can use this subcommand
     */
    boolean isPlayerOnly();
    
    /**
     * Get the parent command of this subcommand.
     *
     * @return The parent command
     */
    RVNKCommand getParent();

    /**
     * Worked examples for this subcommand, served by {@code /&lt;command&gt; help &lt;verb&gt;}.
     *
     * <p>Return concrete, runnable lines with real-looking arguments &mdash; not a restatement of
     * {@link #getUsage()}, which the help prints above them. A line beginning with two spaces is
     * rendered as an indented note under the example above it.</p>
     *
     * <p><b>Why the examples live in the jar.</b> Restating them in {@code docs/plugins/commands/}
     * means an assistant must read a whole page to answer a question about one verb, and the copy
     * drifts from the build with nothing to catch it &mdash; RVNKWorlds' page had lost six verbs
     * and RVNKQuests' index advertised three commands with the wrong grammar. Shipping them here
     * makes them per-verb and unable to drift. The doc pages keep what no command can print:
     * sequence diagrams, backend class references and changelogs. (#1981)</p>
     *
     * <p>Default is empty, so existing subcommands need no change. A verb whose whole grammar fits
     * in one usage line does not need examples, and the help marks which verbs have them.</p>
     *
     * @return example lines, or an empty list when the usage string says everything
     */
    default List<String> getExamples() {
        return List.of();
    }
}
