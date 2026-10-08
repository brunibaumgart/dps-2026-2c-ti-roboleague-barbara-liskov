package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing common audit metadata across all attempt domain events.
 */
public record EventMetadata(String eventId, AttemptId attemptId, LocalDateTime timestamp) {
    public EventMetadata {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public static EventMetadata of(String eventId, AttemptId attemptId, LocalDateTime timestamp) {
        return new EventMetadata(eventId, attemptId, timestamp);
    }
}
