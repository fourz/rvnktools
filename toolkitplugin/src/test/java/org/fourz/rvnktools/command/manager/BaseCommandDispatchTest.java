package org.fourz.rvnktools.command.manager;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Dispatch tests for {@link BaseCommand#execute(CommandSender, String[])} (#1600).
 *
 * <p>The defect: a bare invocation returned help unconditionally, so any command with no arguments
 * never reached {@link BaseCommand#executeCommand(CommandSender, String[])}. On Dev this made
 * {@code /ping} and {@code /discord} answer with their own usage text instead of running. Only 2 of
 * 23 BaseCommand subclasses register subcommands, so the great majority were leaf commands hitting
 * the parent-command path.</p>
 *
 * <p>Control: reverting {@code execute()} to {@code if (args.length == 0 || ...) sendHelp} fails
 * {@code leafCommandWithNoArgsReachesExecuteCommand} and
 * {@code leafCommandWithNoArgsDoesNotShowHelp}, and passes every other case here — so these tests
 * discriminate the fix rather than merely covering the method.</p>
 */
class BaseCommandDispatchTest {

    private RVNKCore plugin;

    /** Minimal concrete BaseCommand that records how it was dispatched. */
    private static class ProbeCommand extends BaseCommand {
        boolean executeCommandCalled;
        boolean helpShown;
        String[] receivedArgs;

        ProbeCommand(RVNKCore plugin) {
            super(plugin, "probe", "probe description", "/probe", null);
        }

        @Override
        protected boolean executeCommand(CommandSender sender, String[] args) {
            executeCommandCalled = true;
            receivedArgs = args;
            return true;
        }

        @Override
        public void sendHelp(CommandSender sender) {
            helpShown = true;
        }
    }

    /** Stub subcommand that records invocation. */
    private static class ProbeSubCommand implements SubCommand {
        boolean executed;
        String[] receivedArgs;

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            executed = true;
            receivedArgs = args;
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return List.of();
        }

        @Override
        public String getName() {
            return "sub";
        }

        @Override
        public String getDescription() {
            return "sub description";
        }

        @Override
        public String getUsage() {
            return "/probe sub";
        }

        @Override
        public String getPermission() {
            return null;
        }

        @Override
        public boolean hasPermission(CommandSender sender) {
            return true;
        }

        @Override
        public boolean isPlayerOnly() {
            return false;
        }

        @Override
        public RVNKCommand getParent() {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        plugin = mock(RVNKCore.class);
    }

    @Test
    @DisplayName("Leaf command with no args reaches executeCommand")
    void leafCommandWithNoArgsReachesExecuteCommand() {
        ProbeCommand command = new ProbeCommand(plugin);

        assertTrue(command.execute(mock(CommandSender.class), new String[0]));

        assertTrue(command.executeCommandCalled,
                "a command with no subcommands must run on bare invocation");
        assertEquals(0, command.receivedArgs.length);
    }

    @Test
    @DisplayName("Leaf command with no args does not show help")
    void leafCommandWithNoArgsDoesNotShowHelp() {
        ProbeCommand command = new ProbeCommand(plugin);

        command.execute(mock(CommandSender.class), new String[0]);

        assertFalse(command.helpShown, "bare invocation of a leaf command is not a help request");
    }

    @Test
    @DisplayName("Parent command with no args still shows help and does not execute")
    void parentCommandWithNoArgsShowsHelp() {
        ProbeCommand command = new ProbeCommand(plugin);
        command.registerSubCommand("sub", new ProbeSubCommand());

        command.execute(mock(CommandSender.class), new String[0]);

        assertTrue(command.helpShown, "a command that dispatches to subcommands lists them");
        assertFalse(command.executeCommandCalled);
    }

    @Test
    @DisplayName("Explicit help argument shows help on a leaf command")
    void explicitHelpArgumentShowsHelp() {
        ProbeCommand command = new ProbeCommand(plugin);

        command.execute(mock(CommandSender.class), new String[]{"help"});

        assertTrue(command.helpShown);
        assertFalse(command.executeCommandCalled);
    }

    @Test
    @DisplayName("Leaf command passes its arguments through to executeCommand")
    void leafCommandPassesArgumentsThrough() {
        ProbeCommand command = new ProbeCommand(plugin);

        command.execute(mock(CommandSender.class), new String[]{"alpha", "beta"});

        assertTrue(command.executeCommandCalled);
        assertArrayEquals(new String[]{"alpha", "beta"}, command.receivedArgs);
    }

    @Test
    @DisplayName("Known subcommand receives the remaining arguments")
    void knownSubCommandReceivesRemainingArgs() {
        ProbeCommand command = new ProbeCommand(plugin);
        ProbeSubCommand sub = new ProbeSubCommand();
        command.registerSubCommand("sub", sub);

        command.execute(mock(CommandSender.class), new String[]{"sub", "alpha"});

        assertTrue(sub.executed);
        assertArrayEquals(new String[]{"alpha"}, sub.receivedArgs);
        assertFalse(command.executeCommandCalled);
    }

    @Test
    @DisplayName("Unknown first argument falls through to executeCommand")
    void unknownArgumentFallsThroughToExecuteCommand() {
        ProbeCommand command = new ProbeCommand(plugin);
        command.registerSubCommand("sub", new ProbeSubCommand());

        command.execute(mock(CommandSender.class), new String[]{"notasubcommand"});

        assertTrue(command.executeCommandCalled);
        assertArrayEquals(new String[]{"notasubcommand"}, command.receivedArgs);
    }
}
