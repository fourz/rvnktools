package org.fourz.rvnkcore.service.kit;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kit storage (#2415): the payload envelope, the SQLite table and the service. Item bytes here are
 * stand-ins; the envelope must return them byte for byte, which is what keeps every data component
 * the codec wrote. The codec itself needs a running Paper server and is checked on RVNK Dev.
 */
class KitStoreTest {

    private Path file;
    private JdbcKitStore store;

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("kits", ".db");
        store = new JdbcKitStore(new ConnectionProvider() {
            @Override public Connection getConnection() throws SQLException {
                return DriverManager.getConnection("jdbc:sqlite:" + file);
            }
            @Override public boolean isValid() { return true; }
            @Override public String getDatabaseType() { return "SQLite"; }
            @Override public void close() { }
        });
        store.ensureSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(file);
    }

    private static SortedMap<Integer, byte[]> items() {
        SortedMap<Integer, byte[]> m = new TreeMap<>();
        m.put(0, "sword{enchants:sharpness 3}".getBytes(StandardCharsets.UTF_8));
        m.put(KitSlots.HEAD, new byte[] {0, (byte) 0xFF, 7, 0});
        m.put(KitSlots.OFF_HAND, "torch".getBytes(StandardCharsets.UTF_8));
        m.put(KitSlots.FEET, new byte[0]);
        return m;
    }

    private static Kit kit(String name, boolean clear, SortedMap<Integer, byte[]> items) {
        return new Kit(name, clear, "paper", items.size(), items, 0, "player:tester", 1_792_000_000_000L);
    }

    private static void assertSameItems(SortedMap<Integer, byte[]> want, java.util.Map<Integer, byte[]> got) {
        assertEquals(want.keySet(), got.keySet());
        want.forEach((slot, bytes) -> assertArrayEquals(bytes, got.get(slot), "slot " + slot));
    }

    @Test
    void payloadRoundTripsByteForByte() {
        SortedMap<Integer, byte[]> in = items();
        assertSameItems(in, KitPayload.decode(KitPayload.encode(in)));
        assertTrue(KitPayload.decode("").isEmpty());
    }

    @Test
    void payloadRejectsGarbageAndBadSlots() {
        assertThrows(IllegalArgumentException.class,
                () -> KitPayload.decode(Base64.getEncoder().encodeToString("nope".getBytes())));
        String good = KitPayload.encode(items());
        assertThrows(IllegalArgumentException.class, () -> KitPayload.decode(good.substring(0, good.length() / 2)));
        assertThrows(IllegalArgumentException.class, () -> KitPayload.encode(java.util.Map.of(41, new byte[1])));
    }

    @Test
    void saveGetRoundTripKeepsItemsAndFlags() throws Exception {
        assertEquals(1, store.save(kit("qa-survival", true, items())));
        Optional<Kit> got = store.get("qa-survival");
        assertTrue(got.isPresent());
        Kit k = got.get();
        assertTrue(k.clearFirst());
        assertEquals("paper", k.codec());
        assertEquals(4, k.itemCount());
        assertEquals(1, k.version());
        assertEquals("player:tester", k.savedBy());
        assertSameItems(items(), k.items());
    }

    @Test
    void saveOverBumpsVersionAndReplacesItems() throws Exception {
        store.save(kit("event-basic", false, items()));
        SortedMap<Integer, byte[]> fewer = new TreeMap<>();
        fewer.put(3, new byte[] {9});
        assertEquals(2, store.save(kit("event-basic", true, fewer)));
        Kit k = store.get("event-basic").orElseThrow();
        assertEquals(2, k.version());
        assertTrue(k.clearFirst());
        assertSameItems(fewer, k.items());
    }

    @Test
    void listIsSortedAndCarriesNoPayload() throws Exception {
        store.save(kit("zeta", false, items()));
        store.save(kit("alpha", false, items()));
        List<Kit> all = store.list();
        assertEquals(List.of("alpha", "zeta"), all.stream().map(Kit::name).toList());
        assertEquals(4, all.get(0).itemCount());
        assertTrue(all.get(0).items().isEmpty());
    }

    @Test
    void deleteReportsWhetherAKitWentAway() throws Exception {
        store.save(kit("gone", false, items()));
        assertTrue(store.delete("gone"));
        assertFalse(store.delete("gone"));
        assertTrue(store.get("gone").isEmpty());
    }

    @Test
    void serviceKeepsTheNameCacheCurrent() throws Exception {
        KitService svc = new KitService(store);
        try {
            svc.save(kit("b", false, items())).get();
            svc.save(kit("a", false, items())).get();
            assertEquals(List.of("a", "b"), svc.cachedNames());
            svc.delete("b").get();
            assertEquals(List.of("a"), svc.cachedNames());
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void mysqlUpsertBumpsTheVersionToo() {
        assertTrue(JdbcKitStore.upsertSql(true).contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(JdbcKitStore.upsertSql(true).contains("version = version + 1"));
        assertTrue(JdbcKitStore.ddl(true).contains("MEDIUMTEXT"));
    }

    @Test
    void codecLookup() {
        assertSame(ItemCodec.BUKKIT, ItemCodec.forId("bukkit"));
        assertThrows(IllegalArgumentException.class, () -> ItemCodec.forId("nbt-v9"));
        // spigot-api on the test classpath has no serializeAsBytes, so Paper is absent here.
        assertNull(ItemCodec.PAPER);
        assertSame(ItemCodec.BUKKIT, ItemCodec.best());
        assertThrows(IllegalArgumentException.class, () -> ItemCodec.forId("paper"));
    }

    @Test
    void grantPlanAddKeepsFreeSlotsAndSpillsTheRest() {
        java.util.Map<Integer, String> kit = new TreeMap<>(java.util.Map.of(
                0, "sword", 1, "bread", KitSlots.HEAD, "helmet", KitSlots.OFF_HAND, "torch"));
        // Slot 0 and the head are taken.
        KitGrantPlan<String> plan = KitGrantPlan.of(kit, s -> s != 0 && s != KitSlots.HEAD, false);
        assertFalse(plan.clear());
        assertEquals(List.of(1, KitSlots.OFF_HAND), List.copyOf(plan.place().keySet()));
        assertEquals(List.of("sword", "helmet"), plan.add(), "occupied slots go to free space, in slot order");
    }

    @Test
    void grantPlanClearFirstPlacesEverythingInItsSlot() {
        java.util.Map<Integer, String> kit = new TreeMap<>(java.util.Map.of(0, "sword", KitSlots.CHEST, "plate"));
        KitGrantPlan<String> plan = KitGrantPlan.of(kit, s -> false, true);
        assertTrue(plan.clear());
        assertEquals(kit, plan.place());
        assertTrue(plan.add().isEmpty());
    }

    @Test
    void kitNames() {
        assertEquals("qa-survival", Kit.normaliseName(" QA-Survival "));
        assertThrows(IllegalArgumentException.class, () -> Kit.normaliseName("admin"), "collides with rvnkcore.kit.admin");
        assertThrows(IllegalArgumentException.class, () -> Kit.normaliseName("has space"));
        assertThrows(IllegalArgumentException.class, () -> Kit.normaliseName("x".repeat(33)));
        assertEquals("rvnkcore.kit.qa-survival", Kit.permission("qa-survival"));
    }
}
