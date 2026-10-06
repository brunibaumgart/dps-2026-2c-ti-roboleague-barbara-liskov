package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;

/**
 * A scored attempt with no appeal open: evaluated as captured, or adjusted after a fault adjustment or an accepted
 * appeal. Both accept the same changes; only how they are shown differs.
 */
final class SettledAttemptState implements AttemptState {
    private final AttemptStatus status;

    private SettledAttemptState(AttemptStatus status) {
        this.status = status;
    }

    static SettledAttemptState evaluated() {
        return new SettledAttemptState(AttemptStatus.EVALUATED);
    }

    static SettledAttemptState adjusted() {
        return new SettledAttemptState(AttemptStatus.ADJUSTED);
    }

    @Override
    public AttemptStatus status() {
        return status;
    }

    @Override
    public AttemptState appealFiled() {
        return new UnderAppealAttemptState(1, this);
    }

    @Override
    public AttemptState faultsAdjusted() {
        return adjusted();
    }

    @Override
    public AttemptState disqualified() {
        return new DisqualifiedAttemptState();
    }
}
