package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.util.Debug;
import org.fourz.rvnktools.util.ChatFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnnounceMotd {
    private static final String CLASS_NAME = "AnnounceMotd";
    private final Debug debug;
    private final JavaPlugin plugin;
    private final List<Announcement> motdAnnouncements = new ArrayList<>();
    private String currentMotd = null;
    private final Random random = new Random();
    private final AnnounceConfig config;

    public AnnounceMotd(JavaPlugin plugin, AnnounceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.debug = new Debug(plugin, CLASS_NAME, AnnounceConfig.getLogLevel()) {};
    }

    public void setMotd(List<Announcement> announcements) {
        if (!config.isMotdEnabled()) {
            debug.info("MOTD system is disabled in config");
            return;
        }

        motdAnnouncements.clear();
        for (Announcement announcement : announcements) {
            if ("motd".equalsIgnoreCase(announcement.getType())) {
                motdAnnouncements.add(announcement);
            }
        }
        debug.debug("Loaded " + motdAnnouncements.size() + " MOTD announcements");
        
        if (!motdAnnouncements.isEmpty()) {
            rotateMotd();
        }
    }

    public void rotateMotd() {
        if (motdAnnouncements.isEmpty()) {
            debug.warning("No MOTD announcements available");
            return;
        }

        // Select a random MOTD that's different from the current one
        Announcement newMotd;
        if (motdAnnouncements.size() > 1 && currentMotd != null) {
            do {
                newMotd = motdAnnouncements.get(random.nextInt(motdAnnouncements.size()));
            } while (newMotd.getMessage().equals(currentMotd));
        } else {
            newMotd = motdAnnouncements.get(random.nextInt(motdAnnouncements.size()));
        }

        // Update server MOTD
        //String motdMessage = newMotd.getMessage();
        String motdMessage = ChatFormat.parseMotd(newMotd.getMessage());
        //colorize(newMotd.getMessage());
        currentMotd = newMotd.getMessage();
        
        try {
            // Set the server MOTD
            Bukkit.getServer().setMotd(motdMessage);
            debug.info("Updated server MOTD: " + motdMessage);
        } catch (Exception e) {
            debug.error("Failed to set server MOTD", e);
        }
    }

    public String getCurrentMotd() {
        return currentMotd;
    }

    public List<Announcement> getMotdAnnouncements() {
        return new ArrayList<>(motdAnnouncements);
    }

    public boolean isEnabled() {
        return config.isMotdEnabled();
    }

    public boolean shouldScheduleBroadcast() {
        return config.isMotdEnabled() && config.isMotdScheduleBroadcast();
    }
}
