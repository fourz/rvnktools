package org.fourz.rvnktools.announceManager.api;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.util.Debug;
import java.util.logging.Level;

public class DebugLogger extends Debug {
    public DebugLogger(JavaPlugin plugin) {
        super(plugin, "AnnounceAPI", Level.INFO);
    }
}
