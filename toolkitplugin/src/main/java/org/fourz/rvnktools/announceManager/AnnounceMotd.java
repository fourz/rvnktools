package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.log.RVNKLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnnounceMotd {
    private final RVNKLogger logger;
    private final List<Announcement> motdAnnouncements = new ArrayList<>();
    private String currentMotd = null;
    private final Random random = new Random();
    private final AnnounceConfig config;

    public AnnounceMotd(JavaPlugin plugin, AnnounceConfig config) {
        this.config = config;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    public void setMotd(List<Announcement> announcements) {
        if (!config.isMotdEnabled()) {
            logger.info("MOTD system is disabled in config");
            return;
        }

        motdAnnouncements.clear();
        for (Announcement announcement : announcements) {
            if ("motd".equalsIgnoreCase(announcement.getType())) {
                motdAnnouncements.add(announcement);
            }
        }
        logger.debug("Loaded " + motdAnnouncements.size() + " MOTD announcements");
        
        if (!motdAnnouncements.isEmpty()) {
            rotateMotd();
        }
    }

    public void rotateMotd() {
        if (motdAnnouncements.isEmpty()) {
            logger.warning("No MOTD announcements available");
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
        String motdMessage = ChatFormat.parseMotd(newMotd.getMessage());
        currentMotd = newMotd.getMessage();
        
        try {
            // Set the server MOTD
            Bukkit.getServer().setMotd(motdMessage);
            logger.info("Updated server MOTD: " + motdMessage);
        } catch (Exception e) {
            logger.error("Failed to set server MOTD", e);
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
