package org.fourz.rvnkcore.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Authentication filter for RVNKCore API endpoints.
 * Implements API key authentication and IP-based access control.
 */
public class AuthFilter implements Filter {
    private final String apiKey;
    private final Set<String> allowedIPs;
    private final LogManager logger;
    private final boolean ipWhitelistEnabled;

    /**
     * Creates an AuthFilter with API key authentication.
     *
     * @param apiKey The required API key for authentication
     * @param allowedIPs Array of allowed IP addresses (empty for no IP filtering)
     * @param plugin Plugin instance for logging
     */
    public AuthFilter(String apiKey, String[] allowedIPs, Plugin plugin) {
        this.apiKey = apiKey;
        this.allowedIPs = allowedIPs != null ? new HashSet<>(Arrays.asList(allowedIPs)) : new HashSet<>();
        this.ipWhitelistEnabled = !this.allowedIPs.isEmpty();
        this.logger = LogManager.getInstance(plugin);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIP = getClientIP(httpRequest);
        String method = httpRequest.getMethod();
        String requestURI = httpRequest.getRequestURI();
        
        logger.info("API Request: " + method + " " + requestURI + " from IP: " + clientIP);
        
        // Check IP whitelist if enabled
        if (ipWhitelistEnabled) {
            if (!allowedIPs.contains(clientIP)) {
                logger.warning("API access denied for IP: " + clientIP + " (not in whitelist: " + allowedIPs + ")");
                sendUnauthorized(httpResponse, "IP not allowed");
                return;
            } else {
                logger.info("IP whitelist check passed for: " + clientIP);
            }
        } else {
            logger.info("IP whitelist disabled - allowing all IPs");
        }
        
        // Check API key
        String providedKey = httpRequest.getHeader("X-API-Key");
        if (providedKey == null) {
            logger.warning("API access denied - No API key provided from IP: " + clientIP);
            sendUnauthorized(httpResponse, "Missing API key");
            return;
        }
        
        if (!apiKey.equals(providedKey)) {
            logger.warning("API access denied - Invalid API key '" + providedKey + "' from IP: " + clientIP);
            sendUnauthorized(httpResponse, "Invalid API key");
            return;
        }
        
        logger.info("API authentication successful for IP: " + clientIP);
        
        // Authentication successful, continue with request
        chain.doFilter(request, response);
    }

    /**
     * Extracts client IP address, handling forwarded headers.
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Sends unauthorized response with error message.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\", \"status\": 401}");
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
