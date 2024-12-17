
package org.fourz.rvnktools.announceManager.api;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnounceManager announceManager;

    public AnnouncementController(AnnounceManager announceManager) {
        this.announceManager = announceManager;
    }

    @PostMapping
    public String createAnnouncement(@RequestBody Announcement announcement) {
        boolean result = announceManager.addAnnouncement(announcement);
        if (result) {
            return "Announcement created with ID: " + announcement.getId();
        } else {
            return "Failed to create announcement";
        }
    }
}