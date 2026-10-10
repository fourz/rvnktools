package org.fourz.rvnkcore.api.model.worlds;

import java.time.Instant;

/**
 * Status of a long-running world operation (World Forge, #2200).
 *
 * <p>Returned with HTTP 202 by a v2 create, and by {@code GET /rvnkworlds/jobs/{id}}.
 * Clients poll the job until {@link #state} is {@link State#DONE} or {@link State#FAILED}.</p>
 *
 * @since 1.5.96
 */
public class JobDTO {

    /** Job lifecycle. Terminal states are {@link #DONE} and {@link #FAILED}. */
    public enum State { QUEUED, RUNNING, DONE, FAILED }

    /** Opaque id; RVNKCore only requires it to be URL-path safe ({@code [A-Za-z0-9_-]+}). */
    private String id;
    /** Operation kind, e.g. {@code "CREATE_WORLD"}. */
    private String type;
    private State state;
    /** 0.0 .. 1.0. Implementations that cannot measure progress report 0 then 1. */
    private double progress;
    /** Human-readable current step, e.g. "Generating spawn chunks". */
    private String message;
    private String worldName;
    private Instant createdAt;
    /** Null until the job reaches a terminal state. */
    private Instant finishedAt;
    /** Failure reason when {@link #state} is {@link State#FAILED}; null otherwise. */
    private String error;

    public JobDTO() {
    }

    public JobDTO(String id, String type, State state, double progress, String message,
                  String worldName, Instant createdAt, Instant finishedAt, String error) {
        this.id = id;
        this.type = type;
        this.state = state;
        this.progress = progress;
        this.message = message;
        this.worldName = worldName;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.error = error;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = Math.max(0.0, Math.min(1.0, progress)); }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
