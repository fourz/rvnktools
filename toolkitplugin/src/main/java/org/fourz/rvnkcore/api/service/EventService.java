package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.EventDTO;
import org.fourz.rvnkcore.api.model.EventSectionDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Event CRUD operations.
 *
 * Sections are managed via the parent event — the service joins them into the
 * returned DTO and replaces the section list on full-event updates.
 *
 * @since 1.5.0
 */
public interface EventService {

    CompletableFuture<List<EventDTO>> getAllEvents();

    CompletableFuture<List<EventDTO>> getActiveEvents();

    CompletableFuture<List<EventDTO>> getEventsByStatus(String status);

    CompletableFuture<Optional<EventDTO>> getEvent(String id);

    CompletableFuture<EventDTO> createEvent(EventDTO event);

    CompletableFuture<EventDTO> updateEvent(EventDTO event);

    CompletableFuture<Void> deleteEvent(String id);

    CompletableFuture<EventSectionDTO> addSection(String eventId, EventSectionDTO section);

    CompletableFuture<EventSectionDTO> updateSection(EventSectionDTO section);

    CompletableFuture<Void> deleteSection(String sectionId);

    CompletableFuture<Void> replaceSections(String eventId, List<EventSectionDTO> sections);
}
