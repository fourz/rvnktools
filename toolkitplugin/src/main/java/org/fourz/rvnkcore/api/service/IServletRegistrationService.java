package org.fourz.rvnkcore.api.service;

import jakarta.servlet.http.HttpServlet;

/**
 * Service interface for registering external plugin HTTP servlets with RVNKCore's Jetty server.
 *
 * <p>This service enables other plugins to expose REST API endpoints through RVNKCore's
 * centralized HTTP server, providing consistent authentication, CORS, and SSL handling.</p>
 *
 * <h2>Usage Example (RVNKWorlds):</h2>
 * <pre>{@code
 * // In RVNKWorlds.onEnable()
 * IServletRegistrationService servletService = RVNKCore.getService(IServletRegistrationService.class);
 * if (servletService != null && servletService.isServerRunning()) {
 *     servletService.registerServlet("/api/rvnkworlds/*", new RVNKWorldsServlet(worldApiEndpoint));
 * }
 * }</pre>
 *
 * <h2>Authentication:</h2>
 * <p>By default, all registered servlets are protected by the X-API-Key authentication filter.
 * Use {@link #registerServlet(String, HttpServlet, boolean)} with {@code requireAuth=false}
 * for public endpoints.</p>
 *
 * <h2>Path Specification:</h2>
 * <ul>
 *   <li>Use prefix paths ending with /* for servlet groups: {@code /api/rvnkworlds/*}</li>
 *   <li>Use exact paths for single endpoints: {@code /api/rvnkworlds/health}</li>
 *   <li>Paths are relative to the server's context path (default: /)</li>
 * </ul>
 *
 * @since 1.4.0
 * @see org.fourz.rvnkcore.api.server.jetty.CoreServer
 */
public interface IServletRegistrationService {

    /**
     * Registers an HTTP servlet with API key authentication enabled.
     *
     * <p>The servlet will be protected by the X-API-Key authentication filter.
     * Requests without a valid API key will receive a 401 Unauthorized response.</p>
     *
     * @param pathSpec The URL pattern for the servlet (e.g., "/api/rvnkworlds/*")
     * @param servlet The HttpServlet instance to register
     * @return true if registration succeeded, false if path already registered or server not running
     * @throws IllegalArgumentException if pathSpec is null/empty or servlet is null
     */
    boolean registerServlet(String pathSpec, HttpServlet servlet);

    /**
     * Registers an HTTP servlet with configurable authentication.
     *
     * @param pathSpec The URL pattern for the servlet (e.g., "/api/rvnkworlds/*")
     * @param servlet The HttpServlet instance to register
     * @param requireAuth If true, requires X-API-Key authentication; if false, endpoint is public
     * @return true if registration succeeded, false if path already registered or server not running
     * @throws IllegalArgumentException if pathSpec is null/empty or servlet is null
     */
    boolean registerServlet(String pathSpec, HttpServlet servlet, boolean requireAuth);

    /**
     * Registers an HTTP servlet with a custom display name for logging.
     *
     * <p>The display name is used in log messages and monitoring to identify the servlet source.</p>
     *
     * @param pathSpec The URL pattern for the servlet (e.g., "/api/rvnkworlds/*")
     * @param servlet The HttpServlet instance to register
     * @param displayName A human-readable name for the servlet (e.g., "RVNKWorlds API")
     * @param requireAuth If true, requires X-API-Key authentication; if false, endpoint is public
     * @return true if registration succeeded, false if path already registered or server not running
     * @throws IllegalArgumentException if pathSpec is null/empty or servlet is null
     */
    boolean registerServlet(String pathSpec, HttpServlet servlet, String displayName, boolean requireAuth);

    /**
     * Unregisters a previously registered servlet.
     *
     * <p>Note: Due to Jetty's architecture, unregistering a servlet may require a server restart
     * to fully take effect. The servlet will be removed from the internal registry immediately,
     * but active connections may continue to be handled until restart.</p>
     *
     * @param pathSpec The URL pattern of the servlet to unregister
     * @return true if the servlet was found and marked for removal, false if not found
     */
    boolean unregisterServlet(String pathSpec);

    /**
     * Checks if a servlet is registered at the specified path.
     *
     * @param pathSpec The URL pattern to check
     * @return true if a servlet is registered at this path, false otherwise
     */
    boolean isRegistered(String pathSpec);

    /**
     * Gets the list of all registered external servlet paths.
     *
     * <p>This only returns paths registered by external plugins, not internal RVNKCore servlets.</p>
     *
     * @return Array of registered path specifications
     */
    String[] getRegisteredPaths();

    /**
     * Checks if the HTTP server is running and accepting registrations.
     *
     * <p>Plugins should check this before attempting to register servlets.</p>
     *
     * @return true if the server is running, false otherwise
     */
    boolean isServerRunning();

    /**
     * Gets the base URL for the API server.
     *
     * <p>Useful for plugins that need to construct full URLs for documentation or linking.</p>
     *
     * @return The base URL (e.g., "https://localhost:8443" or "http://localhost:8080")
     */
    String getBaseUrl();

    /**
     * Gets the number of externally registered servlets.
     *
     * @return Count of registered external servlets
     */
    int getRegisteredCount();
}
