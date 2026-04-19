package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * DTO for a single content section within an Event.
 *
 * Events have an ordered list of sections, each with an optional heading and
 * sanitized HTML body. Body HTML is produced by a TipTap WYSIWYG in the WebUI
 * and sanitized server-side with jsoup before persistence.
 *
 * @since 1.5.0
 */
public class EventSectionDTO {

    private String id;
    private String eventId;
    private int position;
    private String heading;
    private String bodyHtml;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public EventSectionDTO() {
    }

    private EventSectionDTO(Builder b) {
        this.id = b.id;
        this.eventId = b.eventId;
        this.position = b.position;
        this.heading = b.heading;
        this.bodyHtml = b.bodyHtml;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }

    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String eventId;
        private int position = 0;
        private String heading;
        private String bodyHtml = "";
        private Timestamp createdAt;
        private Timestamp updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder position(int position) { this.position = position; return this; }
        public Builder heading(String heading) { this.heading = heading; return this; }
        public Builder bodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; return this; }
        public Builder createdAt(Timestamp createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; return this; }

        public EventSectionDTO build() { return new EventSectionDTO(this); }
    }
}
