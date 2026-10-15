package org.fourz.rvnkcore.service.whitelist;

import net.luckperms.api.model.data.DataMutateResult;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.AddResult;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.GroupResult;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.Profile;
import org.fourz.rvnkcore.service.whitelist.luckperms.LuckPermsGroupAssigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/** Whitelist + member group by UUID (#2346, #2347). */
@DisplayName("WhitelistMembership")
class WhitelistMembershipTest {

    private static final UUID HORNY = UUID.fromString("315e02ae-e225-4594-bdf6-6430940ea36d");

    /** whitelist.json keeps the name from when the entry was added; the store is keyed by UUID only. */
    private final Set<UUID> whitelist = new HashSet<>();
    private final Map<UUID, Set<String>> groups = new HashMap<>();
    private final Set<String> knownGroups = new HashSet<>(Set.of("default", "resident", "veteran"));

    private final WhitelistMembership.ProfileLookup lookup = ign -> CompletableFuture.completedFuture(
            "HORNYC4T".equalsIgnoreCase(ign) ? Optional.of(new Profile(HORNY, "HORNYC4T")) : Optional.empty());

    private final WhitelistMembership.WhitelistStore store = new WhitelistMembership.WhitelistStore() {
        @Override public CompletableFuture<Boolean> isWhitelisted(UUID uuid) {
            return CompletableFuture.completedFuture(whitelist.contains(uuid));
        }
        @Override public CompletableFuture<Void> add(Profile profile) {
            whitelist.add(profile.uuid());
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> remove(UUID uuid) {
            whitelist.remove(uuid);
            return CompletableFuture.completedFuture(null);
        }
    };

    private final WhitelistMembership.GroupAssigner assigner = (uuid, group) -> {
        if (!knownGroups.contains(group)) return CompletableFuture.completedFuture(GroupResult.GROUP_NOT_FOUND);
        boolean added = groups.computeIfAbsent(uuid, u -> new HashSet<>()).add(group);
        return CompletableFuture.completedFuture(added ? GroupResult.ADDED : GroupResult.ALREADY_MEMBER);
    };

    private final WhitelistMembership membership = new WhitelistMembership(lookup, store, assigner);

    @Test
    @DisplayName("a renamed player reads whitelisted: the check goes by UUID, not the stored name")
    void renamedPlayerReadsWhitelisted() {
        whitelist.add(HORNY); // entry stored as SERIOUSCALLSONLY on Event
        assertTrue(membership.check("HORNYC4T").join().whitelisted());
    }

    @Test
    @DisplayName("add whitelists by UUID and adds the group")
    void addWhitelistsAndGroups() {
        AddResult r = membership.add("HORNYC4T", "resident").join();
        assertTrue(whitelist.contains(HORNY));
        assertEquals(GroupResult.ADDED, r.groupResult());
        assertEquals(HORNY, r.uuid());
        assertTrue(r.ok());
    }

    @Test
    @DisplayName("re-running add is idempotent: already a member is still ok")
    void rerunIsIdempotent() {
        membership.add("HORNYC4T", "resident").join();
        AddResult again = membership.add("HORNYC4T", "resident").join();
        assertEquals(GroupResult.ALREADY_MEMBER, again.groupResult());
        assertTrue(again.ok());
        assertEquals(Set.of("resident"), groups.get(HORNY));
    }

    @Test
    @DisplayName("add without a group only whitelists")
    void addWithoutGroup() {
        AddResult r = membership.add("HORNYC4T", null).join();
        assertNull(r.groupResult());
        assertTrue(r.ok());
        assertNull(groups.get(HORNY));
    }

    @Test
    @DisplayName("a missing group keeps the whitelist and reports not ok")
    void missingGroupKeepsWhitelist() {
        AddResult r = membership.add("HORNYC4T", "member").join();
        assertTrue(whitelist.contains(HORNY));
        assertEquals(GroupResult.GROUP_NOT_FOUND, r.groupResult());
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("a group step that throws reads FAILED and keeps the whitelist")
    void throwingAssignerReadsFailed() {
        WhitelistMembership broken = new WhitelistMembership(lookup, store,
                (uuid, g) -> CompletableFuture.failedFuture(new IllegalStateException("db down")));
        AddResult r = broken.add("HORNYC4T", "resident").join();
        assertEquals(GroupResult.FAILED, r.groupResult());
        assertTrue(whitelist.contains(HORNY));
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("an unknown name fails with UnknownPlayerException and changes nothing")
    void unknownPlayer() {
        CompletionException e = assertThrows(CompletionException.class,
                () -> membership.add("NoSuchPlayer", "resident").join());
        assertInstanceOf(WhitelistMembership.UnknownPlayerException.class, e.getCause());
        assertTrue(whitelist.isEmpty());
    }

    @Test
    @DisplayName("remove takes the UUID off the whitelist and leaves groups alone")
    void removeKeepsGroups() {
        membership.add("HORNYC4T", "resident").join();
        assertFalse(membership.remove("HORNYC4T").join().whitelisted());
        assertFalse(whitelist.contains(HORNY));
        assertEquals(Set.of("resident"), groups.get(HORNY));
    }

    @Test
    @DisplayName("group names are trimmed, lower-cased and validated; blank means none")
    void normaliseGroup() {
        assertEquals(Optional.of("resident"), WhitelistMembership.normaliseGroup(" Resident "));
        assertEquals(Optional.empty(), WhitelistMembership.normaliseGroup(""));
        assertEquals(Optional.empty(), WhitelistMembership.normaliseGroup(null));
        assertThrows(IllegalArgumentException.class, () -> WhitelistMembership.normaliseGroup("res ident"));
        assertThrows(IllegalArgumentException.class, () -> WhitelistMembership.normaliseGroup("group.resident"));
    }

    @Test
    @DisplayName("Mojang: 200 parses the undashed id; 404 and 204 mean no account; other codes fail")
    void mojangParse() {
        Optional<Profile> p = MojangProfileLookup.parse(200,
                "{\"id\":\"315e02aee2254594bdf66430940ea36d\",\"name\":\"HORNYC4T\"}");
        assertEquals(HORNY, p.orElseThrow().uuid());
        assertEquals("HORNYC4T", p.orElseThrow().name());
        assertTrue(MojangProfileLookup.parse(404, "").isEmpty());
        assertTrue(MojangProfileLookup.parse(204, "").isEmpty());
        assertThrows(IllegalStateException.class, () -> MojangProfileLookup.parse(429, "{}"));
        assertThrows(IllegalArgumentException.class, () -> MojangProfileLookup.undashed("abc"));
    }

    @Test
    @DisplayName("LuckPerms mutate result maps to added / already member / failed")
    void luckPermsResultMapping() {
        assertEquals(GroupResult.ADDED, LuckPermsGroupAssigner.toResult(DataMutateResult.SUCCESS));
        assertEquals(GroupResult.ALREADY_MEMBER, LuckPermsGroupAssigner.toResult(DataMutateResult.FAIL_ALREADY_HAS));
        assertEquals(GroupResult.FAILED, LuckPermsGroupAssigner.toResult(DataMutateResult.FAIL));
        assertEquals(GroupResult.FAILED, LuckPermsGroupAssigner.toResult(null));
    }
}
