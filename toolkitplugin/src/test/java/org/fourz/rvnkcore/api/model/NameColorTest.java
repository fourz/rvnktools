package org.fourz.rvnkcore.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Name color parsing and the brightness floor (#2468). */
class NameColorTest {

    @Test
    @DisplayName("hex with or without # is accepted and lowercased")
    void hex() {
        assertEquals("#ff8800", NameColor.parse("#FF8800").hex());
        assertEquals("#ff8800", NameColor.parse("ff8800").hex());
        assertEquals("#ff8800", NameColor.parse("  #ff8800 ").hex());
    }

    @Test
    @DisplayName("color names map to hex, spaces and dashes count as underscores")
    void names() {
        assertEquals("#ffaa00", NameColor.parse("gold").hex());
        assertEquals("#ff55ff", NameColor.parse("Light Purple").hex());
        assertEquals("#00aaaa", NameColor.parse("dark-aqua").hex());
    }

    @Test
    @DisplayName("reset words clear the color")
    void reset() {
        for (String w : new String[] {"reset", "DEFAULT", "none", "clear", "off"}) {
            NameColor.Result r = NameColor.parse(w);
            assertTrue(r.ok() && r.reset() && r.hex() == null, w);
        }
    }

    @Test
    @DisplayName("colors darker than the floor are refused with the reason")
    void tooDark() {
        NameColor.Result r = NameColor.parse("#000010");
        assertFalse(r.ok());
        assertTrue(r.error().contains("too dark"));
        assertFalse(NameColor.parse("#0000aa").ok(), "dark_blue fails the floor");
        assertTrue(NameColor.parse("#aa0000").ok(), "dark_red passes the floor");
        for (String hex : NameColor.NAMED.values()) {
            assertTrue(NameColor.brightness(hex) >= NameColor.MIN_BRIGHTNESS, hex);
        }
    }

    @Test
    @DisplayName("bad input gives an error, never a color")
    void bad() {
        for (String s : new String[] {null, "", "  ", "#ff88", "#gg0000", "black", "dark_blue", "#ff880000"}) {
            NameColor.Result r = NameColor.parse(s);
            assertFalse(r.ok(), String.valueOf(s));
            assertNull(r.hex());
        }
    }

    @Test
    @DisplayName("only a line that is a single #RRGGBB counts as the chat shortcut")
    void hexLine() {
        assertTrue(NameColor.isHexLine("#ff8800"));
        assertTrue(NameColor.isHexLine(" #FF8800 "));
        assertFalse(NameColor.isHexLine("ff8800"));
        assertFalse(NameColor.isHexLine("my color is #ff8800"));
        assertFalse(NameColor.isHexLine("#ff8800 #00ff00"));
        assertFalse(NameColor.isHexLine(null));
    }

    @Test
    @DisplayName("stored values are checked again on load")
    void stored() {
        assertEquals("#ff8800", NameColor.normalizeStored("#FF8800"));
        assertNull(NameColor.normalizeStored("#000000"));
        assertNull(NameColor.normalizeStored("reset"));
        assertNull(NameColor.normalizeStored(null));
    }
}
