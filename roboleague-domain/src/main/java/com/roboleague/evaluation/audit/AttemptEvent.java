package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;

/**
 * Common domain event interface for attempt lifecycle occurrences. The set is closed: persistence stores each kind.
 */
public sealed interface AttemptEvent permits SourceReceivedEvent, ResultRegisteredEvent, PenaltyAppliedEvent,
        ScoreAdjustedEvent, AppealAcceptedEvent, AttemptDisqualifiedEvent {
    String eventId();
    String attemptId();
    LocalDateTime timestamp();
    String eventType();
    String description();
}
