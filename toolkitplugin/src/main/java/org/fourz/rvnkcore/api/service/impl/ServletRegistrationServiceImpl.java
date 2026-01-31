package org.fourz.rvnkcore.api.service.impl;

import jakarta.servlet.http.HttpServlet;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.service.IServletRegistrationService;
import org.fourz.rvnkcore.util.log.LogManager;

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
    
    // Thread-safe registry of external servlets
    private final Map<String, ServletRegistration> registeredServlets = new ConcurrentHashMap<>();
    
    // Reference to the servlet context (set when server starts)
    private volatile ServletContextHandler servletContext;
    
    // Server running state
    private volatile boolean serverRunning = false;

    /**
     * Internal record to track servlet registrations.
     */
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
     */
    public ServletRegistrationServiceImpl(ApiConfig config, Plugin plugin) {
        this.config = config;
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
        
        // Check if already registered
        if (registeredServlets.containsKey(normalizedPath)) {
            logger.warning("Servlet already registered at path: " + normalizedPath);
            return false;
        }
        
        // Create registration
        ServletRegistration registration = new ServletRegistration(servlet, displayName, requireAuth, false);
        registeredServlets.put(normalizedPath, registration);
        
        logger.info("Registered external servlet: " + displayName + " at " + normalizedPath + 
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
            logger.info("Unregistered external servlet at: " + normalizedPath);
            // Note: Actual servlet removal from Jetty requires server restart
            if (removed.applied()) {
                logger.warning("Servlet was active - full removal requires server restart");
            }
            return true;
        }
        return false;
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
     * Applies a single servlet registration to the context.
     */
    private boolean applyRegistration(String pathSpec, ServletRegistration registration) {
        try {
            ServletHolder holder = new ServletHolder(registration.servlet());
            holder.setName(registration.displayName() + "_" + pathSpec.hashCode());
            
            servletContext.addServlet(holder, pathSpec);
            
            // Update registration as applied
            registeredServlets.put(pathSpec, registration.withApplied(true));
            
            logger.debug("Applied servlet registration: " + pathSpec);
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
