package org.fourz.rvnktools.api.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class AnnouncementDTO {
    private String id;
    private String type;
    private String message;
    private String permission;
    private Long recurrence;
    private String recurrenceString;
    private LocalDateTime date;
    private LocalTime time;
    private LocalDateTime expiration;
    private String owner;

    // Add getters/setters
}
