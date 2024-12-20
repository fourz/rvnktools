package org.fourz.rvnktools.api;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

import org.fourz.rvnktools.util.Debug;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.api.config.RestConfig;
import org.fourz.rvnktools.api.server.jetty.JettyServer;

public class RVNKToolsAPI {

    private final JavaPlugin plugin;
    private final AnnounceManager announceManager;
    private final APIDebug debug;
    private JettyServer jettyServer;
    private final RestConfig config;

    public RVNKToolsAPI(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.debug = new APIDebug((JavaPlugin)plugin);
        this.config = new RestConfig(plugin);
    }

    class APIDebug extends Debug {
        public APIDebug(JavaPlugin plugin) {
            super(plugin, "RVNKToolsAPI", Level.INFO);
        }
    }

    public void start() {
        if (config.isEnabled()) {
            jettyServer = new JettyServer(announceManager, config, plugin);
            jettyServer.start();
            debug.info("API server initialized"); // Changed message to avoid duplication
        } else {
            debug.info("API server disabled by configuration");
        }
    }

    public void stop() {
        if (jettyServer != null) {
            jettyServer.stop();
            jettyServer = null;
            debug.info("API server stopped");
        }
    }
}
