package org.fourz.rvnkcore.api.service.impl;

import com.google.gson.Gson;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.security.AuthFilter;
import org.fourz.rvnkcore.api.service.IServletRegistrationService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the servlet registration service for external plugins.
 *
 * <p>This service maintains a registry of externally registered servlets and handles
 * their integration with the RVNKCore Jetty server. It supports dynamic registration
 * after server startup.</p>
 *
 * <h2>Thread Safety:</h2>
 * <p>This implementation is thread-safe. Multiple plugins can register servlets
 * concurrently without synchronization issues.</p>
 *
 * <h2>Lifecycle:</h2>
 * <p>Servlets registered before server startup are queued and applied when the server
 * starts. Servlets registered after startup are applied immediately.</p>
 *
 * @since 1.4.0
 */
public class ServletRegistrationServiceImpl implements IServletRegistrationService {

    private final LogManager logger;
    private final ApiConfig config;
    private final Plugin plugin;
    private final Gson gson;
    
    // Thread-safe registry of external servlets
    private final Map<String, ServletRegistration> registeredServlets = new ConcurrentHashMap<>();
    
    // Reference to the servlet context (set when server starts)
    private volatile ServletContextHandler servletContext;

    /**
     * One AuthFilter reused across every authenticated registration (#1547).
     *
     * <p>Previously a new instance was built per path, which multiplied the filter's
     * internal state — most visibly the rate limiter, whose cleanup thread then leaked
     * once per registration. Every instance is constructed from the same {@link ApiConfig},
     * so a single shared instance is equivalent and cheaper.
     */
    private volatile AuthFilter sharedAuthFilter;
    
    // Server running state
    private volatile boolean serverRunning = false;

    /**
     * Internal record to track servlet registrations.
     */
    /** Path -> permanently-bound wrapper. Survives plugin reloads; only the delegate swaps. */
    private final Map<String, DelegatingServlet> boundWrappers = new ConcurrentHashMap<>();

    private record ServletRegistration(
        HttpServlet servlet,
        String displayName,
        boolean requireAuth,
        boolean applied
    ) {
        /**
         * Creates a new registration marked as applied.
         */
        ServletRegistration withApplied(boolean applied) {
            return new ServletRegistration(servlet, displayName, requireAuth, applied);
        }
    }

    /**
     * Creates a new ServletRegistrationServiceImpl.
     *
     * @param config The API configuration
     * @param plugin The plugin instance for logging
     * @param gson   JSON serializer for auth filter responses
     */
    public ServletRegistrationServiceImpl(ApiConfig config, Plugin plugin, Gson gson) {
        this.config = config;
        this.plugin = plugin;
        this.gson = gson;
        this.logger = LogManager.getInstance(plugin, getClass());
        logger.debug("ServletRegistrationService initialized");
    }

    /**
     * Sets the servlet context handler. Called by CoreServer when the server starts.
     *
     * @param context The servlet context handler
     */
    public void setServletContext(ServletContextHandler context) {
        this.servletContext = context;
        this.serverRunning = (context != null);
        
        if (context != null) {
            // Apply any pending registrations
            applyPendingRegistrations();
        }
    }

    /**
     * Called when the server stops.
     */
    public void onServerStop() {
        this.serverRunning = false;
        // Mark all registrations as not applied (they'll need to be re-applied on restart)
        registeredServlets.replaceAll((path, reg) -> reg.withApplied(false));
    }

    @Override
    public boolean registerServlet(String pathSpec, HttpServlet servlet) {
        return registerServlet(pathSpec, servlet, true);
    }

    @Override
    public boolean registerServlet(String pathSpec, HttpServlet servlet, boolean requireAuth) {
        if (servlet == null) {
            throw new IllegalArgumentException("servlet cannot be null");
        }
        return registerServlet(pathSpec, servlet, servlet.getClass().getSimpleName(), requireAuth);
    }

    @Override
    public boolean registerServlet(String pathSpec, HttpServlet servlet, String displayName, boolean requireAuth) {
        // Validate inputs
        if (pathSpec == null || pathSpec.trim().isEmpty()) {
            throw new IllegalArgumentException("pathSpec cannot be null or empty");
        }
        if (servlet == null) {
            throw new IllegalArgumentException("servlet cannot be null");
        }
        
        String normalizedPath = normalizePath(pathSpec);

        // Re-registration swaps the delegate behind the already-bound wrapper (#1604).
        //
        // This used to return false and log "Servlet already registered". Jetty cannot remove a
        // servlet from a started ServletContextHandler, so the previously-bound servlet stayed
        // mapped and kept serving the OLD plugin's classes after a hot reload — while the plugin
        // logged Enabled and the endpoint returned 200. A deployed REST fix simply had no effect,
        // which reads as "the fix doesn't work" rather than "the fix was never loaded".
        DelegatingServlet existing = boundWrappers.get(normalizedPath);
        if (existing != null) {
            existing.setDelegate(servlet, servletContext);
            registeredServlets.put(normalizedPath,
                    new ServletRegistration(servlet, displayName, requireAuth, true));
            logger.info("Servlet at " + normalizedPath + " rebound to " + displayName
                    + " (hot swap) - previous delegate replaced");
            return true;
        }

        // Create registration
        ServletRegistration registration = new ServletRegistration(servlet, displayName, requireAuth, false);
        registeredServlets.put(normalizedPath, registration);
        
        logger.debug("Registered external servlet: " + displayName + " at " + normalizedPath + 
                   (requireAuth ? " (authenticated)" : " (public)"));
        
        // If server is running, apply immediately
        if (serverRunning && servletContext != null) {
            return applyRegistration(normalizedPath, registration);
        }
        
        // Otherwise, it will be applied when server starts
        logger.debug("Servlet queued for registration (server not yet started): " + normalizedPath);
        return true;
    }

    @Override
    public boolean unregisterServlet(String pathSpec) {
        String normalizedPath = normalizePath(pathSpec);

        ServletRegistration removed = registeredServlets.remove(normalizedPath);
        if (removed != null) {
            logger.debug("Unregistered external servlet at: " + normalizedPath);
            // Clear the delegate rather than trying to unbind — Jetty cannot remove a servlet
            // from a started context. The wrapper stays mapped and answers 503 until something
            // registers again, instead of serving a dead plugin's classes (#1604).
            DelegatingServlet wrapper = boundWrappers.get(normalizedPath);
            if (wrapper != null) {
                wrapper.clearDelegate();
                logger.debug("Cleared delegate for " + normalizedPath + " - path now returns 503");
            }
            return true;
        }
        return false;
    }

    /**
     * A permanently-bound servlet whose target can be replaced.
     *
     * <p>Jetty cannot remove a servlet from a started {@code ServletContextHandler}, so anything
     * bound directly is bound for the JVM's lifetime. Binding this wrapper instead means a plugin
     * reload swaps the delegate while the mapping stays put — REST endpoints then pick up new code
     * the same way commands and event handlers already did.
     *
     * <p>Before this, a hot reload left the previous plugin's servlet serving requests while the
     * plugin reported itself enabled: the deployed fix was present in the jar and unreachable.
     *
     * <p>{@code delegate} is volatile because Jetty request threads read it while the server thread
     * swaps it during a reload.
     */
    private final class DelegatingServlet extends HttpServlet {
        private final String pathSpec;
        private volatile HttpServlet delegate;

        DelegatingServlet(String pathSpec) {
            this.pathSpec = pathSpec;
        }

        void setDelegate(HttpServlet next, ServletContextHandler context) {
            if (next != null && context != null) {
                try {
                    // Give the delegate a real ServletConfig — without init() its
                    // getServletContext()/getInitParameter() calls NPE.
                    next.init(new jakarta.servlet.ServletConfig() {
                        @Override public String getServletName() { return pathSpec; }
                        @Override public jakarta.servlet.ServletContext getServletContext() {
                            return context.getServletContext();
                        }
                        @Override public String getInitParameter(String name) { return null; }
                        @Override public java.util.Enumeration<String> getInitParameterNames() {
                            return java.util.Collections.emptyEnumeration();
                        }
                    });
                } catch (Exception e) {
                    logger.error("Failed to init delegate servlet for " + pathSpec, e);
                }
            }
            this.delegate = next;
        }

        void clearDelegate() {
            this.delegate = null;
        }

        @Override
        public void service(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse resp)
                throws jakarta.servlet.ServletException, java.io.IOException {
            HttpServlet current = delegate;
            if (current == null) {
                if (resp instanceof jakarta.servlet.http.HttpServletResponse http) {
                    http.sendError(503, "No handler registered for " + pathSpec);
                }
                return;
            }
            current.service(req, resp);
        }
    }

    @Override
    public boolean isRegistered(String pathSpec) {
        return registeredServlets.containsKey(normalizePath(pathSpec));
    }

    @Override
    public String[] getRegisteredPaths() {
        return registeredServlets.keySet().toArray(new String[0]);
    }

    @Override
    public boolean isServerRunning() {
        return serverRunning;
    }

    @Override
    public String getBaseUrl() {
        if (config.isHttpsEnabled()) {
            return "https://localhost:" + config.getHttpsPort();
        } else {
            return "http://localhost:" + config.getHttpPort();
        }
    }

    @Override
    public int getRegisteredCount() {
        return registeredServlets.size();
    }

    /**
     * Returns the shared {@link AuthFilter}, creating it on first authenticated
     * registration (#1547).
     */
    private AuthFilter authFilter() {
        AuthFilter filter = sharedAuthFilter;
        if (filter == null) {
            synchronized (this) {
                filter = sharedAuthFilter;
                if (filter == null) {
                    filter = new AuthFilter(config, plugin, gson);
                    sharedAuthFilter = filter;
                }
            }
        }
        return filter;
    }

    /**
     * Applies a single servlet registration to the context.
     * When requireAuth is true, the shared AuthFilter is added for the servlet's path.
     */
    private boolean applyRegistration(String pathSpec, ServletRegistration registration) {
        try {
            // Bind a stable wrapper rather than the servlet itself. Jetty can never unbind it,
            // which is fine — the wrapper outlives every plugin reload and only its delegate
            // changes (#1604).
            DelegatingServlet wrapper = new DelegatingServlet(pathSpec);
            wrapper.setDelegate(registration.servlet(), servletContext);
            boundWrappers.put(pathSpec, wrapper);

            ServletHolder holder = new ServletHolder(wrapper);
            holder.setName(registration.displayName() + "_" + pathSpec.hashCode());

            servletContext.addServlet(holder, pathSpec);

            if (registration.requireAuth()) {
                // The blanket mapping in ServletFactory already covers everything under /v1/ and
                // friends, so this registration is redundant for those paths. It is harmless —
                // #1547's request-scoped guard means one authentication and one rate-limit token
                // per request either way — but the overlap was previously invisible and cost real
                // debugging time. Say so rather than registering silently (#1551).
                String covering = org.fourz.rvnkcore.api.security.AuthPathPatterns.coveringPattern(pathSpec);
                if (covering != null) {
                    // #1742: intentional + harmless (the blanket /v1/* AuthFilter already covers this
                    // path, #1551), so log at DEBUG — emitting it at WARN on every boot of every server
                    // was pure noise that trains operators to ignore warnings.
                    logger.debug("Auth filter for " + pathSpec + " is redundant: already covered by"
                            + " the blanket mapping " + covering + " (#1551). Registering anyway —"
                            + " harmless, but the duplicate mapping is intentional to document.");
                }

                FilterHolder filterHolder = new FilterHolder(authFilter());
                filterHolder.setName("auth_" + registration.displayName() + "_" + pathSpec.hashCode());
                servletContext.addFilter(filterHolder, pathSpec,
                        EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
                logger.debug("Auth filter applied for: " + pathSpec);
            }

            // Update registration as applied
            registeredServlets.put(pathSpec, registration.withApplied(true));

            logger.debug("Applied servlet registration: " + registration.displayName() + " at " + pathSpec +
                    (registration.requireAuth() ? " (authenticated)" : " (public)"));
            return true;

        } catch (Exception e) {
            logger.error("Failed to apply servlet registration: " + pathSpec, e);
            return false;
        }
    }

    /**
     * Applies all pending (not yet applied) servlet registrations.
     */
    private void applyPendingRegistrations() {
        int applied = 0;
        int failed = 0;
        
        for (Map.Entry<String, ServletRegistration> entry : registeredServlets.entrySet()) {
            if (!entry.getValue().applied()) {
                if (applyRegistration(entry.getKey(), entry.getValue())) {
                    applied++;
                } else {
                    failed++;
                }
            }
        }
        
        if (applied > 0 || failed > 0) {
            logger.info("Applied " + applied + " pending servlet registrations" + 
                       (failed > 0 ? " (" + failed + " failed)" : ""));
        }
    }

    /**
     * Normalizes a path specification for consistent comparison.
     */
    private String normalizePath(String pathSpec) {
        String path = pathSpec.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /**
     * Gets detailed information about registered servlets for monitoring.
     *
     * @return Map of path to servlet info (display name, auth required, applied status)
     */
    public Map<String, String> getRegistrationDetails() {
        Map<String, String> details = new ConcurrentHashMap<>();
        for (Map.Entry<String, ServletRegistration> entry : registeredServlets.entrySet()) {
            ServletRegistration reg = entry.getValue();
            details.put(entry.getKey(), String.format("%s (auth=%s, applied=%s)", 
                reg.displayName(), reg.requireAuth(), reg.applied()));
        }
        return details;
    }
}
