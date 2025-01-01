package org.fourz.rvnktools.api.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class CreateAnnouncementRequest {
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

    // Required field getters
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    // Optional field getters
    public String getPermission() {
        return permission;
    }

    public Long getRecurrence() {
        return recurrence;
    }

    public String getRecurrenceString() {
        return recurrenceString;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    public String getOwner() {
        return owner;
    }

    public boolean isValid() {
        return id != null && !id.isEmpty() 
            && type != null && !type.isEmpty()
            && message != null && !message.isEmpty();
    }
}
