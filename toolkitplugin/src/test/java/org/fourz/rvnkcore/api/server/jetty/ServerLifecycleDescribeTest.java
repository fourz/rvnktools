package org.fourz.rvnkcore.api.server.jetty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ServerLifecycleDescribeTest {

    @Test
    void nullMessageStillNamesTheClass() {
        String d = ServerLifecycle.describe(new InterruptedException());
        assertEquals("java.lang.InterruptedException", d);
        assertFalse(d.contains("null"));
    }

    @Test
    void causeChainIsListed() {
        Exception e = new Exception("outer", new IllegalStateException());
        assertEquals("java.lang.Exception: outer <- caused by java.lang.IllegalStateException", ServerLifecycle.describe(e));
    }
}
