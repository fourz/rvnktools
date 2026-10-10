package org.fourz.rvnkcore.service.npc.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Skin result wording (#2248, 1.5.101): no false FAILED for a despawned NPC or a slow fetch. */
class NpcSkinsTest {

    @Test
    void requestMessageSaysWhenCitizensFetches() {
        assertEquals("skin set to player 'Notch'; Citizens fetches it when the NPC spawns",
                NpcSkins.requestMessage("Notch", false));
        assertEquals("skin set to player 'Notch'; Citizens fetches it in the background",
                NpcSkins.requestMessage("Notch", true));
    }

    @Test
    void despawnedNpcIsNotAFailure() {
        String line = NpcSkins.checkMessage("qa_guide", "Notch", false, false, null);
        assertEquals("skin qa_guide: skin set to 'Notch'; Citizens fetches it when the NPC spawns", line);
        assertFalse(line.contains("FAILED"));
    }

    @Test
    void slowFetchIsNotYetLoadedNotFailed() {
        String line = NpcSkins.checkMessage("qa_guide", "Notch", true, false, null);
        assertTrue(line.contains("not yet loaded after 5 s (Citizens retries)"), line);
        assertTrue(line.contains(NpcSkins.NOT_YET));
        assertFalse(line.contains("FAILED"), line);
    }

    @Test
    void loadedAndHardError() {
        assertEquals("skin qa_guide: texture for 'Notch' loaded",
                NpcSkins.checkMessage("qa_guide", "Notch", true, true, null));
        assertEquals("skin qa_guide: texture for 'Notch' loaded",
                NpcSkins.checkMessage("qa_guide", "Notch", false, true, "ignored"), "a texture wins");
        assertEquals("skin qa_guide: FAILED - skin check failed: boom",
                NpcSkins.checkMessage("qa_guide", "Notch", true, false, "skin check failed: boom"));
    }
}
