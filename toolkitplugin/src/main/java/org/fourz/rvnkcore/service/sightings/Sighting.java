package org.fourz.rvnkcore.service.sightings;

import java.util.List;
import java.util.UUID;

/**
 * One player sighting (#2351): a player was seen at a public IPv4 address on this server, with the
 * lists the player is entitled to at that moment. z-office pulls these from {@code /v1/net-sightings}.
 *
 * <p>A {@link #REVOKE} row (#2375) is different: it says staff removed {@code revoked} from the
 * player. Its {@code lists} is always empty, because z-office reads a list in {@code lists} as a
 * grant, and its {@code address} is null when the player was offline.</p>
 *
 * @param id      row id, increasing per server; 0 before the row is stored
 * @param address public IPv4; null only on a {@link #REVOKE} row
 * @param at      epoch milliseconds, UTC
 * @param reason  {@link #JOIN}, {@link #ENTITLEMENT} or {@link #REVOKE}
 * @param lists   entitled lists, sorted; may be empty; always empty on a revoke row
 * @param revoked lists removed; empty except on a revoke row
 */
public record Sighting(long id, UUID uuid, String name, String address, String server, long at,
                       String reason, List<String> lists, List<String> revoked) {

    public static final String JOIN = "join";
    public static final String ENTITLEMENT = "entitlement";
    public static final String REVOKE = "revoke";

    public Sighting {
        lists = REVOKE.equals(reason) ? List.of() : List.copyOf(lists);
        revoked = revoked == null ? List.of() : List.copyOf(revoked);
    }

    /** A join or entitlement row. */
    public Sighting(long id, UUID uuid, String name, String address, String server, long at,
                    String reason, List<String> lists) {
        this(id, uuid, name, address, server, at, reason, lists, List.of());
    }

    public Sighting withId(long newId) {
        return new Sighting(newId, uuid, name, address, server, at, reason, lists, revoked);
    }
}
