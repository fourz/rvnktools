package org.fourz.rvnkcore.service.kit;

/**
 * Kit slot numbers. They follow Bukkit's {@code PlayerInventory#getContents()} order: storage 0-35
 * (hotbar 0-8), then boots, leggings, chestplate, helmet, then the off-hand.
 */
public final class KitSlots {

    public static final int STORAGE_SIZE = 36;
    public static final int FEET = 36;
    public static final int LEGS = 37;
    public static final int CHEST = 38;
    public static final int HEAD = 39;
    public static final int OFF_HAND = 40;
    public static final int SIZE = 41;

    private KitSlots() { }

    public static boolean valid(int slot) {
        return slot >= 0 && slot < SIZE;
    }

    /** Human label for {@code show}. */
    public static String label(int slot) {
        return switch (slot) {
            case FEET -> "feet";
            case LEGS -> "legs";
            case CHEST -> "chest";
            case HEAD -> "head";
            case OFF_HAND -> "off-hand";
            default -> slot < 9 ? "hotbar " + slot : "slot " + slot;
        };
    }
}
