package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.ResultSource;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when one source of an attempt's results arrives (F3), with the judge who loaded it.
 */
public record SourceReceivedEvent(EventMetadata metadata, ResultSource source, String judgeId) implements AttemptEvent {

    public SourceReceivedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
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
        return "SOURCE_RECEIVED";
    }

    @Override
    public String description() {
        return source.label() + " loaded by " + judgeId;
    }

    public static SourceReceivedEvent create(EventMetadata metadata, ResultSource source, String judgeId) {
        return new SourceReceivedEvent(metadata, source, judgeId);
    }
}
