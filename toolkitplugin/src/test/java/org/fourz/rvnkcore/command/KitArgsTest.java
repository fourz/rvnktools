package org.fourz.rvnkcore.command;

import org.fourz.rvnkcore.command.KitArgs.Verb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@code /rvnk kit} parsing and the permission rule (#2415). */
class KitArgsTest {

    private static KitArgs p(String line) {
        return KitArgs.parse(line.split(" "));
    }

    @Test
    void parsesEveryVerb() {
        assertEquals(new KitArgs(Verb.LIST, null, null, false), p("list"));
        assertEquals(new KitArgs(Verb.SHOW, "qa-survival", null, false), p("show QA-Survival"));
        assertEquals(new KitArgs(Verb.DELETE, "qa-survival", null, false), p("delete qa-survival"));
        assertEquals(new KitArgs(Verb.GIVE, "qa-survival", null, false), p("give qa-survival"));
        assertEquals(new KitArgs(Verb.GIVE, "qa-survival", "Shad0melt", false), p("give qa-survival Shad0melt"));
        assertEquals(new KitArgs(Verb.SAVE, "event-basic", null, false), p("save event-basic"));
        assertEquals(new KitArgs(Verb.SAVE, "event-basic", null, true), p("save event-basic --clear"));
        assertEquals(new KitArgs(Verb.SAVE, "event-basic", null, false), p("save event-basic --clear --add"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> KitArgs.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> p("grant qa"));
        assertThrows(IllegalArgumentException.class, () -> p("give"));
        assertThrows(IllegalArgumentException.class, () -> p("give qa a b"));
        assertThrows(IllegalArgumentException.class, () -> p("save qa --force"));
        assertThrows(IllegalArgumentException.class, () -> p("save admin"));
        assertThrows(IllegalArgumentException.class, () -> p("list extra"));
        assertThrows(IllegalArgumentException.class, () -> p("show bad/name"));
    }

    @Test
    void adminMayDoEverything() {
        for (Verb v : Verb.values()) {
            assertTrue(KitArgs.allowed(v, true, false, false), v.name());
        }
    }

    @Test
    void kitNodeGivesYourselfOnly() {
        assertTrue(KitArgs.allowed(Verb.GIVE, false, true, true));
        assertFalse(KitArgs.allowed(Verb.GIVE, false, true, false), "giving another player needs admin");
        assertFalse(KitArgs.allowed(Verb.GIVE, false, false, true), "no kit node");
        assertTrue(KitArgs.allowed(Verb.SHOW, false, true, true));
        assertFalse(KitArgs.allowed(Verb.SHOW, false, false, true));
    }

    @Test
    void saveAndDeleteNeedAdmin() {
        assertFalse(KitArgs.allowed(Verb.SAVE, false, true, true));
        assertFalse(KitArgs.allowed(Verb.DELETE, false, true, true));
        assertTrue(KitArgs.allowed(Verb.LIST, false, false, true), "list is open; it filters its rows");
    }
}
