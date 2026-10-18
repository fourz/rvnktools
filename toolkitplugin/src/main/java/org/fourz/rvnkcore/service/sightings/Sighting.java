package org.fourz.rvnkcore.service.sightings;

import java.util.List;
import java.util.UUID;

/**
 * One player sighting (#2351): a player was seen at a public IPv4 address on this server, with the
 * lists the player is entitled to at that moment. z-office pulls these from {@code /v1/net-sightings}.
 *
 * @param id      row id, increasing per server; 0 before the row is stored
 * @param at      epoch milliseconds, UTC
 * @param reason  {@link #JOIN} or {@link #ENTITLEMENT}
 * @param lists   entitled lists, sorted; may be empty
 */
public record Sighting(long id, UUID uuid, String name, String address, String server, long at,
                       String reason, List<String> lists) {

    public static final String JOIN = "join";
    public static final String ENTITLEMENT = "entitlement";

    public Sighting {
        lists = List.copyOf(lists);
    }

    public Sighting withId(long newId) {
        return new Sighting(newId, uuid, name, address, server, at, reason, lists);
    }
}
