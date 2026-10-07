package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

import java.util.Objects;

/**
 * Where an attempt is in its lifecycle, as data to store it: its status and, while it is under appeal, how many
 * appeals are open and whether it goes back to evaluated or adjusted when they close.
 */
public record AttemptStage(AttemptStatus status, int openAppeals, AttemptStatus settledStatus) {
    public AttemptStage {
        Objects.requireNonNull(status, "status cannot be null");
    }

    static AttemptStage of(AttemptStatus status) {
        return new AttemptStage(status, 0, null);
    }

    /**
     * Rebuilds the state this stage describes. Only restoring a stored attempt needs to choose a state by its status.
     */
    AttemptState state() {
        return switch (status) {
            case SCHEDULED -> WaitingAttemptState.scheduled();
            case AWAITING_SOURCES -> WaitingAttemptState.awaitingSources();
            case EVALUATED -> SettledAttemptState.evaluated();
            case ADJUSTED -> SettledAttemptState.adjusted();
            case UNDER_APPEAL -> new UnderAppealAttemptState(openAppeals, settledState());
            case DISQUALIFIED -> new DisqualifiedAttemptState();
        };
    }

    private SettledAttemptState settledState() {
        if (settledStatus == AttemptStatus.ADJUSTED) {
            return SettledAttemptState.adjusted();
        }
        if (settledStatus == AttemptStatus.EVALUATED) {
            return SettledAttemptState.evaluated();
        }
        throw new IllegalStateException("an attempt under appeal goes back to evaluated or adjusted, not to "
                + settledStatus);
    }
}
