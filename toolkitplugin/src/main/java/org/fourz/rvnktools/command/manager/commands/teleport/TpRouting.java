package org.fourz.rvnktools.command.manager.commands.teleport;

import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a {@code /tp} or {@code /teleport} goes to RVNKCore or to vanilla.
 *
 * <p>RVNKCore declares {@code tp} and {@code teleport} in plugin.yml, so every {@code tp} reaches it,
 * including a map's command block. On RVNK Event a command block ran
 * {@code tp @a[distance=0..] -7 87 1 -90 5}; RVNKCore's routing read {@code @a[...]} as a player name,
 * found nobody, and Bukkit printed the usage line. Nobody moved.</p>
 *
 * <p>RVNKCore's {@code /tp} handles four shapes only: {@code <player>}, {@code <player> <player>},
 * {@code <x> <y> <z>} and {@code <player> <x> <y> <z>}, with {@code ~} relative coordinates. Anything
 * else goes to {@code minecraft:tp} as the same sender, so vanilla resolves selectors, rotation,
 * {@code ^} local coordinates, {@code facing} and entity UUIDs, and reports its own errors.</p>
 *
 * <p>{@link #route} is a pure function so the decision is unit-tested without a server.</p>
 */
public final class TpRouting {

    /** Who sent the command, reduced to what routing needs. */
    public enum SenderKind {
        PLAYER, CONSOLE,
        /** A command block. */
        BLOCK,
        /** A command block minecart. */
        MINECART,
        /** {@code /execute as|at ... run tp}: the sender is a proxy for another source. */
        PROXIED,
        /** Remote console and anything else. */
        OTHER
    }

    public enum Route { RVNK, VANILLA }

    private static final Pattern UUID_SHAPE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private TpRouting() { }

    public static SenderKind kindOf(CommandSender sender) {
        if (sender instanceof BlockCommandSender) return SenderKind.BLOCK;
        if (sender instanceof CommandMinecart) return SenderKind.MINECART;
        if (sender instanceof ProxiedCommandSender) return SenderKind.PROXIED;
        if (sender instanceof Player) return SenderKind.PLAYER;
        if (sender instanceof ConsoleCommandSender) return SenderKind.CONSOLE;
        return SenderKind.OTHER;
    }

    /**
     * @param kind        the sender kind
     * @param args        the command arguments, without the label
     * @param subcommands RVNKCore's own first-argument verbs (worldswap, here, ...), lower case
     * @return where the command goes
     */
    public static Route route(SenderKind kind, String[] args, Set<String> subcommands) {
        // Command blocks, command minecarts and /execute ... run tp are map and datapack logic.
        // They are written for vanilla syntax and never for RVNKCore's verbs.
        if (kind == SenderKind.BLOCK || kind == SenderKind.MINECART || kind == SenderKind.PROXIED) {
            return Route.VANILLA;
        }
        if (args == null || args.length == 0) return Route.RVNK; // RVNKCore help
        if (subcommands.contains(args[0].toLowerCase(Locale.ROOT))) return Route.RVNK;

        for (String a : args) {
            if (a.startsWith("@")) return Route.VANILLA;                    // selector
            if (a.startsWith("^")) return Route.VANILLA;                    // local coordinates
            if (a.equalsIgnoreCase("facing")) return Route.VANILLA;         // facing <pos|entity>
            if (UUID_SHAPE.matcher(a).matches()) return Route.VANILLA;      // entity UUID
        }

        // The four shapes RVNKCore handles. Anything else, such as x y z yaw pitch, is vanilla's.
        return switch (args.length) {
            case 1 -> isCoordinate(args[0]) ? Route.VANILLA : Route.RVNK;
            case 2 -> !isCoordinate(args[0]) && !isCoordinate(args[1]) ? Route.RVNK : Route.VANILLA;
            case 3 -> allCoordinates(args, 0) ? Route.RVNK : Route.VANILLA;
            case 4 -> !isCoordinate(args[0]) && allCoordinates(args, 1) ? Route.RVNK : Route.VANILLA;
            default -> Route.VANILLA;
        };
    }

    /** The vanilla command line for these arguments. */
    public static String vanillaCommandLine(String[] args) {
        return args == null || args.length == 0 ? "minecraft:tp" : "minecraft:tp " + String.join(" ", args);
    }

    /**
     * Runs {@code minecraft:tp <args>} as the same sender, so selectors and {@code ~} resolve from the
     * command block's position. Vanilla checks its own permission and reports its own errors.
     */
    public static void dispatchVanilla(CommandSender sender, String[] args) {
        Bukkit.dispatchCommand(sender, vanillaCommandLine(args));
    }

    /** A number, or {@code ~} with an optional number. */
    static boolean isCoordinate(String arg) {
        String s = arg.startsWith("~") ? arg.substring(1) : arg;
        if (s.isEmpty()) return arg.startsWith("~");
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean allCoordinates(String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            if (!isCoordinate(args[i])) return false;
        }
        return true;
    }
}
