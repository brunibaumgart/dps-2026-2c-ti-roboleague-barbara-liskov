package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;

/**
 * Common domain event interface for attempt lifecycle occurrences.
 */
public interface AttemptEvent {
    String eventId();
    String attemptId();
    LocalDateTime timestamp();
    String eventType();
    String description();
}
