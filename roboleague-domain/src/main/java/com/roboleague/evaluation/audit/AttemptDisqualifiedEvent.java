package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when a judge disqualifies an attempt; the author is the judge as an actor.
 */
public record AttemptDisqualifiedEvent(
        EventMetadata metadata,
        String reason,
        ActorId authorId
) implements AttemptEvent {

    public AttemptDisqualifiedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
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
        return "Attempt disqualified by " + authorId + ": " + reason;
    }

    public static AttemptDisqualifiedEvent create(EventMetadata metadata, AuditNote note) {
        return new AttemptDisqualifiedEvent(metadata, note.reason(), note.authorId());
    }
}
