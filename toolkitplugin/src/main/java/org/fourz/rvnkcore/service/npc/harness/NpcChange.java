package org.fourz.rvnkcore.service.npc.harness;

/**
 * One field that differs between a spec and the live NPC (#2248).
 *
 * @param field   the field
 * @param current the live value, as text ("missing" when absent)
 * @param wanted  the spec value, as text
 * @since 1.5.100-alpha
 */
public record NpcChange(NpcField field, String current, String wanted) {

    @Override
    public String toString() {
        return field.id() + ": " + current + " -> " + wanted;
    }
}
