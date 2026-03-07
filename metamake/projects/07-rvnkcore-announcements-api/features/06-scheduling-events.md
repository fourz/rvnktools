# Feature Specification: Scheduling and Event Management

**Feature ID**: 06-scheduling-events  
**Priority**: High  
**Dependencies**: Service Architecture, Database Schema  
**Implementation Phase**: Week 3

## Overview

This feature provides comprehensive scheduling and event management capabilities for announcements, supporting time-based triggers, recurring schedules, event-driven announcements, and advanced scheduling patterns.

## Scheduling Architecture

### Core Components

```text
                        ┌─────────────────────────────────────────────┐
                        │           Scheduling System                 │
                        ├─────────────────────────────────────────────┤
                        │                                             │
                        │  ┌─────────────────┐  ┌─────────────────┐   │
                        │  │   Schedule      │  │   Event         │   │
                        │  │   Manager       │  │   Manager       │   │
                        │  └─────────────────┘  └─────────────────┘   │
                        │           │                     │           │
                        │           └─────────┬───────────┘           │
                        │                     │                       │
                        │  ┌─────────────────────────────────────┐   │
                        │  │        Trigger Engine               │   │
                        │  │  ┌─────────┐    ┌─────────────────┐ │   │
                        │  │  │  Cron   │    │   Event-Based   │ │   │
                        │  │  │Triggers │    │    Triggers     │ │   │
                        │  │  └─────────┘    └─────────────────┘ │   │
                        │  └─────────────────────────────────────┘   │
                        │                     │                       │
                        │  ┌─────────────────────────────────────┐   │
                        │  │     Announcement Dispatcher         │   │
                        │  └─────────────────────────────────────┘   │
                        └─────────────────────────────────────────────┘
```

## Service Implementation

### ScheduleService Interface

```java
package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.dto.ScheduleDTO;
import org.fourz.rvnkcore.api.dto.ScheduleExecutionDTO;
import org.fourz.rvnkcore.api.dto.EventTriggerDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing announcement schedules and event-driven triggers.
 * Supports cron-based scheduling, recurring patterns, and custom event triggers.
 */
public interface ScheduleService {
    
    // === Schedule Management ===
    
    /**
     * Creates a new announcement schedule.
     */
    CompletableFuture<String> createSchedule(ScheduleDTO schedule);
    
    /**
     * Updates an existing schedule.
     */
    CompletableFuture<Void> updateSchedule(ScheduleDTO schedule);
    
    /**
     * Deletes a schedule by ID.
     */
    CompletableFuture<Boolean> deleteSchedule(String scheduleId);
    
    /**
     * Gets a schedule by ID.
     */
    CompletableFuture<ScheduleDTO> getSchedule(String scheduleId);
    
    /**
     * Gets all schedules for a specific announcement.
     */
    CompletableFuture<List<ScheduleDTO>> getSchedulesByAnnouncement(String announcementId);
    
    /**
     * Gets all active schedules.
     */
    CompletableFuture<List<ScheduleDTO>> getAllActiveSchedules();
    
    /**
     * Gets schedules that should execute within the specified time window.
     */
    CompletableFuture<List<ScheduleDTO>> getSchedulesDue(Instant startTime, Instant endTime);
    
    // === Schedule Control ===
    
    /**
     * Enables a schedule for execution.
     */
    CompletableFuture<Void> enableSchedule(String scheduleId);
    
    /**
     * Disables a schedule temporarily.
     */
    CompletableFuture<Void> disableSchedule(String scheduleId);
    
    /**
     * Pauses a schedule until manually resumed.
     */
    CompletableFuture<Void> pauseSchedule(String scheduleId);
    
    /**
     * Resumes a paused schedule.
     */
    CompletableFuture<Void> resumeSchedule(String scheduleId);
    
    /**
     * Immediately executes a schedule regardless of timing.
     */
    CompletableFuture<ScheduleExecutionDTO> executeScheduleNow(String scheduleId, String triggeredBy);
    
    // === Event-Driven Triggers ===
    
    /**
     * Creates an event-based trigger for announcements.
     */
    CompletableFuture<String> createEventTrigger(EventTriggerDTO trigger);
    
    /**
     * Updates an existing event trigger.
     */
    CompletableFuture<Void> updateEventTrigger(EventTriggerDTO trigger);
    
    /**
     * Deletes an event trigger.
     */
    CompletableFuture<Boolean> deleteEventTrigger(String triggerId);
    
    /**
     * Gets all event triggers for a specific event type.
     */
    CompletableFuture<List<EventTriggerDTO>> getEventTriggers(String eventType);
    
    /**
     * Triggers announcement execution based on a specific event.
     */
    CompletableFuture<List<ScheduleExecutionDTO>> triggerEvent(String eventType, 
                                                              UUID playerId, 
                                                              String world,
                                                              String eventData);
    
    // === Execution History ===
    
    /**
     * Gets execution history for a specific schedule.
     */
    CompletableFuture<List<ScheduleExecutionDTO>> getScheduleExecutions(String scheduleId, 
                                                                         int limit, 
                                                                         int offset);
    
    /**
     * Gets recent executions across all schedules.
     */
    CompletableFuture<List<ScheduleExecutionDTO>> getRecentExecutions(int hours, int limit);
    
    /**
     * Gets failed executions that may need attention.
     */
    CompletableFuture<List<ScheduleExecutionDTO>> getFailedExecutions(int hours);
    
    /**
     * Marks a failed execution as resolved.
     */
    CompletableFuture<Void> markExecutionResolved(String executionId);
    
    // === Schedule Statistics ===
    
    /**
     * Gets execution statistics for a schedule.
     */
    CompletableFuture<ScheduleStatsDTO> getScheduleStatistics(String scheduleId, int days);
    
    /**
     * Gets system-wide scheduling statistics.
     */
    CompletableFuture<SystemScheduleStatsDTO> getSystemStatistics();
    
    /**
     * Gets the next scheduled execution time for a schedule.
     */
    CompletableFuture<Instant> getNextExecutionTime(String scheduleId);
    
    /**
     * Calculates upcoming executions for a schedule within a time range.
     */
    CompletableFuture<List<Instant>> calculateUpcomingExecutions(String scheduleId, 
                                                                Instant startTime, 
                                                                Instant endTime);
}
```

### ScheduleDTO Classes

```java
package org.fourz.rvnkcore.api.dto;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Data transfer object for announcement schedules.
 */
@Data
@Builder
public class ScheduleDTO {
    
    private String id;
    private String announcementId;
    private String name;
    private String description;
    
    // Schedule timing
    private ScheduleType type;
    private String cronExpression;
    private Integer intervalSeconds;
    private Timestamp startTime;
    private Timestamp endTime;
    
    // Execution settings
    private boolean enabled;
    private boolean paused;
    private int maxExecutions;
    private int currentExecutions;
    
    // Targeting
    private String world;
    private String permission;
    private Map<String, String> conditions;
    
    // Metadata
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastExecuted;
    private Timestamp nextExecution;
    
    public enum ScheduleType {
        CRON("cron"),           // Cron expression-based
        INTERVAL("interval"),   // Fixed interval
        ONCE("once"),          // One-time execution
        EVENT("event");        // Event-driven
        
        private final String value;
        
        ScheduleType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
}

/**
 * Data transfer object for event-driven triggers.
 */
@Data
@Builder
public class EventTriggerDTO {
    
    private String id;
    private String announcementId;
    private String name;
    
    // Event configuration
    private String eventType;
    private Map<String, String> eventConditions;
    private int delay; // Delay in seconds before execution
    private int cooldown; // Cooldown period between triggers
    
    // Targeting
    private String world;
    private String permission;
    private boolean requiresPlayer;
    
    // Execution limits
    private boolean enabled;
    private int maxExecutionsPerHour;
    private int maxExecutionsTotal;
    private int currentExecutions;
    
    // Metadata
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastTriggered;
}

/**
 * Data transfer object for schedule execution records.
 */
@Data
@Builder
public class ScheduleExecutionDTO {
    
    private String id;
    private String scheduleId;
    private String announcementId;
    
    // Execution details
    private Timestamp executedAt;
    private String triggeredBy;
    private String triggerType; // "schedule", "manual", "event"
    private ExecutionStatus status;
    private String errorMessage;
    
    // Context information
    private String world;
    private String playerId;
    private Map<String, String> executionContext;
    
    // Results
    private int playersNotified;
    private int playersFiltered;
    private long executionTimeMs;
    
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        PARTIAL,
        SKIPPED,
        CANCELLED
    }
}

/**
 * Schedule statistics data transfer object.
 */
@Data
@Builder
public class ScheduleStatsDTO {
    
    private String scheduleId;
    private int totalExecutions;
    private int successfulExecutions;
    private int failedExecutions;
    private double successRate;
    private long averageExecutionTimeMs;
    private int playersNotifiedTotal;
    private Timestamp lastExecuted;
    private Timestamp nextExecution;
    private boolean isHealthy;
}
```

## Implementation Classes

### DefaultScheduleService

```java
package org.fourz.rvnkcore.service.impl;

import org.fourz.rvnkcore.api.service.ScheduleService;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.dto.ScheduleDTO;
import org.fourz.rvnkcore.api.dto.ScheduleExecutionDTO;
import org.fourz.rvnkcore.api.dto.EventTriggerDTO;
import org.fourz.rvnkcore.repository.ScheduleRepository;
import org.fourz.rvnkcore.repository.EventTriggerRepository;
import org.fourz.rvnkcore.service.ScheduleEngine;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of the ScheduleService interface.
 * Manages announcement schedules, event triggers, and execution tracking.
 */
@Singleton
public class DefaultScheduleService implements ScheduleService {
    
    private final ScheduleRepository scheduleRepository;
    private final EventTriggerRepository eventTriggerRepository;
    private final ScheduleEngine scheduleEngine;
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    @Inject
    public DefaultScheduleService(ScheduleRepository scheduleRepository,
                                 EventTriggerRepository eventTriggerRepository,
                                 ScheduleEngine scheduleEngine,
                                 AnnouncementService announcementService,
                                 LogManager logger) {
        this.scheduleRepository = scheduleRepository;
        this.eventTriggerRepository = eventTriggerRepository;
        this.scheduleEngine = scheduleEngine;
        this.announcementService = announcementService;
        this.logger = logger;
    }
    
    @Override
    public CompletableFuture<String> createSchedule(ScheduleDTO schedule) {
        return scheduleRepository.createSchedule(schedule)
            .thenApply(scheduleId -> {
                // Register with schedule engine
                scheduleEngine.registerSchedule(schedule);
                logger.info("Created schedule: " + scheduleId);
                return scheduleId;
            });
    }
    
    @Override
    public CompletableFuture<Void> updateSchedule(ScheduleDTO schedule) {
        return scheduleRepository.updateSchedule(schedule)
            .thenCompose(v -> {
                // Update in schedule engine
                scheduleEngine.updateSchedule(schedule);
                logger.info("Updated schedule: " + schedule.getId());
                return CompletableFuture.completedFuture(null);
            });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteSchedule(String scheduleId) {
        return scheduleRepository.deleteSchedule(scheduleId)
            .thenApply(deleted -> {
                if (deleted) {
                    // Unregister from schedule engine
                    scheduleEngine.unregisterSchedule(scheduleId);
                    logger.info("Deleted schedule: " + scheduleId);
                }
                return deleted;
            });
    }
    
    @Override
    public CompletableFuture<ScheduleDTO> getSchedule(String scheduleId) {
        return scheduleRepository.getSchedule(scheduleId);
    }
    
    @Override
    public CompletableFuture<List<ScheduleDTO>> getSchedulesByAnnouncement(String announcementId) {
        return scheduleRepository.getSchedulesByAnnouncement(announcementId);
    }
    
    @Override
    public CompletableFuture<List<ScheduleDTO>> getAllActiveSchedules() {
        return scheduleRepository.getAllActiveSchedules();
    }
    
    @Override
    public CompletableFuture<List<ScheduleDTO>> getSchedulesDue(Instant startTime, Instant endTime) {
        return scheduleRepository.getSchedulesDue(startTime, endTime);
    }
    
    @Override
    public CompletableFuture<Void> enableSchedule(String scheduleId) {
        return updateScheduleStatus(scheduleId, true, false);
    }
    
    @Override
    public CompletableFuture<Void> disableSchedule(String scheduleId) {
        return updateScheduleStatus(scheduleId, false, false);
    }
    
    @Override
    public CompletableFuture<Void> pauseSchedule(String scheduleId) {
        return updateScheduleStatus(scheduleId, true, true);
    }
    
    @Override
    public CompletableFuture<Void> resumeSchedule(String scheduleId) {
        return updateScheduleStatus(scheduleId, true, false);
    }
    
    @Override
    public CompletableFuture<ScheduleExecutionDTO> executeScheduleNow(String scheduleId, String triggeredBy) {
        return getSchedule(scheduleId)
            .thenCompose(schedule -> {
                if (schedule == null) {
                    throw new IllegalArgumentException("Schedule not found: " + scheduleId);
                }
                
                return scheduleEngine.executeSchedule(schedule, "manual", triggeredBy);
            });
    }
    
    @Override
    public CompletableFuture<String> createEventTrigger(EventTriggerDTO trigger) {
        return eventTriggerRepository.createEventTrigger(trigger)
            .thenApply(triggerId -> {
                // Register with schedule engine
                scheduleEngine.registerEventTrigger(trigger);
                logger.info("Created event trigger: " + triggerId);
                return triggerId;
            });
    }
    
    @Override
    public CompletableFuture<Void> updateEventTrigger(EventTriggerDTO trigger) {
        return eventTriggerRepository.updateEventTrigger(trigger)
            .thenCompose(v -> {
                // Update in schedule engine
                scheduleEngine.updateEventTrigger(trigger);
                logger.info("Updated event trigger: " + trigger.getId());
                return CompletableFuture.completedFuture(null);
            });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteEventTrigger(String triggerId) {
        return eventTriggerRepository.deleteEventTrigger(triggerId)
            .thenApply(deleted -> {
                if (deleted) {
                    scheduleEngine.unregisterEventTrigger(triggerId);
                    logger.info("Deleted event trigger: " + triggerId);
                }
                return deleted;
            });
    }
    
    @Override
    public CompletableFuture<List<EventTriggerDTO>> getEventTriggers(String eventType) {
        return eventTriggerRepository.getEventTriggersByType(eventType);
    }
    
    @Override
    public CompletableFuture<List<ScheduleExecutionDTO>> triggerEvent(String eventType,
                                                                     UUID playerId,
                                                                     String world,
                                                                     String eventData) {
        return getEventTriggers(eventType)
            .thenCompose(triggers -> {
                List<CompletableFuture<ScheduleExecutionDTO>> executions = triggers.stream()
                    .filter(trigger -> trigger.isEnabled())
                    .filter(trigger -> matchesEventConditions(trigger, playerId, world, eventData))
                    .map(trigger -> scheduleEngine.executeEventTrigger(trigger, playerId, world, eventData))
                    .toList();
                
                return CompletableFuture.allOf(executions.toArray(new CompletableFuture[0]))
                    .thenApply(v -> executions.stream()
                        .map(CompletableFuture::join)
                        .toList());
            });
    }
    
    @Override
    public CompletableFuture<List<ScheduleExecutionDTO>> getScheduleExecutions(String scheduleId, 
                                                                              int limit, 
                                                                              int offset) {
        return scheduleRepository.getScheduleExecutions(scheduleId, limit, offset);
    }
    
    @Override
    public CompletableFuture<List<ScheduleExecutionDTO>> getRecentExecutions(int hours, int limit) {
        return scheduleRepository.getRecentExecutions(hours, limit);
    }
    
    @Override
    public CompletableFuture<List<ScheduleExecutionDTO>> getFailedExecutions(int hours) {
        return scheduleRepository.getFailedExecutions(hours);
    }
    
    @Override
    public CompletableFuture<Void> markExecutionResolved(String executionId) {
        return scheduleRepository.markExecutionResolved(executionId);
    }
    
    @Override
    public CompletableFuture<ScheduleStatsDTO> getScheduleStatistics(String scheduleId, int days) {
        return scheduleRepository.getScheduleStatistics(scheduleId, days);
    }
    
    @Override
    public CompletableFuture<SystemScheduleStatsDTO> getSystemStatistics() {
        return scheduleRepository.getSystemStatistics();
    }
    
    @Override
    public CompletableFuture<Instant> getNextExecutionTime(String scheduleId) {
        return getSchedule(scheduleId)
            .thenApply(schedule -> {
                if (schedule == null) {
                    return null;
                }
                
                return scheduleEngine.calculateNextExecution(schedule);
            });
    }
    
    @Override
    public CompletableFuture<List<Instant>> calculateUpcomingExecutions(String scheduleId,
                                                                       Instant startTime,
                                                                       Instant endTime) {
        return getSchedule(scheduleId)
            .thenApply(schedule -> {
                if (schedule == null) {
                    return List.of();
                }
                
                return scheduleEngine.calculateUpcomingExecutions(schedule, startTime, endTime);
            });
    }
    
    // === Private Helper Methods ===
    
    private CompletableFuture<Void> updateScheduleStatus(String scheduleId, boolean enabled, boolean paused) {
        return getSchedule(scheduleId)
            .thenCompose(schedule -> {
                if (schedule == null) {
                    throw new IllegalArgumentException("Schedule not found: " + scheduleId);
                }
                
                schedule.setEnabled(enabled);
                schedule.setPaused(paused);
                
                return updateSchedule(schedule);
            });
    }
    
    private boolean matchesEventConditions(EventTriggerDTO trigger, 
                                         UUID playerId, 
                                         String world, 
                                         String eventData) {
        // Check world restrictions
        if (trigger.getWorld() != null && !trigger.getWorld().equals(world)) {
            return false;
        }
        
        // Check if player is required
        if (trigger.isRequiresPlayer() && playerId == null) {
            return false;
        }
        
        // Check custom conditions
        if (trigger.getEventConditions() != null && !trigger.getEventConditions().isEmpty()) {
            return evaluateEventConditions(trigger.getEventConditions(), playerId, world, eventData);
        }
        
        return true;
    }
    
    private boolean evaluateEventConditions(Map<String, String> conditions,
                                          UUID playerId,
                                          String world,
                                          String eventData) {
        // Implementation for evaluating custom event conditions
        // This would include logic for checking player properties,
        // world conditions, event data matching, etc.
        
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            String key = condition.getKey();
            String expectedValue = condition.getValue();
            
            switch (key) {
                case "world":
                    if (!expectedValue.equals(world)) {
                        return false;
                    }
                    break;
                case "player_permission":
                    // Check player permission (would need player service)
                    break;
                case "event_data_contains":
                    if (eventData == null || !eventData.contains(expectedValue)) {
                        return false;
                    }
                    break;
                // Add more condition types as needed
            }
        }
        
        return true;
    }
}
```

## Schedule Engine Implementation

### ScheduleEngine

```java
package org.fourz.rvnkcore.service;

import org.fourz.rvnkcore.api.dto.ScheduleDTO;
import org.fourz.rvnkcore.api.dto.ScheduleExecutionDTO;
import org.fourz.rvnkcore.api.dto.EventTriggerDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.util.cron.CronExpression;
import org.fourz.rvnkcore.util.log.LogManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Core scheduling engine that handles execution timing,
 * cron expressions, and event-driven triggers.
 */
@Singleton
public class ScheduleEngine {
    
    private final ScheduledExecutorService schedulerExecutor;
    private final ExecutorService executionExecutor;
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    private final Map<String, ScheduledFuture<?>> activeSchedules = new ConcurrentHashMap<>();
    private final Map<String, ScheduleDTO> scheduleDefinitions = new ConcurrentHashMap<>();
    private final Map<String, EventTriggerDTO> eventTriggers = new ConcurrentHashMap<>();
    
    @Inject
    public ScheduleEngine(AnnouncementService announcementService, LogManager logger) {
        this.announcementService = announcementService;
        this.logger = logger;
        
        // Create thread pools
        this.schedulerExecutor = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "ScheduleEngine-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.executionExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ScheduleEngine-Executor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Registers a schedule for execution.
     */
    public void registerSchedule(ScheduleDTO schedule) {
        if (!schedule.isEnabled() || schedule.isPaused()) {
            return;
        }
        
        scheduleDefinitions.put(schedule.getId(), schedule);
        
        switch (schedule.getType()) {
            case CRON:
                scheduleWithCron(schedule);
                break;
            case INTERVAL:
                scheduleWithInterval(schedule);
                break;
            case ONCE:
                scheduleOnce(schedule);
                break;
            case EVENT:
                // Event-driven schedules don't need timer-based registration
                break;
        }
        
        logger.info("Registered schedule: " + schedule.getId() + " (" + schedule.getType() + ")");
    }
    
    /**
     * Updates an existing schedule registration.
     */
    public void updateSchedule(ScheduleDTO schedule) {
        unregisterSchedule(schedule.getId());
        registerSchedule(schedule);
    }
    
    /**
     * Unregisters a schedule from execution.
     */
    public void unregisterSchedule(String scheduleId) {
        ScheduledFuture<?> future = activeSchedules.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
        }
        scheduleDefinitions.remove(scheduleId);
        logger.info("Unregistered schedule: " + scheduleId);
    }
    
    /**
     * Registers an event trigger.
     */
    public void registerEventTrigger(EventTriggerDTO trigger) {
        eventTriggers.put(trigger.getId(), trigger);
        logger.info("Registered event trigger: " + trigger.getId() + " for event: " + trigger.getEventType());
    }
    
    /**
     * Updates an existing event trigger.
     */
    public void updateEventTrigger(EventTriggerDTO trigger) {
        eventTriggers.put(trigger.getId(), trigger);
    }
    
    /**
     * Unregisters an event trigger.
     */
    public void unregisterEventTrigger(String triggerId) {
        EventTriggerDTO removed = eventTriggers.remove(triggerId);
        if (removed != null) {
            logger.info("Unregistered event trigger: " + triggerId);
        }
    }
    
    /**
     * Executes a schedule manually.
     */
    public CompletableFuture<ScheduleExecutionDTO> executeSchedule(ScheduleDTO schedule,
                                                                  String triggerType,
                                                                  String triggeredBy) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Get the announcement to execute
                var announcement = announcementService.getAnnouncement(schedule.getAnnouncementId()).join();
                if (announcement == null) {
                    throw new IllegalStateException("Announcement not found: " + schedule.getAnnouncementId());
                }
                
                // Execute the announcement
                var result = announcementService.sendAnnouncement(
                    schedule.getAnnouncementId(),
                    schedule.getWorld(),
                    schedule.getPermission()
                ).join();
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                // Create execution record
                ScheduleExecutionDTO execution = ScheduleExecutionDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .scheduleId(schedule.getId())
                    .announcementId(schedule.getAnnouncementId())
                    .executedAt(Timestamp.from(Instant.now()))
                    .triggeredBy(triggeredBy)
                    .triggerType(triggerType)
                    .status(ScheduleExecutionDTO.ExecutionStatus.SUCCESS)
                    .world(schedule.getWorld())
                    .playersNotified(result.getPlayersNotified())
                    .playersFiltered(result.getPlayersFiltered())
                    .executionTimeMs(executionTime)
                    .build();
                
                // Update schedule execution count
                schedule.setCurrentExecutions(schedule.getCurrentExecutions() + 1);
                schedule.setLastExecuted(execution.getExecutedAt());
                
                logger.info("Executed schedule " + schedule.getId() + " - notified " + 
                           result.getPlayersNotified() + " players");
                
                return execution;
                
            } catch (Exception e) {
                logger.error("Failed to execute schedule: " + schedule.getId(), e);
                
                return ScheduleExecutionDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .scheduleId(schedule.getId())
                    .announcementId(schedule.getAnnouncementId())
                    .executedAt(Timestamp.from(Instant.now()))
                    .triggeredBy(triggeredBy)
                    .triggerType(triggerType)
                    .status(ScheduleExecutionDTO.ExecutionStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(0)
                    .build();
            }
        }, executionExecutor);
    }
    
    /**
     * Executes an event-triggered announcement.
     */
    public CompletableFuture<ScheduleExecutionDTO> executeEventTrigger(EventTriggerDTO trigger,
                                                                      UUID playerId,
                                                                      String world,
                                                                      String eventData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cooldown
                if (isInCooldown(trigger)) {
                    return createSkippedExecution(trigger, "Cooldown active");
                }
                
                // Check execution limits
                if (hasExceededLimits(trigger)) {
                    return createSkippedExecution(trigger, "Execution limits exceeded");
                }
                
                // Apply delay if specified
                if (trigger.getDelay() > 0) {
                    try {
                        Thread.sleep(trigger.getDelay() * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Execution interrupted during delay", e);
                    }
                }
                
                long startTime = System.currentTimeMillis();
                
                // Execute the announcement
                var result = announcementService.sendAnnouncement(
                    trigger.getAnnouncementId(),
                    world,
                    trigger.getPermission()
                ).join();
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                // Update trigger stats
                trigger.setCurrentExecutions(trigger.getCurrentExecutions() + 1);
                trigger.setLastTriggered(Timestamp.from(Instant.now()));
                
                ScheduleExecutionDTO execution = ScheduleExecutionDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .scheduleId(trigger.getId())
                    .announcementId(trigger.getAnnouncementId())
                    .executedAt(Timestamp.from(Instant.now()))
                    .triggeredBy(playerId != null ? playerId.toString() : "system")
                    .triggerType("event")
                    .status(ScheduleExecutionDTO.ExecutionStatus.SUCCESS)
                    .world(world)
                    .playerId(playerId != null ? playerId.toString() : null)
                    .playersNotified(result.getPlayersNotified())
                    .playersFiltered(result.getPlayersFiltered())
                    .executionTimeMs(executionTime)
                    .executionContext(Map.of(
                        "event_type", trigger.getEventType(),
                        "event_data", eventData != null ? eventData : ""
                    ))
                    .build();
                
                logger.info("Executed event trigger " + trigger.getId() + " for event " + 
                           trigger.getEventType() + " - notified " + result.getPlayersNotified() + " players");
                
                return execution;
                
            } catch (Exception e) {
                logger.error("Failed to execute event trigger: " + trigger.getId(), e);
                
                return ScheduleExecutionDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .scheduleId(trigger.getId())
                    .announcementId(trigger.getAnnouncementId())
                    .executedAt(Timestamp.from(Instant.now()))
                    .triggeredBy(playerId != null ? playerId.toString() : "system")
                    .triggerType("event")
                    .status(ScheduleExecutionDTO.ExecutionStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .world(world)
                    .playerId(playerId != null ? playerId.toString() : null)
                    .executionTimeMs(0)
                    .build();
            }
        }, executionExecutor);
    }
    
    /**
     * Calculates the next execution time for a schedule.
     */
    public Instant calculateNextExecution(ScheduleDTO schedule) {
        switch (schedule.getType()) {
            case CRON:
                try {
                    CronExpression cron = new CronExpression(schedule.getCronExpression());
                    LocalDateTime next = cron.getNextValidTimeAfter(LocalDateTime.now());
                    return next.atZone(ZoneId.systemDefault()).toInstant();
                } catch (Exception e) {
                    logger.warning("Invalid cron expression for schedule " + schedule.getId(), e);
                    return null;
                }
                
            case INTERVAL:
                if (schedule.getLastExecuted() != null) {
                    return schedule.getLastExecuted().toInstant()
                        .plusSeconds(schedule.getIntervalSeconds());
                } else {
                    return Instant.now().plusSeconds(schedule.getIntervalSeconds());
                }
                
            case ONCE:
                return schedule.getStartTime() != null ? 
                    schedule.getStartTime().toInstant() : null;
                
            default:
                return null;
        }
    }
    
    /**
     * Calculates upcoming executions within a time range.
     */
    public List<Instant> calculateUpcomingExecutions(ScheduleDTO schedule, 
                                                    Instant startTime, 
                                                    Instant endTime) {
        List<Instant> executions = new ArrayList<>();
        
        switch (schedule.getType()) {
            case CRON:
                try {
                    CronExpression cron = new CronExpression(schedule.getCronExpression());
                    LocalDateTime current = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
                    LocalDateTime end = LocalDateTime.ofInstant(endTime, ZoneId.systemDefault());
                    
                    while (current.isBefore(end)) {
                        current = cron.getNextValidTimeAfter(current);
                        if (current.isAfter(end)) {
                            break;
                        }
                        executions.add(current.atZone(ZoneId.systemDefault()).toInstant());
                    }
                } catch (Exception e) {
                    logger.warning("Invalid cron expression for schedule " + schedule.getId(), e);
                }
                break;
                
            case INTERVAL:
                Instant current = startTime;
                while (current.isBefore(endTime)) {
                    current = current.plusSeconds(schedule.getIntervalSeconds());
                    if (current.isBefore(endTime)) {
                        executions.add(current);
                    }
                }
                break;
                
            case ONCE:
                if (schedule.getStartTime() != null) {
                    Instant executeTime = schedule.getStartTime().toInstant();
                    if (executeTime.isAfter(startTime) && executeTime.isBefore(endTime)) {
                        executions.add(executeTime);
                    }
                }
                break;
        }
        
        return executions;
    }
    
    // === Private Helper Methods ===
    
    private void scheduleWithCron(ScheduleDTO schedule) {
        try {
            CronExpression cron = new CronExpression(schedule.getCronExpression());
            LocalDateTime nextExecution = cron.getNextValidTimeAfter(LocalDateTime.now());
            
            long delay = java.time.Duration.between(LocalDateTime.now(), nextExecution).toMillis();
            
            ScheduledFuture<?> future = schedulerExecutor.schedule(() -> {
                executeSchedule(schedule, "schedule", "system");
                // Reschedule for next execution
                scheduleWithCron(schedule);
            }, delay, TimeUnit.MILLISECONDS);
            
            activeSchedules.put(schedule.getId(), future);
            
        } catch (Exception e) {
            logger.error("Failed to schedule cron job for schedule: " + schedule.getId(), e);
        }
    }
    
    private void scheduleWithInterval(ScheduleDTO schedule) {
        long delay = schedule.getStartTime() != null ? 
            java.time.Duration.between(Instant.now(), schedule.getStartTime().toInstant()).toSeconds() : 0;
        
        if (delay < 0) delay = 0;
        
        ScheduledFuture<?> future = schedulerExecutor.scheduleAtFixedRate(() -> {
            if (shouldExecuteSchedule(schedule)) {
                executeSchedule(schedule, "schedule", "system");
            }
        }, delay, schedule.getIntervalSeconds(), TimeUnit.SECONDS);
        
        activeSchedules.put(schedule.getId(), future);
    }
    
    private void scheduleOnce(ScheduleDTO schedule) {
        Instant executeTime = schedule.getStartTime() != null ? 
            schedule.getStartTime().toInstant() : Instant.now();
        
        long delay = java.time.Duration.between(Instant.now(), executeTime).toMillis();
        if (delay < 0) delay = 0;
        
        ScheduledFuture<?> future = schedulerExecutor.schedule(() -> {
            executeSchedule(schedule, "schedule", "system");
        }, delay, TimeUnit.MILLISECONDS);
        
        activeSchedules.put(schedule.getId(), future);
    }
    
    private boolean shouldExecuteSchedule(ScheduleDTO schedule) {
        // Check if schedule is still enabled
        if (!schedule.isEnabled() || schedule.isPaused()) {
            return false;
        }
        
        // Check execution limits
        if (schedule.getMaxExecutions() > 0 && 
            schedule.getCurrentExecutions() >= schedule.getMaxExecutions()) {
            return false;
        }
        
        // Check end time
        if (schedule.getEndTime() != null && 
            Instant.now().isAfter(schedule.getEndTime().toInstant())) {
            return false;
        }
        
        return true;
    }
    
    private boolean isInCooldown(EventTriggerDTO trigger) {
        if (trigger.getCooldown() <= 0 || trigger.getLastTriggered() == null) {
            return false;
        }
        
        Instant cooldownEnd = trigger.getLastTriggered().toInstant()
            .plusSeconds(trigger.getCooldown());
        
        return Instant.now().isBefore(cooldownEnd);
    }
    
    private boolean hasExceededLimits(EventTriggerDTO trigger) {
        // Check total executions
        if (trigger.getMaxExecutionsTotal() > 0 && 
            trigger.getCurrentExecutions() >= trigger.getMaxExecutionsTotal()) {
            return true;
        }
        
        // Check hourly limit (would need additional tracking)
        if (trigger.getMaxExecutionsPerHour() > 0) {
            // Implementation would require hourly execution tracking
            // For now, return false
        }
        
        return false;
    }
    
    private ScheduleExecutionDTO createSkippedExecution(EventTriggerDTO trigger, String reason) {
        return ScheduleExecutionDTO.builder()
            .id(UUID.randomUUID().toString())
            .scheduleId(trigger.getId())
            .announcementId(trigger.getAnnouncementId())
            .executedAt(Timestamp.from(Instant.now()))
            .triggeredBy("system")
            .triggerType("event")
            .status(ScheduleExecutionDTO.ExecutionStatus.SKIPPED)
            .errorMessage(reason)
            .executionTimeMs(0)
            .build();
    }
    
    /**
     * Shuts down the schedule engine and cancels all active schedules.
     */
    public void shutdown() {
        logger.info("Shutting down schedule engine...");
        
        // Cancel all active schedules
        activeSchedules.values().forEach(future -> future.cancel(false));
        activeSchedules.clear();
        
        // Shutdown executors
        schedulerExecutor.shutdown();
        executionExecutor.shutdown();
        
        try {
            if (!schedulerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                schedulerExecutor.shutdownNow();
            }
            if (!executionExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                executionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            schedulerExecutor.shutdownNow();
            executionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Schedule engine shutdown complete");
    }
}
```

## Event Integration

### Event Types and Triggers

```java
/**
 * Common event types that can trigger announcements.
 */
public class EventTypes {
    
    // Player events
    public static final String PLAYER_JOIN = "player_join";
    public static final String PLAYER_QUIT = "player_quit";
    public static final String PLAYER_DEATH = "player_death";
    public static final String PLAYER_LEVEL_UP = "player_level_up";
    public static final String PLAYER_ACHIEVEMENT = "player_achievement";
    
    // Server events
    public static final String SERVER_START = "server_start";
    public static final String SERVER_STOP = "server_stop";
    public static final String PLUGIN_ENABLE = "plugin_enable";
    public static final String PLUGIN_DISABLE = "plugin_disable";
    
    // World events
    public static final String WORLD_LOAD = "world_load";
    public static final String WORLD_UNLOAD = "world_unload";
    public static final String WEATHER_CHANGE = "weather_change";
    public static final String TIME_CHANGE = "time_change";
    
    // Economy events (if available)
    public static final String ECONOMY_TRANSACTION = "economy_transaction";
    public static final String BALANCE_MILESTONE = "balance_milestone";
    
    // Custom events
    public static final String CUSTOM_EVENT = "custom_event";
    public static final String SCHEDULED_EVENT = "scheduled_event";
}
```

This comprehensive scheduling and event management feature provides the foundation for time-based and event-driven announcement delivery with robust execution tracking and management capabilities.
