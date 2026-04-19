package org.fourz.rvnkcore.service.events;

import org.fourz.rvnkcore.api.model.EventDTO;
import org.fourz.rvnkcore.api.model.EventSectionDTO;
import org.fourz.rvnkcore.api.service.EventService;
import org.fourz.rvnkcore.api.webhook.WebhookNotifier;
import org.fourz.rvnkcore.database.repository.EventRepository;
import org.fourz.rvnkcore.database.repository.EventSectionRepository;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of EventService.
 *
 * <p>All mutating operations sanitize {@code bodyHtml} through jsoup Cleaner to
 * strip scripts/event handlers before persistence. TipTap output is already
 * clean but trust-at-boundary is enforced here.</p>
 *
 * <p>Webhook notifications are fired on every mutation for cache invalidation on
 * the WebUI side. The WebhookNotifier is resolved lazily from ServiceRegistry
 * because it is registered after core services during initialization.</p>
 *
 * @since 1.5.0
 */
public class DefaultEventService implements EventService {

    private final EventRepository eventRepo;
    private final EventSectionRepository sectionRepo;
    private final LogManager logger;
    private final ServiceRegistry serviceRegistry;

    private static final Safelist SAFE_LIST = Safelist.basic()
        .addTags("h1", "h2", "h3", "u", "s")
        .addAttributes("a", "class")
        .addAttributes("code", "class");

    public DefaultEventService(EventRepository eventRepo,
                               EventSectionRepository sectionRepo,
                               LogManager logger,
                               ServiceRegistry serviceRegistry) {
        this.eventRepo = eventRepo;
        this.sectionRepo = sectionRepo;
        this.logger = logger;
        this.serviceRegistry = serviceRegistry;
        logger.info("DefaultEventService initialized");
    }

    @Override
    public CompletableFuture<List<EventDTO>> getAllEvents() {
        return eventRepo.findAll().thenCompose(this::attachSectionsToAll);
    }

    @Override
    public CompletableFuture<List<EventDTO>> getActiveEvents() {
        return eventRepo.findActive().thenCompose(this::attachSectionsToAll);
    }

    @Override
    public CompletableFuture<List<EventDTO>> getEventsByStatus(String status) {
        return eventRepo.findByStatus(status).thenCompose(this::attachSectionsToAll);
    }

    @Override
    public CompletableFuture<Optional<EventDTO>> getEvent(String id) {
        return eventRepo.findById(id).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
            EventDTO event = opt.get();
            return sectionRepo.findByEventId(id).thenApply(sections -> {
                event.setSections(sections);
                return Optional.of(event);
            });
        });
    }

    @Override
    public CompletableFuture<EventDTO> createEvent(EventDTO event) {
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Event title cannot be null or empty");
        }
        if (event.getId() == null || event.getId().isEmpty()) {
            event.setId(UUID.randomUUID().toString());
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (event.getCreatedAt() == null) event.setCreatedAt(now);
        event.setUpdatedAt(now);

        List<EventSectionDTO> sections = event.getSections() != null ? event.getSections() : new ArrayList<>();
        sanitizeSections(event.getId(), sections);

        return eventRepo.save(event)
            .thenCompose(saved -> persistSections(saved.getId(), sections).thenApply(v -> saved))
            .whenComplete((saved, err) -> {
                if (err == null) notifyWebhook(saved.getId());
            });
    }

    @Override
    public CompletableFuture<EventDTO> updateEvent(EventDTO event) {
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("Event and ID required for update");
        }
        event.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        List<EventSectionDTO> sections = event.getSections() != null ? event.getSections() : new ArrayList<>();
        sanitizeSections(event.getId(), sections);

        return eventRepo.save(event)
            .thenCompose(saved -> persistSections(saved.getId(), sections).thenApply(v -> saved))
            .whenComplete((saved, err) -> {
                if (err == null) notifyWebhook(saved.getId());
            });
    }

    @Override
    public CompletableFuture<Void> deleteEvent(String id) {
        return eventRepo.softDelete(id)
            .whenComplete((v, err) -> {
                if (err == null) notifyWebhook(id);
            });
    }

    @Override
    public CompletableFuture<EventSectionDTO> addSection(String eventId, EventSectionDTO section) {
        section.setEventId(eventId);
        if (section.getId() == null || section.getId().isEmpty()) {
            section.setId(UUID.randomUUID().toString());
        }
        section.setBodyHtml(sanitize(section.getBodyHtml()));
        return sectionRepo.save(section)
            .whenComplete((s, err) -> { if (err == null) notifyWebhook(eventId); });
    }

    @Override
    public CompletableFuture<EventSectionDTO> updateSection(EventSectionDTO section) {
        section.setBodyHtml(sanitize(section.getBodyHtml()));
        return sectionRepo.save(section)
            .whenComplete((s, err) -> { if (err == null) notifyWebhook(section.getEventId()); });
    }

    @Override
    public CompletableFuture<Void> deleteSection(String sectionId) {
        return sectionRepo.deleteById(sectionId)
            .whenComplete((v, err) -> { if (err == null) notifyWebhook(null); });
    }

    @Override
    public CompletableFuture<Void> replaceSections(String eventId, List<EventSectionDTO> sections) {
        sanitizeSections(eventId, sections);
        return persistSections(eventId, sections)
            .whenComplete((v, err) -> { if (err == null) notifyWebhook(eventId); });
    }

    // --- helpers ---

    private CompletableFuture<Void> persistSections(String eventId, List<EventSectionDTO> sections) {
        return sectionRepo.deleteByEventId(eventId).thenCompose(v -> {
            if (sections == null || sections.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<CompletableFuture<?>> saves = new ArrayList<>();
            int pos = 0;
            for (EventSectionDTO s : sections) {
                s.setEventId(eventId);
                s.setPosition(pos++);
                if (s.getId() == null || s.getId().isEmpty()) {
                    s.setId(UUID.randomUUID().toString());
                }
                saves.add(sectionRepo.save(s));
            }
            return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]));
        });
    }

    private CompletableFuture<List<EventDTO>> attachSectionsToAll(List<EventDTO> events) {
        if (events == null || events.isEmpty()) {
            return CompletableFuture.completedFuture(events);
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (EventDTO e : events) {
            futures.add(sectionRepo.findByEventId(e.getId()).thenAccept(e::setSections));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> events);
    }

    private void sanitizeSections(String eventId, List<EventSectionDTO> sections) {
        for (EventSectionDTO s : sections) {
            s.setEventId(eventId);
            s.setBodyHtml(sanitize(s.getBodyHtml()));
        }
    }

    private String sanitize(String html) {
        if (html == null || html.isEmpty()) return "";
        return Jsoup.clean(html, SAFE_LIST);
    }

    private void notifyWebhook(String eventId) {
        if (serviceRegistry == null) return;
        if (!serviceRegistry.hasService(WebhookNotifier.class)) return;
        try {
            WebhookNotifier notifier = serviceRegistry.getService(WebhookNotifier.class);
            if (notifier != null) {
                notifier.notifyEventChange(eventId);
            }
        } catch (Exception e) {
            logger.debug("Webhook notifier unavailable; skipping event notify: " + e.getMessage());
        }
    }
}
