package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.service.region.Cuboid;
import org.fourz.rvnkcore.service.region.IRegionService;
import org.fourz.rvnkcore.service.region.IRegionService.RegionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Carries out an {@link NpcPlanStep} against an {@link NpcHarness} and an {@link IRegionService}
 * (#2248). It holds no Citizens or WorldGuard type, so the tests run a full apply against in-memory
 * fakes and then check that a second plan is all NOOP.
 *
 * <p>Write order per key: create or rename, then one move for world/position/rotation, then skin,
 * lookclose, protected, pose, hold, nameplate, zone. Skins finish asynchronously; their final line
 * goes to the {@code later} sink.</p>
 *
 * <p><b>Standable Y (1.5.101).</b> Create and move snap the target Y to the standable Y at that X/Z
 * ({@link NpcGround#snap}, block reads from {@link NpcHarness#terrain()}), and the step result says
 * so: {@code position: snapped 68 -> 67}. The zone is built around the same standable position.
 * When no standable spot is in the window the requested Y is kept and a WARNING line is added; the
 * step still succeeds.</p>
 *
 * @since 1.5.100-alpha
 */
public final class NpcSpecExecutor {

    /** Builds a Location, or returns null when the world is not loaded. */
    @FunctionalInterface
    public interface Locator {
        Location at(String world, double x, double y, double z, float yaw, float pitch);
    }

    /**
     * Result of one step.
     *
     * @param lines one line per write, already formatted
     */
    public record StepResult(String key, NpcPlanStep.Action action, boolean ok, List<String> lines) {
    }

    private final NpcHarness harness;
    private final IRegionService regions;
    private final Locator locator;
    private final CommandSender sender;
    private final Consumer<String> later;

    /**
     * @param harness the NPC writer
     * @param regions the region tool (may be unavailable)
     * @param locator builds locations
     * @param sender  who runs the command; passed to WorldGuard for flag parsing; may be null
     * @param later   receives asynchronous results (skins)
     */
    public NpcSpecExecutor(NpcHarness harness, IRegionService regions, Locator locator, CommandSender sender,
                           Consumer<String> later) {
        this.harness = harness;
        this.regions = regions;
        this.locator = locator;
        this.sender = sender;
        this.later = later == null ? line -> { } : later;
    }

    /**
     * @param step    the planned step
     * @param spec    the spec entry for the step's key
     * @param current the live NPC before the step, or null for CREATE
     */
    public StepResult execute(NpcPlanStep step, NpcSpec spec, NpcState current) {
        List<String> lines = new ArrayList<>();
        switch (step.action()) {
            case NOOP, BLOCKED -> {
                return new StepResult(step.key(), step.action(), step.action() == NpcPlanStep.Action.NOOP, lines);
            }
            default -> {
                // CREATE and UPDATE below
            }
        }

        boolean ok = true;
        NpcGround.Snap snap = NpcGround.snap(harness.terrain(), spec.world(), spec.x(), spec.y(), spec.z());
        if (step.action() == NpcPlanStep.Action.CREATE) {
            Location at = locator.at(spec.world(), spec.x(), snap.y(), spec.z(),
                    spec.yaw() == null ? 0f : spec.yaw(), spec.pitch() == null ? 0f : spec.pitch());
            if (at == null) {
                lines.add("create: world '" + spec.world() + "' is not loaded");
                return new StepResult(step.key(), step.action(), false, lines);
            }
            NpcHarness.Result created = harness.create(spec.key(), spec.name(), at);
            lines.add("create: " + created.message());
            if (!created.ok()) {
                return new StepResult(step.key(), step.action(), false, lines);
            }
            addSnapNote(lines, snap);
        }

        boolean moved = false;
        for (NpcChange change : step.changes()) {
            NpcField field = change.field();
            if (step.action() == NpcPlanStep.Action.CREATE
                    && (field == NpcField.NAME || field == NpcField.POSITION || field == NpcField.WORLD)) {
                continue; // set by create
            }
            NpcHarness.Result result;
            switch (field) {
                case NAME -> result = harness.rename(spec.key(), spec.name());
                case WORLD, POSITION -> {
                    if (moved) {
                        continue;
                    }
                    moved = true;
                    float yaw = spec.yaw() != null ? spec.yaw() : current != null ? current.yaw() : 0f;
                    float pitch = spec.pitch() != null ? spec.pitch() : current != null ? current.pitch() : 0f;
                    Location to = locator.at(spec.world(), spec.x(), snap.y(), spec.z(), yaw, pitch);
                    result = to == null ? NpcHarness.Result.fail("world '" + spec.world() + "' is not loaded")
                            : harness.move(spec.key(), to);
                    if (result.ok() && snap.note() != null) {
                        result = NpcHarness.Result.ok(result.message() + " (" + snap.note() + ")");
                    }
                }
                case SKIN -> result = harness.skin(spec.key(), spec.skin(), later);
                case LOOKCLOSE -> result = harness.lookClose(spec.key(), spec.lookClose());
                case PROTECTED -> result = harness.setProtected(spec.key(), spec.protect());
                case POSE -> result = harness.pose(spec.key(), spec.pose());
                case HOLD -> result = harness.hold(spec.key(), spec.hold());
                case NAMEPLATE -> result = harness.nameplate(spec.key(), spec.nameplate());
                case ZONE -> {
                    RegionResult zone = defineZone(regions, spec.key(), spec.world(), spec.x(), snap.y(), spec.z(),
                            spec.zone(), sender);
                    result = new NpcHarness.Result(zone.ok(), zone.message());
                }
                default -> result = NpcHarness.Result.fail("unsupported field " + field);
            }
            lines.add((field == NpcField.WORLD ? "position" : field.id()) + ": " + result.message());
            ok &= result.ok();
        }
        return new StepResult(step.key(), step.action(), ok, lines);
    }

    private static void addSnapNote(List<String> lines, NpcGround.Snap snap) {
        if (snap.note() != null) {
            lines.add("position: " + snap.note());
        }
    }

    /**
     * Creates or updates the zone region {@code npc_<key>} around a position with the zone flags.
     * A new region gets {@link NpcZone#NEW_ZONE_PRIORITY}; an existing one keeps its priority.
     */
    public static RegionResult defineZone(IRegionService regions, String key, String world, double x, double y,
                                          double z, NpcZone zone, CommandSender sender) {
        if (regions == null || !regions.isAvailable()) {
            return RegionResult.fail(regions == null ? IRegionService.NOT_INSTALLED : regions.unavailableReason());
        }
        String id = NpcZone.regionId(key);
        Cuboid box = zone.around(x, y, z);
        boolean exists = regions.info(world, id).isPresent();
        return regions.define(world, id, box, NpcZone.FLAGS, exists ? null : NpcZone.NEW_ZONE_PRIORITY, sender);
    }
}
