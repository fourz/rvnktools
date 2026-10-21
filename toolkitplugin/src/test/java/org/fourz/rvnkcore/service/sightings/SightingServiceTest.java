package org.fourz.rvnkcore.service.sightings;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Sightings and entitled lists against a real SQLite file (#2351). */
@DisplayName("SightingService")
class SightingServiceTest {

    private static final UUID A = UUID.fromString("57d37338-7c06-458c-8cbe-6d9abff5e9af");
    private static final UUID B = UUID.fromString("315e02ae-e225-4594-bdf6-6430940ea36d");

    private Path file;
    private JdbcSightingStore sightings;
    private JdbcEntitlementStore entitlements;
    private SightingService service;
    private final AtomicLong now = new AtomicLong(1_792_000_000_000L);
    private final List<String> logs = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("sightings", ".db");
        ConnectionProvider db = new ConnectionProvider() {
            @Override public Connection getConnection() throws SQLException {
                return DriverManager.getConnection("jdbc:sqlite:" + file);
            }
            @Override public boolean isValid() { return true; }
            @Override public String getDatabaseType() { return "SQLite"; }
            @Override public void close() { }
        };
        sightings = new JdbcSightingStore(db);
        sightings.ensureSchema();
        entitlements = new JdbcEntitlementStore(db);
        entitlements.ensureSchema();
        service = newService(true);
    }

    private SightingService newService(boolean enabled) {
        return new SightingService(new SightingsConfig(enabled, "dev", 7, List.of()), sightings, entitlements,
                new SightingService.Log() {
                    @Override public void info(String m) { logs.add(m); }
                    @Override public void warning(String m) { logs.add("WARN " + m); }
                }, now::get);
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        Files.deleteIfExists(file);
    }

    private List<Sighting> all() throws Exception {
        return sightings.after(0, 500).sightings();
    }

    private void awaitRows(int n) throws Exception {
        long end = System.currentTimeMillis() + 3000;
        while (all().size() < n && System.currentTimeMillis() < end) Thread.sleep(20);
        assertEquals(n, all().size());
    }

    @Test
    void joinFromPublicAddressRecordsLists() throws Exception {
        entitlements.grant(A, "mc-dev", "test");
        assertTrue(service.recordJoin(A, "wizardoffice", InetAddress.getByName("8.8.4.4")));
        awaitRows(1);
        Sighting s = all().get(0);
        assertEquals(A, s.uuid());
        assertEquals("8.8.4.4", s.address());
        assertEquals("dev", s.server());
        assertEquals(Sighting.JOIN, s.reason());
        assertEquals(List.of("mc-dev"), s.lists());
        assertEquals(now.get(), s.at());
    }

    @Test
    void noSightingForLanUnknownOrWhenOff() throws Exception {
        assertFalse(service.recordJoin(A, "a", InetAddress.getByName("10.70.10.50")));
        assertFalse(service.recordJoin(A, "a", InetAddress.getByName("192.168.1.9")));
        assertFalse(service.recordJoin(A, "a", null));
        service.shutdown();
        service = newService(false);
        assertFalse(service.recordJoin(A, "a", InetAddress.getByName("8.8.8.8")));
        Thread.sleep(100);
        assertTrue(all().isEmpty());
    }

    @Test
    void grantWhileOnlineRecordsAnEntitlementSighting() throws Exception {
        SightingService.Change c = service.grant(A, "wizardoffice", "MC-Dev", "quest:dev_key",
                InetAddress.getByName("8.8.4.4")).get(3, TimeUnit.SECONDS);
        assertTrue(c.changed());
        assertTrue(c.sighted());
        assertEquals(List.of("mc-dev"), c.lists());
        awaitRows(1);
        assertEquals(Sighting.ENTITLEMENT, all().get(0).reason());
        assertEquals(List.of("mc-dev"), all().get(0).lists());

        SightingService.Change again = service.grant(A, "wizardoffice", "mc-dev", "x",
                InetAddress.getByName("8.8.4.4")).get(3, TimeUnit.SECONDS);
        assertFalse(again.changed(), "already granted");
        assertFalse(again.sighted(), "no new sighting for a no-op grant");
    }

    @Test
    void grantWhileOfflineChangesStateOnly() throws Exception {
        SightingService.Change c = service.grant(B, "HORNYC4T", "mc-dev", "console", null).get(3, TimeUnit.SECONDS);
        assertTrue(c.changed());
        assertFalse(c.sighted());
        Thread.sleep(100);
        assertTrue(all().isEmpty());
        assertEquals(List.of("mc-dev"), entitlements.lists(B));
        assertTrue(service.revoke(B, "HORNYC4T", "mc-dev", null).get(3, TimeUnit.SECONDS).changed());
        assertEquals(List.of(), entitlements.lists(B));
    }

    @Test
    @DisplayName("#2375: an offline revoke writes a revoke row with no address")
    void revokeOfflineWritesRevokeRow() throws Exception {
        entitlements.grant(B, "mc-dev", "test");
        SightingService.Change c = service.revoke(B, "HORNYC4T", "MC-DEV", null).get(3, TimeUnit.SECONDS);
        assertTrue(c.changed());
        assertTrue(c.sighted(), "a revoke row is written even offline");
        assertEquals(List.of(), entitlements.lists(B));
        awaitRows(1);
        Sighting s = all().get(0);
        assertEquals(Sighting.REVOKE, s.reason());
        assertNull(s.address());
        assertEquals(List.of(), s.lists(), "lists is always empty on a revoke row");
        assertEquals(List.of("mc-dev"), s.revoked());
        assertEquals(B, s.uuid());
        assertEquals("dev", s.server());
    }

    @Test
    @DisplayName("#2375: an online revoke keeps the public address and still never lists the removed list")
    void revokeOnlineWritesRevokeRowWithAddress() throws Exception {
        entitlements.grant(A, "mc-dev", "test");
        entitlements.grant(A, "radio", "test");
        assertTrue(service.revoke(A, "wizardoffice", "mc-dev", InetAddress.getByName("8.8.4.4"))
                .get(3, TimeUnit.SECONDS).sighted());
        awaitRows(1);
        Sighting s = all().get(0);
        assertEquals(Sighting.REVOKE, s.reason());
        assertEquals("8.8.4.4", s.address());
        assertEquals(List.of(), s.lists(), "the kept radio list is not reported on a revoke row");
        assertEquals(List.of("mc-dev"), s.revoked());
    }

    @Test
    @DisplayName("#2375: a LAN address on a revoke is dropped, the row is still written")
    void revokeFromLanAddressHasNoAddress() throws Exception {
        entitlements.grant(A, "mc-dev", "test");
        service.revoke(A, "a", "mc-dev", InetAddress.getByName("10.70.10.50")).get(3, TimeUnit.SECONDS);
        awaitRows(1);
        assertNull(all().get(0).address());
    }

    @Test
    @DisplayName("#2375: a revoke that removes nothing, or with sightings off, writes no row")
    void revokeWithoutChangeWritesNothing() throws Exception {
        SightingService.Change c = service.revoke(A, "a", "mc-dev", null).get(3, TimeUnit.SECONDS);
        assertFalse(c.changed());
        assertFalse(c.sighted());
        service.shutdown();
        service = newService(false);
        entitlements.grant(A, "mc-dev", "test");
        SightingService.Change off = service.revoke(A, "a", "mc-dev", null).get(3, TimeUnit.SECONDS);
        assertTrue(off.changed());
        assertFalse(off.sighted());
        Thread.sleep(100);
        assertTrue(all().isEmpty());
    }

    @Test
    @DisplayName("#2375: a revoke row cannot carry lists, even when built by hand")
    void revokeRowForcesEmptyLists() {
        Sighting s = new Sighting(0, A, "a", null, "dev", 1L, Sighting.REVOKE, List.of("mc-dev"), List.of("mc-dev"));
        assertEquals(List.of(), s.lists());
        Sighting j = new Sighting(0, A, "a", "8.8.8.8", "dev", 1L, Sighting.JOIN, List.of("mc-dev"));
        assertEquals(List.of(), j.revoked());
    }

    @Test
    @DisplayName("#2375: API rows carry revoked, and address null on an offline revoke")
    void apiShapeOfRevokeRow() throws Exception {
        entitlements.grant(B, "mc-dev", "test");
        service.recordJoin(A, "a", InetAddress.getByName("8.8.8.8"));
        awaitRows(1);
        service.revoke(B, "HORNYC4T", "mc-dev", null).get(3, TimeUnit.SECONDS);
        awaitRows(2);
        Map<String, Object> body = org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.body(
                "dev", 0, service.page(0, 10).get(3, TimeUnit.SECONDS));
        String json = org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.json(
                new com.google.gson.Gson(), body);
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertTrue(root.get("success").getAsBoolean());
        assertFalse(root.has("error"), "envelope unchanged: no null keys added");
        var rows = root.getAsJsonObject("data").getAsJsonArray("sightings");
        var join = rows.get(0).getAsJsonObject();
        assertEquals("8.8.8.8", join.get("address").getAsString());
        assertEquals(0, join.getAsJsonArray("revoked").size());
        var revoke = rows.get(1).getAsJsonObject();
        assertEquals("revoke", revoke.get("reason").getAsString());
        assertTrue(revoke.has("address"));
        assertTrue(revoke.get("address").isJsonNull());
        assertEquals(0, revoke.getAsJsonArray("lists").size());
        assertEquals("mc-dev", revoke.getAsJsonArray("revoked").get(0).getAsString());
    }

    @Test
    @DisplayName("#2375: a revoke by UUID for an unseen player (name = UUID) still stores the row")
    void revokeWithOverlongNameStoresRow() throws Exception {
        entitlements.grant(B, "mc-dev", "test");
        service.revoke(B, B.toString(), "mc-dev", null).get(3, TimeUnit.SECONDS);
        awaitRows(1);
        assertEquals("", all().get(0).name());
        assertEquals(List.of("mc-dev"), all().get(0).revoked());
    }

    @Test
    @DisplayName("#2375: a 1.5.114 table gains the revoked column, old rows read as revoked=[]")
    void migratesTableWithoutRevokedColumn() throws Exception {
        Path old = Files.createTempFile("sightings-old", ".db");
        try {
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old);
                 var st = c.createStatement()) {
                st.execute("CREATE TABLE " + JdbcSightingStore.TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "uuid TEXT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, server TEXT NOT NULL, "
                        + "reason TEXT NOT NULL, lists TEXT NOT NULL DEFAULT '', at_ms INTEGER NOT NULL)");
                st.execute("INSERT INTO " + JdbcSightingStore.TABLE
                        + " (uuid, name, address, server, reason, lists, at_ms) VALUES ('" + A
                        + "', 'a', '8.8.8.8', 'event', 'join', 'mc-dev', 1)");
            }
            JdbcSightingStore store = new JdbcSightingStore(new ConnectionProvider() {
                @Override public Connection getConnection() throws SQLException {
                    return DriverManager.getConnection("jdbc:sqlite:" + old);
                }
                @Override public boolean isValid() { return true; }
                @Override public String getDatabaseType() { return "SQLite"; }
                @Override public void close() { }
            });
            store.ensureSchema();
            store.ensureSchema(); // idempotent
            store.append(new Sighting(0, B, "b", null, "event", 2, Sighting.REVOKE, List.of(), List.of("mc-dev")));
            List<Sighting> rows = store.after(0, 10).sightings();
            assertEquals(List.of("mc-dev"), rows.get(0).lists());
            assertEquals(List.of(), rows.get(0).revoked());
            assertEquals(List.of("mc-dev"), rows.get(1).revoked());
            assertNull(rows.get(1).address());
        } finally {
            Files.deleteIfExists(old);
        }
    }

    @Test
    void invalidListNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SightingService.normaliseList("mc dev"));
        assertThrows(IllegalArgumentException.class, () -> SightingService.normaliseList(""));
        assertEquals("mc-dev", SightingService.normaliseList(" MC-DEV "));
    }

    @Test
    void pagesInOrderWithCursorAndMore() throws Exception {
        for (int i = 0; i < 5; i++) {
            sightings.append(new Sighting(0, A, "a", "8.8.8." + (i + 1), "dev", now.get() + i, Sighting.JOIN, List.of()));
        }
        SightingStore.Page p1 = service.page(0, 2).get(3, TimeUnit.SECONDS);
        assertEquals(2, p1.sightings().size());
        assertTrue(p1.more());
        long next = p1.sightings().get(1).id();
        SightingStore.Page p2 = service.page(next, 10).get(3, TimeUnit.SECONDS);
        assertEquals(3, p2.sightings().size());
        assertFalse(p2.more());
        assertTrue(p2.sightings().get(0).id() > next);

        Map<String, Object> body = org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.body("dev", next, p2);
        assertEquals(Long.toString(p2.sightings().get(2).id()), body.get("next"));
        assertEquals(false, body.get("gap"));
        Map<String, Object> empty = org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.body(
                "dev", 99, service.page(99, 10).get(3, TimeUnit.SECONDS));
        assertEquals("99", empty.get("next"), "an empty page keeps the cursor");
    }

    @Test
    void pruneKeepsNewestRowAndReportsGap() throws Exception {
        long old = now.get() - TimeUnit.DAYS.toMillis(8);
        long id1 = sightings.append(new Sighting(0, A, "a", "8.8.8.1", "dev", old, Sighting.JOIN, List.of()));
        long id2 = sightings.append(new Sighting(0, A, "a", "8.8.8.2", "dev", old, Sighting.JOIN, List.of()));
        long id3 = sightings.append(new Sighting(0, A, "a", "8.8.8.3", "dev", old, Sighting.JOIN, List.of()));
        assertEquals(2, service.pruneNow(), "the newest row survives even when it is old");
        List<Sighting> left = all();
        assertEquals(1, left.size());
        assertEquals(id3, left.get(0).id());
        long id4 = sightings.append(new Sighting(0, A, "a", "8.8.8.4", "dev", now.get(), Sighting.JOIN, List.of()));
        assertTrue(id4 > id3, "ids never restart");
        SightingStore.Page p = service.page(id1, 10).get(3, TimeUnit.SECONDS);
        assertTrue(SightingService.gap(id1, p.oldestId()), "a cursor before pruned rows reports a gap");
        assertFalse(SightingService.gap(id2, p.oldestId()), "the cursor right before the oldest row is no gap");
        assertFalse(SightingService.gap(0, p.oldestId()), "a fresh start is no gap");
    }

    @Test
    void cursorParsing() {
        assertEquals(0L, org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.parseCursor(null));
        assertEquals(0L, org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.parseCursor(""));
        assertEquals(18342L, org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.parseCursor("18342"));
        assertThrows(NumberFormatException.class,
                () -> org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.parseCursor("-1"));
        assertThrows(NumberFormatException.class,
                () -> org.fourz.rvnkcore.api.controller.NetSightingsControllerAccess.parseCursor("1e5"));
    }
}
