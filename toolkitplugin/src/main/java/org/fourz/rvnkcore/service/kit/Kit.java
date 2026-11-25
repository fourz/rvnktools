package org.fourz.rvnkcore.service.kit;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A named item set (#2415): encoded items by inventory slot, and whether a grant clears the inventory
 * first. Items stay encoded here; they become {@code ItemStack}s only on the main thread at grant time.
 *
 * @param name      lower-case kit name, {@link #NAME}
 * @param clearFirst true: a grant clears the inventory and places every item in its slot;
 *                   false: a grant adds, and an occupied slot sends its item to free space
 * @param codec     the {@link ItemCodec} id that encoded {@code items}
 * @param itemCount number of filled slots (kept in its own column, so a list needs no payload)
 * @param items     encoded item per slot, {@link KitSlots}; empty in a list result
 * @param version   1 on the first save, +1 on each save over it
 * @param savedBy   who saved it, for example {@code player:wizardofire}
 * @param savedAt   epoch millis of the last save
 */
public record Kit(String name, boolean clearFirst, String codec, int itemCount,
                  SortedMap<Integer, byte[]> items, int version, String savedBy, long savedAt) {

    /** Kit names: lower case, digits, dash and underscore. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    /** {@code rvnkcore.kit.admin} is the admin node, so no kit may be called {@code admin}. */
    static final Set<String> RESERVED = Set.of("admin");

    public Kit {
        items = Collections.unmodifiableSortedMap(new TreeMap<>(items));
    }

    /** A list row: no payload. */
    public static Kit summary(String name, boolean clearFirst, String codec, int itemCount,
                              int version, String savedBy, long savedAt) {
        return new Kit(name, clearFirst, codec, itemCount, new TreeMap<>(), version, savedBy, savedAt);
    }

    /** Lower-cases and checks a kit name. */
    public static String normaliseName(String raw) {
        String n = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(n).matches()) {
            throw new IllegalArgumentException("Invalid kit name: " + raw + " (use a-z, 0-9, - and _, at most 32)");
        }
        if (RESERVED.contains(n)) throw new IllegalArgumentException("Kit name is reserved: " + n);
        return n;
    }

    /** The node that lets a player give this kit to themselves. */
    public static String permission(String name) {
        return "rvnkcore.kit." + name;
    }
}
