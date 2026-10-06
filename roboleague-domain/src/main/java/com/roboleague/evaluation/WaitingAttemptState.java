package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * An attempt that is not scored yet: scheduled, with no source in, or awaiting the sources its rulebook still
 * needs (F3). It only accepts results; it is scored when the last source arrives.
 */
final class WaitingAttemptState implements AttemptState {
    private final AttemptStatus status;

    private WaitingAttemptState(AttemptStatus status) {
        this.status = status;
    }

    static WaitingAttemptState scheduled() {
        return new WaitingAttemptState(AttemptStatus.SCHEDULED);
    }

    static WaitingAttemptState awaitingSources() {
        return new WaitingAttemptState(AttemptStatus.AWAITING_SOURCES);
    }

    @Override
    public AttemptStatus status() {
        return status;
    }

    @Override
    public AttemptState sourceReceived(boolean lastOne) {
        return lastOne ? SettledAttemptState.evaluated() : awaitingSources();
    }

    @Override
    public AttemptState scored() {
        return SettledAttemptState.evaluated();
    }
}
