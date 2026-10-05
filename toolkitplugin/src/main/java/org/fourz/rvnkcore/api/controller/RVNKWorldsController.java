package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.model.worlds.CreateWorldV2Request;
import org.fourz.rvnkcore.api.model.worlds.JobDTO;
import org.fourz.rvnkcore.api.model.worlds.PresetDTO;
import org.fourz.rvnkcore.api.model.worlds.PreviewRequest;
import org.fourz.rvnkcore.api.ratelimit.RateLimiter;
import org.fourz.rvnkcore.api.service.IRVNKWorldsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API controller for RVNKWorlds endpoints.
 * Routes HTTP requests to {@link IRVNKWorldsApiService} provided by the RVNKWorlds plugin.
 *
 * <p>If the RVNKWorlds plugin is not loaded (service not registered), GETs are served by the
 * read-only {@link RVNKWorldsFallbackService} and every other method returns 501.</p>
 *
 * <p><b>World Forge routes (#2200, since 1.5.96):</b></p>
 * <pre>
 * GET    /generators                 listGenerators()
 * GET    /generators/{id}            getGenerator(id)
 * GET    /presets[?generator=]       listPresets(generator)
 * GET    /presets/{name}             getPreset(name)
 * POST   /presets                    savePreset(PresetDTO)            400 field errors
 * DELETE /presets/{name}             deletePreset(name)               409 for a built-in
 * POST   /worlds  (v2 body)          createWorldV2(...)               202 + Location: jobs/{id}
 * GET    /jobs/{id}                  getJob(id)
 * POST   /preview                    previewTerrain(PreviewRequest)   429 rate limit, 503 busy
 * GET    /worlds/{name}/gen-settings getWorldGenSettings(name)
 * </pre>
 *
 * <p>Status mapping: success 200 (202 for v2 create); otherwise {@code NOT_SUPPORTED} and
 * {@code PLUGIN_NOT_LOADED} give 501, a 30 s await timeout gives 504, and every other code uses
 * {@link org.fourz.rvnkcore.api.model.response.ApiError#suggestedHttpStatus()}.</p>
 *
 * @since 1.4.0
 */
public class RVNKWorldsController extends HttpServlet {

    private final Gson gson;
    /** Non-pretty encoder for /preview: a pretty-printed 256x256 int array is megabytes of whitespace. */
    private final Gson compactGson;
    private final LogManager logger;
    private final Supplier<IRVNKWorldsApiService> serviceResolver;

    /** Per-API-key token bucket for /preview: {@value #PREVIEW_BURST} burst, {@value #PREVIEW_PER_SECOND}/s refill. */
    static final int PREVIEW_BURST = 4;
    static final int PREVIEW_PER_SECOND = 2;
    private final RateLimiter previewLimiter;

    /**
     * Preview runs here, never on the Bukkit main thread and never on a Jetty request thread's own
     * stack. Bounded so a burst of previews (from all keys together) cannot pin the CPU: at most
     * {@value #PREVIEW_THREADS} run and {@value #PREVIEW_QUEUE} wait; the rest get 503.
     */
    static final int PREVIEW_THREADS = 2;
    static final int PREVIEW_QUEUE = 4;
    private final ThreadPoolExecutor previewExecutor;

    private static final long AWAIT_SECONDS = 30;

    private static final Pattern WORLDS_PATTERN = Pattern.compile("^/worlds/?$");
    private static final Pattern WORLD_NAME_PATTERN = Pattern.compile("^/worlds/([^/]+)/?$");
    private static final Pattern WORLD_LOAD_PATTERN = Pattern.compile("^/worlds/([^/]+)/load/?$");
    private static final Pattern WORLD_UNLOAD_PATTERN = Pattern.compile("^/worlds/([^/]+)/unload/?$");
    private static final Pattern WORLD_RESTORE_PATTERN = Pattern.compile("^/worlds/([^/]+)/restore/?$");
    private static final Pattern TEMPLATES_PATTERN = Pattern.compile("^/templates/?$");
    private static final Pattern GROUPS_PATTERN = Pattern.compile("^/groups/?$");
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^/groups/([^/]+)/?$");
    private static final Pattern METRICS_PATTERN = Pattern.compile("^/metrics/?$");
    private static final Pattern HEALTH_PATTERN = Pattern.compile("^/health/?$");
    /** #1923 — dense site survey for agentic tooling. */
    private static final Pattern SURVEY_PATTERN = Pattern.compile("^/survey/?$");
    /** #2051 — coordinate probes: point/batch, column, heightmap grid, scatter sweep. */
    private static final Pattern PROBE_PATTERN = Pattern.compile("^/probe/?$");
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^/column/?$");
    private static final Pattern HEIGHTMAP_PATTERN = Pattern.compile("^/heightmap/?$");
    private static final Pattern SCATTER_PATTERN = Pattern.compile("^/scatter/?$");
    // ── World Forge (#2200) ──
    private static final Pattern GENERATORS_PATTERN = Pattern.compile("^/generators/?$");
    private static final Pattern GENERATOR_ID_PATTERN = Pattern.compile("^/generators/([^/]+)/?$");
    private static final Pattern PRESETS_PATTERN = Pattern.compile("^/presets/?$");
    private static final Pattern PRESET_NAME_PATTERN = Pattern.compile("^/presets/([^/]+)/?$");
    private static final Pattern JOB_PATTERN = Pattern.compile("^/jobs/([^/]+)/?$");
    private static final Pattern PREVIEW_PATTERN = Pattern.compile("^/preview/?$");
    private static final Pattern WORLD_GEN_SETTINGS_PATTERN = Pattern.compile("^/worlds/([^/]+)/gen-settings/?$");

    public RVNKWorldsController(IRVNKWorldsApiService ignored, Gson gson, LogManager logger) {
        this(RVNKWorldsController::resolveFromRegistry, gson, logger,
            new RateLimiter(PREVIEW_BURST, PREVIEW_PER_SECOND));
    }

    /** Test seam: inject the service lookup and the preview limiter. */
    RVNKWorldsController(Supplier<IRVNKWorldsApiService> serviceResolver, Gson gson, LogManager logger,
                         RateLimiter previewLimiter) {
        this.gson = gson;
        this.compactGson = new GsonBuilder().disableHtmlEscaping().create();
        this.logger = logger;
        this.serviceResolver = serviceResolver;
        this.previewLimiter = previewLimiter;
        AtomicInteger threadNo = new AtomicInteger();
        this.previewExecutor = new ThreadPoolExecutor(PREVIEW_THREADS, PREVIEW_THREADS,
            30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(PREVIEW_QUEUE), r -> {
                Thread t = new Thread(r, "RVNKWorlds-Preview-" + threadNo.incrementAndGet());
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());
        this.previewExecutor.allowCoreThreadTimeOut(true);
    }

    @Override
    public void destroy() {
        previewExecutor.shutdownNow();
        previewLimiter.shutdown();
        super.destroy();
    }

    /** Test seam: previews running plus previews queued. */
    int previewInFlight() {
        return previewExecutor.getActiveCount() + previewExecutor.getQueue().size();
    }

    private IRVNKWorldsApiService getApiService() {
        return serviceResolver.get();
    }

    /**
     * Lazily resolves the API service from ServiceRegistry.
     * The service is registered by the RVNKWorlds plugin after RVNKCore starts.
     */
    private static IRVNKWorldsApiService resolveFromRegistry() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(IRVNKWorldsApiService.class)) {
                return registry.getService(IRVNKWorldsApiService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Fall back to Bukkit-backed read-only service when RVNKWorlds is not loaded.
        // See RVNKWorldsFallbackService — keep response shapes in sync with WorldApiEndpointImpl.
        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            apiService = new RVNKWorldsFallbackService();
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<? extends ApiResponse<?>> future;
            Matcher matcher;

            if (WORLDS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listWorlds();
            } else if ((matcher = WORLD_NAME_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getWorld(matcher.group(1));
            } else if (TEMPLATES_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listTemplates();
            } else if (GROUPS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listGroups();
            } else if ((matcher = GROUP_NAME_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getGroup(matcher.group(1));
            } else if (METRICS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.getMetrics();
            } else if (HEALTH_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.getHealthStatus();
            } else if (SURVEY_PATTERN.matcher(pathInfo).matches()) {
                // The whole query string is handed through: the survey takes several optional
                // params and parsing them here would split the contract across two files.
                future = apiService.surveySite(req.getQueryString());
            } else if (PROBE_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.probePoints(req.getQueryString());
            } else if (COLUMN_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.columnProfile(req.getQueryString());
            } else if (HEIGHTMAP_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.heightmapGrid(req.getQueryString());
            } else if (SCATTER_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.scatterProbe(req.getQueryString());
            } else if (GENERATORS_PATTERN.matcher(pathInfo).matches()) {
                future = apiService.listGenerators();
            } else if ((matcher = GENERATOR_ID_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getGenerator(matcher.group(1));
            } else if (PRESETS_PATTERN.matcher(pathInfo).matches()) {
                String generator = req.getParameter("generator");
                future = apiService.listPresets(generator == null || generator.isBlank() ? null : generator.trim());
            } else if ((matcher = PRESET_NAME_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getPreset(matcher.group(1));
            } else if ((matcher = JOB_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getJob(matcher.group(1));
            } else if ((matcher = WORLD_GEN_SETTINGS_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.getWorldGenSettings(matcher.group(1));
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            sendApiResponse(resp, await(future));

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API GET: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKWorlds plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            CompletableFuture<? extends ApiResponse<?>> future;
            Matcher matcher;

            if (WORLDS_PATTERN.matcher(pathInfo).matches()) {
                String body = ApiUtils.readRequestBody(req);
                JsonObject obj = WorldForgeRequests.parseObject(body);
                if (WorldForgeRequests.isV2CreateBody(obj)) {
                    handleCreateWorldV2(req, resp, apiService, obj);
                    return;
                }
                // Legacy body ({name, environment, seed, templateName, ...}) — passed through
                // verbatim, exactly as before 1.5.96.
                future = apiService.createWorld(body);
            } else if (PRESETS_PATTERN.matcher(pathInfo).matches()) {
                JsonObject obj = WorldForgeRequests.parseObject(ApiUtils.readRequestBody(req));
                if (obj == null) {
                    sendError(resp, 400, "INVALID_REQUEST", "Request body must be a JSON object");
                    return;
                }
                WorldForgeRequests.Parsed<PresetDTO> parsed = WorldForgeRequests.parsePreset(obj);
                if (!parsed.ok()) {
                    sendApiResponse(resp, ApiResponse.validationError(parsed.errors()));
                    return;
                }
                future = apiService.savePreset(parsed.value());
            } else if (PREVIEW_PATTERN.matcher(pathInfo).matches()) {
                handlePreview(req, resp, apiService);
                return;
            } else if ((matcher = WORLD_LOAD_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.loadWorld(matcher.group(1));
            } else if ((matcher = WORLD_UNLOAD_PATTERN.matcher(pathInfo)).matches()) {
                future = apiService.unloadWorld(matcher.group(1));
            } else if (TEMPLATES_PATTERN.matcher(pathInfo).matches()) {
                String body = ApiUtils.readRequestBody(req);
                future = apiService.createTemplate(body);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
                return;
            }

            sendApiResponse(resp, await(future));

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API POST: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKWorlds plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            Matcher matcher = WORLD_NAME_PATTERN.matcher(pathInfo);
            if (matcher.matches()) {
                String worldName = matcher.group(1);
                boolean deleteFiles = "true".equalsIgnoreCase(req.getParameter("deleteFiles"));

                sendApiResponse(resp, await(apiService.deleteWorld(worldName, deleteFiles)));
            } else if ((matcher = PRESET_NAME_PATTERN.matcher(pathInfo)).matches()) {
                sendApiResponse(resp, await(apiService.deletePreset(matcher.group(1))));
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API DELETE: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        IRVNKWorldsApiService apiService = getApiService();
        if (apiService == null) {
            sendError(resp, 501, "PLUGIN_NOT_LOADED", "RVNKWorlds plugin is not loaded");
            return;
        }

        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            Matcher matcher = WORLD_RESTORE_PATTERN.matcher(pathInfo);
            if (matcher.matches()) {
                String worldName = matcher.group(1);
                String body = ApiUtils.readRequestBody(req);
                ApiResponse<?> response = await(apiService.restoreWorldSnapshot(worldName, body));
                // 202 Accepted for a successfully queued restore; other statuses use normal mapping
                ApiUtils.sendJson(resp, gson, statusFor(response, 202), response);
            } else {
                sendError(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling RVNKWorlds API PUT: " + pathInfo, e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    // ── World Forge handlers (#2200) ─────────────────────────────────────────────────────────

    private void handleCreateWorldV2(HttpServletRequest req, HttpServletResponse resp,
                                     IRVNKWorldsApiService apiService, JsonObject obj) throws Exception {
        WorldForgeRequests.Parsed<CreateWorldV2Request> parsed = WorldForgeRequests.parseCreateV2(obj);
        if (!parsed.ok()) {
            sendApiResponse(resp, ApiResponse.validationError(parsed.errors()));
            return;
        }
        ApiResponse<?> response = await(apiService.createWorldV2(parsed.value()));
        if (response.success() && response.data() instanceof JobDTO job && job.getId() != null) {
            resp.setHeader("Location", jobLocation(req, job.getId()));
        }
        ApiUtils.sendJson(resp, gson, statusFor(response, 202), response);
    }

    /** {@code <context><servlet>/jobs/{id}} — e.g. {@code /api/rvnkworlds/jobs/abc}. */
    static String jobLocation(HttpServletRequest req, String jobId) {
        String context = req.getContextPath() != null ? req.getContextPath() : "";
        String servlet = req.getServletPath() != null ? req.getServletPath() : "";
        return context + servlet + "/jobs/" + jobId;
    }

    /**
     * POST /preview: rate limit, shape-check, then run the service on the bounded preview pool.
     * The order matters — the limiter runs first so a flood of malformed bodies is throttled too.
     */
    private void handlePreview(HttpServletRequest req, HttpServletResponse resp,
                               IRVNKWorldsApiService apiService) throws Exception {
        if (!previewLimiter.isAllowed(previewClientId(req))) {
            resp.setHeader("Retry-After", "1");
            ApiUtils.sendJson(resp, gson, 429, ApiResponse.error("RATE_LIMITED",
                "Preview is limited to " + PREVIEW_PER_SECOND + " requests/second (burst "
                    + PREVIEW_BURST + ") per API key"));
            return;
        }

        JsonObject obj = WorldForgeRequests.parseObject(ApiUtils.readRequestBody(req));
        if (obj == null) {
            sendError(resp, 400, "INVALID_REQUEST", "Request body must be a JSON object");
            return;
        }
        WorldForgeRequests.Parsed<PreviewRequest> parsed = WorldForgeRequests.parsePreview(obj);
        if (!parsed.ok()) {
            sendApiResponse(resp, ApiResponse.validationError(parsed.errors()));
            return;
        }

        PreviewRequest request = parsed.value();
        CompletableFuture<? extends ApiResponse<?>> future;
        try {
            future = CompletableFuture
                .supplyAsync(() -> apiService.previewTerrain(request), previewExecutor)
                .thenCompose(f -> f);
        } catch (RejectedExecutionException busy) {
            resp.setHeader("Retry-After", "1");
            sendError(resp, 503, "PREVIEW_BUSY", "Too many previews in progress; retry shortly");
            return;
        }
        ApiResponse<?> response = await(future);
        ApiUtils.sendJson(resp, compactGson, statusFor(response, 200), response);
    }

    /**
     * Rate-limit identity for /preview: a hash of the X-API-Key (never the raw key — it would sit in
     * a map for minutes), else the remote address. AuthFilter has already rejected a missing or
     * wrong key, so in production this is always the key.
     */
    static String previewClientId(HttpServletRequest req) {
        String key = req.getHeader("X-API-Key");
        if (key != null && !key.isEmpty()) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
                return "key:" + HexFormat.of().formatHex(digest, 0, 8);
            } catch (NoSuchAlgorithmException e) {
                return "key:" + key.hashCode();
            }
        }
        return "ip:" + ApiUtils.getClientIP(req);
    }

    // ── response helpers ─────────────────────────────────────────────────────────────────────

    /**
     * Awaits a service future for up to {@value #AWAIT_SECONDS}s. A timeout becomes a TIMEOUT
     * error (504) rather than a generic 500; a failed future is rethrown for the 500 handler.
     */
    private ApiResponse<?> await(CompletableFuture<? extends ApiResponse<?>> future) throws Exception {
        try {
            ApiResponse<?> response = future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            return response != null ? response
                : ApiResponse.error("INTERNAL_ERROR", "RVNKWorlds returned no response");
        } catch (TimeoutException e) {
            future.cancel(true);
            return ApiResponse.error("TIMEOUT", "RVNKWorlds did not answer within " + AWAIT_SECONDS + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw cause instanceof Exception ex ? ex : new RuntimeException(cause);
        }
    }

    /**
     * HTTP status for a service response. NOT_SUPPORTED (an RVNKWorlds build older than the route)
     * and PLUGIN_NOT_LOADED (fallback service) are 501; everything else follows
     * {@code ApiError.suggestedHttpStatus()}.
     */
    static int statusFor(ApiResponse<?> response, int successStatus) {
        if (response.success()) return successStatus;
        if (response.error() == null) return 400;
        String code = response.error().code();
        if ("NOT_SUPPORTED".equals(code) || "PLUGIN_NOT_LOADED".equals(code)) return 501;
        return response.error().suggestedHttpStatus();
    }

    private void sendApiResponse(HttpServletResponse resp, ApiResponse<?> response) {
        ApiUtils.sendJson(resp, gson, statusFor(response, 200), response);
    }

    private void sendError(HttpServletResponse resp, int status, String code, String message) {
        ApiUtils.sendError(resp, gson, status, code, message);
    }
}
