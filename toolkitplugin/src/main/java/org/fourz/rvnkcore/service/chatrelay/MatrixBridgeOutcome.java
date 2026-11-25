package org.fourz.rvnkcore.service.chatrelay;

/**
 * What the Matrix bridge did with a chatbot line from {@code POST /v1/chat/matrix} (#2443). Pure,
 * so it is unit-tested.
 *
 * <p>Before #2443 the tier answered "Matrix post accepted" before the bridge answered, so a line the
 * bridge refused for its rate cap looked delivered. The controller now waits a short time for the
 * bridge's HTTP status and maps it here. The line has already reached the game on every tier in all
 * cases; the outcome is only about Discord and Matrix.</p>
 */
public enum MatrixBridgeOutcome {

    /** The bridge answered 2xx. */
    DELIVERED(200),
    /** This tier has no Matrix bridge peer (or the relay sent nothing to it). */
    NO_BRIDGE(200),
    /** The bridge did not answer inside the wait. The line may still arrive. */
    UNCONFIRMED(202),
    /** The bridge answered 429: its per-room or per-persona cap. The line is not retried. */
    RATE_LIMITED(429),
    /** The bridge answered another non-2xx status. */
    REFUSED(502),
    /** The bridge could not be reached (connect, TLS or read failure). */
    UNREACHABLE(502);

    /** Egress status for "no Matrix peer was sent this line". */
    public static final int NOT_SENT = 0;
    /** Egress status for "the send failed before any HTTP status". */
    public static final int FAILED = -1;
    /** Upper bound for the REST thread's wait, whatever the relay timeout is. */
    static final long MAX_WAIT_MS = 5_000L;

    private final int httpStatus;

    MatrixBridgeOutcome(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** @return the HTTP status {@code POST /v1/chat/matrix} answers for this outcome */
    public int httpStatus() {
        return httpStatus;
    }

    /** @return true when the endpoint answers with a success envelope */
    public boolean success() {
        return httpStatus < 300;
    }

    /**
     * Maps the bridge's egress status.
     *
     * @param status the HTTP status, {@link #NOT_SENT}, {@link #FAILED}, or null when the wait ran out
     */
    public static MatrixBridgeOutcome of(Integer status) {
        if (status == null) return UNCONFIRMED;
        int s = status;
        if (s == NOT_SENT) return NO_BRIDGE;
        if (s == FAILED) return UNREACHABLE;
        if (s >= 200 && s < 300) return DELIVERED;
        if (s == 429) return RATE_LIMITED;
        return REFUSED;
    }

    /**
     * How long the REST thread waits for the bridge: the relay timeout plus a margin, capped.
     *
     * @param relayTimeoutMs {@code chat-relay.timeout-ms}
     */
    public static long waitMs(int relayTimeoutMs) {
        return Math.min(Math.max(relayTimeoutMs, 0) + 500L, MAX_WAIT_MS);
    }

    /** Error code for the failure outcomes (null for a success). */
    public String errorCode() {
        return switch (this) {
            case RATE_LIMITED -> "RATE_LIMITED";
            case REFUSED, UNREACHABLE -> "BAD_GATEWAY";
            default -> null;
        };
    }

    /**
     * The text the endpoint answers.
     *
     * @param status the raw egress status (used for REFUSED), may be null
     * @param waitMs the wait used (for UNCONFIRMED)
     */
    public String message(Integer status, long waitMs) {
        return switch (this) {
            case DELIVERED -> "Matrix post delivered";
            case NO_BRIDGE -> "Matrix post accepted (this tier has no Matrix bridge peer)";
            case UNCONFIRMED -> "Matrix post queued; the bridge did not answer within " + waitMs
                + " ms, so delivery is not confirmed";
            case RATE_LIMITED -> "The Matrix bridge refused the line (rate cap). Players on the tiers"
                + " saw it; Discord and Matrix did not";
            case REFUSED -> "The Matrix bridge answered " + status + ". Players on the tiers saw the"
                + " line; Discord and Matrix did not";
            case UNREACHABLE -> "The Matrix bridge could not be reached. Players on the tiers saw the"
                + " line; Discord and Matrix did not";
        };
    }
}
