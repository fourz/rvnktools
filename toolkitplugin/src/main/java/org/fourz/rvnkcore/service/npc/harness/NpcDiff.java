package org.fourz.rvnkcore.service.npc.harness;

import org.fourz.rvnkcore.service.region.Cuboid;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.service.region.IRegionService.RegionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The pure field diff between a spec entry and a live NPC (#2248). Shared by the apply planner and
 * by verify, so "apply would change it" and "verify reports it" can never disagree.
 *
 * <p><b>Position.</b> Drift when the 3D distance is over {@value #POSITION_TOLERANCE} block. Yaw
 * and pitch are compared (tolerance {@value #ANGLE_TOLERANCE} degree) only when the spec sets them
 * AND LookClose is off: with LookClose on, Citizens turns the NPC towards nearby players all the
 * time, so its live yaw says nothing about the spec.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcDiff {

    public static final double POSITION_TOLERANCE = 0.5;
    public static final double ANGLE_TOLERANCE = 1.0;

    private NpcDiff() {
    }

    /**
     * @param spec    the wanted state
     * @param state   the live NPC
     * @param regions region service; zone fields are skipped when it is unavailable
     * @return every differing field, in {@link NpcField} order; empty when in sync
     */
    public static List<NpcChange> diff(NpcSpec spec, NpcState state, IRegionService regions) {
        List<NpcChange> changes = new ArrayList<>();

        String wantedName = NpcSpecParser.stripColors(spec.name());
        if (!wantedName.equals(state.name())) {
            changes.add(new NpcChange(NpcField.NAME, quote(state.name()), quote(wantedName)));
        }

        if (state.world() == null || !state.hasLocation() || !state.world().equals(spec.world())) {
            changes.add(new NpcChange(NpcField.WORLD, state.world() == null ? "none" : state.world(), spec.world()));
        } else {
            String position = positionDrift(spec, state);
            if (position != null) {
                changes.add(new NpcChange(NpcField.POSITION, position, formatPos(spec.x(), spec.y(), spec.z(), spec.yaw(), spec.pitch())));
            }
        }

        if (spec.skin() != null && !NpcSkins.same(spec.skin(), state.skin())) {
            changes.add(new NpcChange(NpcField.SKIN, state.skin() == null ? "default" : state.skin(), spec.skin()));
        }
        if (spec.lookClose() != null && spec.lookClose() != state.lookClose()) {
            changes.add(new NpcChange(NpcField.LOOKCLOSE, onOff(state.lookClose()), onOff(spec.lookClose())));
        }
        if (spec.protect() != null && spec.protect() != state.protect()) {
            changes.add(new NpcChange(NpcField.PROTECTED, String.valueOf(state.protect()), String.valueOf(spec.protect())));
        }
        if (spec.pose() != null && spec.pose() != state.pose()) {
            changes.add(new NpcChange(NpcField.POSE, state.pose() == null ? "stand" : state.pose().id(), spec.pose().id()));
        }
        if (spec.hold() != null) {
            String current = state.hold() == null ? "none" : state.hold().toLowerCase(Locale.ROOT);
            if (!current.equals(spec.hold())) {
                changes.add(new NpcChange(NpcField.HOLD, current, spec.hold()));
            }
        }
        if (spec.nameplate() != null && spec.nameplate() != state.nameplate()) {
            changes.add(new NpcChange(NpcField.NAMEPLATE,
                    state.nameplate() == null ? "on" : state.nameplate().id(), spec.nameplate().id()));
        }

        NpcChange zone = zoneDrift(spec, regions);
        if (zone != null) {
            changes.add(zone);
        }
        return changes;
    }

    /**
     * Every field a new NPC needs written after it is created: the spec's optional fields and the
     * zone. Name and position are set by the create itself.
     */
    public static List<NpcChange> createChanges(NpcSpec spec, IRegionService regions) {
        List<NpcChange> changes = new ArrayList<>();
        changes.add(new NpcChange(NpcField.NAME, "missing", quote(NpcSpecParser.stripColors(spec.name()))));
        changes.add(new NpcChange(NpcField.POSITION, "missing",
                spec.world() + " " + formatPos(spec.x(), spec.y(), spec.z(), spec.yaw(), spec.pitch())));
        if (spec.skin() != null) {
            changes.add(new NpcChange(NpcField.SKIN, "default", spec.skin()));
        }
        if (spec.lookClose() != null) {
            changes.add(new NpcChange(NpcField.LOOKCLOSE, "off", onOff(spec.lookClose())));
        }
        if (spec.protect() != null) {
            changes.add(new NpcChange(NpcField.PROTECTED, "true", String.valueOf(spec.protect())));
        }
        if (spec.pose() != null && spec.pose() != NpcPose.STAND) {
            changes.add(new NpcChange(NpcField.POSE, "stand", spec.pose().id()));
        }
        if (spec.hold() != null && !"none".equals(spec.hold())) {
            changes.add(new NpcChange(NpcField.HOLD, "none", spec.hold()));
        }
        if (spec.nameplate() != null && spec.nameplate() != NpcNameplate.ON) {
            changes.add(new NpcChange(NpcField.NAMEPLATE, "on", spec.nameplate().id()));
        }
        NpcChange zone = zoneDrift(spec, regions);
        if (zone != null) {
            changes.add(zone);
        }
        return changes;
    }

    /** @return the zone change, or null when the zone matches, is not managed, or cannot be checked */
    static NpcChange zoneDrift(NpcSpec spec, IRegionService regions) {
        if (spec.zone() == null || regions == null || !regions.isAvailable()) {
            return null;
        }
        Cuboid wanted = spec.zone().around(spec.x(), spec.y(), spec.z());
        Optional<RegionInfo> info = regions.info(spec.world(), NpcZone.regionId(spec.key()));
        if (info.isEmpty()) {
            return new NpcChange(NpcField.ZONE, "missing", NpcZone.regionId(spec.key()) + " " + wanted);
        }
        if (!wanted.equals(info.get().bounds())) {
            return new NpcChange(NpcField.ZONE, String.valueOf(info.get().bounds()), wanted.toString());
        }
        List<String> wrongFlags = new ArrayList<>();
        for (Map.Entry<String, String> flag : NpcZone.FLAGS.entrySet()) {
            String actual = info.get().flags().get(flag.getKey());
            if (!flag.getValue().equalsIgnoreCase(actual)) {
                wrongFlags.add(flag.getKey() + "=" + (actual == null ? "unset" : actual));
            }
        }
        if (!wrongFlags.isEmpty()) {
            return new NpcChange(NpcField.ZONE, "flags " + String.join(", ", wrongFlags), "flags " + NpcZone.FLAGS);
        }
        return null;
    }

    /** @return a description of the drift, or null when the position matches */
    static String positionDrift(NpcSpec spec, NpcState state) {
        double dx = state.x() - spec.x();
        double dy = state.y() - spec.y();
        double dz = state.z() - spec.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean moved = distance > POSITION_TOLERANCE;

        boolean lookClose = spec.lookClose() != null ? spec.lookClose() : state.lookClose();
        boolean turned = false;
        if (!lookClose) {
            if (spec.yaw() != null && angleBetween(spec.yaw(), state.yaw()) > ANGLE_TOLERANCE) {
                turned = true;
            }
            if (spec.pitch() != null && Math.abs(spec.pitch() - state.pitch()) > ANGLE_TOLERANCE) {
                turned = true;
            }
        }
        if (!moved && !turned) {
            return null;
        }
        String text = formatPos(state.x(), state.y(), state.z(), state.yaw(), state.pitch());
        return moved ? text + String.format(Locale.ROOT, " (%.2f off)", distance) : text + " (rotation)";
    }

    /** @return the smallest angle between two yaws, 0-180 */
    static double angleBetween(float a, float b) {
        double d = Math.abs(NpcSpecParser.normalizeYaw(a) - NpcSpecParser.normalizeYaw(b)) % 360.0;
        return d > 180 ? 360 - d : d;
    }

    static String formatPos(double x, double y, double z, Float yaw, Float pitch) {
        String base = String.format(Locale.ROOT, "%s,%s,%s", num(x), num(y), num(z));
        if (yaw == null && pitch == null) {
            return base;
        }
        return base + String.format(Locale.ROOT, " yaw %s pitch %s",
                yaw == null ? "-" : num(yaw), pitch == null ? "-" : num(pitch));
    }

    /** @return a number with at most two decimals and no trailing zeros */
    static String num(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return "-0".equals(text) ? "0" : text;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static String quote(String text) {
        return "'" + text + "'";
    }
}
