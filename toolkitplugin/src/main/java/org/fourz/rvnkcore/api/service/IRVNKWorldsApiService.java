package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.worlds.CreateGroupRequest;
import org.fourz.rvnkcore.api.model.worlds.CreateSkyStackRequest;
import org.fourz.rvnkcore.api.model.worlds.CreateWorldV2Request;
import org.fourz.rvnkcore.api.model.worlds.GeneratorInfoDTO;
import org.fourz.rvnkcore.api.model.worlds.GroupWorldRequest;
import org.fourz.rvnkcore.api.model.worlds.JobDTO;
import org.fourz.rvnkcore.api.model.worlds.PresetDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewRequest;
import org.fourz.rvnkcore.api.model.worlds.SkyStackDTO;
import org.fourz.rvnkcore.api.model.worlds.SkyStackSettingsRequest;
import org.fourz.rvnkcore.api.model.worlds.SkyStackTemplateDTO;
import org.fourz.rvnkcore.api.model.worlds.WorldGroupDTO;
import org.fourz.rvnkcore.api.model.worlds.WorldGenSettingsDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API service interface for RVNKWorlds REST endpoints.
 * Implemented by the RVNKWorlds plugin and registered with ServiceRegistry.
 * The RVNKWorldsController in RVNKCore routes HTTP requests to this service.
 *
 * @since 1.4.0
 */
public interface IRVNKWorldsApiService {

    // World operations
    CompletableFuture<ApiResponse<?>> listWorlds();
    CompletableFuture<ApiResponse<?>> getWorld(String worldName);
    CompletableFuture<ApiResponse<?>> createWorld(String requestBody);
    CompletableFuture<ApiResponse<?>> loadWorld(String worldName);
    CompletableFuture<ApiResponse<?>> unloadWorld(String worldName);
    CompletableFuture<ApiResponse<?>> deleteWorld(String worldName, boolean deleteFiles);

    // Template operations
    CompletableFuture<ApiResponse<?>> listTemplates();
    CompletableFuture<ApiResponse<?>> createTemplate(String requestBody);

    // Group operations
    CompletableFuture<ApiResponse<?>> listGroups();
    CompletableFuture<ApiResponse<?>> getGroup(String groupName);

    // Snapshot operations
    CompletableFuture<ApiResponse<?>> restoreWorldSnapshot(String worldName, String requestBody);

    // Metrics & Health
    CompletableFuture<ApiResponse<?>> getMetrics();
    CompletableFuture<ApiResponse<?>> getHealthStatus();

    // ---------------------------------------------------------------------------------------------
    // Runtime holds (#1883)
    // ---------------------------------------------------------------------------------------------

    /**
     * Claims a world as in-use so RVNKWorlds' inactivity cleanup will not reclaim it.
     *
     * <p>Loading a world is not the same as keeping it. {@code WorldCleanupScheduler} unloads any
     * unprotected world that has sat empty past the inactivity threshold and writes it back to
     * {@code IMPORTED}. A world loaded to satisfy a quest requirement has, by definition, nobody
     * standing in it yet — so without a hold it is reclaimed minutes later and the quest silently
     * becomes unplayable again (#1883).</p>
     *
     * <p>A hold is <b>runtime state, not config</b>. It is deliberately distinct from
     * {@code cleanup.protectedWorlds}: that list is an operator's permanent policy, while a hold is
     * a plugin saying "I am using this right now". Holds do not survive a restart, which is correct —
     * whatever placed the hold will re-place it when it loads again.</p>
     *
     * <p>Holds are tracked <b>per holder</b>, so two plugins claiming the same world do not clobber
     * one another; the world stays held until every holder has released it. Re-holding an
     * already-held world is a no-op success.</p>
     *
     * @param worldName World to hold (case-insensitive)
     * @param holder    Stable identifier for the claimant, e.g. the plugin name. Used so releases
     *                  only drop that claimant's hold
     * @return future completing success once the hold is registered
     * @since 1.5.70
     */
    default CompletableFuture<ApiResponse<?>> holdWorld(String worldName, String holder) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support runtime world holds"));
    }

    /**
     * Releases a hold previously placed by {@link #holdWorld}.
     *
     * <p>Only drops the named holder's claim. The world becomes cleanup-eligible again once no
     * holders remain and it is otherwise unprotected. Releasing a hold that was never placed is a
     * no-op success — callers should not have to track whether they hold something.</p>
     *
     * @param worldName World to release (case-insensitive)
     * @param holder    The same identifier passed to {@link #holdWorld}
     * @return future completing success once the hold is gone
     * @since 1.5.70
     */
    default CompletableFuture<ApiResponse<?>> releaseWorld(String worldName, String holder) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support runtime world holds"));
    }

    /**
     * Site survey — one dense read describing a location well enough for an agent to act on (#1923).
     *
     * <p>Answers, in a single call: where this is, whether it is built or wild, what it is made of,
     * what it might be, and who else is nearby. The alternative is several calls plus console
     * scraping, and console output is truncated and lossy.</p>
     *
     * <p>Optional capabilities report {@code available: false} rather than being omitted. An agent
     * must be able to distinguish "no claim here" from "claims cannot be evaluated" — those lead to
     * different decisions, and a missing key looks like the former while meaning the latter.</p>
     *
     * @param query Raw query string: {@code player=<name>} or {@code world=<w>&x=&y=&z=},
     *              plus optional {@code radius} and {@code include}
     * @return future completing with the survey payload
     * @since 1.5.72
     */
    default CompletableFuture<ApiResponse<?>> surveySite(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the survey endpoint"));
    }

    // ── coordinate probes (#2051) — point/column/grid siblings of the survey ──────
    // Defaults, like surveySite: an older RVNKWorlds against a newer core loses the
    // endpoint, not the plugin. All GET, so they stay under rvnk_api_read.

    /** One block or a grouped list ({@code points=x,y,z;x,y,z;...}); {@code load=true} opt-in. */
    default CompletableFuture<ApiResponse<?>> probePoints(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the point probe (needs 1.6.143+)"));
    }

    /** Ground truth for one (x,z): surface heights, top block, RLE layer stack, liquid depth. */
    default CompletableFuture<ApiResponse<?>> columnProfile(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the column profile (needs 1.6.143+)"));
    }

    /** Surface heights + top materials on a step grid — terrain shape in one call. */
    default CompletableFuture<ApiResponse<?>> heightmapGrid(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the heightmap grid (needs 1.6.143+)"));
    }

    /** Strided lattice sweep across a box ({@code from}/{@code to}/{@code step}) with histogram. */
    default CompletableFuture<ApiResponse<?>> scatterProbe(String query) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED",
            "This RVNKWorlds build does not support the scatter probe (needs 1.6.143+)"));
    }

    // ---------------------------------------------------------------------------------------------
    // World Forge (#2196 / #2200) — generators, presets, create v2, jobs, preview, gen-settings
    // ---------------------------------------------------------------------------------------------
    //
    // Every method is a default returning NOT_SUPPORTED, which RVNKWorldsController maps to 501.
    // An RVNKWorlds built against an older core (<= 1.6.207) therefore keeps loading and serving its
    // existing routes; it only lacks these. Implemented by RVNKWorlds 1.6.208+.
    //
    // Error codes the controller maps: NOT_FOUND -> 404, CONFLICT -> 409, VALIDATION_FAILED -> 400
    // (build it with ApiResponse.validationError(List<FieldError>)), NOT_SUPPORTED -> 501.
    // Futures are awaited for up to 30 s; return quickly and push slow work into a job.

    /** Message used by every World Forge default. */
    String WORLD_FORGE_NOT_SUPPORTED = "This RVNKWorlds build does not support World Forge (needs 1.6.208+)";

    /**
     * {@code GET /rvnkworlds/generators} — every generator a v2 create accepts, with its schema.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<List<GeneratorInfoDTO>>> listGenerators() {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/generators/{id}} — one generator; {@code NOT_FOUND} if unknown.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<GeneratorInfoDTO>> getGenerator(String generatorId) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/presets[?generator=]} — built-in and custom presets.
     *
     * @param generatorFilter generator id to filter by, or {@code null} for all
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<List<PresetDTO>>> listPresets(String generatorFilter) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/presets/{name}} — one preset; {@code NOT_FOUND} if unknown.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<PresetDTO>> getPreset(String name) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code POST /rvnkworlds/presets} — create or replace a custom preset; returns the stored preset.
     *
     * <p>Core has already checked {@code name} and {@code generator} are non-blank and
     * {@code settings} is an object, and has forced {@code builtIn=false}, {@code createdAt=null}.
     * The implementation validates the settings against the generator schema
     * ({@code VALIDATION_FAILED} with field paths like {@code settings.seaLevel}) and returns
     * {@code CONFLICT} when {@code name} collides with a built-in.</p>
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<PresetDTO>> savePreset(PresetDTO preset) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code DELETE /rvnkworlds/presets/{name}} — removes a custom preset and returns it.
     * {@code NOT_FOUND} if unknown; {@code CONFLICT} for a built-in.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<PresetDTO>> deletePreset(String name) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * v2 {@code POST /rvnkworlds/worlds} — validate, then queue a world creation and return its job.
     * The controller answers HTTP 202 with a {@code Location: .../jobs/{id}} header on success.
     *
     * <p>The implementation must not block the calling thread on world generation: validate,
     * enqueue, and return the {@link JobDTO} in {@code QUEUED} state. {@code CONFLICT} if the world
     * exists; {@code VALIDATION_FAILED} for a bad name, unknown generator/preset or bad settings.</p>
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<JobDTO>> createWorldV2(CreateWorldV2Request request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/jobs/{id}} — job status; {@code NOT_FOUND} if unknown or expired.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<JobDTO>> getJob(String jobId) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code POST /rvnkworlds/preview} — sample terrain heights/biomes without generating chunks.
     *
     * <p><b>Threading:</b> the controller calls this on its own bounded preview worker pool, never
     * on the Bukkit main thread and never on a Jetty request thread. The implementation must do
     * the sampling on the calling thread (or its own executor) and must <b>not</b> hop to the main
     * thread — a 256x256 sample run there would stall the server tick. Geometry, caps and defaults
     * are already applied; see {@link PreviewRequest}.</p>
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<PreviewDTO>> previewTerrain(PreviewRequest request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/worlds/{name}/gen-settings} — the world's frozen generation snapshot.
     * {@code NOT_FOUND} if the world is unknown or has no snapshot.
     * @since 1.5.96
     */
    default CompletableFuture<ApiResponse<WorldGenSettingsDTO>> getWorldGenSettings(String worldName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", WORLD_FORGE_NOT_SUPPORTED));
    }

    // ---------------------------------------------------------------------------------------------
    // World Forge control plane (#2218) - world group writes and sky stacks
    // ---------------------------------------------------------------------------------------------
    //
    // Same rules as the World Forge block above: every method is a default returning NOT_SUPPORTED,
    // which the controller maps to 501, so RVNKWorlds 1.6.232 and older keep loading against this core
    // and only lack these routes. Implemented by RVNKWorlds 1.6.233+.
    //
    // The implementation runs the same service methods as the /world group and /world skystack console
    // commands, on the server thread. RVNKCore completes the HTTP response from the returned future
    // (an async servlet response), so a slow server thread never holds a Jetty thread.
    //
    // Error codes: NOT_FOUND -> 404, CONFLICT -> 409, VALIDATION_FAILED -> 400, NOT_SUPPORTED -> 501.

    /** Message used by every control-plane default. */
    String CONTROL_PLANE_NOT_SUPPORTED =
        "This RVNKWorlds build does not support the World Forge control plane (needs 1.6.233+)";

    /**
     * {@code POST /rvnkworlds/groups} - {@code /world group create <name> [--link] [--portal]}.
     * {@code VALIDATION_FAILED} for a bad name, {@code CONFLICT} when the name is taken (any case).
     * The controller answers 201 on success.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> createGroup(CreateGroupRequest request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code POST /rvnkworlds/groups/{name}/worlds} - {@code /world group add-world <group> <world> [--force]}.
     * {@code NOT_FOUND} for an unknown group; {@code CONFLICT} when the world is already in this group, or
     * in another group without {@code force}.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> addWorldToGroup(String groupName, GroupWorldRequest request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code DELETE /rvnkworlds/groups/{name}/worlds/{world}} - {@code /world group remove-world <group> <world>}.
     * Only drops the membership; the world itself is untouched. {@code NOT_FOUND} for an unknown group or a
     * world that is not in it.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> removeWorldFromGroup(String groupName, String worldName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code PUT /rvnkworlds/groups/{name}/default} - {@code /world group default <group>}.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> setDefaultGroup(String groupName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code PUT /rvnkworlds/groups/{name}/permission} - {@code /world group permission <group> set|clear}.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> setGroupPermission(String groupName, boolean requiresPermission) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code DELETE /rvnkworlds/groups/{name}} - {@code /world group delete <group>}, plus one REST-only
     * guard: {@code CONFLICT} while a loaded world of the group has players in it. {@code CONFLICT} for the
     * default group. Removes the group from {@code worlds.yml} only; no world is unloaded or deleted.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<WorldGroupDTO>> deleteGroup(String groupName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/skystacks} - {@code /world skystack list}: every group with a sky or deep stack.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<List<SkyStackDTO>>> listSkyStacks() {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/skystacks/{group}} - {@code /world skystack info <group>}. {@code NOT_FOUND} for
     * an unknown group or a group with no {@code skyStack} block.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<SkyStackDTO>> getSkyStack(String groupName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code GET /rvnkworlds/skystack-templates} - the templates a create accepts.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<List<SkyStackTemplateDTO>>> listSkyStackTemplates() {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code POST /rvnkworlds/skystacks} - validate, then queue the layers and return the QUEUED job
     * (type {@code CREATE_SKYSTACK}, polled at {@code GET /rvnkworlds/jobs/{id}}). The controller answers
     * 202 with a {@code Location} header. Must not block: validate, enqueue, return.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<JobDTO>> createSkyStack(CreateSkyStackRequest request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code PUT /rvnkworlds/skystacks/{group}} - change keys of the group's {@code skyStack} block and
     * save {@code worlds.yml}. No console command does this today; {@code /world skystack info} shows it.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<SkyStackDTO>> updateSkyStack(String groupName, SkyStackSettingsRequest request) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }

    /**
     * {@code DELETE /rvnkworlds/skystacks/{group}} - {@code /world skystack delete <group>}: strips the
     * {@code skyStack} block only. The group and every world stay; no world folder is ever deleted.
     * Returns the stack as it was before the strip.
     * @since 1.5.103
     */
    default CompletableFuture<ApiResponse<SkyStackDTO>> deleteSkyStack(String groupName) {
        return CompletableFuture.completedFuture(ApiResponse.error("NOT_SUPPORTED", CONTROL_PLANE_NOT_SUPPORTED));
    }
}
