package org.fourz.rvnkcore.api.security;

import com.google.gson.Gson;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.ratelimit.RateLimiter;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication filter for RVNKCore API endpoints.
 * Implements API key authentication and IP-based access control.
 */
public class AuthFilter implements Filter {
    private final String apiKey;
    private final Set<String> allowedIPs;
    private final LogManager logger;
    private final boolean ipWhitelistEnabled;
    private final Gson gson;
    /** Per-IP throttle (#1043); null when disabled via config. */
    private final RateLimiter rateLimiter;

    /** Minimum gap between rate-limit warnings for the same IP. */
    private static final long RATE_LIMIT_WARN_INTERVAL_MS = 60_000L;
    /** Cap on tracked IPs before stale warn-state is pruned. */
    private static final int RATE_LIMIT_WARN_MAX_TRACKED = 10_000;

    /*
     * Rate-limit state is deliberately STATIC and shared across every AuthFilter instance.
     *
     * AuthFilter is constructed in two places: once by ServletFactory for the fixed API
     * paths, and again by ServletRegistrationServiceImpl for *each* dynamically registered
     * servlet path. Per-instance state would therefore mean:
     *   - the limit is not global — a caller spread across N registered paths would get
     *     N x the configured allowance;
     *   - warn state fragments, so a single flood logs once per filter instance;
     *   - every instance leaks a RateLimiter-Cleanup daemon thread, since the filter has
     *     no lifecycle hook that shuts its limiter down.
     * Sharing one limiter across instances fixes all three.
     */
    /**
     * Marks a request as already rate-limit-checked. A single HTTP request can traverse more
     * than one AuthFilter instance (ServletFactory registers one for the fixed paths and
     * ServletRegistrationServiceImpl registers another per servlet path), and without this
     * guard each pass would consume another token from the shared bucket — halving the
     * effective limit on any double-filtered path. One request must cost exactly one token.
     */
    private static final String RATE_LIMIT_CHECKED_ATTR = "org.fourz.rvnkcore.rateLimitChecked";

    private static final Object RATE_LIMITER_LOCK = new Object();
    private static RateLimiter sharedRateLimiter;
    private static int sharedRateLimitPerMinute;
    /** Per-IP warn state: {lastWarnMs, suppressedSinceWarn, suppressedToReport}. */
    private static final ConcurrentHashMap<String, long[]> rateLimitWarnState = new ConcurrentHashMap<>();

    /**
     * Creates an AuthFilter with API key authentication.
     *
     * @param config API configuration containing security settings
     * @param plugin Plugin instance for logging
     * @param gson   JSON serializer for canonical error responses
     */
    public AuthFilter(ApiConfig config, Plugin plugin, Gson gson) {
        this.apiKey = config.getApiKey();
        this.allowedIPs = config.getAllowedIPs() != null ? new HashSet<>(Arrays.asList(config.getAllowedIPs())) : new HashSet<>();
        this.ipWhitelistEnabled = !this.allowedIPs.isEmpty();
        this.logger = LogManager.getInstance(plugin, getClass());
        this.gson = gson;
        
        // Debug logging is configured globally in RVNKCoreBootstrap
        
        // Log whitelist configuration once during initialization
        if (ipWhitelistEnabled) {
            logger.info("IP whitelist enabled with " + this.allowedIPs.size() + " allowed IPs");
        } else {
            logger.info("IP whitelist disabled - allowing all IPs");
        }

        // Per-IP throttling (#1043) — shared across all AuthFilter instances.
        if (config.isRateLimitEnabled()) {
            int perMinute = config.getRateLimitRequestsPerMinute();
            this.rateLimiter = acquireSharedRateLimiter(perMinute);
            logger.info("API rate limiting enabled - " + perMinute + " requests/minute per IP (shared)");
        } else {
            this.rateLimiter = null;
            logger.info("API rate limiting disabled");
        }
    }

    /**
     * Returns the process-wide {@link RateLimiter}, creating it on first use and replacing
     * it only when the configured limit changes (e.g. across a config reload). The previous
     * limiter is shut down on replacement so its cleanup thread does not leak.
     */
    private static RateLimiter acquireSharedRateLimiter(int perMinute) {
        synchronized (RATE_LIMITER_LOCK) {
            if (sharedRateLimiter == null || sharedRateLimitPerMinute != perMinute) {
                if (sharedRateLimiter != null) {
                    sharedRateLimiter.shutdown();
                    rateLimitWarnState.clear();
                }
                sharedRateLimiter = RateLimiter.forRequestsPerMinute(perMinute);
                sharedRateLimitPerMinute = perMinute;
            }
            return sharedRateLimiter;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIP = ApiUtils.getClientIP(httpRequest);
        String method = httpRequest.getMethod();
        String requestURI = httpRequest.getRequestURI();
        
        // Move API request logging to debug level to reduce verbosity
        logger.debug("API Request: " + method + " " + requestURI + " from IP: " + clientIP);
        
        // Health endpoint is public — allow unauthenticated probes (Caddy, uptime monitors)
        if (requestURI.endsWith("/v1/health") || requestURI.contains("/v1/health/")) {
            chain.doFilter(request, response);
            return;
        }

        // Per-IP throttling (#1043). Deliberately runs BEFORE the whitelist and API-key
        // checks so an unauthenticated flood — including API-key brute-forcing — is
        // throttled too. The health endpoint returns above and is never throttled.
        if (rateLimiter != null && httpRequest.getAttribute(RATE_LIMIT_CHECKED_ATTR) == null) {
            httpRequest.setAttribute(RATE_LIMIT_CHECKED_ATTR, Boolean.TRUE);
            if (!rateLimiter.isAllowed(clientIP)) {
                warnRateLimited(clientIP, method, requestURI);
                sendRateLimited(httpResponse);
                return;
            }
        }

        // Check IP whitelist if enabled
        if (ipWhitelistEnabled) {
            if (!allowedIPs.contains(clientIP)) {
                logger.warning("API access denied for IP: " + clientIP + " (not in whitelist)");
                sendUnauthorized(httpResponse, "IP not allowed");
                return;
            }
            // Only log successful IP whitelist check in debug mode
            logger.debug("IP whitelist check passed for: " + clientIP);
        }
        // Remove the repetitive "IP whitelist disabled" message since it's logged once during init
        
        // Check API key
        String providedKey = httpRequest.getHeader("X-API-Key");
        if (providedKey == null) {
            logger.warning("API access denied - No API key provided from IP: " + clientIP);
            sendUnauthorized(httpResponse, "Missing API key");
            return;
        }
        
        if (!ApiUtils.constantTimeEquals(apiKey, providedKey)) {
            logger.warning("API access denied - Invalid API key from IP: " + clientIP);
            logger.debug("API key mismatch: provided key length=" + providedKey.length());
            sendUnauthorized(httpResponse, "Invalid API key");
            return;
        }
        
        // Move successful authentication to debug level
        logger.debug("API authentication successful for IP: " + clientIP);
        
        // Authentication successful, continue with request
        chain.doFilter(request, response);
    }

    /**
     * Logs a rate-limit rejection at most once per IP per minute, reporting how many
     * further rejections were swallowed in the meantime (#1043 follow-up).
     *
     * <p>Warning per rejection made the log itself an amplifier: a verification flood of
     * 1200 requests produced ~548 WARN lines in six seconds. Under a real attack that
     * buries every other line in the log — the denial-of-service succeeds against the
     * operator's visibility even though the API held.
     */
    private void warnRateLimited(String clientIP, String method, String requestURI) {
        long now = System.currentTimeMillis();

        // Bound memory against rotating/spoofed source IPs.
        if (rateLimitWarnState.size() > RATE_LIMIT_WARN_MAX_TRACKED) {
            rateLimitWarnState.entrySet()
                    .removeIf(e -> now - e.getValue()[0] > RATE_LIMIT_WARN_INTERVAL_MS);
        }

        long[] state = rateLimitWarnState.compute(clientIP, (ip, prev) -> {
            if (prev == null) {
                return new long[]{now, 0L, 0L};
            }
            if (now - prev[0] >= RATE_LIMIT_WARN_INTERVAL_MS) {
                // Window reopened — emit, and report what was suppressed while it was closed.
                return new long[]{now, 0L, prev[1]};
            }
            // Still inside the window — count it and stay quiet.
            return new long[]{prev[0], prev[1] + 1L, -1L};
        });

        if (state[2] < 0) {
            return;
        }

        String suppressed = state[2] > 0
                ? " (" + state[2] + " further rejections suppressed in the previous minute)"
                : "";
        logger.warning("API rate limit exceeded for IP: " + clientIP
                + " on " + method + " " + requestURI + suppressed);
    }

    /**
     * Sends a 429 using the canonical ApiResponse envelope (#1043).
     */
    private void sendRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(gson.toJson(
                ApiResponse.error("RATE_LIMITED", "Too many requests.")));
    }

    /**
     * Sends unauthorized response using the canonical ApiResponse envelope.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(ApiResponse.error("UNAUTHORIZED", message)));
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
