package org.fourz.rvnktools.announceManager;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.fourz.rvnktools.announceManager.api.SpringBootServer;

public class AnnounceREST {
    private final SpringBootServer springBootServer;
    private final Plugin plugin;
    private final AnnounceManager announceManager;
    private final AnnounceRESTDebug debug;

    public AnnounceREST(Plugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        this.debug = new AnnounceRESTDebug((JavaPlugin)plugin);
        this.springBootServer = new SpringBootServer(announceManager, debug);
    }

    public void start() {
        try {
            debug.debug("Creating Spring application context");
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            
            debug.debug("Registering AnnounceManager bean");
            context.getBeanFactory().registerSingleton("announceManager", announceManager);
            
            debug.debug("Refreshing context");
            context.refresh();
            
            debug.debug("Starting SpringBootServer");
            springBootServer.start(context);
            debug.log("Announce REST API server started on port 8080");
        } catch (Exception e) {
            debug.error("Failed to start REST API server", e);
            debug.debug("Full exception trace:");
            for (StackTraceElement element : e.getStackTrace()) {
                debug.debug("  at " + element.toString());
            }
        }
    }

    public void stop() {
        try {
            if (springBootServer != null) {
                springBootServer.stop();
                debug.log("Announce REST API server stopped");
            }
        } catch (Exception e) {
            debug.error("Error stopping REST API server", e);
        }
    }
}
