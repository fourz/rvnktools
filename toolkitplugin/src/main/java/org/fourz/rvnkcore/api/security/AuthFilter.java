package org.fourz.rvnkcore.api.security;

import com.google.gson.Gson;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.util.ApiUtils;
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
    private final Gson gson;

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
            logger.warning("API access denied - Invalid API key from IP: " + clientIP);
            logger.debug("API key mismatch: provided key length=" + (providedKey != null ? providedKey.length() : 0));
            sendUnauthorized(httpResponse, "Invalid API key");
            return;
        }
        
        // Move successful authentication to debug level
        logger.debug("API authentication successful for IP: " + clientIP);
        
        // Authentication successful, continue with request
        chain.doFilter(request, response);
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
