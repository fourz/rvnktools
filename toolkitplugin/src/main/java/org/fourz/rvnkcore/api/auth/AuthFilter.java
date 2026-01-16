package org.fourz.rvnkcore.api.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.fourz.rvnkcore.api.exception.AuthorizationException;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servlet filter for JWT authentication.
 *
 * Intercepts requests and validates JWT tokens in the Authorization header.
 * Supports configurable paths that bypass authentication.
 */
public class AuthFilter implements Filter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ATTR_PLUGIN_ID = "pluginId";
    private static final String ATTR_PERMISSIONS = "permissions";

    private final JwtService jwtService;
    private final Set<String> publicPaths;
    private final Set<String> publicPrefixes;

    /**
     * Creates an AuthFilter with the given JWT service.
     *
     * @param jwtService The JWT service for token validation
     */
    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
        this.publicPaths = new HashSet<>();
        this.publicPrefixes = new HashSet<>();

        // Default public paths
        addPublicPath("/api/health");
        addPublicPath("/api/auth/token");
        addPublicPrefix("/api/public/");
    }

    /**
     * Adds a path that doesn't require authentication.
     *
     * @param path The exact path to make public
     */
    public void addPublicPath(String path) {
        publicPaths.add(path);
    }

    /**
     * Adds a path prefix where all sub-paths are public.
     *
     * @param prefix The path prefix (e.g., "/api/public/")
     */
    public void addPublicPrefix(String prefix) {
        publicPrefixes.add(prefix);
    }

    /**
     * Removes a public path.
     *
     * @param path The path to remove
     */
    public void removePublicPath(String path) {
        publicPaths.remove(path);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Check if path is public
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Get Authorization header
        String authHeader = httpRequest.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(httpResponse, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // Validate token
            DecodedJWT jwt = jwtService.validateToken(token);

            // Check that it's an access token, not a refresh token
            String type = jwt.getClaim("type").asString();
            if (!"access".equals(type)) {
                sendUnauthorized(httpResponse, "Invalid token type");
                return;
            }

            // Set attributes for downstream handlers
            request.setAttribute(ATTR_PLUGIN_ID, jwt.getSubject());
            request.setAttribute(ATTR_PERMISSIONS, jwt.getClaim("permissions").asList(String.class));

            // Continue with the request
            chain.doFilter(request, response);

        } catch (AuthorizationException e) {
            sendUnauthorized(httpResponse, e.getMessage());
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    /**
     * Checks if a path requires authentication.
     *
     * @param path The request path
     * @return true if the path is public (no auth required)
     */
    private boolean isPublicPath(String path) {
        if (publicPaths.contains(path)) {
            return true;
        }

        for (String prefix : publicPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sends an unauthorized response.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"error\": \"UNAUTHORIZED\", \"message\": \"%s\"}", message));
    }

    // ========== Static Utility Methods ==========

    /**
     * Gets the authenticated plugin ID from a request.
     *
     * @param request The servlet request
     * @return The plugin ID, or null if not authenticated
     */
    public static String getPluginId(ServletRequest request) {
        return (String) request.getAttribute(ATTR_PLUGIN_ID);
    }

    /**
     * Gets the authenticated permissions from a request.
     *
     * @param request The servlet request
     * @return The permissions list, or null if not authenticated
     */
    @SuppressWarnings("unchecked")
    public static List<String> getPermissions(ServletRequest request) {
        return (List<String>) request.getAttribute(ATTR_PERMISSIONS);
    }

    /**
     * Checks if a request has a specific permission.
     *
     * @param request The servlet request
     * @param permission The permission to check
     * @return true if the request has the permission
     */
    public static boolean hasPermission(ServletRequest request, String permission) {
        List<String> permissions = getPermissions(request);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Requires a specific permission, throwing if not present.
     *
     * @param request The servlet request
     * @param permission The required permission
     * @throws AuthorizationException if permission is missing
     */
    public static void requirePermission(ServletRequest request, String permission)
            throws AuthorizationException {
        if (!hasPermission(request, permission)) {
            throw AuthorizationException.forbidden(
                ((HttpServletRequest) request).getRequestURI(), permission);
        }
    }
}
