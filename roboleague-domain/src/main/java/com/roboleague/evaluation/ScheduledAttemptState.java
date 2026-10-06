package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * An attempt that has not been scored yet. It only accepts its first result.
 */
final class ScheduledAttemptState implements AttemptState {

    @Override
    public AttemptStatus status() {
        return AttemptStatus.SCHEDULED;
    }

    @Override
    public AttemptState scored() {
        return SettledAttemptState.evaluated();
    }
}
