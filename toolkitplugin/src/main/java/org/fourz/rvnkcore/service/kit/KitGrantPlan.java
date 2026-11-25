package org.fourz.rvnkcore.service.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Where each kit item lands in a player's inventory. Pure and generic over the item type, so it is
 * tested without a server.
 *
 * <ul>
 *   <li><b>clear-first</b>: clear the inventory, then place every item in its own slot.</li>
 *   <li><b>add</b>: an item goes to its own slot when that slot is empty, else into free space
 *       ({@code addItem}, which also stacks). What does not fit is dropped at the player's feet.</li>
 * </ul>
 *
 * @param clear true when the inventory is cleared first
 * @param place items set directly into their slot
 * @param add   items handed to {@code addItem}, in slot order
 */
public record KitGrantPlan<T>(boolean clear, SortedMap<Integer, T> place, List<T> add) {

    public static <T> KitGrantPlan<T> of(Map<Integer, T> kit, IntPredicate slotEmpty, boolean clearFirst) {
        SortedMap<Integer, T> place = new TreeMap<>();
        List<T> add = new ArrayList<>();
        for (Map.Entry<Integer, T> e : new TreeMap<>(kit).entrySet()) {
            if (clearFirst || slotEmpty.test(e.getKey())) {
                place.put(e.getKey(), e.getValue());
            } else {
                add.add(e.getValue());
            }
        }
        return new KitGrantPlan<>(clearFirst, Collections.unmodifiableSortedMap(place),
                Collections.unmodifiableList(add));
    }
}
