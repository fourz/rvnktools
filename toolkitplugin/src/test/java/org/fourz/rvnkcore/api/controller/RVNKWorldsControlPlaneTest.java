package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.response.FieldError;
import org.fourz.rvnkcore.api.model.worlds.CreateGroupRequest;
import org.fourz.rvnkcore.api.model.worlds.CreateSkyStackRequest;
import org.fourz.rvnkcore.api.model.worlds.GroupWorldRequest;
import org.fourz.rvnkcore.api.model.worlds.JobDTO;
import org.fourz.rvnkcore.api.model.worlds.SkyStackDTO;
import org.fourz.rvnkcore.api.model.worlds.SkyStackLayerDTO;
import org.fourz.rvnkcore.api.model.worlds.SkyStackSettingsRequest;
import org.fourz.rvnkcore.api.model.worlds.SkyStackTemplateDTO;
import org.fourz.rvnkcore.api.model.worlds.WorldGroupDTO;
import org.fourz.rvnkcore.api.ratelimit.RateLimiter;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The World Forge control plane routes (#2218): world group writes and sky stacks. Covers routing, the
 * shape validation RVNKCore does before calling RVNKWorlds, DTO serialization, the version-skew defaults
 * (an RVNKWorlds without these methods answers 501), and the async response (no Jetty thread waits).
 */
@DisplayName("RVNKWorldsController - control plane routes (#2218)")
class RVNKWorldsControlPlaneTest {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
            @Override public void write(JsonWriter out, Instant v) throws IOException {
                out.value(v != null ? v.toString() : null);
            }
            @Override public Instant read(JsonReader in) throws IOException {
                return Instant.parse(in.nextString());
            }
        })
        .create();

    private final List<RVNKWorldsController> controllers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        controllers.forEach(RVNKWorldsController::destroy);
    }

    // ── harness ──────────────────────────────────────────────────────────────────────────────

    /** Simulates RVNKWorlds 1.6.233+: records each call and answers like the real implementation. */
    static class ControlService extends RVNKWorldsControllerTest.LegacyService {
        final List<String> calls = new ArrayList<>();
        CreateGroupRequest createGroup;
        GroupWorldRequest groupWorld;
        Boolean permission;
        CreateSkyStackRequest createStack;
        SkyStackSettingsRequest settings;

        static <T> CompletableFuture<ApiResponse<T>> done(ApiResponse<T> r) {
            return CompletableFuture.completedFuture(r);
        }

        static WorldGroupDTO group(String name) {
            return new WorldGroupDTO(name, List.of("world"), false, true, false, false, false);
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> createGroup(CreateGroupRequest r) {
            calls.add("createGroup");
            createGroup = r;
            if ("taken".equals(r.getName())) return done(ApiResponse.error("CONFLICT", "A group named 'taken' already exists"));
            return done(ApiResponse.success(group(r.getName())));
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> addWorldToGroup(String g, GroupWorldRequest r) {
            calls.add("addWorldToGroup:" + g);
            groupWorld = r;
            if ("nope".equals(g)) return done(ApiResponse.error("NOT_FOUND", "No group named 'nope' exists"));
            return done(ApiResponse.success(group(g)));
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> removeWorldFromGroup(String g, String w) {
            calls.add("removeWorldFromGroup:" + g + ":" + w);
            return done(ApiResponse.success(group(g)));
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> setDefaultGroup(String g) {
            calls.add("setDefaultGroup:" + g);
            return done(ApiResponse.success(group(g)));
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> setGroupPermission(String g, boolean p) {
            calls.add("setGroupPermission:" + g);
            permission = p;
            return done(ApiResponse.success(group(g)));
        }

        @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> deleteGroup(String g) {
            calls.add("deleteGroup:" + g);
            if ("busy".equals(g)) {
                return done(ApiResponse.error("CONFLICT", "Group 'busy' has players in loaded worlds: busy_world (2)"));
            }
            return done(ApiResponse.success(group(g)));
        }

        @Override public CompletableFuture<ApiResponse<List<SkyStackDTO>>> listSkyStacks() {
            calls.add("listSkyStacks");
            return done(ApiResponse.success(List.of(stack("koz"))));
        }

        @Override public CompletableFuture<ApiResponse<SkyStackDTO>> getSkyStack(String g) {
            calls.add("getSkyStack:" + g);
            if ("plain".equals(g)) return done(ApiResponse.error("NOT_FOUND", "Group 'plain' has no sky-stack config"));
            return done(ApiResponse.success(stack(g)));
        }

        @Override public CompletableFuture<ApiResponse<List<SkyStackTemplateDTO>>> listSkyStackTemplates() {
            calls.add("listSkyStackTemplates");
            return done(ApiResponse.success(List.of(new SkyStackTemplateDTO("classic-3-tier", "skyblock", 3,
                "{group}_sky_{n}", "SAFE_PLATFORM", 10, 3))));
        }

        @Override public CompletableFuture<ApiResponse<JobDTO>> createSkyStack(CreateSkyStackRequest r) {
            calls.add("createSkyStack");
            createStack = r;
            return done(ApiResponse.success(new JobDTO("stack-1", "CREATE_SKYSTACK", JobDTO.State.QUEUED, 0.0,
                "Queued", r.getGroup(), Instant.parse("2026-10-10T00:00:00Z"), null, null)));
        }

        @Override public CompletableFuture<ApiResponse<SkyStackDTO>> updateSkyStack(String g, SkyStackSettingsRequest r) {
            calls.add("updateSkyStack:" + g);
            settings = r;
            return done(ApiResponse.success(stack(g)));
        }

        @Override public CompletableFuture<ApiResponse<SkyStackDTO>> deleteSkyStack(String g) {
            calls.add("deleteSkyStack:" + g);
            return done(ApiResponse.success(stack(g)));
        }

        static SkyStackDTO stack(String g) {
            SkyStackDTO s = new SkyStackDTO();
            s.setGroup(g);
            s.setEnabled(true);
            s.setConfigured(true);
            s.setGround(g);
            s.setStack(List.of(g + "_sky_0", g, g + "_deep_0"));
            s.setLayers(List.of(
                new SkyStackLayerDTO(g + "_sky_0", SkyStackLayerDTO.Role.SKY, 0, true, "no seal bands"),
                new SkyStackLayerDTO(g, SkyStackLayerDTO.Role.GROUND, -1, true, "floor band Y -64..-61"),
                new SkyStackLayerDTO(g + "_deep_0", SkyStackLayerDTO.Role.DEEP, 0, false, null)));
            s.setLandingMode("SAFE_PLATFORM");
            s.setAscendCooldownSeconds(10);
            s.setDescentTriggerYOffset(3);
            s.setDeepAscendOffset(4);
            return s;
        }
    }

    record Result(int status, JsonObject body, String raw, Map<String, String> headers, boolean asyncCompleted) {
        String errorCode() {
            return body.getAsJsonObject("error").get("code").getAsString();
        }
    }

    private RVNKWorldsController controller(IRVNKWorldsApiService service) {
        RVNKWorldsController c = new RVNKWorldsController(() -> service, GSON, mock(LogManager.class),
            new RateLimiter(RVNKWorldsController.PREVIEW_BURST, RVNKWorldsController.PREVIEW_PER_SECOND));
        controllers.add(c);
        return c;
    }

    /** A request that supports async, as Jetty's embedded holders do. */
    private static Result call(String method, RVNKWorldsController c, String path, String body) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getMethod()).thenReturn(method);
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body != null ? body : "")));
        when(req.getContextPath()).thenReturn("/api");
        when(req.getServletPath()).thenReturn("/rvnkworlds");
        when(req.getHeader("X-API-Key")).thenReturn("k");
        when(req.isAsyncSupported()).thenReturn(true);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        AsyncContext ctx = mock(AsyncContext.class);
        when(req.startAsync()).thenReturn(ctx);
        when(ctx.getResponse()).thenReturn(resp);
        boolean[] completed = {false};
        doAnswer(inv -> { completed[0] = true; return null; }).when(ctx).complete();

        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        int[] status = {200};
        doAnswer(inv -> { status[0] = inv.getArgument(0); return null; }).when(resp).setStatus(anyInt());
        Map<String, String> headers = new HashMap<>();
        doAnswer(inv -> { headers.put(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(resp).setHeader(anyString(), anyString());
        switch (method) {
            case "GET" -> c.doGet(req, resp);
            case "POST" -> c.doPost(req, resp);
            case "DELETE" -> c.doDelete(req, resp);
            case "PUT" -> c.doPut(req, resp);
            default -> throw new IllegalArgumentException(method);
        }
        String raw = out.toString();
        JsonObject json = raw.isEmpty() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
        return new Result(status[0], json, raw, headers, completed[0]);
    }

    private static List<String> fieldNames(Result r) {
        List<String> names = new ArrayList<>();
        r.body().getAsJsonObject("error").getAsJsonArray("fieldErrors")
            .forEach(e -> names.add(e.getAsJsonObject().get("field").getAsString()));
        return names;
    }

    // ── groups ───────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("group writes")
    class Groups {

        @Test
        @DisplayName("POST /groups creates with the console flags and answers 201 asynchronously")
        void create() throws Exception {
            ControlService svc = new ControlService();
            Result r = call("POST", controller(svc), "/groups",
                "{\"name\":\"isles\",\"inventoryLink\":true,\"portalEmulation\":true}");
            assertEquals(201, r.status());
            assertTrue(r.asyncCompleted(), "the async response must be completed");
            assertEquals("isles", svc.createGroup.getName());
            assertTrue(svc.createGroup.isInventoryLink());
            assertTrue(svc.createGroup.isPortalEmulation());
            JsonObject data = r.body().getAsJsonObject("data");
            assertEquals("isles", data.get("name").getAsString());
            assertTrue(data.get("inventoryLink").getAsBoolean());
            assertFalse(data.get("isDefault").getAsBoolean());
        }

        @Test
        @DisplayName("POST /groups: flags default to false; missing name is 400 before RVNKWorlds is called")
        void createValidation() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            call("POST", c, "/groups", "{\"name\":\"plain\"}");
            assertFalse(svc.createGroup.isInventoryLink());
            assertFalse(svc.createGroup.isPortalEmulation());

            ControlService svc2 = new ControlService();
            Result bad = call("POST", controller(svc2), "/groups", "{\"inventoryLink\":\"yes\"}");
            assertEquals(400, bad.status());
            assertEquals(List.of("name", "inventoryLink"), fieldNames(bad));
            assertTrue(svc2.calls.isEmpty());
        }

        @Test
        @DisplayName("POST /groups: a non-JSON body is 400 INVALID_REQUEST; a taken name is 409")
        void createErrors() throws Exception {
            assertEquals("INVALID_REQUEST", call("POST", controller(new ControlService()), "/groups", "x").errorCode());
            Result taken = call("POST", controller(new ControlService()), "/groups", "{\"name\":\"taken\"}");
            assertEquals(409, taken.status());
            assertEquals("CONFLICT", taken.errorCode());
        }

        @Test
        @DisplayName("POST /groups/{g}/worlds adds with force; unknown group is 404")
        void addWorld() throws Exception {
            ControlService svc = new ControlService();
            Result r = call("POST", controller(svc), "/groups/isles/worlds", "{\"world\":\"isles_sky_0\",\"force\":true}");
            assertEquals(200, r.status());
            assertEquals(List.of("addWorldToGroup:isles"), svc.calls);
            assertEquals("isles_sky_0", svc.groupWorld.getWorld());
            assertTrue(svc.groupWorld.isForce());
            assertEquals(404, call("POST", controller(new ControlService()), "/groups/nope/worlds",
                "{\"world\":\"w\"}").status());
            Result missing = call("POST", controller(new ControlService()), "/groups/isles/worlds", "{}");
            assertEquals(List.of("world"), fieldNames(missing));
        }

        @Test
        @DisplayName("DELETE /groups/{g}/worlds/{w} routes to remove-world, not delete-group")
        void removeWorld() throws Exception {
            ControlService svc = new ControlService();
            assertEquals(200, call("DELETE", controller(svc), "/groups/isles/worlds/isles_sky_0", null).status());
            assertEquals(List.of("removeWorldFromGroup:isles:isles_sky_0"), svc.calls);
        }

        @Test
        @DisplayName("PUT /groups/{g}/default and /permission")
        void defaultAndPermission() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            assertEquals(200, call("PUT", c, "/groups/isles/default", null).status());
            assertEquals(200, call("PUT", c, "/groups/isles/permission", "{\"requiresPermission\":true}").status());
            assertEquals(List.of("setDefaultGroup:isles", "setGroupPermission:isles"), svc.calls);
            assertTrue(svc.permission);
            Result missing = call("PUT", controller(new ControlService()), "/groups/isles/permission", "{}");
            assertEquals(400, missing.status());
            assertEquals(List.of("requiresPermission"), fieldNames(missing));
        }

        @Test
        @DisplayName("DELETE /groups/{g}: occupied group is 409 with the reason")
        void delete() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            assertEquals(200, call("DELETE", c, "/groups/isles", null).status());
            Result busy = call("DELETE", c, "/groups/busy", null);
            assertEquals(409, busy.status());
            assertTrue(busy.body().getAsJsonObject("error").get("message").getAsString().contains("players"));
        }

        @Test
        @DisplayName("existing GET /groups and /groups/{name} still route to the read methods")
        void readsUnchanged() throws Exception {
            RVNKWorldsController c = controller(new ControlService());
            assertEquals(200, call("GET", c, "/groups", null).status());
            assertEquals("isles", call("GET", c, "/groups/isles", null).body().get("data").getAsString());
        }
    }

    // ── sky stacks ───────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sky stacks")
    class SkyStacks {

        @Test
        @DisplayName("GET /skystacks, /skystacks/{g}, /skystack-templates")
        void reads() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            Result list = call("GET", c, "/skystacks", null);
            assertEquals(200, list.status());
            JsonObject first = list.body().getAsJsonArray("data").get(0).getAsJsonObject();
            assertEquals("koz", first.get("group").getAsString());
            assertEquals("DEEP", first.getAsJsonArray("layers").get(2).getAsJsonObject().get("role").getAsString());
            assertFalse(first.has("deepTriggerY"), "a null deepTriggerY is omitted");

            assertEquals(200, call("GET", c, "/skystacks/koz", null).status());
            assertEquals(404, call("GET", c, "/skystacks/plain", null).status());
            Result templates = call("GET", c, "/skystack-templates", null);
            assertEquals("skyblock", templates.body().getAsJsonArray("data").get(0).getAsJsonObject()
                .get("layerGenerator").getAsString());
            assertEquals(List.of("listSkyStacks", "getSkyStack:koz", "getSkyStack:plain", "listSkyStackTemplates"),
                svc.calls);
        }

        @Test
        @DisplayName("POST /skystacks: 202 + Location, direction parsed, settings kept as numbers")
        void create() throws Exception {
            ControlService svc = new ControlService();
            Result r = call("POST", controller(svc), "/skystacks", """
                {"group":"koz","direction":"Both","count":2,"template":"skyland-1",
                 "generator":"cavern","preset":"grand_halls","settings":{"floorSeal":4}}""");
            assertEquals(202, r.status());
            assertEquals("/api/rvnkworlds/jobs/stack-1", r.headers().get("Location"));
            assertEquals("CREATE_SKYSTACK", r.body().getAsJsonObject("data").get("type").getAsString());
            CreateSkyStackRequest req = svc.createStack;
            assertEquals(CreateSkyStackRequest.Direction.BOTH, req.getDirection());
            assertTrue(req.buildsUp());
            assertTrue(req.buildsDown());
            assertEquals(2, req.getCount());
            assertEquals("skyland-1", req.getTemplate());
            assertEquals(4L, req.getSettings().get("floorSeal"));
        }

        @Test
        @DisplayName("POST /skystacks: direction defaults to up; bottomWorld alone is enough")
        void createDefaults() throws Exception {
            ControlService svc = new ControlService();
            call("POST", controller(svc), "/skystacks", "{\"bottomWorld\":\"world\",\"count\":1}");
            assertEquals(CreateSkyStackRequest.Direction.UP, svc.createStack.getDirection());
            assertNull(svc.createStack.getGroup());
            assertEquals("world", svc.createStack.getBottomWorld());
        }

        @Test
        @DisplayName("POST /skystacks: shape errors are 400 field errors and RVNKWorlds is not called")
        void createValidation() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            Result none = call("POST", c, "/skystacks", "{}");
            assertEquals(400, none.status());
            assertEquals(List.of("group", "count"), fieldNames(none));
            Result bad = call("POST", c, "/skystacks",
                "{\"group\":\"koz\",\"direction\":\"sideways\",\"count\":0,\"settings\":[]}");
            assertEquals(List.of("direction", "count", "settings"), fieldNames(bad));
            Result notInt = call("POST", c, "/skystacks", "{\"group\":\"koz\",\"count\":1.5}");
            assertEquals(List.of("count"), fieldNames(notInt));
            assertTrue(svc.calls.isEmpty());
        }

        @Test
        @DisplayName("PUT /skystacks/{g}: partial update, explicit null resets deepTriggerY")
        void update() throws Exception {
            ControlService svc = new ControlService();
            Result r = call("PUT", controller(svc), "/skystacks/koz",
                "{\"landingMode\":\"SPAWN_TELEPORT\",\"ascendCooldownSeconds\":5,\"deepTriggerY\":null}");
            assertEquals(200, r.status());
            SkyStackSettingsRequest s = svc.settings;
            assertEquals("SPAWN_TELEPORT", s.getLandingMode());
            assertEquals(5, s.getAscendCooldownSeconds());
            assertNull(s.getDeepTriggerY());
            assertTrue(s.isResetDeepTriggerY());
            assertNull(s.getEnabled());
            assertNull(s.getDescentTriggerYOffset());
        }

        @Test
        @DisplayName("PUT /skystacks/{g}: roof/floor/seal keys are refused as frozen; unknown keys and empty bodies too")
        void updateValidation() throws Exception {
            ControlService svc = new ControlService();
            RVNKWorldsController c = controller(svc);
            Result frozen = call("PUT", c, "/skystacks/koz", "{\"roof\":\"none\",\"floorSeal\":4,\"enabled\":true}");
            assertEquals(400, frozen.status());
            assertEquals(List.of("roof", "floorSeal"), fieldNames(frozen));
            assertTrue(frozen.raw().contains("frozen generation setting"));
            Result unknown = call("PUT", c, "/skystacks/koz", "{\"layers\":[\"a\"]}");
            assertEquals(List.of("layers"), fieldNames(unknown));
            Result empty = call("PUT", c, "/skystacks/koz", "{}");
            assertEquals(List.of("settings"), fieldNames(empty));
            Result badType = call("PUT", c, "/skystacks/koz", "{\"ascendCooldownSeconds\":\"10\"}");
            assertEquals(List.of("ascendCooldownSeconds"), fieldNames(badType));
            assertTrue(svc.calls.isEmpty());
        }

        @Test
        @DisplayName("DELETE /skystacks/{g} routes to deleteSkyStack")
        void delete() throws Exception {
            ControlService svc = new ControlService();
            assertEquals(200, call("DELETE", controller(svc), "/skystacks/koz", null).status());
            assertEquals(List.of("deleteSkyStack:koz"), svc.calls);
        }
    }

    // ── version skew ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("version skew: RVNKWorlds 1.6.232 and older")
    class VersionSkew {

        @Test
        @DisplayName("every control-plane route answers 501 NOT_SUPPORTED naming 1.6.233")
        void legacyServiceGets501() throws Exception {
            RVNKWorldsController c = controller(new RVNKWorldsControllerTest.LegacyService());
            String[][] routes = {
                {"POST", "/groups", "{\"name\":\"g\"}"},
                {"POST", "/groups/g/worlds", "{\"world\":\"w\"}"},
                {"DELETE", "/groups/g/worlds/w", null},
                {"PUT", "/groups/g/default", null},
                {"PUT", "/groups/g/permission", "{\"requiresPermission\":false}"},
                {"DELETE", "/groups/g", null},
                {"GET", "/skystacks", null},
                {"GET", "/skystacks/g", null},
                {"GET", "/skystack-templates", null},
                {"POST", "/skystacks", "{\"group\":\"g\",\"count\":1}"},
                {"PUT", "/skystacks/g", "{\"enabled\":false}"},
                {"DELETE", "/skystacks/g", null},
            };
            for (String[] route : routes) {
                Result r = call(route[0], c, route[1], route[2]);
                assertEquals(501, r.status(), route[0] + " " + route[1]);
                assertEquals("NOT_SUPPORTED", r.errorCode(), route[0] + " " + route[1]);
                assertTrue(r.body().getAsJsonObject("error").get("message").getAsString().contains("1.6.233"));
                assertNull(r.headers().get("Location"));
            }
        }

        @Test
        @DisplayName("the old read routes keep working against an old RVNKWorlds")
        void legacyReadsStillWork() throws Exception {
            RVNKWorldsController c = controller(new RVNKWorldsControllerTest.LegacyService());
            assertEquals(200, call("GET", c, "/groups", null).status());
            assertEquals(200, call("GET", c, "/groups/g", null).status());
        }

        @Test
        @DisplayName("without RVNKWorlds the read routes are 501 PLUGIN_NOT_LOADED, never an empty list")
        void fallbackService() throws Exception {
            RVNKWorldsController c = controller(null);
            for (String path : List.of("/skystacks", "/skystacks/g", "/skystack-templates")) {
                Result r = call("GET", c, path, null);
                assertEquals(501, r.status(), path);
                assertEquals("PLUGIN_NOT_LOADED", r.errorCode(), path);
            }
            assertEquals(501, call("POST", c, "/groups", "{\"name\":\"g\"}").status());
        }
    }

    // ── non-blocking response ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("async response (#1552)")
    class Async {

        private HttpServletRequest asyncRequest(String path, AsyncContext ctx) throws IOException {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getPathInfo()).thenReturn(path);
            when(req.getMethod()).thenReturn("DELETE");
            when(req.getReader()).thenReturn(new BufferedReader(new StringReader("")));
            when(req.isAsyncSupported()).thenReturn(true);
            when(req.startAsync()).thenReturn(ctx);
            return req;
        }

        @Test
        @DisplayName("the request thread returns before the service answers; the response is written on completion")
        void writesOnCompletion() throws Exception {
            CompletableFuture<ApiResponse<WorldGroupDTO>> pending = new CompletableFuture<>();
            ControlService svc = new ControlService() {
                @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> deleteGroup(String g) {
                    return pending;
                }
            };
            AsyncContext ctx = mock(AsyncContext.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter out = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(out));
            when(ctx.getResponse()).thenReturn(resp);

            controller(svc).doDelete(asyncRequest("/groups/isles", ctx), resp);
            assertEquals("", out.toString(), "nothing is written while the service is pending");
            verify(ctx, never()).complete();
            verify(ctx).setTimeout(30_000L);

            pending.complete(ApiResponse.success(ControlService.group("isles")));
            assertTrue(out.toString().contains("\"isles\""));
            verify(resp).setStatus(200);
            verify(ctx).complete();
        }

        @Test
        @DisplayName("a service that never answers gets 504 TIMEOUT from the async timeout, written once")
        void timeout() throws Exception {
            CompletableFuture<ApiResponse<WorldGroupDTO>> pending = new CompletableFuture<>();
            ControlService svc = new ControlService() {
                @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> deleteGroup(String g) {
                    return pending;
                }
            };
            AsyncContext ctx = mock(AsyncContext.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter out = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(out));
            when(ctx.getResponse()).thenReturn(resp);

            controller(svc).doDelete(asyncRequest("/groups/isles", ctx), resp);
            ArgumentCaptor<AsyncListener> listener = ArgumentCaptor.forClass(AsyncListener.class);
            verify(ctx).addListener(listener.capture());
            listener.getValue().onTimeout(null);
            verify(resp).setStatus(504);
            assertTrue(out.toString().contains("TIMEOUT"));
            assertTrue(pending.isCancelled());

            // A late completion must not write a second body
            pending.obtrudeValue(ApiResponse.success(ControlService.group("isles")));
            verify(ctx, times(1)).complete();
            verify(resp, times(1)).setStatus(anyInt());
        }

        @Test
        @DisplayName("a failed future is 500 INTERNAL_ERROR")
        void failedFuture() throws Exception {
            ControlService svc = new ControlService() {
                @Override public CompletableFuture<ApiResponse<WorldGroupDTO>> deleteGroup(String g) {
                    return CompletableFuture.failedFuture(new IllegalStateException("boom"));
                }
            };
            AsyncContext ctx = mock(AsyncContext.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            when(ctx.getResponse()).thenReturn(resp);
            controller(svc).doDelete(asyncRequest("/groups/isles", ctx), resp);
            verify(resp).setStatus(500);
            verify(ctx).complete();
        }

        @Test
        @DisplayName("without async support the route still answers (bounded wait fallback)")
        void noAsyncSupport() throws Exception {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getPathInfo()).thenReturn("/groups/isles");
            when(req.isAsyncSupported()).thenReturn(false);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter out = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(out));
            controller(new ControlService()).doDelete(req, resp);
            verify(req, never()).startAsync();
            verify(resp).setStatus(200);
            assertTrue(out.toString().contains("\"isles\""));
        }
    }

    // ── DTO mapping ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("WorldGroupDTO is a superset of the old {name, worlds, isDefault} group record")
    void groupDtoSuperset() {
        JsonObject json = JsonParser.parseString(GSON.toJson(ControlService.group("isles"))).getAsJsonObject();
        assertEquals("isles", json.get("name").getAsString());
        assertEquals("world", json.getAsJsonArray("worlds").get(0).getAsString());
        assertFalse(json.get("isDefault").getAsBoolean());
        assertFalse(json.has("description"), "a null description is not sent, as before");
        for (String key : List.of("inventoryLink", "portalEmulation", "requiresPermission", "skyStack")) {
            assertTrue(json.has(key), key);
        }
    }

    @Test
    @DisplayName("FieldError list from a parser is carried into the envelope unchanged")
    void fieldErrorsCarried() {
        WorldForgeRequests.Parsed<CreateSkyStackRequest> p =
            WorldForgeRequests.parseCreateSkyStack(JsonParser.parseString("{\"group\":\"g\"}").getAsJsonObject());
        assertFalse(p.ok());
        assertEquals(List.of(new FieldError("count", "is required")), p.errors());
    }
}
