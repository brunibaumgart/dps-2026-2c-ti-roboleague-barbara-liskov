package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when a judge disqualifies an attempt.
 */
public record AttemptDisqualifiedEvent(
        EventMetadata metadata,
        String reason,
        String judgeId
) implements AttemptEvent {

    public AttemptDisqualifiedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    @Override
    public String eventId() {
        return metadata.eventId();
    }

    @Override
    public String attemptId() {
        return metadata.attemptId();
    }

    @Override
    public LocalDateTime timestamp() {
        return metadata.timestamp();
    }

    @Override
    public String eventType() {
        return "ATTEMPT_DISQUALIFIED";
    }

    @Override
    public String description() {
        return "Attempt disqualified by " + judgeId + ": " + reason;
    }

    public static AttemptDisqualifiedEvent create(String attemptId, String reason, String judgeId) {
        return new AttemptDisqualifiedEvent(EventMetadata.create(attemptId), reason, judgeId);
    }
}
