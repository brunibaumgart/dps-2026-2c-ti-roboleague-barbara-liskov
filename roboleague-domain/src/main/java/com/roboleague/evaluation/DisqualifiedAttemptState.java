package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * A disqualified attempt. It accepts no change: no result, no adjustment and no appeal.
 */
final class DisqualifiedAttemptState implements AttemptState {

    @Override
    public AttemptStatus status() {
        return AttemptStatus.DISQUALIFIED;
    }
}
