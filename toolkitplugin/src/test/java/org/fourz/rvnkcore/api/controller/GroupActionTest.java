package org.fourz.rvnkcore.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PUT /v1/players/{uuid}/groups ignored "action" and replaced the whole list (#2126). */
@DisplayName("Player group update actions")
class GroupActionTest {

    private static final List<String> CURRENT = List.of("default", "trusted", "vip");

    @Test
    @DisplayName("remove drops only the named groups")
    void removeDropsOnlyNamedGroups() {
        assertEquals(List.of("default", "trusted"), PlayerController.applyGroupAction(CURRENT, "remove", List.of("vip")));
    }

    @Test
    @DisplayName("add appends without duplicating")
    void addAppendsWithoutDuplicates() {
        assertEquals(List.of("default", "trusted", "vip", "staff"),
                PlayerController.applyGroupAction(CURRENT, "add", List.of("vip", "staff")));
    }

    @Test
    @DisplayName("set replaces the list")
    void setReplaces() {
        assertEquals(List.of("staff"), PlayerController.applyGroupAction(CURRENT, "set", List.of("staff")));
    }

    @Test
    @DisplayName("remove and add work for a player with no groups yet")
    void noCurrentGroups() {
        assertEquals(List.of(), PlayerController.applyGroupAction(null, "remove", List.of("vip")));
        assertEquals(List.of("vip"), PlayerController.applyGroupAction(List.of(), "add", List.of("vip")));
    }
}
