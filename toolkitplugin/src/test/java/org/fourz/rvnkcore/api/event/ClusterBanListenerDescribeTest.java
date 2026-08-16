package org.fourz.rvnkcore.api.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnosability of the fail-open ban check (#1995).
 *
 * <p>The reported line was:</p>
 *
 * <pre>Network ban check failed for wizardofire - allowing login (fail-open): null</pre>
 *
 * <p>{@code TimeoutException} carries a null message, as do many NPEs, so {@code getMessage()}
 * produced the literal string "null" - a log line recording that enforcement had stopped while
 * recording nothing at all about why. On a path that fails open, that log line is the only signal
 * anyone gets, which is what made a one-line WARN worth fixing.</p>
 *
 * <p>These tests pin the property that no input yields a description of "null" or "".</p>
 */
class ClusterBanListenerDescribeTest {

    /** describe() is private; it is a formatting detail, not API worth widening for a test. */
    private static String describe(Throwable t) throws Exception {
        Method m = ClusterBanListener.class.getDeclaredMethod("describe", Throwable.class);
        m.setAccessible(true);
        return (String) m.invoke(null, t);
    }

    @Test
    @DisplayName("a null-message throwable still names its type - the #1995 regression")
    void nullMessageStillIdentifiesTheFailure() throws Exception {
        String out = describe(new TimeoutException());

        assertAll(
                () -> assertFalse(out.equals("null"), "must never render as the bare string null"),
                () -> assertFalse(out.isBlank(), "must never render as empty"),
                () -> assertTrue(out.contains("TimeoutException"),
                        "must name the failure mode, got: " + out));
    }

    @Test
    @DisplayName("a message is kept when there is one")
    void messageIsPreserved() throws Exception {
        String out = describe(new SQLException("Connection is closed"));
        assertTrue(out.contains("SQLException"), out);
        assertTrue(out.contains("Connection is closed"), out);
    }

    @Test
    @DisplayName("wrappers are unwrapped - ExecutionException: null hides the real cause")
    void wrappersAreUnwrapped() throws Exception {
        String out = describe(new ExecutionException(new SQLException("pool exhausted")));

        assertTrue(out.startsWith("java.sql.SQLException"),
                "the cause should lead, not the wrapper, got: " + out);
        assertTrue(out.contains("pool exhausted"), out);
        assertTrue(out.contains("[wrapped in ExecutionException]"), out);
    }

    @Test
    @DisplayName("CompletionException is unwrapped too")
    void completionExceptionIsUnwrapped() throws Exception {
        String out = describe(new CompletionException(new IllegalStateException("no service")));
        assertTrue(out.startsWith("java.lang.IllegalStateException"), out);
        assertTrue(out.contains("no service"), out);
    }

    @Test
    @DisplayName("a wrapper with no cause does not loop or blow up")
    void wrapperWithoutCauseIsSafe() throws Exception {
        String out = describe(new ExecutionException("wrapper only", null));
        assertTrue(out.contains("ExecutionException"), out);
        assertFalse(out.contains("[wrapped in"), "nothing was unwrapped, so say nothing: " + out);
    }

    @Test
    @DisplayName("a self-referencing cause terminates")
    void selfReferencingCauseTerminates() throws Exception {
        // Defensive: a throwable whose cause is itself would spin the unwrap loop forever.
        ExecutionException loop = new ExecutionException("self", null) {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        String out = describe(loop);
        assertTrue(out.contains("self") || out.contains("ExecutionException"), out);
    }

    @Test
    @DisplayName("null throwable is handled")
    void nullThrowable() throws Exception {
        assertEquals("unknown (null throwable)", describe(null));
    }

    @Test
    @DisplayName("a stack frame is included so the failure has a location")
    void includesAFrame() throws Exception {
        String out = describe(new TimeoutException());
        assertTrue(out.contains(" at "), "expected a stack frame, got: " + out);
    }
}
