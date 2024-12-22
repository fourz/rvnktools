package org.fourz.rvnktools.api.model;

public class CreateAnnouncementRequest {
    private String id;
    private String type;
    private String message;

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isValid() {
        return id != null && !id.isEmpty() 
            && type != null && !type.isEmpty()
            && message != null && !message.isEmpty();
    }
}
