package org.fourz.rvnktools.command.manager.commands.teleport;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.fourz.rvnktools.command.manager.commands.teleport.TpRouting.Route;
import org.fourz.rvnktools.command.manager.commands.teleport.TpRouting.SenderKind;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * The /tp routing decision. The defect: a command block's {@code tp @a[distance=0..] -7 87 1 -90 5}
 * reached RVNKCore's /tp, which answered with its usage line and moved nobody.
 */
class TpRoutingTest {

    private static final Set<String> SUBS = Set.of("worldswap", "player", "coords", "here");

    private static Route r(SenderKind kind, String line) {
        return TpRouting.route(kind, line.isEmpty() ? new String[0] : line.split(" "), SUBS);
    }

    @Test
    void theEventCommandBlockLineGoesToVanilla() {
        assertEquals(Route.VANILLA, r(SenderKind.BLOCK, "@a[distance=0..] -7 87 1 -90 5"));
    }

    @Test
    void commandBlocksMinecartsAndExecuteAlwaysGoToVanilla() {
        // Even a shape RVNKCore could handle: map logic is written for vanilla.
        for (SenderKind k : new SenderKind[] {SenderKind.BLOCK, SenderKind.MINECART, SenderKind.PROXIED}) {
            assertEquals(Route.VANILLA, r(k, "Steve"), k.name());
            assertEquals(Route.VANILLA, r(k, "10 64 10"), k.name());
            assertEquals(Route.VANILLA, r(k, ""), k.name());
        }
    }

    @Test
    void playerShapesRvnkHandlesStayWithRvnk() {
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "Steve"));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "Steve Alex"));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "10 64 -10"));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "~ ~5 ~-2.5"));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "Steve 10 64 -10"));
        assertEquals(Route.RVNK, r(SenderKind.CONSOLE, "Steve Alex"));
        assertEquals(Route.RVNK, r(SenderKind.CONSOLE, "Steve 1 2 3"));
    }

    @Test
    void rvnkVerbsAndHelpStayWithRvnk() {
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, ""));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "worldswap event"));
        assertEquals(Route.RVNK, r(SenderKind.PLAYER, "HERE Steve"));
    }

    @Test
    void vanillaOnlySyntaxFromAPlayerGoesToVanilla() {
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "@p 1 2 3"), "selector target");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "Steve @r"), "selector destination");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "1 2 3 90 0"), "rotation");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "Steve 1 2 3 90 0"), "target + rotation");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "^ ^ ^5"), "local coordinates");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "Steve 1 2 3 facing Alex"), "facing");
        assertEquals(Route.VANILLA,
                r(SenderKind.PLAYER, "0f2c7b44-1a2b-4c3d-8e9f-001122334455"), "entity uuid");
        assertEquals(Route.VANILLA, r(SenderKind.CONSOLE, "@a 0 64 0"), "console selector");
    }

    @Test
    void shapesRvnkCannotParseGoToVanilla() {
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "5"), "a lone number");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "Steve 1 2"), "player + two coords");
        assertEquals(Route.VANILLA, r(SenderKind.PLAYER, "1 2 Steve"), "mixed");
    }

    @Test
    void coordinateShapes() {
        assertEquals(true, TpRouting.isCoordinate("~"));
        assertEquals(true, TpRouting.isCoordinate("~-3"));
        assertEquals(true, TpRouting.isCoordinate("-90"));
        assertEquals(false, TpRouting.isCoordinate("~x"));
        assertEquals(false, TpRouting.isCoordinate("Steve"));
    }

    @Test
    void vanillaLineKeepsTheArgumentsVerbatim() {
        assertEquals("minecraft:tp @a[distance=0..] -7 87 1 -90 5",
                TpRouting.vanillaCommandLine("@a[distance=0..] -7 87 1 -90 5".split(" ")));
    }

    @Test
    void senderKinds() {
        assertEquals(SenderKind.BLOCK, TpRouting.kindOf(mock(BlockCommandSender.class)));
        assertEquals(SenderKind.MINECART, TpRouting.kindOf(mock(CommandMinecart.class)));
        assertEquals(SenderKind.PROXIED, TpRouting.kindOf(mock(ProxiedCommandSender.class)));
        assertEquals(SenderKind.PLAYER, TpRouting.kindOf(mock(Player.class)));
        assertEquals(SenderKind.CONSOLE, TpRouting.kindOf(mock(ConsoleCommandSender.class)));
        assertEquals(SenderKind.OTHER, TpRouting.kindOf(mock(CommandSender.class)));
    }
}
