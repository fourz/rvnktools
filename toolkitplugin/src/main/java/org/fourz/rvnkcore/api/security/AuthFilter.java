package org.fourz.rvnkcore.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.util.log.LogManager;
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
     * @param config API configuration containing security settings
     * @param plugin Plugin instance for logging
     */
    public AuthFilter(ApiConfig config, Plugin plugin) {
        this.apiKey = config.getApiKey();
        this.allowedIPs = config.getAllowedIPs() != null ? new HashSet<>(Arrays.asList(config.getAllowedIPs())) : new HashSet<>();
        this.ipWhitelistEnabled = !this.allowedIPs.isEmpty();
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Debug logging is configured globally in RVNKCoreBootstrap
        
        // Log whitelist configuration once during initialization
        if (ipWhitelistEnabled) {
            logger.info("IP whitelist enabled with " + this.allowedIPs.size() + " allowed IPs");
        } else {
            logger.info("IP whitelist disabled - allowing all IPs");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIP = getClientIP(httpRequest);
        String method = httpRequest.getMethod();
        String requestURI = httpRequest.getRequestURI();
        
        // Move API request logging to debug level to reduce verbosity
        logger.debug("API Request: " + method + " " + requestURI + " from IP: " + clientIP);
        
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
        
        if (!apiKey.equals(providedKey)) {
            logger.warning("API access denied - Invalid API key '" + providedKey + "' from IP: " + clientIP);
            logger.debug("API key comparison: expected='" + apiKey + "', provided='" + providedKey + "'");
            sendUnauthorized(httpResponse, "Invalid API key");
            return;
        }
        
        // Move successful authentication to debug level
        logger.debug("API authentication successful for IP: " + clientIP);
        
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
