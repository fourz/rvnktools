package org.fourz.rvnktools.webserver;

import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

import java.nio.file.Paths;

public class WebServerPlugin extends JavaPlugin {
    private Server server;

    @Override
    public void onEnable() {
        getLogger().info("WebServerPlugin enabled!");

        // Start the embedded HTTPS server
        try {
            startWebServer();
        } catch (Exception e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("WebServerPlugin disabled!");

        // Stop the web server
        if (server != null && server.isRunning()) {
            try {
                server.stop();
            } catch (Exception e) {
                getLogger().severe("Failed to stop web server: " + e.getMessage());
            }
        }
    }

    private void startWebServer() throws Exception {
        // Initialize Jetty server on port 8443 for HTTPS
        server = new Server();

        // SSL setup (Replace with valid certificate path and password)
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(Paths.get("plugins/your_plugin/keystore.jks").toString());
        sslContextFactory.setKeyStorePassword("password");
        sslContextFactory.setKeyManagerPassword("password");

        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new org.eclipse.jetty.server.SecureRequestCustomizer());

        // SSL Connector
        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https));
        sslConnector.setPort(8443);

        // Add connector to server
        server.addConnector(sslConnector);

        // Set up context for Next.js app
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase("plugins/your_plugin/web");

        // Start the server
        server.setHandler(context);
        server.start();
    }
}
