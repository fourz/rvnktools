package org.fourz.rvnktools.announceManager;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
//import org.springframework.context.annotation.AnnotationConfigApplicationContext;
//import org.fourz.rvnktools.announceManager.api.SpringBootServer;

public class AnnounceREST {
    //private final SpringBootServer springBootServer;
    private final Plugin plugin;
    private final AnnounceManager announceManager;
    //private final AnnounceRESTDebug debug;

    public AnnounceREST(Plugin plugin, AnnounceManager announceManager) {
        this.plugin = plugin;
        this.announceManager = announceManager;
        //this.debug = new AnnounceRESTDebug((JavaPlugin)plugin);

    }

    public void start() {

    }

    public void stop() {

    }
}
