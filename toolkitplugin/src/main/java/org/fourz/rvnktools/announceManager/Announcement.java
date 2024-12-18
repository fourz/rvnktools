package org.fourz.rvnktools.announceManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Announcement {
    private String id;
    private String message;
    private long interval;
    private boolean enabled;
    private String type;
    private Long recurrence;
    private String recurrenceString;
    private String owner;
    private String permission;
    private LocalDate date;
    private LocalTime time;
    private LocalDateTime expiration;
    private boolean imported;

    public Announcement() { 
        this.type = "system";   
        this.imported = false;  // Initialize as not imported
    }

    // Getters and Setters
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public long getInterval() {
        return interval;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        if (type != null) {
            this.type = type;            
        } else {
            this.type = "system";
        }
        return;
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
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public String getPermission() {
        return permission;
    }
    public void setPermission(String permission) {
        this.permission = permission;
    }
    public LocalDate getDate() {
        return date;
    }
    public void setDate(LocalDate date) {
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
    
    public boolean isImported() {
        return imported;
    }
    
    public void setImported() {
        this.imported = true;
    }
}
