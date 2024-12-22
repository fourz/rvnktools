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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Long getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(Long recurrence) {
        this.recurrence = recurrence;
    }

    public String getRecurrenceString() {
        return recurrenceString;
    }

    public void setRecurrenceString(String recurrenceString) {
        this.recurrenceString = recurrenceString;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDateTime expiration) {
        this.expiration = expiration;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
