package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for an Event — admin-editable content with an ordered list of sections.
 *
 * <p>Events replace the legacy static JSON events on the WebUI hub. The
 * authoritative store is the RVNKCore plugin + MySQL; the WebUI reads via the
 * fourzorg-api proxy (DB-direct) and writes through the live plugin so
 * LuckPerms permission checks happen where game state is authoritative.</p>
 *
 * @since 1.5.0
 */
public class EventDTO {

    private String id;
    private String title;
    private String emoji;
    private String category;
    private String status;
    private String intro;
    private String location;
    private Timestamp startAt;
    private Timestamp endAt;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String metadata;
    private String ownerUuid;
    private List<EventSectionDTO> sections;

    public EventDTO() {
        this.sections = new ArrayList<>();
    }

    private EventDTO(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.emoji = b.emoji;
        this.category = b.category;
        this.status = b.status;
        this.intro = b.intro;
        this.location = b.location;
        this.startAt = b.startAt;
        this.endAt = b.endAt;
        this.active = b.active;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
        this.metadata = b.metadata;
        this.ownerUuid = b.ownerUuid;
        this.sections = b.sections != null ? b.sections : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Timestamp getStartAt() { return startAt; }
    public void setStartAt(Timestamp startAt) { this.startAt = startAt; }

    public Timestamp getEndAt() { return endAt; }
    public void setEndAt(Timestamp endAt) { this.endAt = endAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }

    public List<EventSectionDTO> getSections() { return sections; }
    public void setSections(List<EventSectionDTO> sections) {
        this.sections = sections != null ? sections : new ArrayList<>();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String title;
        private String emoji;
        private String category = "general";
        private String status = "draft";
        private String intro;
        private String location;
        private Timestamp startAt;
        private Timestamp endAt;
        private boolean active = true;
        private Timestamp createdAt;
        private Timestamp updatedAt;
        private String metadata;
        private String ownerUuid;
        private List<EventSectionDTO> sections = new ArrayList<>();

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder emoji(String emoji) { this.emoji = emoji; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder intro(String intro) { this.intro = intro; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder startAt(Timestamp startAt) { this.startAt = startAt; return this; }
        public Builder endAt(Timestamp endAt) { this.endAt = endAt; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder createdAt(Timestamp createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder metadata(String metadata) { this.metadata = metadata; return this; }
        public Builder ownerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; return this; }
        public Builder sections(List<EventSectionDTO> sections) { this.sections = sections; return this; }

        public EventDTO build() { return new EventDTO(this); }
    }
}
