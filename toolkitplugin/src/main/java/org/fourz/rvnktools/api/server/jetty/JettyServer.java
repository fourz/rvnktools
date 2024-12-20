package org.fourz.rvnktools.api.server.jetty;

import com.google.gson.Gson;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.api.config.RestConfig;
import org.fourz.rvnktools.api.security.ApiKeyAuthFilter;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
import java.io.BufferedReader;
import java.io.IOException;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.fourz.rvnktools.util.Debug;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class JettyServer {
    private Server server;
    private final AnnounceManager announceManager;
    private final Gson gson = new Gson();
    private final RestConfig config;
    private final JettyServerDebug debug;
    private final JavaPlugin plugin;

    private class JettyServerDebug extends Debug {
        public JettyServerDebug(JavaPlugin plugin) {
            super(plugin, "JettyServer", Level.INFO);
        }
    }

    public JettyServer(AnnounceManager announceManager, RestConfig config, JavaPlugin plugin) {
        this.announceManager = announceManager;
        this.config = config;
        this.plugin = plugin;
        this.debug = new JettyServerDebug(plugin);
    }

    private boolean validateTlsConfig() {
        if (!config.isTlsEnabled()) {
            return true;
        }

        boolean isValid = true;
        if (config.getKeystorePath().equals("keystore.jks")) {
            debug.warning("Using default keystore path. Please configure a proper keystore for HTTPS.");
            isValid = false;
        }
        if (config.getKeystorePassword().equals("changeme")) {
            debug.warning("Using default keystore password. Please configure a secure password for HTTPS.");
            isValid = false;
        }
        if (config.getKeyManagerPassword().equals("changeme")) {
            debug.warning("Using default key manager password. Please configure a secure password for HTTPS.");
            isValid = false;
        }
        
        if (!isValid) {
            debug.warning("HTTPS is enabled but using default configuration values. This is not recommended for production use.");
            debug.warning("Please generate a proper keystore using:");
            debug.warning("keytool -genkeypair -alias jetty -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 365");
        }
        
        return isValid;
    }

    public void start() {
        // Configure Jetty logging before creating server
        org.eclipse.jetty.util.log.Log.setLog(new org.eclipse.jetty.util.log.Logger() {
            public String getName() { return "Jetty"; }
            
            public void warn(String msg, Object... args) { 
                if (msg != null) debug.warning(formatMessage(msg, args)); 
            }
            
            public void warn(Throwable thrown) { 
                if (thrown != null) debug.warning(thrown.getMessage()); 
            }
            
            public void warn(String msg, Throwable thrown) { 
                debug.warning(msg + ": " + (thrown != null ? thrown.getMessage() : "")); 
            }
            
            public void info(String msg, Object... args) { 
                if (msg != null) {
                    String formatted = formatMessage(msg, args);
                    // Filter out noisy startup messages but log everything else
                    if (!formatted.contains("Started @")) {
                        debug.debug(formatted);
                    }
                }
            }
            
            public void info(Throwable thrown) { 
                if (thrown != null) debug.debug(thrown.getMessage()); 
            }
            
            public void info(String msg, Throwable thrown) { 
                String message = msg;
                if (thrown != null) message += ": " + thrown.getMessage();
                debug.debug(message);
            }

            private String formatMessage(String msg, Object... args) {
                try {
                    return String.format(msg, args);
                } catch (Exception e) {
                    return msg;
                }
            }
            
            // Required no-op methods
            public boolean isDebugEnabled() { return true; }
            public void setDebugEnabled(boolean enabled) {}
            public void debug(String msg, Object... args) {}
            public void debug(String msg, long value) {}
            public void debug(String msg, Throwable thrown) {}
            public void debug(Throwable thrown) {}
            public boolean isWarnEnabled() { return true; }
            public org.eclipse.jetty.util.log.Logger getLogger(String name) { return this; }
            public void ignore(Throwable ignored) {}
            public void debug(String msg, Object arg0, Object... args) {}
        });

        server = new Server();
        
        // HTTP Configuration
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(config.isSendServerVersion());
        httpConfig.setSendDateHeader(config.isSendDateHeader());
        httpConfig.setRequestHeaderSize(config.getRequestHeaderSize());
        httpConfig.setResponseHeaderSize(config.getResponseHeaderSize());

        // Add HTTP connector
        ServerConnector connector = new ServerConnector(server,
                new HttpConnectionFactory(httpConfig));
        connector.setPort(config.getPort());
        connector.setIdleTimeout(config.getIdleTimeout());

        // Add HTTPS connector if TLS is enabled
        if (config.isTlsEnabled()) {
            validateTlsConfig();
            
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
            sslContextFactory.setKeyManagerPassword(config.getKeyManagerPassword());

            ServerConnector sslConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig));
            sslConnector.setPort(config.getHttpsPort());
            sslConnector.setIdleTimeout(config.getIdleTimeout());

            server.addConnector(sslConnector);
        }

        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        
        // Add authentication filter
        context.addFilter(new FilterHolder(new ApiKeyAuthFilter(config.getApiKey())), "/api/*", null);
        
        // Add servlets
        context.addServlet(new ServletHolder(new AnnouncementServlet()), "/api/announcements/*");
        context.addServlet(new ServletHolder(new StatusServlet()), "/api/status");
        
        server.setHandler(context);

        try {
            server.start();
            debug.info("HTTP server started on port " + config.getPort());
            if (config.isTlsEnabled()) {
                debug.info("HTTPS server started on port " + config.getHttpsPort());
            }
        } catch (Exception e) {
            debug.error("Failed to start server", e);
        }
    }

    private class AnnouncementServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                // Add announcement
                BufferedReader reader = req.getReader();
                Announcement announcement = gson.fromJson(reader, Announcement.class);
                
                boolean success = announceManager.addAnnouncement(announcement);
                
                resp.setContentType("application/json");
                if (success) {
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                    resp.getWriter().println("{\"status\":\"success\",\"message\":\"Announcement created\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{\"status\":\"error\",\"message\":\"Failed to create announcement\"}");
                }
            }
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.length() > 1) {
                String id = pathInfo.substring(1); // Remove leading slash
                boolean success = announceManager.deleteAnnouncement(id);
                
                resp.setContentType("application/json");
                if (success) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().println("{\"status\":\"success\",\"message\":\"Announcement deleted\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().println("{\"status\":\"error\",\"message\":\"Announcement not found\"}");
                }
            }
        }
    }

    private class StatusServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println("{ \"status\": \"running\", \"message\": \"AnnounceManagerAPI is running\" }");
        }
    }

    public void stop() {
        if (server != null) {
            try {
                server.stop();
                server.join();
                debug.info("HTTP server stopped");
            } catch (Exception e) {
                debug.error("Failed to stop server", e);
            }
        }
    }
}
