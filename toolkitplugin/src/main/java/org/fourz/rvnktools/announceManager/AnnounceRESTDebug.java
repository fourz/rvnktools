package org.fourz.rvnktools.announceManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.util.Debug;
import java.util.logging.Level;

public class AnnounceRESTDebug extends Debug {
    public AnnounceRESTDebug(JavaPlugin plugin) {
        super(plugin, "AnnounceREST", Level.INFO);
    }
}
