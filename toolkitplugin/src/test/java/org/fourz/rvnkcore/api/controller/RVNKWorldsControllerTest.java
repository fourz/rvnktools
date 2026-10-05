package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.response.FieldError;
import org.fourz.rvnkcore.api.model.worlds.CreateWorldV2Request;
import org.fourz.rvnkcore.api.model.worlds.GeneratorInfoDTO;
import org.fourz.rvnkcore.api.model.worlds.JobDTO;
import org.fourz.rvnkcore.api.model.worlds.PresetDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewRequest;
import org.fourz.rvnkcore.api.model.worlds.WorldGenSettingsDTO;
import org.fourz.rvnkcore.api.ratelimit.RateLimiter;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Routing and status contract of the World Forge REST surface (#2200), plus backward compatibility
 * of the legacy {@code POST /worlds} body. Servlet request/response are Mockito mocks; the service
 * is a hand-written stub so the defaults of {@link IRVNKWorldsApiService} are exercised for real.
 */
@DisplayName("RVNKWorldsController — World Forge routes")
class RVNKWorldsControllerTest {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
            @Override public void write(JsonWriter out, Instant v) throws IOException {
                out.value(v != null ? v.toString() : null);
            }
            @Override public Instant read(JsonReader in) throws IOException {
                return Instant.parse(in.nextString());
            }
        })
        .setPrettyPrinting()
        .create();

    private final List<RVNKWorldsController> controllers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        controllers.forEach(RVNKWorldsController::destroy);
    }

    // ── harness ──────────────────────────────────────────────────────────────────────────────

    /** Simulates RVNKWorlds <= 1.6.207: only the pre-World-Forge abstract methods are implemented. */
    static class LegacyService implements IRVNKWorldsApiService {
        String lastCreateBody;

        static <T> CompletableFuture<ApiResponse<?>> ok(T data) {
            return CompletableFuture.completedFuture(ApiResponse.success(data));
        }

        @Override public CompletableFuture<ApiResponse<?>> listWorlds() { return ok(List.of()); }
        @Override public CompletableFuture<ApiResponse<?>> getWorld(String n) { return ok(Map.of("name", n)); }
        @Override public CompletableFuture<ApiResponse<?>> createWorld(String body) {
            lastCreateBody = body;
            return ok(Map.of("legacy", true));
        }
        @Override public CompletableFuture<ApiResponse<?>> loadWorld(String n) { return ok(n); }
        @Override public CompletableFuture<ApiResponse<?>> unloadWorld(String n) { return ok(n); }
        @Override public CompletableFuture<ApiResponse<?>> deleteWorld(String n, boolean f) { return ok(n); }
        @Override public CompletableFuture<ApiResponse<?>> listTemplates() { return ok(List.of()); }
        @Override public CompletableFuture<ApiResponse<?>> createTemplate(String body) { return ok(body); }
        @Override public CompletableFuture<ApiResponse<?>> listGroups() { return ok(List.of()); }
        @Override public CompletableFuture<ApiResponse<?>> getGroup(String n) { return ok(n); }
        @Override public CompletableFuture<ApiResponse<?>> restoreWorldSnapshot(String n, String b) { return ok(n); }
        @Override public CompletableFuture<ApiResponse<?>> getMetrics() { return ok(Map.of()); }
        @Override public CompletableFuture<ApiResponse<?>> getHealthStatus() { return ok(Map.of()); }
    }

    /** Simulates RVNKWorlds 1.6.208+: implements the World Forge methods. */
    static class ForgeService extends LegacyService {
        String presetFilter = "unset";
        PresetDTO savedPreset;
        CreateWorldV2Request createV2;
        PreviewRequest previewRequest;
        volatile String previewThread;
        CountDownLatch previewGate;

        static <T> CompletableFuture<ApiResponse<T>> done(ApiResponse<T> r) {
            return CompletableFuture.completedFuture(r);
        }

        @Override public CompletableFuture<ApiResponse<List<GeneratorInfoDTO>>> listGenerators() {
            return done(ApiResponse.success(List.of(new GeneratorInfoDTO("archipelago", "Archipelago",
                "Islands", 1, Map.of("seaLevel", Map.of("type", "int", "min", 40, "max", 90))))));
        }

        @Override public CompletableFuture<ApiResponse<GeneratorInfoDTO>> getGenerator(String id) {
            if (!"archipelago".equals(id)) return done(ApiResponse.error("NOT_FOUND", "no generator " + id));
            return done(ApiResponse.success(new GeneratorInfoDTO(id, "Archipelago", null, 1, Map.of())));
        }

        @Override public CompletableFuture<ApiResponse<List<PresetDTO>>> listPresets(String generatorFilter) {
            presetFilter = generatorFilter;
            return done(ApiResponse.success(List.of()));
        }

        @Override public CompletableFuture<ApiResponse<PresetDTO>> getPreset(String name) {
            return done(ApiResponse.error("NOT_FOUND", "no preset " + name));
        }

        @Override public CompletableFuture<ApiResponse<PresetDTO>> savePreset(PresetDTO preset) {
            savedPreset = preset;
            if ("bad-settings".equals(preset.getName())) {
                return done(ApiResponse.validationError(List.of(
                    new FieldError("settings.seaLevel", "must be between 40 and 90"))));
            }
            return done(ApiResponse.success(preset));
        }

        @Override public CompletableFuture<ApiResponse<PresetDTO>> deletePreset(String name) {
            if ("default".equals(name)) return done(ApiResponse.error("CONFLICT", "built-in preset"));
            return done(ApiResponse.success(new PresetDTO(name, "archipelago", null, null, false, null, null, null)));
        }

        @Override public CompletableFuture<ApiResponse<JobDTO>> createWorldV2(CreateWorldV2Request request) {
            createV2 = request;
            return done(ApiResponse.success(new JobDTO("job-1", "CREATE_WORLD", JobDTO.State.QUEUED, 0.0,
                "Queued", request.getName(), Instant.parse("2026-10-05T00:00:00Z"), null, null)));
        }

        @Override public CompletableFuture<ApiResponse<JobDTO>> getJob(String jobId) {
            if (!"job-1".equals(jobId)) return done(ApiResponse.error("NOT_FOUND", "no job " + jobId));
            return done(ApiResponse.success(new JobDTO(jobId, "CREATE_WORLD", JobDTO.State.DONE, 1.0,
                "Done", "isles", null, null, null)));
        }

        @Override public CompletableFuture<ApiResponse<PreviewDTO>> previewTerrain(PreviewRequest request) {
            previewThread = Thread.currentThread().getName();
            previewRequest = request;
            if (previewGate != null) {
                try {
                    previewGate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            PreviewDTO dto = new PreviewDTO();
            int side = request.samplesPerSide();
            dto.setOriginX(request.originX());
            dto.setOriginZ(request.originZ());
            dto.setStep(request.getStep());
            dto.setWidth(side);
            dto.setHeight(side);
            dto.setHeights(new int[side * side]);
            dto.setWaterLevel(63);
            dto.setBiomePalette(new String[]{"minecraft:ocean"});
            dto.setBiomes(new int[side * side]);
            return done(ApiResponse.success(dto));
        }

        @Override public CompletableFuture<ApiResponse<WorldGenSettingsDTO>> getWorldGenSettings(String worldName) {
            return done(ApiResponse.success(new WorldGenSettingsDTO(worldName, "archipelago", 1, Map.of("seaLevel", 63L))));
        }
    }

    record Result(int status, JsonObject body, String raw, Map<String, String> headers) {
        String errorCode() {
            return body.getAsJsonObject("error").get("code").getAsString();
        }
    }

    private RVNKWorldsController controller(Supplier<IRVNKWorldsApiService> resolver, RateLimiter limiter) {
        RVNKWorldsController c = new RVNKWorldsController(resolver, GSON, mock(LogManager.class), limiter);
        controllers.add(c);
        return c;
    }

    private RVNKWorldsController controller(IRVNKWorldsApiService service) {
        return controller(() -> service, new RateLimiter(RVNKWorldsController.PREVIEW_BURST,
            RVNKWorldsController.PREVIEW_PER_SECOND));
    }

    private static HttpServletRequest request(String path, String body, Map<String, String> params,
                                              String apiKey) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body != null ? body : "")));
        when(req.getContextPath()).thenReturn("/api");
        when(req.getServletPath()).thenReturn("/rvnkworlds");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("X-API-Key")).thenReturn(apiKey);
        if (params != null) params.forEach((k, v) -> when(req.getParameter(k)).thenReturn(v));
        return req;
    }

    private static Result call(String method, RVNKWorldsController c, String path, String body,
                               Map<String, String> params, String apiKey) throws Exception {
        HttpServletRequest req = request(path, body, params, apiKey);
        HttpServletResponse resp = mock(HttpServletResponse.class);
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
        return new Result(status[0], json, raw, headers);
    }

    private static Result get(RVNKWorldsController c, String path) throws Exception {
        return call("GET", c, path, null, null, "k");
    }

    private static Result post(RVNKWorldsController c, String path, String body) throws Exception {
        return call("POST", c, path, body, null, "k");
    }

    private static List<String> fieldNames(Result r) {
        List<String> names = new ArrayList<>();
        r.body().getAsJsonObject("error").getAsJsonArray("fieldErrors")
            .forEach(e -> names.add(e.getAsJsonObject().get("field").getAsString()));
        return names;
    }

    // ── routing ──────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generators, presets, jobs, gen-settings")
    class Routing {

        @Test
        @DisplayName("GET /generators lists generators with their opaque schema")
        void listGenerators() throws Exception {
            Result r = get(controller(new ForgeService()), "/generators");
            assertEquals(200, r.status());
            JsonObject gen = r.body().getAsJsonArray("data").get(0).getAsJsonObject();
            assertEquals("archipelago", gen.get("id").getAsString());
            assertEquals(1, gen.get("schemaVersion").getAsInt());
            assertTrue(gen.getAsJsonObject("schema").has("seaLevel"));
        }

        @Test
        @DisplayName("GET /generators/{id} returns one, unknown id is 404")
        void getGenerator() throws Exception {
            RVNKWorldsController c = controller(new ForgeService());
            assertEquals(200, get(c, "/generators/archipelago").status());
            Result missing = get(c, "/generators/nope");
            assertEquals(404, missing.status());
            assertEquals("NOT_FOUND", missing.errorCode());
        }

        @Test
        @DisplayName("GET /presets passes the ?generator filter, or null without it")
        void presetFilter() throws Exception {
            ForgeService svc = new ForgeService();
            RVNKWorldsController c = controller(svc);
            call("GET", c, "/presets", null, Map.of("generator", " archipelago "), "k");
            assertEquals("archipelago", svc.presetFilter);
            get(c, "/presets/");
            assertNull(svc.presetFilter);
        }

        @Test
        @DisplayName("GET /presets/{name} unknown is 404")
        void presetNotFound() throws Exception {
            assertEquals(404, get(controller(new ForgeService()), "/presets/nope").status());
        }

        @Test
        @DisplayName("GET /jobs/{id} returns the job, unknown is 404")
        void jobs() throws Exception {
            RVNKWorldsController c = controller(new ForgeService());
            Result r = get(c, "/jobs/job-1");
            assertEquals(200, r.status());
            assertEquals("DONE", r.body().getAsJsonObject("data").get("state").getAsString());
            assertEquals(404, get(c, "/jobs/other").status());
        }

        @Test
        @DisplayName("GET /worlds/{name}/gen-settings routes to the snapshot, not to getWorld")
        void genSettings() throws Exception {
            Result r = get(controller(new ForgeService()), "/worlds/isles/gen-settings");
            assertEquals(200, r.status());
            JsonObject data = r.body().getAsJsonObject("data");
            assertEquals("isles", data.get("world").getAsString());
            assertEquals(63, data.getAsJsonObject("settings").get("seaLevel").getAsInt());
        }

        @Test
        @DisplayName("existing GET /worlds/{name} still routes to getWorld")
        void legacyGetWorld() throws Exception {
            Result r = get(controller(new ForgeService()), "/worlds/isles");
            assertEquals("isles", r.body().getAsJsonObject("data").get("name").getAsString());
        }

        @Test
        @DisplayName("unknown path is 404")
        void unknown() throws Exception {
            assertEquals(404, get(controller(new ForgeService()), "/forge").status());
        }
    }

    // ── presets write ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST/DELETE /presets")
    class Presets {

        @Test
        @DisplayName("POST saves, forcing builtIn=false and createdAt=null")
        void saveForcesServerOwnedFields() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/presets", """
                {"name":"tall","generator":"archipelago","builtIn":true,
                 "createdAt":"2020-01-01T00:00:00Z","author":"derek","settings":{"seaLevel":70}}""");
            assertEquals(200, r.status());
            assertFalse(svc.savedPreset.isBuiltIn());
            assertNull(svc.savedPreset.getCreatedAt());
            assertEquals("derek", svc.savedPreset.getAuthor());
            assertEquals(70L, svc.savedPreset.getSettings().get("seaLevel"));
        }

        @Test
        @DisplayName("POST with missing fields is 400 with one field error per field")
        void saveMissingFields() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/presets", "{\"settings\":[1,2]}");
            assertEquals(400, r.status());
            assertEquals("VALIDATION_FAILED", r.errorCode());
            assertEquals(List.of("name", "generator", "settings"), fieldNames(r));
            assertEquals(3, r.body().getAsJsonObject("error").getAsJsonArray("details").size());
            assertNull(svc.savedPreset, "service must not be called on a shape error");
        }

        @Test
        @DisplayName("POST with a non-JSON body is 400 INVALID_REQUEST")
        void saveNotJson() throws Exception {
            Result r = post(controller(new ForgeService()), "/presets", "not json");
            assertEquals(400, r.status());
            assertEquals("INVALID_REQUEST", r.errorCode());
        }

        @Test
        @DisplayName("POST: the service's own validation errors come back as 400 with field paths")
        void saveServiceValidation() throws Exception {
            Result r = post(controller(new ForgeService()), "/presets",
                "{\"name\":\"bad-settings\",\"generator\":\"archipelago\",\"settings\":{\"seaLevel\":5}}");
            assertEquals(400, r.status());
            assertEquals(List.of("settings.seaLevel"), fieldNames(r));
        }

        @Test
        @DisplayName("DELETE a built-in is 409, a custom preset is 200")
        void delete() throws Exception {
            RVNKWorldsController c = controller(new ForgeService());
            Result builtIn = call("DELETE", c, "/presets/default", null, null, "k");
            assertEquals(409, builtIn.status());
            assertEquals("CONFLICT", builtIn.errorCode());
            assertEquals(200, call("DELETE", c, "/presets/mine", null, null, "k").status());
        }
    }

    // ── create world ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /worlds legacy and v2")
    class CreateWorld {

        @Test
        @DisplayName("legacy body goes to createWorld verbatim with the old 200")
        void legacyBody() throws Exception {
            ForgeService svc = new ForgeService();
            String body = "{\"name\":\"old\",\"environment\":\"NETHER\",\"seed\":42,\"groupName\":\"g\",\"autoLoad\":true}";
            Result r = post(controller(svc), "/worlds", body);
            assertEquals(200, r.status());
            assertEquals(body, svc.lastCreateBody);
            assertNull(svc.createV2);
        }

        @Test
        @DisplayName("explicit nulls for generator/preset/settings stay on the legacy path")
        void legacyWithNulls() throws Exception {
            ForgeService svc = new ForgeService();
            post(controller(svc), "/worlds", "{\"name\":\"old\",\"generator\":null,\"preset\":null,\"settings\":null}");
            assertNotNull(svc.lastCreateBody);
            assertNull(svc.createV2);
        }

        @Test
        @DisplayName("a malformed body stays on the legacy path, which reports its own error")
        void legacyMalformed() throws Exception {
            ForgeService svc = new ForgeService();
            post(controller(svc), "/worlds", "{not json");
            assertEquals("{not json", svc.lastCreateBody);
        }

        @Test
        @DisplayName("legacy body against an RVNKWorlds 1.6.207 still works")
        void legacyAgainstOldPlugin() throws Exception {
            LegacyService svc = new LegacyService();
            assertEquals(200, post(controller(svc), "/worlds", "{\"name\":\"old\"}").status());
            assertEquals("{\"name\":\"old\"}", svc.lastCreateBody);
        }

        @Test
        @DisplayName("v2 body returns 202, a Location header, and a parsed request")
        void v2() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/worlds", """
                {"name":" isles ","seed":-9007199254740993,"generator":"archipelago","preset":"default",
                 "settings":{"seaLevel":70,"scale":1.5,"features":["ruins"]},"groupName":"forge","autoLoad":true}""");
            assertEquals(202, r.status());
            assertEquals("/api/rvnkworlds/jobs/job-1", r.headers().get("Location"));
            assertEquals("QUEUED", r.body().getAsJsonObject("data").get("state").getAsString());

            CreateWorldV2Request q = svc.createV2;
            assertEquals("isles", q.getName());
            assertEquals(-9007199254740993L, q.getSeed());
            assertEquals("archipelago", q.getGenerator());
            assertEquals("default", q.getPreset());
            assertEquals(70L, q.getSettings().get("seaLevel"));
            assertEquals(1.5, q.getSettings().get("scale"));
            assertEquals(List.of("ruins"), q.getSettings().get("features"));
            assertEquals("forge", q.getGroupName());
            assertTrue(q.isAutoLoad());
            assertNull(svc.lastCreateBody, "legacy createWorld must not run for a v2 body");
        }

        @Test
        @DisplayName("settings alone selects v2 and then requires generator or preset")
        void v2NeedsGeneratorOrPreset() throws Exception {
            Result r = post(controller(new ForgeService()), "/worlds", "{\"name\":\"x\",\"settings\":{}}");
            assertEquals(400, r.status());
            assertEquals(List.of("generator"), fieldNames(r));
        }

        @Test
        @DisplayName("v2 type errors are 400 field errors, not a 500")
        void v2TypeErrors() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/worlds",
                "{\"seed\":\"abc\",\"generator\":\"archipelago\",\"settings\":[],\"autoLoad\":\"yes\"}");
            assertEquals(400, r.status());
            assertEquals("VALIDATION_FAILED", r.errorCode());
            assertEquals(List.of("name", "seed", "settings", "autoLoad"), fieldNames(r));
            assertNull(svc.createV2);
        }

        @Test
        @DisplayName("v2 against an RVNKWorlds 1.6.207 is 501 NOT_SUPPORTED")
        void v2AgainstOldPlugin() throws Exception {
            Result r = post(controller(new LegacyService()), "/worlds",
                "{\"name\":\"x\",\"generator\":\"archipelago\"}");
            assertEquals(501, r.status());
            assertEquals("NOT_SUPPORTED", r.errorCode());
        }
    }

    // ── preview ──────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /preview")
    class Preview {

        @Test
        @DisplayName("runs on the preview pool, fills defaults, and answers compact JSON")
        void offThreadWithDefaults() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/preview", "{\"generator\":\"archipelago\",\"seed\":7}");
            assertEquals(200, r.status());
            assertNotNull(svc.previewThread);
            assertTrue(svc.previewThread.startsWith("RVNKWorlds-Preview-"), svc.previewThread);
            assertNotEquals(Thread.currentThread().getName(), svc.previewThread);

            PreviewRequest q = svc.previewRequest;
            assertEquals(PreviewRequest.DEFAULT_SIZE, q.getSize());
            assertEquals(PreviewRequest.DEFAULT_STEP, q.getStep());
            assertEquals(0, q.getCenterX());
            assertEquals(-256, q.originX());
            assertEquals(128, q.samplesPerSide());

            JsonObject data = r.body().getAsJsonObject("data");
            assertEquals(128, data.get("width").getAsInt());
            assertEquals(128 * 128, data.getAsJsonArray("heights").size());
            assertFalse(r.raw().contains("\n"), "preview must not be pretty-printed");
        }

        @Test
        @DisplayName("too many samples is a 400 on step, before the service runs")
        void sampleCap() throws Exception {
            ForgeService svc = new ForgeService();
            Result r = post(controller(svc), "/preview", "{\"generator\":\"archipelago\",\"size\":4096,\"step\":1}");
            assertEquals(400, r.status());
            assertEquals(List.of("step"), fieldNames(r));
            assertNull(svc.previewRequest);
        }

        @Test
        @DisplayName("exactly 256x256 samples is allowed")
        void sampleCapBoundary() throws Exception {
            Result r = post(controller(new ForgeService()), "/preview",
                "{\"preset\":\"default\",\"size\":1024,\"step\":4}");
            assertEquals(200, r.status());
        }

        @Test
        @DisplayName("step < 1, size out of range and missing generator/preset are field errors")
        void ranges() throws Exception {
            Result r = post(controller(new ForgeService()), "/preview",
                "{\"size\":0,\"step\":0,\"centerX\":40000000}");
            assertEquals(400, r.status());
            assertEquals(List.of("generator", "centerX", "size", "step"), fieldNames(r));
        }

        @Test
        @DisplayName("per-key rate limit answers 429 with Retry-After; another key is unaffected")
        void rateLimit() throws Exception {
            ForgeService svc = new ForgeService();
            RVNKWorldsController c = controller(() -> svc, new RateLimiter(1, 1));
            String body = "{\"generator\":\"archipelago\",\"size\":64,\"step\":8}";
            assertEquals(200, call("POST", c, "/preview", body, null, "key-a").status());
            Result limited = call("POST", c, "/preview", body, null, "key-a");
            assertEquals(429, limited.status());
            assertEquals("RATE_LIMITED", limited.errorCode());
            assertEquals("1", limited.headers().get("Retry-After"));
            assertEquals(200, call("POST", c, "/preview", body, null, "key-b").status());
        }

        @Test
        @DisplayName("the default limiter allows a burst of 4 then limits")
        void defaultBurst() throws Exception {
            RVNKWorldsController c = controller(new ForgeService());
            String body = "{\"generator\":\"archipelago\",\"size\":64,\"step\":8}";
            int ok = 0;
            int limited = 0;
            for (int i = 0; i < 6; i++) {
                int s = call("POST", c, "/preview", body, null, "burst").status();
                if (s == 200) ok++;
                if (s == 429) limited++;
            }
            assertTrue(ok >= RVNKWorldsController.PREVIEW_BURST, "ok=" + ok);
            assertTrue(limited >= 1, "limited=" + limited);
        }

        @Test
        @DisplayName("when the preview pool and queue are full the next request is 503 PREVIEW_BUSY")
        void busy() throws Exception {
            ForgeService svc = new ForgeService();
            svc.previewGate = new CountDownLatch(1);
            RVNKWorldsController c = controller(() -> svc, new RateLimiter(100, 100));
            String body = "{\"generator\":\"archipelago\",\"size\":64,\"step\":8}";
            int capacity = RVNKWorldsController.PREVIEW_THREADS + RVNKWorldsController.PREVIEW_QUEUE;
            ExecutorService callers = Executors.newFixedThreadPool(capacity);
            try {
                for (int i = 0; i < capacity; i++) {
                    callers.submit(() -> call("POST", c, "/preview", body, null, "k"));
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (c.previewInFlight() < capacity && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertEquals(capacity, c.previewInFlight());
                Result r = call("POST", c, "/preview", body, null, "k");
                assertEquals(503, r.status());
                assertEquals("PREVIEW_BUSY", r.errorCode());
            } finally {
                svc.previewGate.countDown();
                callers.shutdown();
                assertTrue(callers.awaitTermination(10, TimeUnit.SECONDS));
            }
        }

        @Test
        @DisplayName("preview against an RVNKWorlds 1.6.207 is 501")
        void oldPlugin() throws Exception {
            Result r = post(controller(new LegacyService()), "/preview", "{\"generator\":\"archipelago\"}");
            assertEquals(501, r.status());
            assertEquals("NOT_SUPPORTED", r.errorCode());
        }
    }

    // ── 501 paths ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("501 when the feature or plugin is missing")
    class NotAvailable {

        @Test
        @DisplayName("RVNKWorlds 1.6.207: every World Forge route is 501 NOT_SUPPORTED")
        void oldPluginDefaults() throws Exception {
            RVNKWorldsController c = controller(new LegacyService());
            for (String path : List.of("/generators", "/generators/a", "/presets", "/presets/a",
                    "/jobs/a", "/worlds/a/gen-settings")) {
                Result r = get(c, path);
                assertEquals(501, r.status(), path);
                assertEquals("NOT_SUPPORTED", r.errorCode(), path);
            }
            assertEquals(501, post(c, "/presets", "{\"name\":\"a\",\"generator\":\"b\"}").status());
            assertEquals(501, call("DELETE", c, "/presets/a", null, null, "k").status());
        }

        @Test
        @DisplayName("RVNKWorlds not loaded: GETs go to the fallback and answer 501 PLUGIN_NOT_LOADED")
        void pluginNotLoadedGets() throws Exception {
            RVNKWorldsController c = controller(() -> null, new RateLimiter(4, 2));
            for (String path : List.of("/generators", "/generators/a", "/presets", "/presets/a",
                    "/jobs/a", "/worlds/a/gen-settings")) {
                Result r = get(c, path);
                assertEquals(501, r.status(), path);
                assertEquals("PLUGIN_NOT_LOADED", r.errorCode(), path);
            }
        }

        @Test
        @DisplayName("RVNKWorlds not loaded: POST and DELETE answer 501 PLUGIN_NOT_LOADED")
        void pluginNotLoadedWrites() throws Exception {
            RVNKWorldsController c = controller(() -> null, new RateLimiter(4, 2));
            assertEquals("PLUGIN_NOT_LOADED", post(c, "/preview", "{}").errorCode());
            assertEquals(501, post(c, "/worlds", "{\"name\":\"x\",\"generator\":\"a\"}").status());
            assertEquals(501, post(c, "/presets", "{}").status());
            assertEquals(501, call("DELETE", c, "/presets/a", null, null, "k").status());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("envelope and helpers")
    class Helpers {

        @Test
        @DisplayName("an ordinary error envelope has no fieldErrors key")
        void ordinaryErrorUnchanged() {
            String json = GSON.toJson(ApiResponse.error("NOT_FOUND", "x"));
            assertFalse(json.contains("fieldErrors"), json);
        }

        @Test
        @DisplayName("statusFor maps 501, 409, 429, 504 and the success override")
        void statusFor() {
            assertEquals(202, RVNKWorldsController.statusFor(ApiResponse.success("x"), 202));
            assertEquals(501, RVNKWorldsController.statusFor(ApiResponse.error("NOT_SUPPORTED", "x"), 200));
            assertEquals(501, RVNKWorldsController.statusFor(ApiResponse.error("PLUGIN_NOT_LOADED", "x"), 200));
            assertEquals(409, RVNKWorldsController.statusFor(ApiResponse.error("CONFLICT", "x"), 200));
            assertEquals(429, RVNKWorldsController.statusFor(ApiResponse.error("RATE_LIMITED", "x"), 200));
            assertEquals(504, RVNKWorldsController.statusFor(ApiResponse.error("TIMEOUT", "x"), 200));
            assertEquals(400, RVNKWorldsController.statusFor(
                ApiResponse.validationError(List.of(new FieldError("a", "b"))), 200));
        }

        @Test
        @DisplayName("the rate-limit id is a hash of the key, never the key itself")
        void clientIdHashesKey() throws Exception {
            String id = RVNKWorldsController.previewClientId(request("/preview", null, null, "secret-key"));
            assertTrue(id.startsWith("key:"), id);
            assertFalse(id.contains("secret"), id);
            assertEquals("ip:127.0.0.1",
                RVNKWorldsController.previewClientId(request("/preview", null, null, null)));
        }
    }
}
