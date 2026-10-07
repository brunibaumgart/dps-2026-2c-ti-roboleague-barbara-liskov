package com.roboleague.evaluation.audit;

import java.util.List;
import java.util.Objects;

/**
 * The append-only history of an attempt: every score revision and every event, oldest first.
 */
public record AuditTrail(List<AttemptScoreSnapshot> revisions, List<AttemptEvent> events) {
    public AuditTrail {
        revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions cannot be null"));
        events = List.copyOf(Objects.requireNonNull(events, "events cannot be null"));
    }
}
