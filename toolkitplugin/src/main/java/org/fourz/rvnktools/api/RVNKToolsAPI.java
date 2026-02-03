package org.fourz.rvnktools.api;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.api.config.RestConfig;
import org.fourz.rvnktools.api.server.jetty.JettyServer;

public class RVNKToolsAPI {

    private final JavaPlugin plugin;
    private final AnnounceManager announceManager;
    private JettyServer jettyServer;
    private final RestConfig config;

    public RVNKToolsAPI(JavaPlugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.config = new RestConfig(plugin);
    }

    public void start() {
        if (config.isEnabled()) {
            jettyServer = new JettyServer(announceManager, config, plugin);
            jettyServer.start();
        } else {
        }
    }

    public void stop() {
        if (jettyServer != null) {
            jettyServer.stop();
            jettyServer = null;
        }
    }
}
