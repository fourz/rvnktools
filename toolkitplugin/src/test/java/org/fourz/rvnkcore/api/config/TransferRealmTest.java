package org.fourz.rvnkcore.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for in-fiction realm naming on cross-server crossings.
 *
 * <p>The departure broadcast is the only place a player is told where someone went, and the realm
 * name is resolved from config defaults that no live server carries in its {@code config.yml} —
 * so the defaults are the shipped behaviour, not a fallback.</p>
 */
@DisplayName("Transfer realm naming")
class TransferRealmTest {

    @Nested
    @DisplayName("Default realm names")
    class Defaults {

        @Test
        @DisplayName("The three known servers have their in-fiction names")
        void knownServers() {
            assertEquals("the home realm", realmFor("prod", null, "Nations"));
            assertEquals("the Arcology", realmFor("event", null, "Event"));
            assertEquals("the fragile worlds", realmFor("dev", null, "Dev"));
        }

        @Test
        @DisplayName("'test' is an alias for dev")
        void testAliasesDev() {
            // Dev's chat-relay server-label is "test" while its webhook server-id is "dev";
            // whichever name a transfer target uses must land on the same realm.
            assertEquals("the fragile worlds", realmFor("test", null, "Test"));
        }

        @Test
        @DisplayName("Target name matching is case-insensitive")
        void caseInsensitive() {
            assertEquals("the Arcology", realmFor("EVENT", null, "Event"));
            assertEquals("the home realm", realmFor("Prod", null, "Nations"));
        }
    }

    @Nested
    @DisplayName("Overrides and fallbacks")
    class Overrides {

        @Test
        @DisplayName("An explicit realm: key wins over the default")
        void explicitWins() {
            assertEquals("the drowned city", realmFor("event", "the drowned city", "Event"));
        }

        @Test
        @DisplayName("A blank realm: key falls through to the default")
        void blankFallsThrough() {
            assertEquals("the Arcology", realmFor("event", "   ", "Event"));
        }

        @Test
        @DisplayName("An unknown target falls back to its display name")
        void unknownUsesDisplay() {
            // A fourth server added to config without a realm: key should read as something,
            // not as an empty string in the middle of a sentence.
            assertEquals("Sandbox", realmFor("sandbox", null, "Sandbox"));
        }

        @Test
        @DisplayName("An unknown target with no display falls back to a neutral phrase")
        void unknownWithNoDisplay() {
            assertEquals(TransferConfig.UNKNOWN_REALM, realmFor("sandbox", null, ""));
            assertEquals(TransferConfig.UNKNOWN_REALM, realmFor("sandbox", null, null));
        }
    }

    @Nested
    @DisplayName("Departure message")
    class Message {

        @Test
        @DisplayName("Default names the destination realm")
        void defaultNamesDestination() {
            String rendered = TransferConfig.DEFAULT_BROADCAST
                .replace("{player}", "Twinkies97")
                .replace("{realm}", "the Arcology");

            assertEquals("&dTwinkies97 &7has crossed to &fthe Arcology", rendered);
        }

        @Test
        @DisplayName("Default carries both placeholders")
        void carriesBothPlaceholders() {
            assertTrue(TransferConfig.DEFAULT_BROADCAST.contains("{player}"),
                "departure must name who crossed");
            assertTrue(TransferConfig.DEFAULT_BROADCAST.contains("{realm}"),
                "departure must name where they went - the old copy named neither");
        }

        @Test
        @DisplayName("Default uses no smart punctuation (#1753)")
        void asciiSafe() {
            for (char c : TransferConfig.DEFAULT_BROADCAST.toCharArray()) {
                assertTrue(c < 128, "non-ASCII char in default broadcast: " + c
                    + " - the Minecraft font renders these as diamonds");
            }
        }
    }

    /** Calls the real resolver — same package, so no reimplementation of the table under test. */
    private static String realmFor(String targetName, String configured, String display) {
        return TransferConfig.resolveRealm(targetName, configured, display);
    }
}
