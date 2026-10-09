package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;

import java.time.LocalDateTime;

/**
 * Common domain event interface for attempt lifecycle occurrences. The set is closed: persistence stores each kind.
 */
public sealed interface AttemptEvent permits SourceReceivedEvent, ResultRegisteredEvent, PenaltyAppliedEvent,
        ScoreAdjustedEvent, AppealAcceptedEvent, AttemptDisqualifiedEvent {
    String eventId();
    AttemptId attemptId();
    LocalDateTime timestamp();
    String eventType();
    String description();
}
