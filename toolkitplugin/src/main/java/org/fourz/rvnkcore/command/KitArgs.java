package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.service.kit.Kit;

import java.util.Locale;

/**
 * Parsed {@code /rvnk kit} arguments and the permission rule (#2415). Pure, so both are tested
 * without a server.
 *
 * <pre>
 * save &lt;name&gt; [--clear|--add]   capture the sender's inventory; --clear makes a clear-first kit
 * give &lt;kit&gt; [player]           grant into the player's current inventory (console names the player)
 * list | show &lt;kit&gt; | delete &lt;kit&gt;
 * </pre>
 *
 * @param verb       the verb
 * @param kit        normalised kit name, or null for {@code list}
 * @param player     the {@code give} target as typed, or null for the sender
 * @param clearFirst {@code save} only: the kit clears the inventory first
 */
public record KitArgs(Verb verb, String kit, String player, boolean clearFirst) {

    public static final String PERM_ADMIN = "rvnkcore.kit.admin";

    public enum Verb { SAVE, GIVE, LIST, SHOW, DELETE }

    /** @throws IllegalArgumentException with a message for the sender */
    public static KitArgs parse(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("Missing verb");
        Verb verb;
        try {
            verb = Verb.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown verb: " + args[0]);
        }
        if (verb == Verb.LIST) {
            if (args.length > 1) throw new IllegalArgumentException("list takes no arguments");
            return new KitArgs(verb, null, null, false);
        }
        if (args.length < 2) throw new IllegalArgumentException(verb.name().toLowerCase(Locale.ROOT) + " needs a kit name");
        String kit = Kit.normaliseName(args[1]);
        return switch (verb) {
            case SAVE -> {
                boolean clear = false;
                for (int i = 2; i < args.length; i++) {
                    switch (args[i].toLowerCase(Locale.ROOT)) {
                        case "--clear" -> clear = true;
                        case "--add" -> clear = false;
                        default -> throw new IllegalArgumentException("Unknown option: " + args[i] + " (use --clear or --add)");
                    }
                }
                yield new KitArgs(verb, kit, null, clear);
            }
            case GIVE -> {
                if (args.length > 3) throw new IllegalArgumentException("give takes a kit and one player");
                yield new KitArgs(verb, kit, args.length == 3 ? args[2] : null, false);
            }
            default -> {
                if (args.length > 2) throw new IllegalArgumentException("Too many arguments");
                yield new KitArgs(verb, kit, null, false);
            }
        };
    }

    /**
     * The permission rule. {@code rvnkcore.kit.admin} may do everything. {@code rvnkcore.kit.<name>}
     * lets a player give that kit to themselves and see it. {@code list} is open; it shows only the
     * kits the sender may see.
     *
     * @param admin   the sender holds {@link #PERM_ADMIN}
     * @param kitPerm the sender holds {@code rvnkcore.kit.<kit>}
     * @param self    the {@code give} target is the sender
     */
    public static boolean allowed(Verb verb, boolean admin, boolean kitPerm, boolean self) {
        if (admin) return true;
        return switch (verb) {
            case LIST -> true;
            case SHOW -> kitPerm;
            case GIVE -> kitPerm && self;
            case SAVE, DELETE -> false;
        };
    }
}
