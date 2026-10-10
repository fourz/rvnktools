package org.fourz.rvnkcore.service.npc.harness;

/**
 * One finding of {@code /rvnk npc verify} (#2248).
 *
 * @param key    RVNK key
 * @param kind   what kind of finding
 * @param change the differing field for {@link Kind#FIELD}, else null
 * @param detail human-readable detail
 * @since 1.5.100-alpha
 */
public record NpcDrift(String key, Kind kind, NpcChange change, String detail) {

    public enum Kind {
        /** The spec declares the key and no NPC carries it. */
        MISSING(true),
        /** A field differs from the spec. */
        FIELD(true),
        /** More than one NPC carries the key. */
        DUPLICATE(true),
        /** The NPC has no stored location. */
        NO_LOCATION(true),
        /** The NPC's world (or the spec's world) is not loaded; nothing else was checked. */
        WORLD_UNLOADED(true),
        /** A tagged key the spec does not declare. */
        ORPHAN(true),
        /** The NPC is not spawned; usually only its chunk is unloaded. Informational. */
        DESPAWNED(false),
        /** The spec has a zone but WorldGuard is not available. Informational. */
        ZONE_UNCHECKED(false);

        private final boolean problem;

        Kind(boolean problem) {
            this.problem = problem;
        }

        /** @return true when this finding means the server does not match */
        public boolean isProblem() {
            return problem;
        }
    }

    @Override
    public String toString() {
        return key + " " + kind + ": " + detail;
    }
}
