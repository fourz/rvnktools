package org.fourz.rvnkcore.command;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The /rvnk npc click gate matrix (#2255). */
class NpcClickGateTest {

    @ParameterizedTest(name = "tier={0} admin={1} qaSubject={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            // Dev tiers allow any online target
            "dev,      true,  false, true",
            "dev,      true,  true,  true",
            "test,     true,  false, true",
            "DEV,      true,  false, true",
            "' dev ',  true,  false, true",
            // Event (and every other known tier) needs the target's QA permission
            "event,    true,  false, false",
            "event,    true,  true,  true",
            "nations,  true,  false, false",
            "nations,  true,  true,  true",
            "prod,     true,  false, false",
            "staging,  true,  true,  true",
            // Unknown tier refuses, even for a QA subject
            "NULL,     true,  true,  false",
            "NULL,     true,  false, false",
            "'',       true,  true,  false",
            "'   ',    true,  true,  false",
            "local,    true,  true,  false",
            "LOCAL,    true,  false, false",
            // No sender admin permission refuses everywhere
            "dev,      false, true,  false",
            "test,     false, false, false",
            "event,    false, true,  false",
    })
    void matrix(String tier, boolean admin, boolean qaSubject, boolean allowed) {
        NpcClickGate.Decision decision = NpcClickGate.evaluate(tier, admin, qaSubject);
        assertEquals(allowed, decision.allowed(), decision.reason());
        assertNotNull(decision.reason());
        assertFalse(decision.reason().isBlank());
    }

    @Test
    void refusalOnEventNamesThePermission() {
        NpcClickGate.Decision decision = NpcClickGate.evaluate("event", true, false);
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("rvnkcore.qa.subject"), decision.reason());
        assertTrue(decision.reason().contains("event"), decision.reason());
    }

    @Test
    void refusalOnUnknownTierSaysTheTierIsUnknown() {
        assertTrue(NpcClickGate.evaluate(null, true, true).reason().contains("unknown"));
        assertTrue(NpcClickGate.evaluate("local", true, true).reason().contains("'local'"));
    }

    @Test
    void refusalWithoutSenderPermissionNamesTheAdminNode() {
        assertTrue(NpcClickGate.evaluate("dev", false, true).reason().contains("rvnkcore.npc.admin"));
    }

    @Test
    void devTierHelper() {
        assertTrue(NpcClickGate.isDevTier("dev"));
        assertTrue(NpcClickGate.isDevTier("Test"));
        assertFalse(NpcClickGate.isDevTier("event"));
        assertFalse(NpcClickGate.isDevTier(null));
        assertFalse(NpcClickGate.isDevTier("local"));
    }

    @Test
    void qaPermissionNodeIsTheDocumentedOne() {
        assertEquals("rvnkcore.qa.subject", NpcClickGate.PERM_QA_SUBJECT);
    }
}
