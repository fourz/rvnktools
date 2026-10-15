package org.fourz.rvnkcore.service.whitelist;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Whitelist and member-group operations by UUID (#2346, #2347).
 *
 * <p>A name is resolved to a UUID first, then every change acts on the UUID. {@code whitelist.json}
 * keeps the name from when an entry was added, so a renamed player would read "not whitelisted" by
 * name while the server still lets them in by UUID (#2347).</p>
 *
 * <p>No Bukkit or LuckPerms types here: the three seams below carry them, so the rules are testable.</p>
 */
public class WhitelistMembership {

    /** Minecraft account names. */
    public static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    /** LuckPerms stores group names lower-case. */
    public static final Pattern GROUP = Pattern.compile("[a-z0-9_-]{1,36}");

    /** A resolved Minecraft account. */
    public record Profile(UUID uuid, String name) { }

    /** Name to UUID. Empty when no account has that name. Fails when the lookup itself fails. */
    public interface ProfileLookup {
        CompletableFuture<Optional<Profile>> byName(String ign);
    }

    /** The server whitelist, keyed by UUID. {@code add} also gets the name so the entry is not blank. */
    public interface WhitelistStore {
        CompletableFuture<Boolean> isWhitelisted(UUID uuid);
        CompletableFuture<Void> add(Profile profile);
        CompletableFuture<Void> remove(UUID uuid);
    }

    /** Outcome of adding a member group. */
    public enum GroupResult {
        /** The player now inherits the group. */
        ADDED,
        /** The player already inherited the group. */
        ALREADY_MEMBER,
        /** No group of that name exists on this server. */
        GROUP_NOT_FOUND,
        /** LuckPerms is not installed or not loaded. */
        UNAVAILABLE,
        /** LuckPerms threw. */
        FAILED;

        /** True when the player is in the group after the call. */
        public boolean ok() {
            return this == ADDED || this == ALREADY_MEMBER;
        }
    }

    /** Adds one inheritance node; never removes groups. */
    public interface GroupAssigner {
        CompletableFuture<GroupResult> addGroup(UUID uuid, String group);
    }

    /** Thrown (wrapped) when no account has the requested name. */
    public static class UnknownPlayerException extends RuntimeException {
        public UnknownPlayerException(String ign) {
            super("No Minecraft account named " + ign);
        }
    }

    /** Thrown (wrapped) when the name lookup itself failed, for example a Mojang outage. */
    public static class LookupFailedException extends RuntimeException {
        public LookupFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result of an add.
     *
     * @param group       the requested group, or null when none was requested
     * @param groupResult null when no group was requested
     */
    public record AddResult(String ign, UUID uuid, String name, boolean whitelisted,
                            String group, GroupResult groupResult) {
        /** True when the whitelist is set and the group (if requested) is in place. */
        public boolean ok() {
            return whitelisted && (groupResult == null || groupResult.ok());
        }
    }

    /** Result of a whitelist check. */
    public record CheckResult(String ign, UUID uuid, String name, boolean whitelisted) { }

    private final ProfileLookup profiles;
    private final WhitelistStore whitelist;
    private final GroupAssigner groups;

    public WhitelistMembership(ProfileLookup profiles, WhitelistStore whitelist, GroupAssigner groups) {
        this.profiles = profiles;
        this.whitelist = whitelist;
        this.groups = groups;
    }

    /**
     * Whitelists the player by UUID and, when {@code group} is not null, adds that group.
     * A group failure does not undo the whitelist: the result reports each part.
     */
    public CompletableFuture<AddResult> add(String ign, String group) {
        return resolve(ign).thenCompose(profile ->
            whitelist.add(profile).thenCompose(v -> {
                if (group == null) {
                    return CompletableFuture.completedFuture(
                        new AddResult(ign, profile.uuid(), profile.name(), true, null, null));
                }
                return groups.addGroup(profile.uuid(), group)
                    .exceptionally(ex -> GroupResult.FAILED)
                    .thenApply(r -> new AddResult(ign, profile.uuid(), profile.name(), true, group, r));
            }));
    }

    /** Reads the whitelist by UUID. */
    public CompletableFuture<CheckResult> check(String ign) {
        return resolve(ign).thenCompose(profile ->
            whitelist.isWhitelisted(profile.uuid())
                .thenApply(w -> new CheckResult(ign, profile.uuid(), profile.name(), w)));
    }

    /** Removes the player from the whitelist by UUID. Groups are left alone. */
    public CompletableFuture<CheckResult> remove(String ign) {
        return resolve(ign).thenCompose(profile ->
            whitelist.remove(profile.uuid())
                .thenApply(v -> new CheckResult(ign, profile.uuid(), profile.name(), false)));
    }

    private CompletableFuture<Profile> resolve(String ign) {
        return profiles.byName(ign).thenApply(p -> p.orElseThrow(() -> new UnknownPlayerException(ign)));
    }

    /** Normalises a requested group: null or blank means "no group"; otherwise lower-case and validated. */
    public static Optional<String> normaliseGroup(Object raw) {
        if (raw == null) return Optional.empty();
        String g = raw.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (g.isEmpty()) return Optional.empty();
        if (!GROUP.matcher(g).matches()) throw new IllegalArgumentException("Invalid group name: " + raw);
        return Optional.of(g);
    }
}
