package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.scheduling.JudgeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when a judge disqualifies an attempt.
 */
public record AttemptDisqualifiedEvent(
        EventMetadata metadata,
        String reason,
        JudgeId judgeId
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
    public AttemptId attemptId() {
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

    public static AttemptDisqualifiedEvent create(EventMetadata metadata, String reason, JudgeId judgeId) {
        return new AttemptDisqualifiedEvent(metadata, reason, judgeId);
    }
}
