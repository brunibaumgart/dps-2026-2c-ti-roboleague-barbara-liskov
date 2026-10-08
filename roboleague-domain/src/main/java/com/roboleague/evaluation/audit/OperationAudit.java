package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/** Values prepared by the caller for one transition, which can append up to two events. */
public record OperationAudit(LocalDateTime timestamp, String firstEventId, String secondEventId) {
    public OperationAudit {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(firstEventId, "firstEventId cannot be null");
        Objects.requireNonNull(secondEventId, "secondEventId cannot be null");
        if (firstEventId.isBlank() || secondEventId.isBlank() || firstEventId.equals(secondEventId)) {
            throw new IllegalArgumentException("event ids must be nonblank and distinct");
        }
    }

    public EventMetadata firstEvent(String attemptId) {
        return EventMetadata.of(firstEventId, attemptId, timestamp);
    }

    public EventMetadata secondEvent(String attemptId) {
        return EventMetadata.of(secondEventId, attemptId, timestamp);
    }
}
