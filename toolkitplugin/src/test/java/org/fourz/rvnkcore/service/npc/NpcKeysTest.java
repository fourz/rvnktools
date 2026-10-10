package org.fourz.rvnkcore.service.npc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Key rules for the RVNK NPC bridge (#2213): lower-case {@code [a-z0-9_-]{1,48}}. */
class NpcKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "guide", "harbour_master", "npc-01", "0", "_", "-"})
    void acceptsValidKeys(String key) {
        assertEquals(key, NpcKeys.normalize(key));
        assertTrue(NpcKeys.isValid(key));
    }

    @Test
    void acceptsExactlyMaxLength() {
        String key = "a".repeat(NpcKeys.MAX_LENGTH);
        assertEquals(key, NpcKeys.normalize(key));
    }

    @Test
    void rejectsOverMaxLength() {
        assertNull(NpcKeys.normalize("a".repeat(NpcKeys.MAX_LENGTH + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "has space", "dot.key", "colon:key", "slash/key", "ümlaut", "key!", "a%b"})
    void rejectsInvalidKeys(String key) {
        assertNull(NpcKeys.normalize(key));
        assertFalse(NpcKeys.isValid(key));
    }

    @Test
    void rejectsNull() {
        assertNull(NpcKeys.normalize(null));
    }

    @Test
    void lowerCasesAndTrims() {
        assertEquals("harbour_master", NpcKeys.normalize("  Harbour_MASTER "));
    }

    @Test
    void metadataNameIsStable() {
        // Changing this orphans every key already written to saves.yml on every server.
        assertEquals("rvnk-key", NpcKeys.METADATA_KEY);
    }
}
