package org.fourz.rvnkcore.service.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QA tap gate: config on, a QA tier, BKCommonLib enabled - never prod (#2359)")
class QaTapServiceGateTest {

    @Test
    @DisplayName("runs only with all three conditions")
    void allThree() {
        assertNull(QaTapService.refusal(true, "dev", true));
        assertNull(QaTapService.refusal(true, "Event", true), "tier match ignores case");
        assertNull(QaTapService.refusal(true, " test ", true));
    }

    @Test
    @DisplayName("refuses prod, unknown tiers, config off, or no BKCommonLib")
    void refusals() {
        assertNotNull(QaTapService.refusal(true, "nations", true), "prod is never a QA tier");
        assertNotNull(QaTapService.refusal(true, "local", true), "an unset server id refuses");
        assertNotNull(QaTapService.refusal(true, null, true));
        assertNotNull(QaTapService.refusal(false, "dev", true));
        assertNotNull(QaTapService.refusal(true, "dev", false));
        assertTrue(QaTapService.refusal(false, "nations", false).contains("qa.inbox.enabled"),
                "config is reported first");
    }

    @Test
    @DisplayName("an inactive service records nothing")
    void inactiveRecordsNothing() {
        QaTapService svc = new QaTapService(new QaInbox(50));
        UUID p = UUID.randomUUID();
        svc.onPacket(p, new QaPacketTextTest.ClientboundSetTitleTextPacket(new QaPacketTextTest.FakeComponent("x")));
        assertFalse(svc.isActive());
        assertEquals(0, svc.getInbox().since(p, 0, 10).lines().size());
    }

    @Test
    @DisplayName("resolve takes a UUID string; an unseen name resolves to null")
    void resolve() {
        QaTapService svc = new QaTapService(new QaInbox(50));
        UUID id = UUID.randomUUID();
        assertEquals(id, svc.resolve(id.toString()));
        assertNull(svc.resolve("NobodySeen"));
        assertNull(svc.resolve(" "));
    }
}
